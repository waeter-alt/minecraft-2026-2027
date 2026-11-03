package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The regional treasuries: one per colony, kept at the colony's Banque régionale and saved with the world.
 * Constructions and the Intendance spend from it; its capacity grows with the bank's level.
 * The treasury belongs to the colony, not to the bank block: breaking and replacing the bank keeps the money and the level.
 */
public final class RegionalTreasury extends SavedData
{
    private static final String NAME = "interregne_regional_treasury";

    public static final int MAX_LEVEL = 5;

    /**
     * One colony's treasury.
     */
    private static final class Entry
    {
        private final String dimension;
        private long balance;
        private int level = 1;
        @Nullable
        private BlockPos bank;

        private Entry(final String dimension)
        {
            this.dimension = dimension;
        }
    }

    private final Map<String, Entry> treasuries = new HashMap<>();

    /**
     * Colonies whose vault decorations and client copies must be refreshed. Not saved: a restart has nothing to refresh.
     */
    private final Set<String> changed = new LinkedHashSet<>();

    /**
     * Set when loaded, to migrate the old treasuries kept in the Imperial Bank.
     */
    private MinecraftServer server;

    public static RegionalTreasury get(final MinecraftServer server)
    {
        final RegionalTreasury treasury = server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RegionalTreasury::new, RegionalTreasury::load, null), NAME);
        treasury.server = server;
        return treasury;
    }

    /**
     * The key of a colony: colony ids are only unique within a dimension.
     */
    public static String keyOf(final IColony colony)
    {
        return keyOf(colony.getDimension(), colony.getID());
    }

    /**
     * Same key from a colony's dimension and id, as the client knows them from a colony view.
     */
    public static String keyOf(final ResourceKey<Level> dimension, final int colonyId)
    {
        return dimension.location() + "|" + colonyId;
    }

    /**
     * Capacity of a bank of this level, from the config.
     */
    public static long capacityFor(final int level)
    {
        return Config.BANK_CAPACITY.get(Math.clamp(level, 1, MAX_LEVEL) - 1).get();
    }

    /**
     * Price of upgrading a bank to this level (2 to 5).
     */
    public static long upgradeCostTo(final int level)
    {
        return Config.BANK_UPGRADE_COST.get(Math.clamp(level, 2, MAX_LEVEL) - 2).get();
    }

    public long balance(final IColony colony)
    {
        return entry(colony).balance;
    }

    public int level(final IColony colony)
    {
        return entry(colony).level;
    }

    public long capacity(final IColony colony)
    {
        return capacityFor(level(colony));
    }

    /**
     * How much can still be deposited before the vault is full.
     */
    public long room(final IColony colony)
    {
        return Math.max(0, capacity(colony) - balance(colony));
    }

    /**
     * Add Couronnes without checking the capacity: refunds and migrations must never be lost.
     * Players' deposits check {@link #room(IColony)} first.
     */
    public void credit(final IColony colony, final long amount)
    {
        final Entry entry = entry(colony);
        entry.balance = Math.max(0, entry.balance + amount);
        changed(colony);
    }

    /**
     * Spend from the treasury.
     *
     * @return false, changing nothing, when the balance is too low.
     */
    public boolean spend(final IColony colony, final long amount)
    {
        if (amount < 0 || balance(colony) < amount)
        {
            return false;
        }
        if (amount > 0)
        {
            credit(colony, -amount);
        }
        return true;
    }

    /**
     * Raise the bank one level; the caller has checked and paid the price.
     */
    public void upgrade(final IColony colony)
    {
        final Entry entry = entry(colony);
        entry.level = Math.min(MAX_LEVEL, entry.level + 1);
        changed(colony);
    }

    /**
     * Where the colony's bank stands, or null when it has none.
     */
    @Nullable
    public BlockPos bank(final IColony colony)
    {
        return entry(colony).bank;
    }

    public void setBank(final IColony colony, @Nullable final BlockPos pos)
    {
        entry(colony).bank = pos == null ? null : pos.immutable();
        changed(colony);
    }

    /**
     * Forget a bank that was removed, unless the colony's bank record already points elsewhere.
     */
    public void forgetBank(final ResourceKey<Level> dimension, final BlockPos pos)
    {
        final String prefix = dimension.location() + "|";
        treasuries.forEach((key, entry) -> {
            if (key.startsWith(prefix) && pos.equals(entry.bank))
            {
                entry.bank = null;
                setDirty();
            }
        });
    }

    /**
     * A bank still to refresh: where it stands, how full it is.
     */
    public record Vault(String colonyKey, ResourceKey<Level> dimension, int colonyId, @Nullable BlockPos bank, long balance, long capacity)
    {
    }

    /**
     * Take the treasuries changed since the last call.
     */
    public List<Vault> drainChanged()
    {
        final List<Vault> vaults = new ArrayList<>();
        for (final String key : changed)
        {
            final Entry entry = treasuries.get(key);
            if (entry != null)
            {
                vaults.add(new Vault(key, ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.dimension)),
                  Integer.parseInt(key.substring(key.lastIndexOf('|') + 1)), entry.bank, entry.balance, capacityFor(entry.level)));
            }
        }
        changed.clear();
        return vaults;
    }

    private void changed(final IColony colony)
    {
        changed.add(keyOf(colony));
        setDirty();
    }

    /**
     * The colony's treasury, created on first use. Treasuries used to live in the Imperial Bank: their balance moves here once.
     */
    private Entry entry(final IColony colony)
    {
        final String key = keyOf(colony);
        Entry entry = treasuries.get(key);
        if (entry == null)
        {
            entry = new Entry(colony.getDimension().location().toString());
            treasuries.put(key, entry);
            if (server != null)
            {
                final ImperialBank bank = ImperialBank.get(server);
                final long legacy = bank.balance(ImperialBank.treasuryOf(colony));
                if (legacy > 0)
                {
                    entry.balance = legacy;
                    bank.credit(ImperialBank.treasuryOf(colony), -legacy);
                    changed.add(key);
                }
            }
            setDirty();
        }
        return entry;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final CompoundTag list = new CompoundTag();
        treasuries.forEach((key, entry) -> {
            final CompoundTag one = new CompoundTag();
            one.putString("dimension", entry.dimension);
            one.putLong("balance", entry.balance);
            one.putInt("level", entry.level);
            if (entry.bank != null)
            {
                one.put("bank", NbtUtils.writeBlockPos(entry.bank));
            }
            list.put(key, one);
        });
        tag.put("treasuries", list);
        return tag;
    }

    private static RegionalTreasury load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final RegionalTreasury treasury = new RegionalTreasury();
        final CompoundTag list = tag.getCompound("treasuries");
        for (final String key : list.getAllKeys())
        {
            final CompoundTag one = list.getCompound(key);
            final Entry entry = new Entry(one.getString("dimension"));
            entry.balance = one.getLong("balance");
            entry.level = Math.clamp(one.getInt("level"), 1, MAX_LEVEL);
            entry.bank = NbtUtils.readBlockPos(one, "bank").orElse(null);
            treasury.treasuries.put(key, entry);
        }
        return treasury;
    }
}
