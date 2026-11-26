package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.ImperialBank;
import fr.kodistan.interregne.network.AssaultDeathPayload;
import fr.kodistan.interregne.politics.Alliances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dying during an assault: the wait before respawning grows with each death in that assault (8 s, then +4 s, up to
 * 30 s), the defender and the allies who came to help respawn at the defender's town hall, the attacker at their own
 * (until the siege banner exists), beds don't count, and the first 3 s after respawning are protected until the
 * player strikes.
 * <p>
 * The wait itself is held by the client's death screen ({@code AssaultDeathScreen}); the server only logs a respawn
 * that comes early, it doesn't refuse it.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class AssaultRespawn
{
    static final int FIRST_WAIT = 8;
    static final int WAIT_STEP  = 4;
    static final int WAIT_CAP   = 30;

    /** How far from the defender's town hall a death still counts as fought in the assault. */
    static final int ZONE = 192;

    static final int PROTECTION_TICKS = 60;

    private record Death(int defender, int count) {}

    private record Pending(long readyAt, ResourceKey<Level> dimension, BlockPos hall) {}

    private static final Map<UUID, Death>   DEATHS    = new HashMap<>();
    private static final Map<UUID, Pending> PENDING   = new HashMap<>();
    private static final Map<UUID, Long>    PROTECTED = new HashMap<>();

    private AssaultRespawn()
    {
    }

    /** Seconds to wait for the given death (1 = first) of one assault. */
    public static int waitSeconds(final int death)
    {
        return Math.min(WAIT_CAP, FIRST_WAIT + WAIT_STEP * (death - 1));
    }

    /** After the other listeners, so a death a totem or another mod cancels never counts. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(final LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled())
        {
            return;
        }
        final IColony own = ImperialBank.colonyOf(player);
        if (own == null)
        {
            return;
        }
        final MinecraftServer server = player.server;
        for (final IColony[] pair : WarAssaults.running())
        {
            final IColony attacker = pair[0];
            final IColony defender = pair[1];
            if (!defender.getDimension().equals(player.level().dimension())
                  || player.blockPosition().distSqr(defender.getCenter()) > (double) ZONE * ZONE)
            {
                continue;
            }
            final IColony home;
            final String where;
            if (own.getID() == defender.getID())
            {
                home = defender;
                where = "votre hôtel de ville";
            }
            else if (own.getID() == attacker.getID())
            {
                home = attacker;
                where = "votre hôtel de ville";
            }
            else if (Alliances.get(server).areAllied(own.getID(), defender.getID()))
            {
                home = defender;
                where = "l'hôtel de ville de " + defender.getName();
            }
            else
            {
                continue;
            }
            final Death previous = DEATHS.get(player.getUUID());
            final int count = previous != null && previous.defender() == defender.getID() ? previous.count() + 1 : 1;
            DEATHS.put(player.getUUID(), new Death(defender.getID(), count));
            final int seconds = waitSeconds(count);
            PENDING.put(player.getUUID(), new Pending(now(server) + seconds * 20L, home.getDimension(), home.getCenter()));
            PacketDistributor.sendToPlayer(player, new AssaultDeathPayload(seconds * 20, count, where, defender.getCenter()));
            InterregneMod.LOGGER.info("[war] {} tombé ({}e mort de l'assaut sur {}) : attente {} s", player.getName().getString(), count,
              defender.getName(), seconds);
            return;
        }
    }

    @SubscribeEvent
    public static void onRespawnPosition(final PlayerRespawnPositionEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final Pending pending = PENDING.remove(player.getUUID());
        if (pending == null)
        {
            return;
        }
        final ServerLevel level = player.server.getLevel(pending.dimension());
        if (level == null)
        {
            return;
        }
        final long now = now(player.server);
        // One second of slack for the round trip
        if (now + 20 < pending.readyAt())
        {
            InterregneMod.LOGGER.warn("[war] {} réapparaît {} s avant la fin de son attente", player.getName().getString(),
              (pending.readyAt() - now) / 20);
        }
        final DimensionTransition original = event.getDimensionTransition();
        event.setDimensionTransition(new DimensionTransition(level, standNear(level, pending.hall()), Vec3.ZERO, original.yRot(), original.xRot(),
          DimensionTransition.DO_NOTHING));
        PROTECTED.put(player.getUUID(), now + PROTECTION_TICKS);
    }

    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event)
    {
        if (PROTECTED.isEmpty())
        {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer striker)
        {
            PROTECTED.remove(striker.getUUID());
        }
        if (event.getEntity() instanceof ServerPlayer player)
        {
            final Long until = PROTECTED.get(player.getUUID());
            if (until != null && now(player.server) < until)
            {
                event.setCanceled(true);
            }
        }
    }

    /** Forgets the deaths of assaults that are over, so the next assault starts back at 8 s. */
    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        if (server.getTickCount() % 200 != 0 || DEATHS.isEmpty() && PROTECTED.isEmpty())
        {
            return;
        }
        final Set<Integer> defenders = new HashSet<>();
        for (final IColony[] pair : WarAssaults.running())
        {
            defenders.add(pair[1].getID());
        }
        DEATHS.values().removeIf(death -> !defenders.contains(death.defender()));
        final long now = now(server);
        PROTECTED.values().removeIf(until -> until < now);
    }

    private static long now(final MinecraftServer server)
    {
        return server.overworld().getGameTime();
    }

    /**
     * A free spot to stand on near the town hall's hut block, closest rings first, so the player lands in or right by
     * the town hall rather than on its roof; the top of the column above the hut if nothing fits.
     */
    private static Vec3 standNear(final ServerLevel level, final BlockPos hall)
    {
        for (int r = 2; r <= 10; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                    {
                        continue;
                    }
                    for (final int dy : new int[] {0, -1, 1, -2, 2, 3})
                    {
                        final BlockPos pos = hall.offset(dx, dy, dz);
                        if (standable(level, pos))
                        {
                            return Vec3.atBottomCenterOf(pos);
                        }
                    }
                }
            }
        }
        return Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall));
    }

    private static boolean standable(final ServerLevel level, final BlockPos pos)
    {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                 && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()
                 && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }
}
