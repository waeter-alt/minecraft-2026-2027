package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.ColonyEntryPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a player's client when they walk into a colony (theirs or anyone's), so it can show the colony's sign.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ColonyEntry
{
    private static final int CHECK_INTERVAL = 20;
    /**
     * Walking out and straight back in (a border, a step across) does not show the sign again within this time.
     */
    private static final long REPEAT_TICKS = 30 * 20L;

    private static final class Presence
    {
        @Nullable
        String current;
        @Nullable
        String lastShown;
        long lastShownAt;
    }

    private static final Map<UUID, Presence> PLAYERS = new HashMap<>();

    private ColonyEntry()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0)
        {
            return;
        }
        final RegionData data = RegionData.get(event.getServer());
        for (final ServerPlayer player : event.getServer().getPlayerList().getPlayers())
        {
            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.serverLevel(), player.blockPosition());
            final Presence presence = PLAYERS.computeIfAbsent(player.getUUID(), id -> new Presence());
            if (colony == null)
            {
                presence.current = null;
                continue;
            }
            final String key = RegionData.key(colony);
            if (key.equals(presence.current))
            {
                continue;
            }
            presence.current = key;
            final long now = player.serverLevel().getGameTime();
            if (key.equals(presence.lastShown) && now - presence.lastShownAt < REPEAT_TICKS)
            {
                continue;
            }
            final Crate crate = data.regionOf(colony);
            if (crate == null)
            {
                // Region not given yet: the next check shows the sign
                presence.current = null;
                continue;
            }
            presence.lastShown = key;
            presence.lastShownAt = now;
            InterregneMod.LOGGER.info("Colony entry: {} enters {} ({})", player.getName().getString(), colony.getName(), crate.id());
            PacketDistributor.sendToPlayer(player, new ColonyEntryPayload(colony.getName(), crate.id()));
        }
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        PLAYERS.remove(event.getEntity().getUUID());
    }
}
