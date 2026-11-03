package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import fr.kodistan.interregne.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Imperial Bank: one safe account per player and one treasury per colony, saved with the world.
 * Deposits are free, withdrawals cost a fee.
 * Bets, bounties and contracts will pay straight into these accounts, so winners never walk around with their gains.
 */
public final class ImperialBank extends SavedData
{
    private static final String NAME = "interregne_bank";

    private final Map<UUID, Long> accounts = new HashMap<>();

    public static ImperialBank get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ImperialBank::new, ImperialBank::load, null), NAME);
    }

    /**
     * The account of a colony's treasury, which the Intendance spends from.
     */
    public static UUID treasuryOf(final IColony colony)
    {
        return UUID.nameUUIDFromBytes(("interregne:treasury:" + colony.getDimension().location() + ":" + colony.getID()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The colony a player belongs to: the one they own, else the first one where they are a member.
     */
    @Nullable
    public static IColony colonyOf(final ServerPlayer player)
    {
        final IColony owned = IColonyManager.getInstance().getIColonyByOwner(player.level(), player);
        if (owned != null)
        {
            return owned;
        }
        return IColonyManager.getInstance().getColonies(player.level()).stream()
                 .filter(colony -> colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
                 .findFirst()
                 .orElse(null);
    }

    public long balance(final UUID player)
    {
        return accounts.getOrDefault(player, 0L);
    }

    /**
     * Add (or, when negative, remove) Couronnes; a balance never goes below zero.
     */
    public void credit(final UUID player, final long amount)
    {
        accounts.put(player, Math.max(0, balance(player) + amount));
        setDirty();
    }

    /**
     * The fee on a withdrawal of this amount: a share, at least one Couronne when the share is not zero.
     */
    public static long feeFor(final long amount)
    {
        // Rounded, with no minimum: small withdrawals stay free instead of costing a large share
        return Math.round(amount * Config.WITHDRAWAL_FEE.get());
    }

    /**
     * Spend from an account, e.g. the Intendance paying from a colony treasury.
     *
     * @return false, changing nothing, when the balance is too low.
     */
    public boolean spend(final UUID account, final long amount)
    {
        if (balance(account) < amount)
        {
            return false;
        }
        credit(account, -amount);
        return true;
    }

    /**
     * Take an amount plus its fee from the account.
     *
     * @return false, changing nothing, when the balance cannot cover both.
     */
    public boolean withdraw(final UUID player, final long amount)
    {
        final long total = amount + feeFor(amount);
        if (amount <= 0 || balance(player) < total)
        {
            return false;
        }
        credit(player, -total);
        return true;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final CompoundTag list = new CompoundTag();
        accounts.forEach((player, amount) -> list.putLong(player.toString(), amount));
        tag.put("accounts", list);
        return tag;
    }

    private static ImperialBank load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final ImperialBank bank = new ImperialBank();
        final CompoundTag list = tag.getCompound("accounts");
        for (final String key : list.getAllKeys())
        {
            bank.accounts.put(UUID.fromString(key), list.getLong(key));
        }
        return bank;
    }
}
