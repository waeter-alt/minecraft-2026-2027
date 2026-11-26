package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.util.Tuple;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.BuildContext;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.WarRebuildPayload;
import fr.kodistan.interregne.network.WarRuinsSyncPayload;
import fr.kodistan.interregne.pvp.SafeZones;
import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.walls.WallLevels;
import fr.kodistan.interregne.walls.WallRegistry;
import fr.kodistan.interregne.walls.WallTrace;
import fr.kodistan.interregne.walls.Walls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.politics.Alliances;
import fr.kodistan.interregne.politics.Diplomacy;
import fr.kodistan.interregne.politics.Wars;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import fr.kodistan.interregne.garrison.Garrison;
import org.jetbrains.annotations.Nullable;

/**
 * One assault at a time per defender colony. The manager owns the troops' assignments and the buildings' structure
 * points; the troops ({@link AssaultGoal}) drive everything tick by tick and call back on every hit.
 * <p>
 * Slice shortcuts, on purpose: state is memory-only (a reload leaves surviving troops idle), the sweep on a
 * building's death is instant and container contents vanish, loot is a flat treasury credit to the attacker.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class WarAssaults
{
    /** Structure points of a building: hits needed to bring it down, by level. */
    private static final int SP_BASE  = 15;
    private static final int SP_LEVEL = 10;

    /** Chance a non-foundation block survives the final sweep: a ruin, not a swept plot. */
    private static final float RUIN_SURVIVAL_CHANCE = 0.3F;

    /** Health-bar visibility radius, matching the convention used by the other site boss bars. */
    private static final double BAR_VIEW_DISTANCE_SQ = 48.0D * 48.0D;

    /** Crack overlay is capped to this many blocks regardless of the building's size — a 300-block manor and a
      * 20-block hut cost the same to animate; past this count the sample just gets sparser, not slower. */
    private static final int MAX_CRACK_BLOCKS = 32;

    /** Final collapse is swept this many blocks per server tick instead of in one go, so a big building falling
      * doesn't stall the tick (and every client's frame) in a single instant. */
    private static final int SWEEP_BLOCKS_PER_TICK = 8;

    /** Minimum ticks between full hit-effect bursts (bar, particles, crack overlay) on the same building — the
      * cost of a siege was scaling with the number of attacking troops, not with the building; this bounds it per
      * building instead, regardless of how many troops are landing blows on it at once. Comfortably under a single
      * troop's own battering rhythm (see {@link AssaultGoal#BATTER_EVERY}), so a lone attacker never notices it. */
    private static final int EFFECT_COOLDOWN_TICKS = 4;

    // Combat rules validated 2026-11-25 (mod/docs/assaut.md). Starting values, to revisit at balancing.
    /** A fighting guard this close (horizontal) to the targeted building freezes its structure points. */
    private static final double GUARD_HOLD_RADIUS = 24.0D;
    /** A guard counts as fighting this long after its last blow exchanged with a troop: a guard walled in or out of
      * reach never fights, so it never holds a building. */
    private static final long GUARD_FIGHT_WINDOW = 20L * 10;
    private static final long CLOCK_START = 20L * 60 * 8;
    private static final long CLOCK_CAP = 20L * 60 * 12;
    private static final long CLOCK_PER_STRUCTURE = 20L * 90;
    private static final long CLOCK_PER_GUARD = 20L * 30;
    /** A troop that gets hit calls the troops of its assault this close to it. */
    private static final double ALERT_RADIUS = 16.0D;

    private static final Map<Integer, Assault> BY_DEFENDER = new HashMap<>();

    /** Paced final sweeps in progress, drained a few blocks per tick by {@link #onServerTick}. */
    private static final List<SweepJob> PENDING_SWEEPS = new ArrayList<>();

    /** A building's collapse, already decided (loot paid, marked ruined) — only the world edit itself is paced. */
    private static final class SweepJob
    {
        final ServerLevel   level;
        final Deque<BlockPos> remaining;

        SweepJob(final ServerLevel level, final List<BlockPos> blocks)
        {
            this.level = level;
            this.remaining = new ArrayDeque<>(blocks);
        }
    }

    /** Round-robin cursor per colony: each call to pickTarget hands out the next surviving building. */
    private static final Map<Integer, Integer> TARGET_CURSOR = new HashMap<>();


    private static final class Assault
    {
        final IColony        defender;
        final IColony        attacker;
        final UUID           commander;
        final List<Integer>  troopIds   = new ArrayList<>();
        final List<BlockPos> targets    = new ArrayList<>();
        final Map<BlockPos, Integer> structure = new HashMap<>();
        final Map<BlockPos, Integer> spTotal   = new HashMap<>();
        // Keyed by troop id, not building: several troops share one target now, so this is what keeps them from
        // all scanning into the exact same wall column (see occupiedStands/claimStand, used by siegeSpot).
        final Map<Integer, BlockPos> standByTroop = new HashMap<>();
        final Map<BlockPos, ServerBossEvent> bars = new HashMap<>();
        // Computed once, on first use, from the live world — nothing removes any of a building's blocks before
        // its final sweep, so re-scanning the whole volume on every single blow (the previous behaviour) bought
        // nothing but cost: it's the same list every time, just paid for again and again.
        final Map<BlockPos, List<BlockPos>> footprints    = new HashMap<>();
        final Map<BlockPos, List<BlockPos>> crackSamples  = new HashMap<>();
        final Map<BlockPos, Long>           lastEffectTick = new HashMap<>();
        // Set in planTargets when the defender has a wall, cleared once it's breached — assignedTarget always
        // hands this out first (see WallBreach), so no légionnaire or archer ever reaches a building target while
        // it's still standing.
        WallBreach wallBreach;
        // Troops that came out of the attacker's garrison, by entity id: the living ones go back to it when the
        // assault ends, the dead ones are lost.
        final Map<Integer, Marched> marched = new HashMap<>();
        int sent;
        int destroyed;
        long loot;
        /** Defending citizens killed while the assault ran. */
        int fallen;
        boolean engaged;
        boolean over;
        /** Set when the defender's town hall falls; the war is settled on the next server tick. */
        boolean townHallTaken;
        /** A town hall in ruins is taken, not battered: where it stands and how far the taking went (see {@link RuinCapture}). */
        BlockPos ruinHall;
        RuinCapture capture;
        /** Defender guards by entity id: game time of their last blow exchanged with a troop. */
        final Map<Integer, Long> guardFights = new HashMap<>();
        /** Troops fallen so far, and how many of them a defender guard killed. */
        int troopsDead;
        int guardKills;
        /** Where the troops spawned: the targets are sorted by distance to it. */
        BlockPos rallyPoint;
        /** Game time at which the assault is pushed back if it hasn't won. */
        long deadline;
        ServerBossEvent clock;

        Assault(final IColony defender, final IColony attacker, final UUID commander)
        {
            this.defender = defender;
            this.attacker = attacker;
            this.commander = commander;
        }
    }

    /**
     * Where the colony's wall gets breached this assault: a point on the trace, the wall's own running direction
     * there ({@code alongX,alongZ}, one of the 8 compass directions — the trace is 45°-snapped) and which way is
     * outward, away from the colony ({@code outX,outZ}, the perpendicular). Everything about the breach — its
     * footprint, the troops' stand slots along it, the stand/wall points melee and ranged siege spots use — is
     * computed from these five numbers; nothing here touches {@link com.minecolonies.api.colony.buildings.IBuilding}.
     */
    private record WallBreach(BlockPos center, int alongX, int alongZ, int outX, int outZ) {}

    /** A garrison guard on the assault: its {@link Garrison} kind and troop level. */
    private record Marched(int kind, int level) {}

    /**
     * Launches an assault of {@code legionnaires} légionnaires against {@code defender}, with no archers.
     *
     * @return a human-readable refusal, or null when the assault is on.
     */
    public static String launch(final ServerLevel level, final IColony attacker, final IColony defender, final Player commander, final int legionnaires)
    {
        return launch(level, attacker, defender, commander, legionnaires, 0);
    }

    /**
     * Launches an assault of {@code legionnaires} légionnaires and {@code archers} archers against {@code defender}.
     *
     * @return a human-readable refusal, or null when the assault is on.
     */
    public static String launch(final ServerLevel level, final IColony attacker, final IColony defender, final Player commander,
      final int legionnaires, final int archers)
    {
        return launch(level, attacker, defender, commander, Collections.nCopies(Math.max(0, legionnaires), 0),
          Collections.nCopies(Math.max(0, archers), 0), null, false);
    }

    /**
     * Launches an assault with one entry per troop, its level (0 = the default level). {@code rally} is where the
     * troops appear (the end of the war road), or null for a spot on the ring around the colony. {@code fromGarrison}
     * troops go back to the attacker's garrison when the assault ends, if they survived.
     *
     * @return a human-readable refusal, or null when the assault is on.
     */
    public static String launch(final ServerLevel level, final IColony attacker, final IColony defender, @Nullable final Player commander,
      final List<Integer> legionLevels, final List<Integer> archerLevels, @Nullable final BlockPos rally, final boolean fromGarrison)
    {
        final int legionnaires = legionLevels.size();
        final int archers = archerLevels.size();
        if (BY_DEFENDER.containsKey(defender.getID()))
        {
            return "Cette colonie subit déjà un assaut.";
        }
        // The dev slice attacks as the defender itself; a real attacker needs a war it declared, its notice over
        if (attacker.getID() != defender.getID()
              && !fr.kodistan.interregne.politics.Diplomacy.mayAssault(level.getServer(), attacker.getID(), defender.getID()))
        {
            return "Impossible : aucune guerre ouverte contre cette colonie.";
        }
        if (attacker.getID() != defender.getID())
        {
            final String locked = fr.kodistan.interregne.politics.WarRules.lockRefusal(level.getServer(), attacker.getID(), defender.getID());
            if (locked != null)
            {
                return locked;
            }
        }
        if (SafeZones.get(level.getServer()).at(level.dimension(), defender.getCenter()) != null)
        {
            return "Impossible : cette colonie est en zone sûre.";
        }
        if (defender.getBuildingManager().getBuildings().isEmpty())
        {
            return "Impossible : cette colonie n'a aucun bâtiment.";
        }

        final BlockPos spawnPos = rally != null ? rally : pickSpawn(level, defender);
        if (spawnPos == null)
        {
            return "Impossible de trouver un point de ralliement autour de la colonie.";
        }

        final AssautEvent event = new AssautEvent(defender);
        event.setSpawnPoint(spawnPos);
        defender.getEventManager().addEvent(event);
        // The stock march reads the raid history (getRandomBuilding → getLastRaid()): seed one entry,
        // otherwise an empty history nulls out and the walk AI idles on a swallowed NPE.
        ((fr.kodistan.interregne.mixin.RaidManagerAccessor) defender.getRaiderManager()).interregne$getRaidHistories()
          .add(new com.minecolonies.core.colony.events.raid.RaidManager.RaidHistory(legionnaires + archers, level.getGameTime()));

        final Assault assault = new Assault(defender, attacker, commander != null ? commander.getUUID() : null);
        BY_DEFENDER.put(defender.getID(), assault);
        STRIKES.remove(defender.getID());
        GARRISON_WINS.remove(defender.getID());

        spawnTroops(level, WarEntities.LEGIONNAIRE.get(), legionLevels, Garrison.LEGIONNAIRE, 0, spawnPos, defender, event, assault, fromGarrison);
        spawnTroops(level, WarEntities.ARCHER.get(), archerLevels, Garrison.ARCHERE, legionnaires, spawnPos, defender, event, assault, fromGarrison);
        if (assault.troopIds.isEmpty())
        {
            BY_DEFENDER.remove(defender.getID());
            event.setStatus(com.minecolonies.api.colony.colonyEvents.EventStatus.CANCELED);
            return "Le spawn des troupes a échoué.";
        }

        assault.rallyPoint = spawnPos;
        planTargets(assault, level, spawnPos);
        assault.deadline = level.getGameTime() + CLOCK_START;
        if (assault.capture != null)
        {
            // The troops still have to be there for the whole taking: the chrono gets its length on top
            assault.deadline += assault.capture.neededTicks();
        }
        if (attacker.getID() != defender.getID())
        {
            fr.kodistan.interregne.politics.WarRules.onAssaultLaunched(level.getServer(), attacker.getID(), defender.getID());
            fr.kodistan.interregne.politics.AllyAid.onAssault(level.getServer(), defender);
        }
        if (commander != null)
        {
            commander.sendSystemMessage(Component.literal(
              "§6Assaut lancé§r : " + legionnaires + " légionnaire(s), " + archers + " archer(s) en route vers " + defender.getName() + "."));
        }
        if (attacker.getID() != defender.getID())
        {
            Diplomacy.notifyMembers(level.getServer(), defender, "§c⚔ " + attacker.getName() + " lance un assaut sur " + defender.getName()
              + "§r : " + (legionnaires + archers) + " soldat(s) en approche.");
        }
        return null;
    }

    /** Spawns {@code count} troops of one type, rallied around {@code spawnPos} — {@code offset} just keeps two
      * calls for two troop types (see {@link #launch}) from spawning on top of each other. */
    private static <T extends AssaultTroop> void spawnTroops(final ServerLevel level, final EntityType<T> type, final List<Integer> levels,
      final int kind, final int offset, final BlockPos spawnPos, final IColony defender, final AssautEvent event, final Assault assault,
      final boolean fromGarrison)
    {
        for (int i = 0; i < levels.size(); i++)
        {
            final T troop = type.create(level);
            if (troop == null)
            {
                if (fromGarrison)
                {
                    Garrison.welcomeBack(level.getServer(), assault.attacker, kind, levels.get(i));
                }
                continue;
            }
            if (levels.get(i) > 0)
            {
                troop.setTroopLevel(levels.get(i));
            }
            final int slot = offset + i;
            final BlockPos at = BlockPosUtil.findAround(level, spawnPos.offset(slot % 3, 0, slot / 3), 4, 3, BlockPosUtil.SOLID_AIR_POS_SELECTOR);
            troop.absMoveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            // Colony and event before the first tick: aiStep registers the troop with the event, and an
            // unregistered (eventID 0) raider discards itself.
            troop.setColony(defender);
            troop.setEventID(event.getID());
            level.addFreshEntity(troop);
            // Glowing outline, trackable from afar through walls: setGlowingTag alone doesn't stick, since
            // LivingEntity re-syncs that flag every tick from the real GLOWING effect (absent here, it
            // snapped back off within a tick). The effect itself is what has to be applied.
            troop.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, true, false));
            RaiderMobUtils.setMobAttributes(troop, defender);
            assault.troopIds.add(troop.getId());
            if (fromGarrison)
            {
                assault.marched.put(troop.getId(), new Marched(kind, troop.getTroopLevel()));
                assault.sent++;
            }
        }
    }

    /**
     * Rally ring for the troops. Building claims (3-4 chunks per hut) can push the colony far past its
     * nominal radius, so the ring walks outwards 90 to 180 blocks; outside-the-claim spots win, any
     * solid-air spot is a last resort so an assault never refuses to exist.
     */
    private static BlockPos pickSpawn(final ServerLevel level, final IColony defender)
    {
        BlockPos outside = null;
        BlockPos anywhere = null;
        for (final int margin : new int[] {Walls.COLONY_RADIUS * 16 + 10, 120, 150, 180})
        {
            for (int attempt = 0; attempt < 24; attempt++)
            {
                final double angle = level.random.nextDouble() * Math.PI * 2.0D;
                final int x = defender.getCenter().getX() + (int) (Math.cos(angle) * margin);
                final int z = defender.getCenter().getZ() + (int) (Math.sin(angle) * margin);
                // The real ground, not the colony centre's height ± a few blocks (found nothing on a superflat test
                // world); loading the chunk first, since on a fresh world nobody has been out on the ring yet.
                level.getChunkAt(new BlockPos(x, 0, z));
                final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                final BlockPos found = standable(level, x, y, z) ? new BlockPos(x, y, z) : null;
                if (found == null)
                {
                    continue;
                }
                if (anywhere == null)
                {
                    anywhere = found;
                }
                if (outside == null && !defender.isCoordInColony(level, found))
                {
                    outside = found;
                }
            }
            if (outside != null)
            {
                return outside;
            }
        }
        return anywhere;
    }

    /** Every building is a target, nearest to the rally point first so the band advances through the colony, the
      * town hall last (victory target of the full war system) — but if the colony has a wall, it's the very first
      * target of all: see {@link #planWallBreach}. */
    private static void planTargets(final Assault assault, final ServerLevel level, final BlockPos rallyPoint)
    {
        planWallBreach(assault, level, rallyPoint);

        final List<IBuilding> buildings = new ArrayList<>(assault.defender.getBuildingManager().getBuildings().values());
        final WarRuinsData ruins = WarRuinsData.get(level);
        buildings.removeIf(b -> b.getBuildingLevel() <= 0 || ruins.isRuined(b.getID()));
        buildings.sort(Comparator.comparingDouble(b -> BlockPosUtil.getDistance(b.getID(), rallyPoint)));
        final IBuilding townHall = buildings.stream().filter(b -> b.getID().equals(assault.defender.getCenter())).findFirst().orElse(null);
        if (townHall != null)
        {
            buildings.remove(townHall);
            buildings.add(townHall);
        }
        for (final IBuilding building : buildings)
        {
            final int sp = SP_BASE + SP_LEVEL * building.getBuildingLevel();
            assault.targets.add(building.getID());
            assault.structure.put(building.getID(), sp);
            assault.spTotal.put(building.getID(), sp);
        }
        // A town hall already in ruins is no longer battered, so it would never fall: it stays the last target, taken
        // by standing on it (the dev slice, a colony against itself, has no war to win)
        if (assault.attacker != assault.defender)
        {
            final BlockPos hall = assault.defender.getCenter();
            assault.capture = RuinCapture.plan(assault.targets, hall, ruins.isRuined(hall), Config.WAR_RUIN_CAPTURE_SECONDS.get() * 20L);
            assault.ruinHall = assault.capture == null ? null : hall;
        }
    }

    /** Width of the hole troops punch through a wall — a battering ram (not built yet) will one day make a wider
      * one and leave this narrower width to troops alone; see the war rule this came from. */
    private static final int TROOP_BREACH_HALF = 3;
    /** How far down from the terrain's top surface the footprint scan looks for wall blocks — comfortably past the
      * tallest wall level (11 blocks, {@link WallLevels#MAX}) plus the ground it stands on. */
    private static final int WALL_SCAN_DOWN = 20;
    /** Centreline-to-outer-face distance: half the wall's fixed thickness ({@link WallLevels#WIDTH}) — the
      * centreline sits inside the wall's body, so every wall siege point (and the footprint/crack scan's own
      * across-the-wall reach: exactly this far either side, since the thickness is known, not guessed) is measured
      * out from here, never from the centreline itself. */
    private static final int WALL_OUTER_HALF = WallLevels.WIDTH / 2;

    /** If the defender has a wall, picks where it gets breached this assault (nearest point on the trace to the
      * troops' own rally point) and makes it the colony's first target — nothing reaches a building while the wall
      * still stands. A no-op when there's no wall, or it has no trace yet. */
    private static void planWallBreach(final Assault assault, final ServerLevel level, final BlockPos rallyPoint)
    {
        final WallRegistry.Wall wall = WallRegistry.get(level.getServer()).of(assault.defender);
        if (wall == null || wall.points().isEmpty())
        {
            return;
        }
        final WallBreach breach = pickWallBreach(level, wall, rallyPoint, assault.defender.getCenter());
        assault.wallBreach = breach;
        final int sp = SP_BASE + SP_LEVEL * wall.level();
        assault.targets.add(breach.center());
        assault.structure.put(breach.center(), sp);
        assault.spTotal.put(breach.center(), sp);
    }

    /** Nearest point of the wall's trace to {@code from} (the troops' rally point): projects {@code from} onto
      * every segment between consecutive trace points (wrapping, for a closed wall) and keeps the closest. The
      * segment's own direction, snapped to one of the 8 compass points (every trace segment already is, per
      * {@link WallTrace}'s 45° rule), becomes the breach's "along the wall" axis; its perpendicular is "through
      * the wall", signed toward whichever side sits farther from the colony centre — the attacker's side. */
    private static WallBreach pickWallBreach(final ServerLevel level, final WallRegistry.Wall wall, final BlockPos from, final BlockPos colonyCenter)
    {
        final List<WallTrace.Point> points = wall.points();
        final int n = points.size();
        final int segments = wall.closed() ? n : n - 1;
        double best = Double.MAX_VALUE;
        int bestX = points.get(0).x();
        int bestZ = points.get(0).z();
        int bestAlongX = 1;
        int bestAlongZ = 0;
        for (int i = 0; i < segments; i++)
        {
            final WallTrace.Point a = points.get(i);
            final WallTrace.Point b = points.get((i + 1) % n);
            final double dx = b.x() - a.x();
            final double dz = b.z() - a.z();
            final double len2 = dx * dx + dz * dz;
            if (len2 < 1.0D)
            {
                continue;
            }
            final double t = Mth.clamp(((from.getX() - a.x()) * dx + (from.getZ() - a.z()) * dz) / len2, 0.0D, 1.0D);
            final double px = a.x() + t * dx;
            final double pz = a.z() + t * dz;
            final double d2 = (from.getX() - px) * (from.getX() - px) + (from.getZ() - pz) * (from.getZ() - pz);
            if (d2 < best)
            {
                best = d2;
                bestX = (int) Math.round(px);
                bestZ = (int) Math.round(pz);
                bestAlongX = Integer.signum((int) dx);
                bestAlongZ = Integer.signum((int) dz);
            }
        }
        // Perpendicular to (alongX, alongZ); the sign is picked below, whichever side is actually outward.
        final int outX = -bestAlongZ;
        final int outZ = bestAlongX;
        final double outward = (bestX + outX - colonyCenter.getX()) * (double) (bestX + outX - colonyCenter.getX())
          + (bestZ + outZ - colonyCenter.getZ()) * (double) (bestZ + outZ - colonyCenter.getZ());
        final double inward = (bestX - outX - colonyCenter.getX()) * (double) (bestX - outX - colonyCenter.getX())
          + (bestZ - outZ - colonyCenter.getZ()) * (double) (bestZ - outZ - colonyCenter.getZ());
        final int signedOutX = outward >= inward ? outX : -outX;
        final int signedOutZ = outward >= inward ? outZ : -outZ;
        final int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bestX, bestZ) - 1;
        return new WallBreach(new BlockPos(bestX, groundY, bestZ), bestAlongX, bestAlongZ, signedOutX, signedOutZ);
    }

    /**
     * The foe this troop should go after, within {@code range}: the defender's guards first, then enemy players, never
     * a civilian or a neutral (those only become targets by hitting a troop first: HurtByTargetGoal and
     * {@link #rally}). A guard counts only if the troop can see it or it is fighting — a guard walled in out of sight
     * must not lure the band away from the siege for nothing. Players need to be in sight.
     */
    @Nullable
    static LivingEntity pickFoe(final AssaultTroop troop, final double range)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null || !(troop.level() instanceof ServerLevel level))
        {
            return null;
        }
        final long now = level.getGameTime();
        final double rangeSq = range * range;
        LivingEntity best = null;
        double bestSq = rangeSq;
        for (final AbstractEntityCitizen citizen : level.getEntitiesOfClass(AbstractEntityCitizen.class, troop.getBoundingBox().inflate(range),
          c -> c.isAlive() && isGuardOf(assault.defender, c)))
        {
            final double distSq = troop.distanceToSqr(citizen);
            if (distSq <= bestSq && !troop.shuns(citizen) && (fighting(assault, citizen, now) || troop.hasLineOfSight(citizen)))
            {
                bestSq = distSq;
                best = citizen;
            }
        }
        if (best != null)
        {
            return best;
        }
        for (final ServerPlayer player : level.players())
        {
            final double distSq = troop.distanceToSqr(player);
            if (distSq <= bestSq && !troop.shuns(player) && isEnemyPlayer(assault, player) && troop.hasLineOfSight(player))
            {
                bestSq = distSq;
                best = player;
            }
        }
        return best;
    }

    /** A guard of {@code colony}, alive or not (a dying guard is already dead to isAlive). */
    private static boolean isGuardOf(final IColony colony, final AbstractEntityCitizen citizen)
    {
        final var data = citizen.getCitizenData();
        return citizen.getCitizenColonyHandler().getColonyId() == colony.getID() && data != null && data.getJob() != null && data.getJob().isGuard();
    }

    /**
     * A player the troops attack on sight: a member of the defending colony, or of a colony at open war with the
     * attacker. Never a member of the attacking colony, and never a neutral or an ally who stays out of the war.
     */
    private static boolean isEnemyPlayer(final Assault assault, final Player player)
    {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || assault.attacker.getPermissions().isColonyMember(player))
        {
            return false;
        }
        if (assault.defender.getPermissions().isColonyMember(player))
        {
            return true;
        }
        final MinecraftServer server = player.getServer();
        if (server == null)
        {
            return false;
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (colony.getPermissions().isColonyMember(player)
                  && (Diplomacy.mayAssault(server, assault.attacker.getID(), colony.getID()) || Diplomacy.mayAssault(server, colony.getID(), assault.attacker.getID())))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean fighting(final Assault assault, final Entity guard, final long now)
    {
        final Long last = assault.guardFights.get(guard.getId());
        return last != null && now - last <= GUARD_FIGHT_WINDOW;
    }

    /** Whether a fighting defender guard stands close enough to this building to keep it from losing structure. */
    private static boolean guardHolds(final Assault assault, final ServerLevel level, final BlockPos hut)
    {
        final long now = level.getGameTime();
        final Iterator<Map.Entry<Integer, Long>> it = assault.guardFights.entrySet().iterator();
        while (it.hasNext())
        {
            final Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > GUARD_FIGHT_WINDOW)
            {
                it.remove();
                continue;
            }
            final Entity guard = level.getEntity(entry.getKey());
            if (guard == null || !guard.isAlive())
            {
                it.remove();
                continue;
            }
            final double dx = guard.getX() - (hut.getX() + 0.5D);
            final double dz = guard.getZ() - (hut.getZ() + 0.5D);
            if (dx * dx + dz * dz <= GUARD_HOLD_RADIUS * GUARD_HOLD_RADIUS)
            {
                return true;
            }
        }
        return false;
    }

    /** Pushes the assault's deadline back after progress, never past {@link #CLOCK_CAP} from now. */
    private static void addTime(final Assault assault, final long ticks)
    {
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            assault.deadline = Math.min(level.getGameTime() + CLOCK_CAP, assault.deadline + ticks);
        }
    }

    /** Shared aggro: the troops of this assault near the one that got hit turn on whoever hit it, unless already busy. */
    private static void rally(final Assault assault, final ServerLevel level, final AssaultTroop hurt, final LivingEntity hitter)
    {
        if (hitter instanceof Player player && (player.isCreative() || player.isSpectator() || assault.attacker.getPermissions().isColonyMember(player)))
        {
            return;
        }
        for (final int id : assault.troopIds)
        {
            if (level.getEntity(id) instanceof AssaultTroop troop && troop != hurt && troop.isAlive()
                  && troop.distanceToSqr(hurt) <= ALERT_RADIUS * ALERT_RADIUS
                  && (troop.getTarget() == null || !troop.getTarget().isAlive()))
            {
                troop.setTarget(hitter);
            }
        }
    }

    /**
     * The next march target for the stock raider walk (called from RaidManagerMixin). Round-robin over the
     * surviving targets: no more piling on the warehouse, no more re-picking destroyed ruins. Null when no
     * assault is running, so the vanilla random pick applies.
     */
    public static BlockPos pickTarget(final IColony defender)
    {
        final Assault assault = defender == null ? null : BY_DEFENDER.get(defender.getID());
        if (assault == null || assault.targets.isEmpty())
        {
            return null;
        }
        final int cursor = TARGET_CURSOR.merge(defender.getID(), 1, Integer::sum);
        return assault.targets.get(cursor % assault.targets.size());
    }

    /**
     * The troop's own building: every troop of the assault, légionnaire or archer, always the same one — the
     * nearest surviving target to the rally point ({@link #planTargets}, town hall last). The march
     * ({@link AssaultGoal}/{@link AssaultRangedGoal}) and the battering point at this same structure, so the whole
     * war band converges on one building at a time instead of scattering across the colony; once it falls,
     * {@link #destroyBuilding} drops it from {@code targets} and everyone pivots to the new first entry on their
     * next tick. Each troop still picks its own wall face in {@link #siegeSpot}, from its own approach, so a
     * converged band spreads around the building's sides rather than stacking on one block.
     */
    public static BlockPos assignedTarget(final AssaultTroop troop)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null || assault.targets.isEmpty())
        {
            return null;
        }
        return assault.targets.get(0);
    }

    /** Mining-crack stage (0-9) of the building under battering: how close each blow leaves it to collapse. */
    static int crackStage(final AssaultTroop troop, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null)
        {
            return 0;
        }
        final int left = assault.structure.getOrDefault(hut, 1);
        final int total = Math.max(1, assault.spTotal.getOrDefault(hut, left));
        return Mth.clamp((int) Math.round(9.0D * (1.0D - (double) left / total)), 0, 9);
    }

    /** One battering hit on the building the troop is standing at. Structure points always drop, immediately — only
      * the visual refresh (bar, particles, crack overlay) is rate-limited per building, below. Returns whether this
      * call actually showed effects, so {@link AssaultGoal} knows whether to add its own blow particles/sound on
      * top: those are the other half of the per-hit cost and would otherwise defeat the point of the cap. */
    static boolean onHit(final AssaultTroop troop, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null || !assault.structure.containsKey(hut))
        {
            return false;
        }
        final boolean isWall = assault.wallBreach != null && hut.equals(assault.wallBreach.center());
        // The wall can be breached under fire; a building can't lose structure while a guard fights near it.
        if (!isWall && assault.defender.getWorld() instanceof ServerLevel held && guardHolds(assault, held, hut))
        {
            if (effectsDue(assault, held, hut))
            {
                updateBar(assault, hut, assault.structure.get(hut), false, true);
            }
            return false;
        }
        final int left = assault.structure.merge(hut, -1, Integer::sum);
        boolean showEffects = false;
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            showEffects = effectsDue(assault, level, hut);
            if (showEffects)
            {
                updateBar(assault, hut, left, isWall, false);
                if (isWall)
                {
                    wallDamageEffects(level, assault.wallBreach);
                    updateWallCracks(assault, level, assault.wallBreach, crackStage(troop, hut));
                }
                else
                {
                    final IBuilding building = assault.defender.getBuildingManager().getBuilding(hut);
                    if (building != null)
                    {
                        damageEffects(level, building);
                        updateCracks(assault, level, building, hut, crackStage(troop, hut));
                    }
                }
            }
        }
        if (left <= 0)
        {
            addTime(assault, CLOCK_PER_STRUCTURE);
            if (isWall)
            {
                breachWall(assault, hut);
            }
            else
            {
                destroyBuilding(assault, hut);
            }
        }
        return showEffects;
    }

    /** One troop battering alone still gets a full effect on every blow (cooldown well under its own rhythm): the
      * cap only kicks in once several troops land hits on the same building faster than a single one would, which
      * is exactly the case — more attackers — where thinning the effects out is free (nobody can tell five
      * overlapping blow-bursts from one slightly bigger one in the middle of a siege). */
    private static boolean effectsDue(final Assault assault, final ServerLevel level, final BlockPos hut)
    {
        final long now = level.getGameTime();
        final Long last = assault.lastEffectTick.get(hut);
        if (last != null && now - last < EFFECT_COOLDOWN_TICKS)
        {
            return false;
        }
        assault.lastEffectTick.put(hut, now);
        return true;
    }

    /** Every currently-standing designed block of a building's footprint, excluding the (invincible) hut itself —
      * what the crack overlay and the final ruin sweep both walk. */
    private static List<BlockPos> footprintBlocks(final ServerLevel level, final IBuilding building, final BlockPos hut)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final BlockPos min = new BlockPos(Math.min(corners.getA().getX(), corners.getB().getX()),
          Math.min(corners.getA().getY(), corners.getB().getY()),
          Math.min(corners.getA().getZ(), corners.getB().getZ()));
        final BlockPos max = new BlockPos(Math.max(corners.getA().getX(), corners.getB().getX()),
          Math.max(corners.getA().getY(), corners.getB().getY()),
          Math.max(corners.getA().getZ(), corners.getB().getZ()));
        final List<BlockPos> out = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(min, max))
        {
            if (!cursor.equals(hut) && isDesignedBlock(level.getBlockState(cursor)))
            {
                out.add(cursor.immutable());
            }
        }
        return out;
    }

    /** A stable fake "breaker id" per block position, so each of a building's blocks can carry its own crack overlay
      * at once — vanilla's destroyBlockProgress lets one breaker id show progress on only one block at a time, and
      * these overlays aren't tied to any one troop (several could be hitting the same building). Never collides with
      * a real entity id: those are always non-negative. */
    private static int breakerIdFor(final BlockPos pos)
    {
        return -(Math.abs(pos.hashCode()) + 2);
    }

    /** This hut's full footprint, scanned from the live world once and cached on the assault from then on. */
    private static List<BlockPos> cachedFootprint(final Assault assault, final ServerLevel level, final IBuilding building, final BlockPos hut)
    {
        return assault.footprints.computeIfAbsent(hut, h -> footprintBlocks(level, building, hut));
    }

    /** Up to {@link #MAX_CRACK_BLOCKS} positions spread evenly across the footprint — enough to read as "the whole
      * building is cracking" without paying for every single block of a large structure. Cached alongside the full
      * footprint, same lifetime. */
    private static List<BlockPos> crackSample(final Assault assault, final ServerLevel level, final IBuilding building, final BlockPos hut)
    {
        return assault.crackSamples.computeIfAbsent(hut, h ->
        {
            final List<BlockPos> full = cachedFootprint(assault, level, building, hut);
            if (full.size() <= MAX_CRACK_BLOCKS)
            {
                return full;
            }
            final List<BlockPos> sample = new ArrayList<>(MAX_CRACK_BLOCKS);
            for (int i = 0; i < MAX_CRACK_BLOCKS; i++)
            {
                sample.add(full.get(i * full.size() / MAX_CRACK_BLOCKS));
            }
            return sample;
        });
    }

    /** Crack overlay across the whole building, intensity matching its remaining structure points — every standing
      * block visibly deteriorates together, not just the one spot being hit. The hut itself is excluded by
      * {@link #footprintBlocks}: it's the one block in the footprint meant to survive regardless, so it never cracks. */
    private static void updateCracks(final Assault assault, final ServerLevel level, final IBuilding building, final BlockPos hut, final int stage)
    {
        for (final BlockPos pos : crackSample(assault, level, building, hut))
        {
            level.destroyBlockProgress(breakerIdFor(pos), pos, stage);
        }
    }

    /** Drops every crack overlay this building is currently showing. */
    private static void clearCracks(final Assault assault, final ServerLevel level, final IBuilding building, final BlockPos hut)
    {
        for (final BlockPos pos : crackSample(assault, level, building, hut))
        {
            level.destroyBlockProgress(breakerIdFor(pos), pos, -1);
        }
    }

    /** Every currently-standing designed block around the breach point: scanned column by column (no building box
      * to walk, unlike {@link #footprintBlocks}) across {@link #TROOP_BREACH_HALF} blocks each way along the wall
      * and exactly {@link #WALL_OUTER_HALF} each way through it (the thickness is fixed and known, not guessed),
      * from the terrain's own top surface down — the same "trust the live world, not a blueprint" approach
      * {@link Terrain#probe} already uses for laying the wall out. */
    private static List<BlockPos> wallFootprintBlocks(final ServerLevel level, final WallBreach breach)
    {
        final List<BlockPos> out = new ArrayList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int along = -TROOP_BREACH_HALF; along <= TROOP_BREACH_HALF; along++)
        {
            final int cx = breach.center().getX() + breach.alongX() * along;
            final int cz = breach.center().getZ() + breach.alongZ() * along;
            for (int across = -WALL_OUTER_HALF; across <= WALL_OUTER_HALF; across++)
            {
                final int x = cx + breach.outX() * across;
                final int z = cz + breach.outZ() * across;
                final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                for (int dy = 0; dy <= WALL_SCAN_DOWN; dy++)
                {
                    cursor.set(x, top - dy, z);
                    if (isDesignedBlock(level.getBlockState(cursor)))
                    {
                        out.add(cursor.immutable());
                    }
                }
            }
        }
        return out;
    }

    /** The breach's full footprint, scanned once and cached — same lifetime and reuse rationale as
      * {@link #cachedFootprint}. */
    private static List<BlockPos> cachedWallFootprint(final Assault assault, final ServerLevel level, final WallBreach breach)
    {
        return assault.footprints.computeIfAbsent(breach.center(), h -> wallFootprintBlocks(level, breach));
    }

    /** Up to {@link #MAX_CRACK_BLOCKS} positions spread evenly across the breach's footprint — same rationale as
      * {@link #crackSample}. */
    private static List<BlockPos> wallCrackSample(final Assault assault, final ServerLevel level, final WallBreach breach)
    {
        return assault.crackSamples.computeIfAbsent(breach.center(), h ->
        {
            final List<BlockPos> full = cachedWallFootprint(assault, level, breach);
            if (full.size() <= MAX_CRACK_BLOCKS)
            {
                return full;
            }
            final List<BlockPos> sample = new ArrayList<>(MAX_CRACK_BLOCKS);
            for (int i = 0; i < MAX_CRACK_BLOCKS; i++)
            {
                sample.add(full.get(i * full.size() / MAX_CRACK_BLOCKS));
            }
            return sample;
        });
    }

    /** Crack overlay across the whole breach volume, same intensity contract as {@link #updateCracks}. */
    private static void updateWallCracks(final Assault assault, final ServerLevel level, final WallBreach breach, final int stage)
    {
        for (final BlockPos pos : wallCrackSample(assault, level, breach))
        {
            level.destroyBlockProgress(breakerIdFor(pos), pos, stage);
        }
    }

    /** Drops every crack overlay the breach is currently showing. */
    private static void clearWallCracks(final Assault assault, final ServerLevel level, final WallBreach breach)
    {
        for (final BlockPos pos : wallCrackSample(assault, level, breach))
        {
            level.destroyBlockProgress(breakerIdFor(pos), pos, -1);
        }
    }

    /** Same dusty-impact language as {@link #damageEffects}, centred on the breach point instead of a random spot
      * across a building's footprint — there's no building box to pick a random point inside of. */
    private static void wallDamageEffects(final ServerLevel level, final WallBreach breach)
    {
        final BlockPos c = breach.center();
        level.sendParticles(ParticleTypes.EXPLOSION, c.getX() + 0.5D, c.getY() + 2.0D, c.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5D, c.getY() + 2.3D, c.getZ() + 0.5D, 6, 1.5D, 1.5D, 1.5D, 0.02D);
        level.playSound(null, c.getX() + 0.5D, c.getY() + 2.0D, c.getZ() + 0.5D, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
          net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.1F + level.random.nextFloat() * 0.3F);
    }

    /** The wall's SP hit zero: unlike a building, every block of the breach comes down for real — no ruin-survival
      * chance, a hole the player can't walk through would defeat the point of making one. Still paced through
      * {@link #PENDING_SWEEPS} (the breach can be a few hundred blocks). No loot: this opens the colony up, it
      * doesn't destroy anything of value. {@link #assignedTarget} pivots everyone to the next target (a building,
      * if any) the moment {@code wallBreach} goes null, same as it already pivots off a fallen building. */
    private static void breachWall(final Assault assault, final BlockPos hut)
    {
        final WallBreach breach = assault.wallBreach;
        final ServerLevel level = (ServerLevel) assault.defender.getWorld();
        clearWallCracks(assault, level, breach);
        final List<BlockPos> toSweep = cachedWallFootprint(assault, level, breach);
        if (!toSweep.isEmpty())
        {
            PENDING_SWEEPS.add(new SweepJob(level, new ArrayList<>(toSweep)));
        }
        level.playSound(null, hut, net.minecraft.world.level.block.SoundType.STONE.getBreakSound(),
          net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
        announce(assault, "§cLa muraille est percée§r : la voie est libre.");
        removeBar(assault, hut);
        assault.structure.remove(hut);
        assault.spTotal.remove(hut);
        assault.targets.remove(hut);
        assault.footprints.remove(hut);
        assault.crackSamples.remove(hut);
        assault.lastEffectTick.remove(hut);
        assault.wallBreach = null;
        // Flagged for "Tout reconstruire" ({@link #onRebuildAll}) — the player's own wall-planner "Réparer" button
        // already fixes it without this flag (it just replays the unchanged trace), but the flag is what lets the
        // town hall screen show it needs fixing at all, and what lets one click cover it alongside ruined buildings.
        WarRuinsData.get(level).markWallDamaged(RegionalTreasury.keyOf(assault.defender));
        syncRuins(level, assault.defender);
    }

    /**
     * A building under siege reads as such from anywhere in the colony: a random explosion puff and smoke
     * somewhere across its footprint on every hit — the same "something's happening here" language already used
     * for the construction/demolition sites' own ambient effects, not just a single point on one wall.
     */
    private static void damageEffects(final ServerLevel level, final IBuilding building)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final double minX = Math.min(corners.getA().getX(), corners.getB().getX());
        final double maxX = Math.max(corners.getA().getX(), corners.getB().getX()) + 1.0D;
        final double minY = Math.min(corners.getA().getY(), corners.getB().getY());
        final double maxY = Math.max(corners.getA().getY(), corners.getB().getY()) + 1.0D;
        final double minZ = Math.min(corners.getA().getZ(), corners.getB().getZ());
        final double maxZ = Math.max(corners.getA().getZ(), corners.getB().getZ()) + 1.0D;
        final double x = Mth.lerp(level.random.nextDouble(), minX, maxX);
        final double y = Mth.lerp(level.random.nextDouble(), minY, maxY);
        final double z = Mth.lerp(level.random.nextDouble(), minZ, maxZ);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3D, z, 6, 0.3D, 0.3D, 0.3D, 0.02D);
        level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
          net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.1F + level.random.nextFloat() * 0.3F);
    }

    /** Shows (and keeps in sync) the target's health bar, visible to nearby players only; blue while guards hold it. */
    private static void updateBar(final Assault assault, final BlockPos hut, final int left, final boolean isWall, final boolean held)
    {
        if (!(assault.defender.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final int total = Math.max(1, assault.spTotal.getOrDefault(hut, left));
        final ServerBossEvent bar = assault.bars.computeIfAbsent(hut, h ->
        {
            final Component name;
            if (isWall)
            {
                name = Component.literal("Muraille");
            }
            else
            {
                final IBuilding b = assault.defender.getBuildingManager().getBuilding(h);
                name = b != null ? Component.translatableEscape(b.getBuildingDisplayName()) : Component.literal("bâtiment");
            }
            return new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        });
        bar.setProgress(Mth.clamp((float) left / total, 0.0F, 1.0F));
        bar.setColor(held ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED);
        for (final ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(hut.getX() + 0.5D, hut.getY() + 0.5D, hut.getZ() + 0.5D) <= BAR_VIEW_DISTANCE_SQ)
            {
                bar.addPlayer(player);
            }
            else
            {
                bar.removePlayer(player);
            }
        }
    }

    /** Drops the building's health bar for everyone watching it. */
    private static void removeBar(final Assault assault, final BlockPos hut)
    {
        final ServerBossEvent bar = assault.bars.remove(hut);
        if (bar != null)
        {
            bar.removeAllPlayers();
        }
    }

    /**
     * Moves the loot of one fallen building from the defender's treasury to the attacker's: capped per building and
     * per assault, and never below the defender's reserve. In the dev slice (a colony against itself) nothing is
     * spent, the loot is only credited.
     */
    private static long takeLoot(final Assault assault, final net.minecraft.server.MinecraftServer server)
    {
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        long loot = Math.min(Config.WAR_LOOT_PER_BUILDING.get(), Config.WAR_LOOT_PER_ASSAULT.get() - assault.loot);
        if (assault.attacker != assault.defender)
        {
            loot = Math.min(loot, treasury.balance(assault.defender) - Config.WAR_TREASURY_RESERVE.get());
        }
        if (loot <= 0 || assault.attacker != assault.defender && !treasury.spend(assault.defender, loot))
        {
            return 0;
        }
        treasury.credit(assault.attacker, loot);
        return loot;
    }

    /** Sweeps the structure, pays the loot, moves the troops on to their next target. */
    private static void destroyBuilding(final Assault assault, final BlockPos hut)
    {
        final IBuilding building = assault.defender.getBuildingManager().getBuilding(hut);
        final ServerLevel level = (ServerLevel) assault.defender.getWorld();
        if (building != null)
        {
            // Every block is about to either vanish or become a ruin block with no fight left in it: wipe their
            // crack overlays before the sweep, not after — a block destroyBlock() already turned to air drops its
            // overlay client-side on its own, but a surviving ruin block wouldn't otherwise.
            clearCracks(assault, level, building, hut);
            final int minY = Math.min(building.getCorners().getA().getY(), building.getCorners().getB().getY());
            // The foundation row stays standing, and roughly a third of the rest survives by chance — a ruin the
            // player can still read as "that building", not a bare swept plot. Decided up front, against the
            // cached footprint (nothing's moved since it was scanned): the actual block removal only happens a
            // few at a time, below, so a big building falling never stalls a single tick.
            final List<BlockPos> toSweep = new ArrayList<>();
            for (final BlockPos pos : cachedFootprint(assault, level, building, hut))
            {
                if (pos.getY() != minY && level.random.nextFloat() >= RUIN_SURVIVAL_CHANCE)
                {
                    toSweep.add(pos);
                }
            }
            if (!toSweep.isEmpty())
            {
                PENDING_SWEEPS.add(new SweepJob(level, toSweep));
            }
            final int swept = toSweep.size();
            final long loot = takeLoot(assault, level.getServer());
            assault.destroyed++;
            if (hut.equals(assault.defender.getCenter()) && assault.attacker != assault.defender)
            {
                assault.townHallTaken = true;
            }
            assault.loot += loot;
            // One deep crash reads "building coming down" from anywhere in the colony.
            level.playSound(null, hut, net.minecraft.world.level.block.SoundType.STONE.getBreakSound(),
              net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
            announce(assault, Component.literal("§c").append(Component.translatableEscape(building.getBuildingDisplayName()))
              .append(Component.literal("§r en ruines (" + swept + " blocs)" + (loot > 0 ? " — butin " + loot + " couronnes." : " — rien à piller."))));
        }
        removeBar(assault, hut);
        WarRuinsData.get(level).markRuined(hut);
        syncRuins(level, assault.defender);
        assault.structure.remove(hut);
        assault.spTotal.remove(hut);
        assault.targets.remove(hut);
        assault.footprints.remove(hut);
        assault.crackSamples.remove(hut);
        assault.lastEffectTick.remove(hut);
    }

    /** Man-made only: terrain, water and plants are never touched (the user's war rule). */
    public static boolean isDesignedBlock(final BlockState state)
    {
        return !state.isAir() && !state.liquid() && !Terrain.natural(state);
    }

    /** The wall block a troop batters and the ground spot (feet position) right outside it where it plants itself. */
    record SiegeSpot(BlockPos wall, BlockPos stand) {}

    /**
     * The real exterior wall or door nearest the troop, and the ground spot just outside it. Computed once per
     * troop-target assignment and kept fixed for the whole fight (see {@link AssaultGoal}).
     * <p>
     * {@code getCorners()} is the whole blueprint box — yards, paths, gardens, padding — not the walls: measured on
     * the medievalspruce blueprints, the first wall sits 3 to 15 blocks (up to 21) inside the box on the approach
     * face. Snapping to the box edge is what left troops battering thin air several blocks from the building. So we
     * start from the nearest box face and walk inward along the troop's line until the first wall column, then stand
     * on the last walkable column before it — on the actual ground, which is rarely at hut height.
     * <p>
     * {@code occupiedStands} (see {@link #occupiedStands}) steers the scan past whatever column another living
     * troop already stands at, onto the next one along the same wall — several troops converged on one building
     * (see {@link #assignedTarget}) now spread along its walls instead of overlapping on the single nearest column.
     */
    static SiegeSpot siegeSpot(final ServerLevel level, final IBuilding building, final BlockPos hut, final BlockPos from,
      final Set<BlockPos> occupiedStands)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final int minX = Math.min(corners.getA().getX(), corners.getB().getX());
        final int maxX = Math.max(corners.getA().getX(), corners.getB().getX());
        final int minZ = Math.min(corners.getA().getZ(), corners.getB().getZ());
        final int maxZ = Math.max(corners.getA().getZ(), corners.getB().getZ());
        final int minY = Math.min(corners.getA().getY(), corners.getB().getY());
        final int cx = Mth.clamp(from.getX(), minX, maxX);
        final int cz = Mth.clamp(from.getZ(), minZ, maxZ);
        final int toMinX = cx - minX;
        final int toMaxX = maxX - cx;
        final int toMinZ = cz - minZ;
        final int toMaxZ = maxZ - cz;
        final int nearest = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
        // Edge column, inward step, and the lateral coordinate swapped in for the hut's own line as a fallback.
        final int ex, ez, ix, iz, length;
        final boolean alongX;
        if (nearest == toMinX) { ex = minX; ez = cz; ix = 1; iz = 0; length = maxX - minX; alongX = true; }
        else if (nearest == toMaxX) { ex = maxX; ez = cz; ix = -1; iz = 0; length = maxX - minX; alongX = true; }
        else if (nearest == toMinZ) { ex = cx; ez = minZ; ix = 0; iz = 1; length = maxZ - minZ; alongX = false; }
        else { ex = cx; ez = maxZ; ix = 0; iz = -1; length = maxZ - minZ; alongX = false; }

        SiegeSpot spot = scanInward(level, ex, ez, ix, iz, length, hut, minY, occupiedStands);
        if (spot == null)
        {
            // The troop's line can slip through a gap (an open yard side): the hut's own line always meets the house.
            spot = scanInward(level, alongX ? ex : hut.getX(), alongX ? hut.getZ() : ez, ix, iz, length, hut, minY, occupiedStands);
        }
        if (spot == null)
        {
            final int feet = groundAt(level, ex - ix, ez - iz, hut.getY() + 4, minY - 4);
            spot = new SiegeSpot(new BlockPos(ex, hut.getY() + 1, ez),
              new BlockPos(ex - ix, feet == Integer.MIN_VALUE ? hut.getY() : feet, ez - iz));
        }
        return spot;
    }

    /** How far back an archer plants itself from the wall it's bombarding — melee distance would make it look and
      * play like a légionnaire with a bow. Also the archer's standoff against a wall-breach target
      * ({@link #wallSiegeSpot}), hence package visibility. */
    static final int RANGED_STANDOFF = 8;

    /**
     * Same wall and approach line as {@link #siegeSpot} (same wall column, same occupancy spread across troops),
     * but the stand point is walked {@link #RANGED_STANDOFF} blocks further back along that line instead of the
     * single column right outside the wall — an archer keeps its distance and bombards from range, it doesn't
     * press up against the building like the légionnaire. Walked one column at a time so it follows the terrain
     * and stops short (shy of the full standoff) rather than overshoot into a cliff, another building or open air;
     * it never ends up closer than the melee spot {@code siegeSpot} already found.
     * <p>
     * Claims {@code siegeSpot}'s own near-wall column for this troop ({@link #claimStand}), not the backed-off
     * point it actually stands at: {@link #occupiedStands} is read by {@code siegeSpot}'s column scan, which only
     * ever compares against near-wall positions — claiming the far stand instead left every archer comparing
     * against a key nothing else used, so none of them ever looked occupied and they all picked the same column
     * (and so the same backed-off block) regardless of how many were already there.
     */
    static SiegeSpot rangedSiegeSpot(final ServerLevel level, final IBuilding building, final BlockPos hut, final AssaultTroop troop)
    {
        final SiegeSpot near = siegeSpot(level, building, hut, troop.blockPosition(), occupiedStands(troop));
        claimStand(troop, near.stand());
        final BlockPos wall = near.wall();
        final int dx = Integer.signum(near.stand().getX() - wall.getX());
        final int dz = Integer.signum(near.stand().getZ() - wall.getZ());
        if (dx == 0 && dz == 0)
        {
            // siegeSpot's own last-resort fallback has no real approach line to walk back along: stay put rather
            // than guess a direction.
            return near;
        }
        BlockPos farthest = near.stand();
        int ground = farthest.getY();
        for (int n = 2; n <= RANGED_STANDOFF; n++)
        {
            final int x = wall.getX() + dx * n;
            final int z = wall.getZ() + dz * n;
            final int feet = groundNear(level, x, z, ground);
            if (feet == Integer.MIN_VALUE)
            {
                break;
            }
            final BlockPos candidate = new BlockPos(x, feet, z);
            if (isDesignedBlock(level.getBlockState(candidate.above())) || isDesignedBlock(level.getBlockState(candidate.above(2))))
            {
                // Backed into another structure's wall: as far back as it goes.
                break;
            }
            ground = feet;
            farthest = candidate;
        }
        return new SiegeSpot(wall, farthest);
    }

    private static SiegeSpot scanInward(final ServerLevel level, final int ex, final int ez, final int ix, final int iz,
      final int length, final BlockPos hut, final int minY, final Set<BlockPos> occupiedStands)
    {
        int ground = groundAt(level, ex - ix, ez - iz, hut.getY() + 4, minY - 4);
        if (ground == Integer.MIN_VALUE)
        {
            ground = hut.getY();
        }
        BlockPos stand = new BlockPos(ex - ix, ground, ez - iz);
        // Kept in case every free wall column is already somebody else's: better a stacked troop than no fight at
        // all, exactly the old behaviour (first wall found, period) when there's nowhere else to put it.
        SiegeSpot fallback = null;
        for (int i = 0; i <= length; i++)
        {
            final int x = ex + ix * i;
            final int z = ez + iz * i;
            final int wallY = wallFace(level, x, z, ground);
            if (wallY != Integer.MIN_VALUE)
            {
                final SiegeSpot candidate = new SiegeSpot(new BlockPos(x, wallY, z), stand);
                if (fallback == null)
                {
                    fallback = candidate;
                }
                if (!occupiedStands.contains(horizontalKey(stand)))
                {
                    return candidate;
                }
            }
            final int feet = groundNear(level, x, z, ground);
            if (feet != Integer.MIN_VALUE)
            {
                ground = feet;
                stand = new BlockPos(x, feet, z);
            }
        }
        return fallback;
    }

    /** Horizontal-only key for an occupancy check: the stand's feet height can shift a block or two between
      * adjacent wall columns on uneven ground, which would otherwise make two genuinely different spots compare
      * as "free" or vice versa for no reason tied to actual crowding. */
    private static BlockPos horizontalKey(final BlockPos pos)
    {
        return new BlockPos(pos.getX(), 0, pos.getZ());
    }

    /**
     * Horizontal spots other living troops of this assault are currently standing at, so a troop (re-)picking its
     * siege spot in {@link #siegeSpot} can walk past them instead of ending up in the same block — MineColonies'
     * raider collision stops pushing entities apart once a spot gets crowded enough, so without this several
     * troops converged on one building (see {@link #assignedTarget}) would otherwise just overlap.
     */
    static Set<BlockPos> occupiedStands(final AssaultTroop troop)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null || !(assault.defender.getWorld() instanceof ServerLevel level))
        {
            return Set.of();
        }
        final Set<BlockPos> occupied = new HashSet<>();
        for (final Map.Entry<Integer, BlockPos> entry : assault.standByTroop.entrySet())
        {
            if (entry.getKey() == troop.getId())
            {
                continue;
            }
            if (level.getEntity(entry.getKey()) instanceof LivingEntity living && living.isAlive())
            {
                occupied.add(horizontalKey(entry.getValue()));
            }
        }
        return occupied;
    }

    /** Records the ground spot a troop just claimed for its current siege, so {@link #occupiedStands} can steer
      * the next troop (re-)picking a spot away from it. */
    static void claimStand(final AssaultTroop troop, final BlockPos stand)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault != null)
        {
            assault.standByTroop.put(troop.getId(), stand);
        }
    }

    /** Whether this troop's currently assigned target ({@link #assignedTarget}) is the colony's wall breach rather
      * than a building — {@link AssaultGoal}/{@link AssaultRangedGoal} branch on this to pick the right siege-spot
      * method, since there's no {@link IBuilding} behind a wall target. */
    static boolean isWallTarget(final AssaultTroop troop, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        return assault != null && assault.wallBreach != null && hut.equals(assault.wallBreach.center());
    }

    private static BlockPos wallPoint(final WallBreach breach, final int along, final int out, final int y)
    {
        return new BlockPos(breach.center().getX() + breach.alongX() * along + breach.outX() * out, y,
          breach.center().getZ() + breach.alongZ() * along + breach.outZ() * out);
    }

    /**
     * The wall-breach equivalent of {@link #siegeSpot}/{@link #rangedSiegeSpot}: there's no building box to search
     * — the wall's own running direction is already known ({@link WallBreach}) — so each troop just claims the
     * next free slot along the breach's {@link #TROOP_BREACH_HALF}-wide span (centre first, then alternating
     * outward) instead of scanning for a free wall column. {@code standoff} is how far beyond the wall's outer
     * face ({@link #WALL_OUTER_HALF} out from the centreline) the returned stand point sits — 1 for a légionnaire,
     * {@link #RANGED_STANDOFF} for an archer. Claims the near-wall point (one block past the face) regardless of
     * the caller's own standoff: occupancy is only ever compared in that one coordinate space, the lesson the
     * archer "same block" bug already taught (see {@link #rangedSiegeSpot}'s doc).
     */
    static SiegeSpot wallSiegeSpot(final AssaultTroop troop, final int standoff)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        final WallBreach breach = assault.wallBreach;
        final ServerLevel level = (ServerLevel) troop.level();
        final Set<BlockPos> occupied = occupiedStands(troop);
        final int ground = breach.center().getY();

        int slot = 0;
        int feet = Integer.MIN_VALUE;
        outer:
        for (int d = 0; d <= TROOP_BREACH_HALF; d++)
        {
            for (final int candidate : d == 0 ? new int[] {0} : new int[] {d, -d})
            {
                final BlockPos probe = wallPoint(breach, candidate, WALL_OUTER_HALF + 1, ground);
                final int candidateFeet = groundNear(level, probe.getX(), probe.getZ(), ground);
                if (candidateFeet != Integer.MIN_VALUE
                      && !occupied.contains(horizontalKey(wallPoint(breach, candidate, WALL_OUTER_HALF + 1, candidateFeet))))
                {
                    slot = candidate;
                    feet = candidateFeet;
                    break outer;
                }
            }
        }
        if (feet == Integer.MIN_VALUE)
        {
            // Every slot taken (more troops than the breach is wide), or none walkable on this terrain: stand at
            // the centre regardless — better a stacked troop than no fight at all.
            final BlockPos centre = wallPoint(breach, 0, WALL_OUTER_HALF + 1, ground);
            final int fallback = groundNear(level, centre.getX(), centre.getZ(), ground);
            feet = fallback == Integer.MIN_VALUE ? ground : fallback;
        }

        final BlockPos near = wallPoint(breach, slot, WALL_OUTER_HALF + 1, feet);
        claimStand(troop, near);
        final BlockPos wall = wallPoint(breach, slot, WALL_OUTER_HALF, feet + 1);
        final BlockPos standProbe = wallPoint(breach, slot, WALL_OUTER_HALF + standoff, feet);
        final int standFeet = groundNear(level, standProbe.getX(), standProbe.getZ(), feet);
        final BlockPos stand = wallPoint(breach, slot, WALL_OUTER_HALF + standoff, standFeet == Integer.MIN_VALUE ? feet : standFeet);
        return new SiegeSpot(wall, stand);
    }

    /**
     * Where a troop standing at {@code ground} would hit this column, or MIN_VALUE when it's open. A wall is three
     * stacked man-made blocks around body height; a roof or lintel overhead also counts, so an open doorway or a
     * porch stops the scan at the threshold instead of leading the troop inside.
     */
    private static int wallFace(final ServerLevel level, final int x, final int z, final int ground)
    {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int base = ground - 1; base <= ground + 1; base++)
        {
            if (isDesignedBlock(level.getBlockState(cursor.set(x, base, z)))
                  && isDesignedBlock(level.getBlockState(cursor.set(x, base + 1, z)))
                  && isDesignedBlock(level.getBlockState(cursor.set(x, base + 2, z))))
            {
                return Math.max(base, ground);
            }
        }
        for (int y = ground + 2; y <= ground + 4; y++)
        {
            if (isDesignedBlock(level.getBlockState(cursor.set(x, y, z))))
            {
                return ground + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Feet height closest to {@code around} (within 3) where a troop can stand in this column, or MIN_VALUE. */
    private static int groundNear(final ServerLevel level, final int x, final int z, final int around)
    {
        for (int d = 0; d <= 3; d++)
        {
            if (standable(level, x, around + d, z))
            {
                return around + d;
            }
            if (d > 0 && standable(level, x, around - d, z))
            {
                return around - d;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Highest standable feet height in this column between {@code top} and {@code bottom}, or MIN_VALUE. */
    private static int groundAt(final ServerLevel level, final int x, final int z, final int top, final int bottom)
    {
        for (int y = top; y >= bottom; y--)
        {
            if (standable(level, x, y, z))
            {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean standable(final ServerLevel level, final int x, final int y, final int z)
    {
        final BlockPos feet = new BlockPos(x, y, z);
        final BlockState below = level.getBlockState(feet.below());
        if (below.getCollisionShape(level, feet.below()).isEmpty() || !below.getFluidState().isEmpty())
        {
            return false;
        }
        for (final BlockPos body : new BlockPos[] {feet, feet.above()})
        {
            final BlockState state = level.getBlockState(body);
            if (!state.getCollisionShape(level, body).isEmpty() || !state.getFluidState().isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Worst horizontal troop→wall distance at a troop's first blow on each wall, per defender colony — the objective
     * check of "the troop batters from right in front of the wall" (WarScenario reads it). Kept past the assault's
     * end, reset at the next launch.
     */
    private static final Map<Integer, double[]> STRIKES = new HashMap<>();

    /** Per defender colony: whether its last assault was pushed back by its guards alone (every troop killed by a
      * guard). Kept past the assault's end for WarScenario, reset at the next launch. */
    private static final Map<Integer, Boolean> GARRISON_WINS = new HashMap<>();

    public static boolean repelledByGarrison(final IColony defender)
    {
        return GARRISON_WINS.getOrDefault(defender.getID(), false);
    }

    static void recordFirstBlow(final AssaultTroop troop, final BlockPos hut, final SiegeSpot spot, final double distance)
    {
        final double[] stats = STRIKES.computeIfAbsent(colonyOf(troop), id -> new double[2]);
        stats[0] = Math.max(stats[0], distance);
        stats[1]++;
        InterregneMod.LOGGER.info("[war] troupe {} frappe {} : mur {} depuis {} (cible {}), distance {}",
          troop.getId(), hut.toShortString(), spot.wall().toShortString(), troop.blockPosition().toShortString(),
          spot.stand().toShortString(), String.format(java.util.Locale.ROOT, "%.2f", distance));
    }

    /** {worst first-blow distance, number of first blows}, or null when no troop has struck yet. */
    public static double[] strikeDistances(final IColony defender)
    {
        return STRIKES.get(defender.getID());
    }

    /** Drains the pending collapse sweeps a few blocks at a time, so a building falling never costs more than
      * {@link #SWEEP_BLOCKS_PER_TICK} block edits in any single tick. */
    /**
     * MineColonies' own barbarian raids stay off whatever the server's config says (decision of 19/11): the only
     * attackers are the war assaults, which start their event directly and never go through RaidManager.canRaid.
     */
    @SubscribeEvent
    public static void onServerStarted(final net.neoforged.neoforge.event.server.ServerStartedEvent event)
    {
        final com.minecolonies.api.configuration.ServerConfiguration config =
          (com.minecolonies.api.configuration.ServerConfiguration) com.minecolonies.core.MineColonies.getConfig().getServer();
        if (config.enableColonyRaids.get())
        {
            config.enableColonyRaids.set(false);
            InterregneMod.LOGGER.info("MineColonies raids forced off: only war assaults attack colonies");
        }
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        for (final Assault assault : new ArrayList<>(BY_DEFENDER.values()))
        {
            if (assault.townHallTaken && !assault.over)
            {
                // end first: it adds this assault to the war's tally, which the victory then reads for the recap
                end(assault, "§cL'hôtel de ville de " + assault.defender.getName() + " est tombé§r : " + assault.attacker.getName() + " gagne la guerre.");
                Diplomacy.victory(event.getServer(), assault.attacker, assault.defender);
                continue;
            }
            if (!assault.over && assault.defender.getWorld() instanceof ServerLevel level)
            {
                final long left = assault.deadline - level.getGameTime();
                if (left <= 0)
                {
                    end(assault, "§aTemps écoulé§r : l'assaut sur " + assault.defender.getName() + " est repoussé.", true);
                }
                else
                {
                    if (assault.capture != null && level.getGameTime() % CAPTURE_STEP == 0)
                    {
                        stepCapture(assault, level);
                    }
                    if (level.getGameTime() % 20 == 0)
                    {
                        updateClock(assault, level, left);
                    }
                }
            }
        }
        if (PENDING_SWEEPS.isEmpty())
        {
            return;
        }
        final Iterator<SweepJob> jobs = PENDING_SWEEPS.iterator();
        while (jobs.hasNext())
        {
            final SweepJob job = jobs.next();
            for (int i = 0; i < SWEEP_BLOCKS_PER_TICK && !job.remaining.isEmpty(); i++)
            {
                job.level.destroyBlock(job.remaining.poll(), false);
            }
            if (job.remaining.isEmpty())
            {
                jobs.remove();
            }
        }
    }

    /** How often the taking of a ruined town hall looks at who stands around it, in ticks. */
    private static final int CAPTURE_STEP = 10;

    /** Whether the ruined town hall is the target the troops are on now: the last one left. */
    private static boolean capturing(final Assault assault)
    {
        return assault.capture != null && assault.targets.size() == 1 && assault.targets.get(0).equals(assault.ruinHall);
    }

    /**
     * Moves the taking of a ruined town hall on by one step when it is the target now: the progress grows while a living
     * troop stands in its zone, waits when nobody does. Complete, it counts as the town hall falling (the loot is paid
     * once, the victory follows on the next tick), and the ruin is left as it is.
     */
    private static void stepCapture(final Assault assault, final ServerLevel level)
    {
        if (!capturing(assault) || assault.townHallTaken)
        {
            return;
        }
        final BlockPos hall = assault.ruinHall;
        int present = 0;
        for (final int id : assault.troopIds)
        {
            if (level.getEntity(id) instanceof AssaultTroop troop && troop.isAlive()
                  && RuinCapture.inZone(troop.getX() - (hall.getX() + 0.5D), troop.getY() - hall.getY(), troop.getZ() - (hall.getZ() + 0.5D)))
            {
                present++;
            }
        }
        if (assault.capture.advance(present, CAPTURE_STEP))
        {
            assault.loot += takeLoot(assault, level.getServer());
            assault.townHallTaken = true;
            assault.targets.remove(hall);
            announce(assault, "§cLes ruines de l'hôtel de ville sont prises§r : " + assault.attacker.getName() + " les occupe.");
        }
    }

    /** True when the troop's target is the ruined town hall, to be taken by standing around it rather than battered. */
    static boolean isCaptureTarget(final AssaultTroop troop, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        return assault != null && assault.capture != null && hut.equals(assault.ruinHall);
    }

    /** The taking of the running assault's ruined town hall, 0 to 1, or -1 when it has none (for tests). */
    public static float captureProgress(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault == null || assault.capture == null ? -1F : assault.capture.progress();
    }

    /** The assault's clock bar, shown to the online members of both colonies wherever they are. */
    private static void updateClock(final Assault assault, final ServerLevel level, final long left)
    {
        if (assault.clock == null)
        {
            assault.clock = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        }
        final long seconds = (left + 19) / 20;
        final boolean taking = capturing(assault);
        assault.clock.setColor(taking ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        assault.clock.setName(Component.literal("⚔ Assaut sur " + assault.defender.getName() + " — "
          + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60)
          + (taking ? " · prise des ruines " + Math.round(assault.capture.progress() * 100) + " %" : "")));
        assault.clock.setProgress(Mth.clamp((float) left / CLOCK_CAP, 0.0F, 1.0F));
        for (final ServerPlayer player : level.getServer().getPlayerList().getPlayers())
        {
            if (assault.attacker.getPermissions().isColonyMember(player) || assault.defender.getPermissions().isColonyMember(player))
            {
                assault.clock.addPlayer(player);
            }
            else
            {
                assault.clock.removePlayer(player);
            }
        }
    }

    /** Closes the assault when its last troop falls; a defender guard's death buys the attacker time. */
    @SubscribeEvent
    public static void onDeath(final LivingDeathEvent event)
    {
        if (event.getEntity() instanceof EntityCitizen citizen && !BY_DEFENDER.isEmpty())
        {
            final Assault defended = BY_DEFENDER.get(citizen.getCitizenColonyHandler().getColonyId());
            // An ally's contingent is counted apart (AllyAid), not among the colony's own dead
            if (defended != null && !defended.over && !fr.kodistan.interregne.politics.AllyAid.isGuest(citizen.getCitizenData()))
            {
                defended.fallen++;
                defended.guardFights.remove(citizen.getId());
                if (isGuardOf(defended.defender, citizen))
                {
                    addTime(defended, CLOCK_PER_GUARD);
                }
            }
            return;
        }
        if (!(event.getEntity() instanceof AssaultTroop troop))
        {
            return;
        }
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null)
        {
            return;
        }
        final Entity killer = event.getSource().getEntity();
        InterregneMod.LOGGER.info("[war] troupe {} tombée : {} ({})", troop.getId(), event.getSource().getMsgId(),
          killer == null ? "?" : killer.getName().getString());
        assault.troopsDead++;
        if (killer instanceof AbstractEntityCitizen citizen && isGuardOf(assault.defender, citizen))
        {
            assault.guardKills++;
        }
        assault.troopIds.remove(Integer.valueOf(troop.getId()));
        assault.standByTroop.remove(troop.getId());
        assault.marched.remove(troop.getId());
        if (assault.troopIds.isEmpty() && !assault.over)
        {
            GARRISON_WINS.put(assault.defender.getID(), assault.guardKills == assault.troopsDead);
            end(assault, "§aAssaut repoussé§r : " + assault.defender.getName() + " tient toujours.", true);
        }
    }

    /**
     * Every blow exchanged between a troop and anything: flags the assault as engaged, stamps the defender guard
     * involved as fighting (what lets it hold a building, see {@link #guardHolds}), and on a troop getting hurt calls
     * its neighbours in ({@link #rally}). The source entity is the shooter for an arrow.
     */
    @SubscribeEvent
    public static void onDamage(final LivingDamageEvent.Post event)
    {
        if (BY_DEFENDER.isEmpty())
        {
            return;
        }
        final LivingEntity victim = event.getEntity();
        final Entity source = event.getSource().getEntity();
        if (victim instanceof AssaultTroop troop)
        {
            final Assault assault = BY_DEFENDER.get(colonyOf(troop));
            if (assault == null || !(troop.level() instanceof ServerLevel level))
            {
                return;
            }
            assault.engaged = true;
            if (source instanceof AbstractEntityCitizen citizen && isGuardOf(assault.defender, citizen))
            {
                assault.guardFights.put(citizen.getId(), level.getGameTime());
            }
            if (source instanceof LivingEntity hitter && !(hitter instanceof AssaultTroop))
            {
                rally(assault, level, troop, hitter);
            }
            return;
        }
        if (source instanceof AssaultTroop troop)
        {
            final Assault assault = BY_DEFENDER.get(colonyOf(troop));
            if (assault == null || !(troop.level() instanceof ServerLevel level))
            {
                return;
            }
            assault.engaged = true;
            // What tells a chase it's getting somewhere (AssaultTroop#keepsChasing), arrows included.
            troop.setLastHurtMob(victim);
            if (victim instanceof AbstractEntityCitizen citizen && isGuardOf(assault.defender, citizen))
            {
                assault.guardFights.put(citizen.getId(), level.getGameTime());
            }
        }
    }

    /** Troops stop at world close so they don't linger across a session without their assault. */
    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        // Survivors of a running assault go back to their towers before the world is saved: troops on the field
        // don't survive a restart.
        for (final Assault assault : new ArrayList<>(BY_DEFENDER.values()))
        {
            end(assault, "Assaut interrompu (arrêt du serveur).");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event)
    {
        BY_DEFENDER.clear();
        TARGET_CURSOR.clear();
        PENDING_SWEEPS.clear();
        archersIgnoreThreats = false;
    }

    /**
     * Readings for scenarios and dev tooling.
     */
    /**
     * Clears a hut's war-ruin status once its repair work order actually finishes (not when it's merely
     * requested — {@link fr.kodistan.interregne.colony.InstantBuilder#complete} calls this). Applies to any way the
     * building gets repaired, not just the "Tout reconstruire" button; a no-op if it wasn't ruined in the first
     * place (an ordinary repair, unrelated to the war system).
     */
    public static void onRepairCompleted(final ServerLevel level, final IColony colony, final BlockPos hut)
    {
        final WarRuinsData ruins = WarRuinsData.get(level);
        if (!ruins.isRuined(hut))
        {
            return;
        }
        ruins.clearRuined(hut);
        syncRuins(level, colony);
    }

    /** Clears the colony's wall-damage flag once its own wall chantier actually finishes — {@link Walls}' wall
      * site completion calls this (both a player's own retouch and {@link #onRebuildAll}'s repair end up here, same
      * as {@link #onRepairCompleted} covers any way a building gets fixed). A no-op if the wall wasn't marked
      * damaged in the first place (an ordinary build/upgrade, unrelated to the war system). */
    public static void onWallRepaired(final ServerLevel level, final IColony colony)
    {
        final WarRuinsData ruins = WarRuinsData.get(level);
        final String key = RegionalTreasury.keyOf(colony);
        if (!ruins.isWallDamaged(key))
        {
            return;
        }
        ruins.clearWallDamaged(key);
        syncRuins(level, colony);
    }

    /** Broadcasts this colony's current ruin count and total rebuild price, plus whether its wall is war-damaged
      * and the flat price to fix it (the town hall's "Tout reconstruire"). */
    private static void syncRuins(final ServerLevel level, final IColony colony)
    {
        final WarRuinsData ruins = WarRuinsData.get(level);
        int count = 0;
        for (final IBuilding b : colony.getBuildingManager().getBuildings().values())
        {
            if (ruins.isRuined(b.getID()))
            {
                count++;
            }
        }
        final String key = RegionalTreasury.keyOf(colony);
        final boolean wallDamaged = ruins.isWallDamaged(key);
        final long wallPrice = wallDamaged ? Walls.repairPrice(level, colony) : 0;
        PacketDistributor.sendToAllPlayers(new WarRuinsSyncPayload(key, count, (long) count * BuildCosts.forRepair(), wallDamaged, wallPrice));
    }

    /** "Tout reconstruire" clicked on the town hall screen: pays once for every ruined building and the colony's
      * wall breach, if any, then requests a repair on each — the same instant-build pipeline as any other chantier
      * for buildings (loads the blueprint, replaces only what's missing), and the wall's own retouch pipeline
      * ({@link Walls#repair}) for the wall. {@link #onRepairCompleted}/{@link #onWallRepaired} clear each one's
      * damage status once its own chantier finishes. */
    public static void onRebuildAll(final WarRebuildPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(payload.colony(), level);
        if (colony == null || !colony.getPermissions().isColonyMember(player))
        {
            return;
        }
        final WarRuinsData ruins = WarRuinsData.get(level);
        final List<IBuilding> targets = new ArrayList<>();
        for (final IBuilding b : colony.getBuildingManager().getBuildings().values())
        {
            if (ruins.isRuined(b.getID()))
            {
                targets.add(b);
            }
        }
        final boolean wallDamaged = ruins.isWallDamaged(RegionalTreasury.keyOf(colony));
        if (targets.isEmpty() && !wallDamaged)
        {
            return;
        }
        final long wallPrice = wallDamaged ? Walls.repairPrice(level, colony) : 0;
        final long cost = player.isCreative() ? 0 : (long) targets.size() * BuildCosts.forRepair() + wallPrice;
        final TreasuryPayment payment = TreasuryPayment.pay(player, colony, cost);
        if (payment == null)
        {
            player.sendSystemMessage(Component.literal("Il te faut " + Wallet.couronnes(cost, null) + " pour tout reconstruire (trésor régional : "
              + Wallet.format(RegionalTreasury.get(level.getServer()).balance(colony)) + ", sur toi : "
              + Wallet.format(Wallet.value(new PlayerMainInvWrapper(player.getInventory()))) + ")."));
            return;
        }
        if (payment.fromTreasury() > 0 || payment.fromPurse() > 0)
        {
            player.sendSystemMessage(Component.literal("Payé : " + payment.describe() + "."));
        }
        BuildContext.begin(player);
        try
        {
            for (final IBuilding building : targets)
            {
                building.requestRepair(BlockPos.ZERO);
            }
        }
        finally
        {
            BuildContext.end();
        }
        if (wallDamaged)
        {
            Walls.repair(player, colony);
        }
        final String wallNote = wallDamaged ? " et la muraille" : "";
        player.sendSystemMessage(Component.literal("§6Reconstruction lancée§r : " + targets.size() + " bâtiment(s)" + wallNote + "."));
    }

    public static boolean hasAssault(final IColony defender)
    {
        return BY_DEFENDER.containsKey(defender.getID());
    }

    /** Debug-only: forces every archer to skip its guard scan and bombard its assigned building regardless —
      * {@code /war debug archers-siege-only}, so the bombardment can be eyeballed without waiting for the
      * colony's guards to clear out on their own. Never touched outside a dev session. */
    private static boolean archersIgnoreThreats;

    static boolean archersIgnoreThreats()
    {
        return archersIgnoreThreats;
    }

    public static void setArchersIgnoreThreats(final boolean value)
    {
        archersIgnoreThreats = value;
    }

    public static int targetsLeft(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault == null ? 0 : assault.targets.size();
    }

    public static int destroyed(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault == null ? 0 : assault.destroyed;
    }

    public static long lootPaid(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault == null ? 0 : assault.loot;
    }

    /** Seconds left on the assault's clock, or -1 when no assault runs. */
    public static long secondsLeft(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        if (assault == null || !(assault.defender.getWorld() instanceof ServerLevel level))
        {
            return -1;
        }
        return Math.max(0, (assault.deadline - level.getGameTime() + 19) / 20);
    }

    /** Structure points left on a target of the running assault, or -1 when it isn't one. */
    public static int structureLeft(final IColony defender, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault == null ? -1 : assault.structure.getOrDefault(hut, -1);
    }

    /** The running assault's remaining targets, in the order they'll be attacked. */
    public static List<BlockPos> targetOrder(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault == null ? List.of() : List.copyOf(assault.targets);
    }

    /** Where the running assault's troops rallied (the targets are ordered by distance to it), or null. */
    @Nullable
    public static BlockPos rallyPoint(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault == null ? null : assault.rallyPoint;
    }

    /** The clock's ceiling in seconds, for tests. */
    public static long clockCapSeconds()
    {
        return CLOCK_CAP / 20L;
    }

    /** Whether a fighting guard currently freezes this target's structure points (read for tests). */
    public static boolean guardHolding(final IColony defender, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault != null && assault.defender.getWorld() instanceof ServerLevel level && guardHolds(assault, level, hut);
    }

    /** The defender's loaded, living guards (read for tests). */
    public static List<AbstractEntityCitizen> guardsOf(final IColony defender)
    {
        final List<AbstractEntityCitizen> guards = new ArrayList<>();
        for (final var data : defender.getCitizenManager().getCitizens())
        {
            data.getEntity().ifPresent(entity ->
            {
                if (entity.isAlive() && isGuardOf(defender, entity))
                {
                    guards.add(entity);
                }
            });
        }
        return guards;
    }

    /** Test hook ({@link fr.kodistan.interregne.dev.WarScenario}): marks a guard as having just traded a blow with a
      * troop, exactly what {@link #onDamage} does, so the freeze can be exercised without waiting for a real duel. */
    public static void debugStampFight(final IColony defender, final AbstractEntityCitizen guard)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        if (assault != null && guard.level() instanceof ServerLevel level)
        {
            assault.guardFights.put(guard.getId(), level.getGameTime());
        }
    }

    public static boolean engaged(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        return assault != null && assault.engaged;
    }

    public static String snapshotOf(final IColony defender)
    {
        final Assault assault = BY_DEFENDER.get(defender.getID());
        if (assault == null)
        {
            return "assaut terminé";
        }
        int alive = 0;
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            for (final int id : assault.troopIds)
            {
                if (level.getEntity(id) instanceof LivingEntity living && living.isAlive())
                {
                    alive++;
                }
            }
        }
        return "troupes " + alive + "/" + assault.troopIds.size() + ", cibles " + assault.targets.size()
          + ", détruits " + assault.destroyed + ", butin " + assault.loot + ", combat " + assault.engaged
          + ", chrono " + secondsLeft(defender) + " s";
    }

    static void stopAll(final MinecraftServer server, final Player reporter)
    {
        int troops = 0;
        for (final Assault assault : new ArrayList<>(BY_DEFENDER.values()))
        {
            troops += assault.troopIds.size();
            end(assault, "Assaut stoppé par un intendant.");
        }
        BY_DEFENDER.clear();
        if (reporter != null)
        {
            reporter.sendSystemMessage(Component.literal("Assauts stoppés, " + troops + " troupes retirées."));
        }
    }

    /**
     * The attacker calls its troops back from the assault on this defender: the survivors go back to the towers.
     * Returns null when done, or why not.
     */
    @Nullable
    public static String recall(final IColony attacker, final int defenderId)
    {
        final Assault assault = BY_DEFENDER.get(defenderId);
        if (assault == null || assault.over || assault.attacker.getID() != attacker.getID())
        {
            return "Aucun assaut de votre colonie en cours contre elle.";
        }
        end(assault, "Les troupes sont rappelées.");
        return null;
    }

    /** Every assault still running, as {attacker, defender} pairs. */
    public static List<IColony[]> running()
    {
        final List<IColony[]> pairs = new ArrayList<>();
        for (final Assault assault : BY_DEFENDER.values())
        {
            if (!assault.over)
            {
                pairs.add(new IColony[] {assault.attacker, assault.defender});
            }
        }
        return pairs;
    }

    /** True while this attacker has an assault running on this defender. */
    public static boolean assaulting(final int attackerId, final int defenderId)
    {
        final Assault assault = BY_DEFENDER.get(defenderId);
        return assault != null && !assault.over && assault.attacker.getID() == attackerId;
    }

    /**
     * Closes an assault however it ends: clears the cracks and health bars it left, cancels its colony event, sends
     * the troops that marched out of the attacker's garrison and are still alive back to their towers (the dead stay
     * lost) and removes what is left of the troops on the field.
     */
    private static void end(final Assault assault, final String line)
    {
        end(assault, line, false);
    }

    /**
     * @param repelled whether the defender pushed it back (its troops all fell, or its time ran out): what lets the
     *                 defender demand the attacker's surrender (see {@link Wars#surrenderWait})
     */
    private static void end(final Assault assault, final String line, final boolean repelled)
    {
        assault.over = true;
        BY_DEFENDER.remove(assault.defender.getID());
        if (assault.clock != null)
        {
            assault.clock.removeAllPlayers();
        }
        int back = 0;
        int homeless = 0;
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            for (final BlockPos pos : assault.targets)
            {
                final IBuilding building = assault.defender.getBuildingManager().getBuilding(pos);
                if (building != null && !pos.equals(assault.ruinHall))
                {
                    clearCracks(assault, level, building, pos);
                }
                removeBar(assault, pos);
            }
            for (final Marched troop : assault.marched.values())
            {
                if (Garrison.welcomeBack(level.getServer(), assault.attacker, troop.kind(), troop.level()))
                {
                    back++;
                }
                else
                {
                    homeless++;
                }
            }
            for (final int id : assault.troopIds)
            {
                if (level.getEntity(id) instanceof AssaultTroop troop)
                {
                    troop.discard();
                }
            }
        }
        final int lost = assault.sent - back - homeless;
        assault.marched.clear();
        assault.troopIds.clear();
        for (final var entry : assault.defender.getEventManager().getEvents().values())
        {
            if (entry instanceof AssautEvent)
            {
                entry.setStatus(com.minecolonies.api.colony.colonyEvents.EventStatus.CANCELED);
            }
        }
        announce(assault, line);
        if (assault.sent > 0 && assault.defender.getWorld() instanceof ServerLevel level)
        {
            Diplomacy.notifyMembers(level.getServer(), assault.attacker, "⚔ Retour de " + assault.defender.getName() + " : "
              + back + " soldat(s) rentrent aux tours, " + lost + " tombé(s)"
              + (homeless > 0 ? ", " + homeless + " sans place libre dans les tours (perdus)" : "") + ".");
        }
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            Wars.get(level.getServer()).record(assault.attacker.getID(), assault.defender.getID(), assault.sent, lost + homeless,
              assault.destroyed, assault.loot, assault.fallen);
            Wars.get(level.getServer()).finishAssault(assault.attacker.getID(), assault.defender.getID(), level.getGameTime(), repelled);
            if (repelled)
            {
                fr.kodistan.interregne.politics.InfluenceSources.repelled(level.getServer(), assault.attacker, assault.defender);
            }
            Alliances.broadcast(level.getServer(), assault.attacker, assault.defender);
        }
    }

    private static int colonyOf(final AssaultTroop troop)
    {
        return troop.getColony() == null ? -1 : troop.getColony().getID();
    }

    private static void announce(final Assault assault, final String line)
    {
        announce(assault, Component.literal(line));
    }

    /** To the commander and the online members of both colonies. */
    private static void announce(final Assault assault, final Component line)
    {
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            for (final ServerPlayer player : level.getServer().getPlayerList().getPlayers())
            {
                if (player.getUUID().equals(assault.commander) || assault.attacker.getPermissions().isColonyMember(player)
                      || assault.defender.getPermissions().isColonyMember(player))
                {
                    player.sendSystemMessage(line);
                }
            }
        }
        InterregneMod.LOGGER.info("[war] {}", line.getString());
    }

    private WarAssaults()
    {
    }
}
