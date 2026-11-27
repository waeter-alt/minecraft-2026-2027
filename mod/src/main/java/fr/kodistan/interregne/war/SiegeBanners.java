package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.ColonyColors;
import fr.kodistan.interregne.economy.ImperialBank;
import fr.kodistan.interregne.network.SiegeBannerSyncPayload;
import fr.kodistan.interregne.politics.Alliances;
import fr.kodistan.interregne.politics.Diplomacy;
import fr.kodistan.interregne.routes.RouteRegistry;
import fr.kodistan.interregne.walls.Walls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The attacker's siege banner: planted on its own when an assault starts, where the war road comes within
 * {@code siegeBannerDistance} blocks of the defender's border, on flat dry ground outside every colony. While it
 * stands, the attackers who die in the assault come back to life at its foot instead of at home.
 * <p>
 * It falls by being held, never broken: defenders (and the defender's allies) standing in its ring with no attacker
 * there bring its flag down, about {@code siegeBannerCaptureSeconds} alone, faster with company (capped at 3); a single
 * attacker in the ring freezes the taking, and an empty ring slowly gives it back. Once down it stays down for the
 * rest of the assault (it cannot be planted again). Lives in memory, like the assault itself.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class SiegeBanners
{
    private static final int STEP = 10;
    private static final int FALLEN_SHOWN = 60;
    private static final int CAP = 3;

    private static final class Banner
    {
        final int attacker;
        final int defender;
        final BlockPos pos;
        final float yaw;
        final String attackerName;
        final String defenderName;
        final int color;
        float progress;
        byte state = SiegeBannerSyncPayload.STANDING;
        /** Set by the test command: the taking stays as it is, whoever stands in the ring. */
        boolean held;
        long fallenAt = -1;
        @Nullable UUID entity;

        Banner(final IColony attacker, final IColony defender, final BlockPos pos, final float yaw)
        {
            this.attacker = attacker.getID();
            this.defender = defender.getID();
            this.pos = pos;
            this.yaw = yaw;
            this.attackerName = attacker.getName();
            this.defenderName = defender.getName();
            this.color = ColonyColors.rgb(attacker);
        }

        boolean fallen()
        {
            return state == SiegeBannerSyncPayload.FALLEN;
        }
    }

    private static final Map<Integer, Banner> BY_DEFENDER = new HashMap<>();
    private static boolean dirty;

    private SiegeBanners()
    {
    }

    /** Plants the attacker's banner for the assault that just started on {@code defender}. */
    public static void plant(final ServerLevel level, final IColony attacker, final IColony defender, @Nullable final BlockPos rally)
    {
        final BlockPos spot = pickSpot(level, attacker, defender, rally);
        if (spot == null)
        {
            InterregneMod.LOGGER.warn("[war] aucun emplacement pour l'étendard de {} devant {}", attacker.getName(), defender.getName());
            return;
        }
        final double dx = defender.getCenter().getX() - spot.getX();
        final double dz = defender.getCenter().getZ() - spot.getZ();
        final float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        BY_DEFENDER.put(defender.getID(), new Banner(attacker, defender, spot, yaw));
        dirty = true;
        final String where = " (" + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + ")";
        Diplomacy.notifyMembers(level.getServer(), attacker, "⚑ Votre étendard de siège est planté devant " + defender.getName() + where
          + " : vos combattants tombés y réapparaissent tant qu'il tient.");
        Diplomacy.notifyMembers(level.getServer(), defender, "⚑ " + attacker.getName() + " plante son étendard de siège devant la colonie" + where
          + " : tenez son cercle pour l'abattre.");
    }

    /** Where an attacker of this assault comes back to life, or null when its banner is down (or there is none). */
    @Nullable
    public static BlockPos respawnFor(final int defenderId)
    {
        final Banner banner = BY_DEFENDER.get(defenderId);
        return banner == null || banner.fallen() ? null : banner.pos;
    }

    static boolean owns(final ServerLevel level, final SiegeBannerEntity entity)
    {
        for (final Banner banner : BY_DEFENDER.values())
        {
            if (entity.getUUID().equals(banner.entity) && !banner.fallen())
            {
                return true;
            }
        }
        return false;
    }

    // --- Placement ---

    @Nullable
    private static BlockPos pickSpot(final ServerLevel level, final IColony attacker, final IColony defender, @Nullable final BlockPos rally)
    {
        final int distance = Config.SIEGE_BANNER_DISTANCE.get();
        BlockPos base = null;
        int sideX = 0;
        int sideZ = 0;
        final BlockPos[] road = RouteRegistry.get(level.getServer()).pathBetween(attacker.getID(), defender.getID());
        if (road != null && road.length > 1)
        {
            // The road runs from the attacker to the defender: back up from its end to the first block outside the
            // defender's land (claims are only known in loaded chunks, and the end is where the few needed ones are)
            int border = road.length - 1;
            for (int i = road.length - 1; i >= 0; i--)
            {
                level.getChunkAt(road[i]);
                if (!defender.isCoordInColony(level, road[i]))
                {
                    border = i;
                    break;
                }
            }
            final int at = Math.max(0, border - distance);
            base = road[at];
            final BlockPos next = road[Math.min(road.length - 1, at + 1)];
            final BlockPos prev = road[Math.max(0, at - 1)];
            // Beside the road, not on it: a step to its left
            sideX = -(next.getZ() - prev.getZ());
            sideZ = next.getX() - prev.getX();
        }
        if (base == null)
        {
            final BlockPos from = rally != null ? rally : attacker.getCenter();
            final double dx = from.getX() - defender.getCenter().getX();
            final double dz = from.getZ() - defender.getCenter().getZ();
            final double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            final double reach = Walls.COLONY_RADIUS * 16 + distance;
            base = new BlockPos((int) (defender.getCenter().getX() + dx / len * reach), 0, (int) (defender.getCenter().getZ() + dz / len * reach));
            sideX = (int) Math.round(-dz / len);
            sideZ = (int) Math.round(dx / len);
        }
        final double side = Math.max(1, Math.sqrt(sideX * sideX + sideZ * sideZ));
        final int cx = base.getX() + (int) Math.round(sideX / side * 11);
        final int cz = base.getZ() + (int) Math.round(sideZ / side * 11);
        for (int r = 0; r <= 12; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                    {
                        continue;
                    }
                    final BlockPos found = flatSpot(level, cx + dx, cz + dz);
                    if (found != null)
                    {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /** The banner's 3x3 footprint on sturdy, dry ground, one block of slope at most, outside every colony. */
    @Nullable
    private static BlockPos flatSpot(final ServerLevel level, final int x, final int z)
    {
        level.getChunkAt(new BlockPos(x, 0, z));
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                final int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                final BlockPos ground = new BlockPos(x + dx, h - 1, z + dz);
                if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()
                      || !level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP))
                {
                    return null;
                }
                low = Math.min(low, h);
                high = Math.max(high, h);
            }
        }
        if (high - low > 1)
        {
            return null;
        }
        final BlockPos pos = new BlockPos(x, high, z);
        if (IColonyManager.getInstance().getIColony(level, pos) != null)
        {
            return null;
        }
        return pos;
    }

    // --- Taking ---

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        if (server.getTickCount() % STEP != 0 || BY_DEFENDER.isEmpty() && !dirty)
        {
            return;
        }
        final ServerLevel level = server.overworld();
        final Set<Integer> running = new HashSet<>();
        for (final IColony[] pair : WarAssaults.running())
        {
            running.add(pair[1].getID());
        }
        final long now = level.getGameTime();
        final Iterator<Banner> it = BY_DEFENDER.values().iterator();
        while (it.hasNext())
        {
            final Banner banner = it.next();
            if (!running.contains(banner.defender) || banner.fallen() && now - banner.fallenAt > FALLEN_SHOWN)
            {
                removeEntity(level, banner);
                it.remove();
                dirty = true;
                continue;
            }
            if (banner.fallen())
            {
                continue;
            }
            if (!banner.held)
            {
                step(level, banner, now);
            }
            if (!banner.fallen())
            {
                keepEntity(level, banner);
            }
        }
        if (dirty)
        {
            dirty = false;
            PacketDistributor.sendToAllPlayers(snapshot());
        }
    }

    private static void step(final ServerLevel level, final Banner banner, final long now)
    {
        final int radius = Config.SIEGE_BANNER_RADIUS.get();
        int defenders = 0;
        int attackers = 0;
        final Alliances alliances = Alliances.get(level.getServer());
        for (final ServerPlayer player : level.players())
        {
            if (!player.isAlive() || player.isSpectator() || !inRing(player, banner.pos, radius))
            {
                continue;
            }
            final IColony own = ImperialBank.colonyOf(player);
            if (own == null)
            {
                continue;
            }
            if (own.getID() == banner.attacker)
            {
                attackers++;
            }
            else if (own.getID() == banner.defender || alliances.areAllied(own.getID(), banner.defender))
            {
                defenders++;
            }
        }
        if (attackers == 0)
        {
            final AABB ring = new AABB(banner.pos).inflate(radius, 4, radius);
            attackers += level.getEntitiesOfClass(AssaultTroop.class, ring, t -> t.isAlive() && inRing(t, banner.pos, radius)).size();
        }
        final float seconds = STEP / 20F;
        final float before = banner.progress;
        final byte state;
        if (defenders > 0 && attackers > 0)
        {
            state = SiegeBannerSyncPayload.CONTESTED;
        }
        else if (defenders > 0)
        {
            state = SiegeBannerSyncPayload.CAPTURING;
            banner.progress = Math.min(1F, banner.progress + seconds * Math.min(defenders, CAP) / Config.SIEGE_BANNER_CAPTURE_SECONDS.get());
        }
        else
        {
            state = SiegeBannerSyncPayload.STANDING;
            banner.progress = Math.max(0F, banner.progress - seconds / (Config.SIEGE_BANNER_CAPTURE_SECONDS.get() * 3F));
        }
        if (state != banner.state || before != banner.progress)
        {
            banner.state = state;
            dirty = true;
        }
        if (banner.progress >= 1F)
        {
            fall(level, banner, now);
        }
    }

    private static boolean inRing(final Entity entity, final BlockPos center, final int radius)
    {
        final double dx = entity.getX() - (center.getX() + 0.5);
        final double dz = entity.getZ() - (center.getZ() + 0.5);
        return dx * dx + dz * dz <= radius * radius && Math.abs(entity.getY() - center.getY()) <= 4;
    }

    private static void fall(final ServerLevel level, final Banner banner, final long now)
    {
        banner.state = SiegeBannerSyncPayload.FALLEN;
        banner.progress = 1F;
        banner.fallenAt = now;
        dirty = true;
        removeEntity(level, banner);
        level.playSound(null, banner.pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64F, 0.7F);
        final MinecraftServer server = level.getServer();
        final IColony attacker = colony(level, banner.attacker);
        final IColony defender = colony(level, banner.defender);
        if (attacker != null)
        {
            Diplomacy.notifyMembers(server, attacker, "§c⚑ Votre étendard de siège est tombé§r : vos combattants réapparaîtront chez vous.");
        }
        if (defender != null)
        {
            Diplomacy.notifyMembers(server, defender, "§a⚑ L'étendard de siège de " + banner.attackerName + " est tombé !");
        }
        InterregneMod.LOGGER.info("[war] étendard de {} abattu devant {}", banner.attackerName, banner.defenderName);
    }

    // --- The entity ---

    private static void keepEntity(final ServerLevel level, final Banner banner)
    {
        if (!level.isPositionEntityTicking(banner.pos))
        {
            return;
        }
        Entity entity = banner.entity != null ? level.getEntity(banner.entity) : null;
        if (!(entity instanceof SiegeBannerEntity))
        {
            final SiegeBannerEntity created = WarEntities.SIEGE_BANNER.get().create(level);
            if (created == null)
            {
                return;
            }
            created.moveTo(banner.pos.getX() + 0.5, banner.pos.getY(), banner.pos.getZ() + 0.5, banner.yaw, 0F);
            created.setYRot(banner.yaw);
            banner.entity = created.getUUID();
            created.show(banner.progress, banner.color);
            level.addFreshEntity(created);
            entity = created;
        }
        ((SiegeBannerEntity) entity).show(banner.progress, banner.color);
    }

    private static void removeEntity(final ServerLevel level, final Banner banner)
    {
        if (banner.entity != null)
        {
            final Entity entity = level.getEntity(banner.entity);
            if (entity != null)
            {
                entity.discard();
            }
            banner.entity = null;
        }
    }

    // --- Sync ---

    private static SiegeBannerSyncPayload snapshot()
    {
        final List<SiegeBannerSyncPayload.Entry> list = new ArrayList<>();
        final int radius = Config.SIEGE_BANNER_RADIUS.get();
        for (final Banner b : BY_DEFENDER.values())
        {
            list.add(new SiegeBannerSyncPayload.Entry(b.defender, b.pos, b.color, b.progress, b.state, radius, b.attackerName, b.defenderName));
        }
        return new SiegeBannerSyncPayload(list);
    }

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && !BY_DEFENDER.isEmpty())
        {
            PacketDistributor.sendToPlayer(player, snapshot());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        final ServerLevel level = event.getServer().overworld();
        for (final Banner banner : BY_DEFENDER.values())
        {
            removeEntity(level, banner);
        }
        BY_DEFENDER.clear();
        dirty = false;
    }

    @Nullable
    private static IColony colony(final ServerLevel level, final int id)
    {
        return IColonyManager.getInstance().getColonyByWorld(id, level);
    }

    /**
     * Test command: freezes the banner in front of {@code defenderId} at this taking and state (to look at each look of
     * the ring), or, with a negative percentage, lets it run again. State 3 brings it down at once.
     */
    public static String hold(final ServerLevel level, final int defenderId, final int percent, final byte state)
    {
        final Banner b = BY_DEFENDER.get(defenderId);
        if (b == null || b.fallen())
        {
            return "aucun étendard debout";
        }
        if (percent < 0)
        {
            b.held = false;
            return "capture rendue au jeu";
        }
        b.progress = Math.min(100, percent) / 100F;
        if (state == SiegeBannerSyncPayload.FALLEN)
        {
            fall(level, b, level.getGameTime());
            return "étendard abattu";
        }
        b.held = true;
        b.state = state;
        dirty = true;
        return describe(defenderId) + " (figé)";
    }

    /** For the dev command and the tests: the banner standing in front of {@code defenderId}, as words. */
    public static String describe(final int defenderId)
    {
        final Banner b = BY_DEFENDER.get(defenderId);
        if (b == null)
        {
            return "aucun étendard";
        }
        return "étendard de " + b.attackerName + " en " + b.pos.toShortString() + ", état " + b.state + ", capture " + Math.round(b.progress * 100) + " %";
    }
}
