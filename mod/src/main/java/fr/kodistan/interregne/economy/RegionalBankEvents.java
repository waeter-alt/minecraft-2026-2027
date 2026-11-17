package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.TreasurySyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Server side of the regional banks: who may place one, and keeping vaults and clients in step with the treasuries.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class RegionalBankEvents
{
    private static final int UPDATE_INTERVAL = 20;

    /**
     * Vaults whose chunk was not loaded when their treasury changed, retried until it is.
     */
    private static final Map<String, RegionalTreasury.Vault> PENDING = new HashMap<>();

    private static int ticks;

    private RegionalBankEvents()
    {
    }

    /**
     * The treasury follows the colony's built bank: its level is the building's, and its position is where the vault stands.
     * A bank that is gone leaves no vault behind.
     */
    private static void followBuildings(final MinecraftServer server)
    {
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final BuildingBanqueRegionale bank = RegionalBank.of(colony);
            final BlockPos recorded = treasury.bank(colony);
            if (bank != null)
            {
                treasury.setLevel(colony, bank.getBuildingLevel());
                if (!bank.getPosition().equals(recorded))
                {
                    treasury.setBank(colony, bank.getPosition());
                }
            }
            else if (recorded != null && colony.getWorld() instanceof ServerLevel level && level.isLoaded(recorded))
            {
                Vault.clear(level, recorded);
                treasury.setBank(colony, null);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (++ticks % UPDATE_INTERVAL != 0)
        {
            return;
        }
        final MinecraftServer server = event.getServer();
        followBuildings(server);
        for (final RegionalTreasury.Vault vault : RegionalTreasury.get(server).drainChanged())
        {
            PENDING.put(vault.colonyKey(), vault);
            sync(server, vault);
        }
        PENDING.values().removeIf(vault -> refreshVault(server, vault));
    }

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final RegionalTreasury treasury = RegionalTreasury.get(player.server);
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
            {
                PacketDistributor.sendToPlayer(player, new TreasurySyncPayload(RegionalTreasury.keyOf(colony), treasury.balance(colony)));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        PENDING.clear();
    }

    /**
     * Lay out the vault's decorations once its chunk is loaded.
     *
     * @return true when done (or nothing to do), false to retry later.
     */
    private static boolean refreshVault(final MinecraftServer server, final RegionalTreasury.Vault vault)
    {
        final ServerLevel level = server.getLevel(vault.dimension());
        if (level == null || vault.bank() == null)
        {
            return true;
        }
        if (!level.isLoaded(vault.bank()))
        {
            return false;
        }
        if (level.getBlockState(vault.bank()).getBlock() instanceof RegionalBankBlock)
        {
            Vault.refresh(level, vault.bank(), vault.balance(), vault.capacity());
        }
        return true;
    }

    /**
     * Tell the colony's online members the new balance, for the hut build window.
     */
    private static void sync(final MinecraftServer server, final RegionalTreasury.Vault vault)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByDimension(vault.colonyId(), vault.dimension());
        if (colony == null)
        {
            return;
        }
        final TreasurySyncPayload payload = new TreasurySyncPayload(vault.colonyKey(), vault.balance());
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
            {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
