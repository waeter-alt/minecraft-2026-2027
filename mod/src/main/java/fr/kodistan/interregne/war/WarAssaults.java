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

    /** Couronnes paid to the attacker's treasury when a building falls. */
    private static final long LOOT_PER_LEVEL = 25;

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
        int destroyed;
        long loot;
        boolean engaged;
        boolean over;

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
        if (BY_DEFENDER.containsKey(defender.getID()))
        {
            return "Cette colonie subit déjà un assaut.";
        }
        if (SafeZones.get(level.getServer()).at(level.dimension(), defender.getCenter()) != null)
        {
            return "Impossible : cette colonie est en zone sûre.";
        }
        if (defender.getBuildingManager().getBuildings().isEmpty())
        {
            return "Impossible : cette colonie n'a aucun bâtiment.";
        }

        final BlockPos spawnPos = pickSpawn(level, defender);
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

        spawnTroops(level, WarEntities.LEGIONNAIRE.get(), legionnaires, 0, spawnPos, defender, event, assault);
        spawnTroops(level, WarEntities.ARCHER.get(), archers, legionnaires, spawnPos, defender, event, assault);
        if (assault.troopIds.isEmpty())
        {
            BY_DEFENDER.remove(defender.getID());
            return "Le spawn des troupes a échoué.";
        }

        planTargets(assault, level, spawnPos);
        if (commander != null)
        {
            commander.sendSystemMessage(Component.literal(
              "§6Assaut lancé§r : " + legionnaires + " légionnaire(s), " + archers + " archer(s) en route vers " + defender.getName() + "."));
        }
        return null;
    }

    /** Spawns {@code count} troops of one type, rallied around {@code spawnPos} — {@code offset} just keeps two
      * calls for two troop types (see {@link #launch}) from spawning on top of each other. */
    private static <T extends AssaultTroop> void spawnTroops(final ServerLevel level, final EntityType<T> type, final int count,
      final int offset, final BlockPos spawnPos, final IColony defender, final AssautEvent event, final Assault assault)
    {
        for (int i = 0; i < count; i++)
        {
            final T troop = type.create(level);
            if (troop == null)
            {
                continue;
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
                final BlockPos pos = defender.getCenter().offset((int) (Math.cos(angle) * margin), 0, (int) (Math.sin(angle) * margin));
                final BlockPos found = BlockPosUtil.findAround(level, pos, 6, 6, BlockPosUtil.SOLID_AIR_POS_SELECTOR);
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

    /** Every building is a target, the town hall last (victory target of the full war system) — but if the colony
      * has a wall, it's the very first target of all: see {@link #planWallBreach}. */
    private static void planTargets(final Assault assault, final ServerLevel level, final BlockPos rallyPoint)
    {
        planWallBreach(assault, level, rallyPoint);

        final BlockPos spawn = assault.defender.getCenter();
        final List<IBuilding> buildings = new ArrayList<>(assault.defender.getBuildingManager().getBuildings().values());
        final WarRuinsData ruins = WarRuinsData.get(level);
        buildings.removeIf(b -> b.getBuildingLevel() <= 0 || ruins.isRuined(b.getID()));
        buildings.sort(Comparator.comparingDouble(b -> BlockPosUtil.getDistance(b.getID(), spawn)));
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

    static boolean isEngaged(final AssaultTroop troop)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        return assault != null && assault.engaged;
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
        final int left = assault.structure.merge(hut, -1, Integer::sum);
        final boolean isWall = assault.wallBreach != null && hut.equals(assault.wallBreach.center());
        boolean showEffects = false;
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            showEffects = effectsDue(assault, level, hut);
            if (showEffects)
            {
                updateBar(assault, hut, left, isWall);
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

    /** Shows (and keeps in sync) the target's health bar, visible to nearby players only. */
    private static void updateBar(final Assault assault, final BlockPos hut, final int left, final boolean isWall)
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
            final long loot = LOOT_PER_LEVEL * Math.max(1, building.getBuildingLevel());
            RegionalTreasury.get(level.getServer()).credit(assault.attacker, loot);
            assault.destroyed++;
            assault.loot += loot;
            // One deep crash reads "building coming down" from anywhere in the colony.
            level.playSound(null, hut, net.minecraft.world.level.block.SoundType.STONE.getBreakSound(),
              net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
            announce(assault, Component.literal("§c").append(Component.translatableEscape(building.getBuildingDisplayName()))
              .append(Component.literal("§r en ruines (" + swept + " blocs) — butin " + loot + " couronnes.")));
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
    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
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

    /** Closes the assault when its last troop falls. */
    @SubscribeEvent
    public static void onDeath(final LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof AssaultTroop troop))
        {
            return;
        }
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null)
        {
            return;
        }
        assault.troopIds.remove(Integer.valueOf(troop.getId()));
        assault.standByTroop.remove(troop.getId());
        if (assault.troopIds.isEmpty() && !assault.over)
        {
            assault.over = true;
            BY_DEFENDER.remove(assault.defender.getID());
            // The fight's done but these buildings aren't: don't leave their crack overlay or health bar stuck
            // showing to players forever.
            if (assault.defender.getWorld() instanceof ServerLevel level)
            {
                for (final BlockPos pos : assault.targets)
                {
                    final IBuilding building = assault.defender.getBuildingManager().getBuilding(pos);
                    if (building != null)
                    {
                        clearCracks(assault, level, building, pos);
                    }
                    removeBar(assault, pos);
                }
            }
            announce(assault, "§aAssaut repoussé§r : la colonie tient toujours.");
        }
    }

    /** The engagement flag: any hit exchanged between a troop and a defender. */
    @SubscribeEvent
    public static void onDamage(final LivingDamageEvent.Post event)
    {
        final LivingEntity victim = event.getEntity();
        if (victim instanceof AssaultTroop || (victim instanceof EntityCitizen && !BY_DEFENDER.isEmpty()))
        {
            for (final Assault assault : BY_DEFENDER.values())
            {
                if (victim instanceof AssaultTroop troop ? colonyOf(troop) == assault.defender.getID()
                      : victim.level() == assault.defender.getWorld())
                {
                    assault.engaged = true;
                }
            }
        }
    }

    /** Troops stop at world close so they don't linger across a session without their assault. */
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
          + ", détruits " + assault.destroyed + ", butin " + assault.loot + ", combat " + assault.engaged;
    }

    static void stopAll(final MinecraftServer server, final Player reporter)
    {
        int troops = 0;
        for (final Assault assault : BY_DEFENDER.values())
        {
            if (assault.defender.getWorld() instanceof ServerLevel level)
            {
                for (final BlockPos pos : assault.targets)
                {
                    final IBuilding building = assault.defender.getBuildingManager().getBuilding(pos);
                    if (building != null)
                    {
                        clearCracks(assault, level, building, pos);
                    }
                    removeBar(assault, pos);
                }
            }
            for (final int id : assault.troopIds)
            {
                if (assault.defender.getWorld() instanceof ServerLevel level
                      && level.getEntity(id) instanceof AssaultTroop troop)
                {
                    troop.discard();
                    troops++;
                }
            }
            for (final var entry : assault.defender.getEventManager().getEvents().values())
            {
                if (entry instanceof AssautEvent)
                {
                    entry.setStatus(com.minecolonies.api.colony.colonyEvents.EventStatus.CANCELED);
                }
            }
        }
        BY_DEFENDER.clear();
        if (reporter != null)
        {
            reporter.sendSystemMessage(Component.literal("Assauts stoppés, " + troops + " troupes retirées."));
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

    private static void announce(final Assault assault, final Component line)
    {
        if (assault.commander != null && assault.defender.getWorld() instanceof ServerLevel level)
        {
            final Player player = level.getServer().getPlayerList().getPlayer(assault.commander);
            if (player != null)
            {
                player.sendSystemMessage(line);
            }
        }
        InterregneMod.LOGGER.info("[war] {}", line.getString());
    }

    private WarAssaults()
    {
    }
}
