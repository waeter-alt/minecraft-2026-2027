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
     * Lowest priority, so the bank is recorded only if no other check refused the placement.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getPlacedBlock().getBlock() instanceof RegionalBankBlock)
              || !(event.getLevel() instanceof ServerLevel level)
              || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final BlockPos pos = event.getPos();
        final IColony colony = IColonyManager.getInstance().getIColony(level, pos);
        final String refusal = refusal(level, pos, colony, player);
        if (refusal != null)
        {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            return;
        }
        RegionalTreasury.get(level.getServer()).setBank(colony, pos);
        player.sendSystemMessage(Component.literal("Banque régionale de " + colony.getName() + " ouverte : le trésor de la colonie y est gardé.")
                                   .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Why this bank may not stand here, or null when it may.
     */
    private static String refusal(final ServerLevel level, final BlockPos pos, final IColony colony, final ServerPlayer player)
    {
        if (colony == null)
        {
            return "Une Banque régionale se construit dans le territoire d'une colonie.";
        }
        if (!player.isCreative() && !colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            return "Seuls les membres de " + colony.getName() + " peuvent y construire sa Banque régionale.";
        }
        final BlockPos existing = RegionalTreasury.get(level.getServer()).bank(colony);
        if (existing != null && !existing.equals(pos)
              && (!level.isLoaded(existing) || level.getBlockState(existing).getBlock() instanceof RegionalBankBlock))
        {
            return colony.getName() + " a déjà une Banque régionale (" + existing.getX() + " " + existing.getY() + " " + existing.getZ() + ").";
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (++ticks % UPDATE_INTERVAL != 0)
        {
            return;
        }
        final MinecraftServer server = event.getServer();
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
