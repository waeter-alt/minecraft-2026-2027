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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final Map<Integer, Assault> BY_DEFENDER = new HashMap<>();

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
        final Map<Integer, BlockPos> byTroop   = new HashMap<>();
        final Map<BlockPos, ServerBossEvent> bars = new HashMap<>();
        int cursor;
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
     * Launches an assault of {@code count} légionnaires against {@code defender}.
     *
     * @return a human-readable refusal, or null when the assault is on.
     */
    public static String launch(final ServerLevel level, final IColony attacker, final IColony defender, final Player commander, final int count)
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
          .add(new com.minecolonies.core.colony.events.raid.RaidManager.RaidHistory(count, level.getGameTime()));

        final Assault assault = new Assault(defender, attacker, commander != null ? commander.getUUID() : null);
        BY_DEFENDER.put(defender.getID(), assault);
        STRIKES.remove(defender.getID());

        for (int i = 0; i < count; i++)
        {
            final EntLegionnaire troop = WarEntities.LEGIONNAIRE.get().create(level);
            if (troop == null)
            {
                continue;
            }
            final BlockPos at = BlockPosUtil.findAround(level, spawnPos.offset(i % 3, 0, i / 3), 4, 3, BlockPosUtil.SOLID_AIR_POS_SELECTOR);
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
        if (assault.troopIds.isEmpty())
        {
            BY_DEFENDER.remove(defender.getID());
            return "Le spawn des troupes a échoué.";
        }

        planTargets(assault);
        if (commander != null)
        {
            commander.sendSystemMessage(Component.literal(
              "§6Assaut lancé§r : " + assault.troopIds.size() + " légionnaires en route vers " + defender.getName() + "."));
        }
        return null;
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

    /** Every building is a target; the town hall goes last (victory target of the full war system). */
    private static void planTargets(final Assault assault)
    {
        final BlockPos spawn = assault.defender.getCenter();
        final List<IBuilding> buildings = new ArrayList<>(assault.defender.getBuildingManager().getBuildings().values());
        final WarRuinsData ruins = assault.defender.getWorld() instanceof ServerLevel level ? WarRuinsData.get(level) : null;
        buildings.removeIf(b -> b.getBuildingLevel() <= 0 || (ruins != null && ruins.isRuined(b.getID())));
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

    static boolean isEngaged(final EntLegionnaire troop)
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
     * The troop's own building: sticky per troop, handed out round-robin (targets already held by a living troop
     * are skipped). The march ({@link AssaultGoal}) and the battering point at this same structure, so a pillager
     * charging a building is destroying THAT building. Re-rolls when the assignment falls out of the target list
     * (destroyed).
     */
    public static BlockPos assignedTarget(final EntLegionnaire troop)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null || assault.targets.isEmpty())
        {
            return null;
        }
        final BlockPos held = assault.byTroop.get(troop.getId());
        if (held != null && assault.targets.contains(held))
        {
            return held;
        }
        assault.byTroop.remove(troop.getId());

        final Set<BlockPos> taken = new HashSet<>();
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            for (final Map.Entry<Integer, BlockPos> entry : assault.byTroop.entrySet())
            {
                if (level.getEntity(entry.getKey()) instanceof LivingEntity living && living.isAlive())
                {
                    taken.add(entry.getValue());
                }
            }
        }
        BlockPos next = null;
        for (int i = 0; i < assault.targets.size(); i++)
        {
            final BlockPos candidate = assault.targets.get((assault.cursor + i) % assault.targets.size());
            if (!taken.contains(candidate))
            {
                next = candidate;
                assault.cursor += i + 1;
                break;
            }
        }
        if (next == null)
        {
            // More troops than surviving buildings: share them round-robin instead.
            next = assault.targets.get(assault.cursor++ % assault.targets.size());
        }
        assault.byTroop.put(troop.getId(), next);
        return next;
    }

    /** Mining-crack stage (0-9) of the building under battering: how close each blow leaves it to collapse. */
    static int crackStage(final EntLegionnaire troop, final BlockPos hut)
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

    /** One battering hit on the building the troop is standing at. */
    static void onHit(final EntLegionnaire troop, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null || !assault.structure.containsKey(hut))
        {
            return;
        }
        final int left = assault.structure.merge(hut, -1, Integer::sum);
        updateBar(assault, hut, left);
        if (assault.defender.getWorld() instanceof ServerLevel level)
        {
            final IBuilding building = assault.defender.getBuildingManager().getBuilding(hut);
            if (building != null)
            {
                damageEffects(level, building);
                updateCracks(level, building, hut, crackStage(troop, hut));
            }
        }
        if (left <= 0)
        {
            destroyBuilding(assault, hut);
        }
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

    /** Crack overlay across the whole building, intensity matching its remaining structure points — every standing
      * block visibly deteriorates together, not just the one spot being hit. The hut itself is excluded by
      * {@link #footprintBlocks}: it's the one block in the footprint meant to survive regardless, so it never cracks. */
    private static void updateCracks(final ServerLevel level, final IBuilding building, final BlockPos hut, final int stage)
    {
        for (final BlockPos pos : footprintBlocks(level, building, hut))
        {
            level.destroyBlockProgress(breakerIdFor(pos), pos, stage);
        }
    }

    /** Drops every crack overlay this building is currently showing. */
    private static void clearCracks(final ServerLevel level, final IBuilding building, final BlockPos hut)
    {
        for (final BlockPos pos : footprintBlocks(level, building, hut))
        {
            level.destroyBlockProgress(breakerIdFor(pos), pos, -1);
        }
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

    /** Shows (and keeps in sync) the building's health bar, visible to nearby players only. */
    private static void updateBar(final Assault assault, final BlockPos hut, final int left)
    {
        if (!(assault.defender.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final int total = Math.max(1, assault.spTotal.getOrDefault(hut, left));
        final ServerBossEvent bar = assault.bars.computeIfAbsent(hut, h ->
        {
            final IBuilding b = assault.defender.getBuildingManager().getBuilding(h);
            final Component name = b != null ? Component.translatableEscape(b.getBuildingDisplayName()) : Component.literal("bâtiment");
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
            clearCracks(level, building, hut);
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            final BlockPos min = new BlockPos(Math.min(corners.getA().getX(), corners.getB().getX()),
              Math.min(corners.getA().getY(), corners.getB().getY()),
              Math.min(corners.getA().getZ(), corners.getB().getZ()));
            final BlockPos max = new BlockPos(Math.max(corners.getA().getX(), corners.getB().getX()),
              Math.max(corners.getA().getY(), corners.getB().getY()),
              Math.max(corners.getA().getZ(), corners.getB().getZ()));
            int swept = 0;
            for (BlockPos cursor : BlockPos.betweenClosed(min, max))
            {
                if (cursor.equals(hut) || !isDesignedBlock(level.getBlockState(cursor)))
                {
                    continue;
                }
                // The foundation row stays standing, and roughly a third of the rest survives by chance — a
                // ruin the player can still read as "that building", not a bare swept plot.
                if (cursor.getY() == min.getY() || level.random.nextFloat() < RUIN_SURVIVAL_CHANCE)
                {
                    continue;
                }
                level.destroyBlock(cursor, false);
                swept++;
            }
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
        assault.byTroop.values().removeIf(hut::equals);
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
     */
    static SiegeSpot siegeSpot(final ServerLevel level, final IBuilding building, final BlockPos hut, final BlockPos from)
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

        SiegeSpot spot = scanInward(level, ex, ez, ix, iz, length, hut, minY);
        if (spot == null)
        {
            // The troop's line can slip through a gap (an open yard side): the hut's own line always meets the house.
            spot = scanInward(level, alongX ? ex : hut.getX(), alongX ? hut.getZ() : ez, ix, iz, length, hut, minY);
        }
        if (spot == null)
        {
            final int feet = groundAt(level, ex - ix, ez - iz, hut.getY() + 4, minY - 4);
            spot = new SiegeSpot(new BlockPos(ex, hut.getY() + 1, ez),
              new BlockPos(ex - ix, feet == Integer.MIN_VALUE ? hut.getY() : feet, ez - iz));
        }
        return spot;
    }

    private static SiegeSpot scanInward(final ServerLevel level, final int ex, final int ez, final int ix, final int iz,
      final int length, final BlockPos hut, final int minY)
    {
        int ground = groundAt(level, ex - ix, ez - iz, hut.getY() + 4, minY - 4);
        if (ground == Integer.MIN_VALUE)
        {
            ground = hut.getY();
        }
        BlockPos stand = new BlockPos(ex - ix, ground, ez - iz);
        for (int i = 0; i <= length; i++)
        {
            final int x = ex + ix * i;
            final int z = ez + iz * i;
            final int wallY = wallFace(level, x, z, ground);
            if (wallY != Integer.MIN_VALUE)
            {
                return new SiegeSpot(new BlockPos(x, wallY, z), stand);
            }
            final int feet = groundNear(level, x, z, ground);
            if (feet != Integer.MIN_VALUE)
            {
                ground = feet;
                stand = new BlockPos(x, feet, z);
            }
        }
        return null;
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

    static void recordFirstBlow(final EntLegionnaire troop, final BlockPos hut, final SiegeSpot spot, final double distance)
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

    /** Closes the assault when its last troop falls. */
    @SubscribeEvent
    public static void onDeath(final LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof EntLegionnaire troop))
        {
            return;
        }
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null)
        {
            return;
        }
        assault.troopIds.remove(Integer.valueOf(troop.getId()));
        assault.byTroop.remove(troop.getId());
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
                        clearCracks(level, building, pos);
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
        if (victim instanceof EntLegionnaire || (victim instanceof EntityCitizen && !BY_DEFENDER.isEmpty()))
        {
            for (final Assault assault : BY_DEFENDER.values())
            {
                if (victim instanceof EntLegionnaire ? colonyOf((EntLegionnaire) victim) == assault.defender.getID()
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

    /** Broadcasts this colony's current ruin count and total rebuild price (the town hall's "Tout reconstruire"). */
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
        PacketDistributor.sendToAllPlayers(new WarRuinsSyncPayload(RegionalTreasury.keyOf(colony), count, (long) count * BuildCosts.forRepair()));
    }

    /** "Tout reconstruire" clicked on the town hall screen: pays once for every ruined building, then requests a
     * repair on each — the same instant-build pipeline as any other chantier (loads the blueprint, replaces only
     * what's missing). {@link #onRepairCompleted} clears each one's ruin status once its own chantier finishes. */
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
        if (targets.isEmpty())
        {
            return;
        }
        final long cost = player.isCreative() ? 0 : (long) targets.size() * BuildCosts.forRepair();
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
        player.sendSystemMessage(Component.literal("§6Reconstruction lancée§r : " + targets.size() + " bâtiment(s)."));
    }

    public static boolean hasAssault(final IColony defender)
    {
        return BY_DEFENDER.containsKey(defender.getID());
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
                        clearCracks(level, building, pos);
                    }
                    removeBar(assault, pos);
                }
            }
            for (final int id : assault.troopIds)
            {
                if (assault.defender.getWorld() instanceof ServerLevel level
                      && level.getEntity(id) instanceof EntLegionnaire troop)
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
            reporter.sendSystemMessage(Component.literal("Assauts stoppés, " + troops + " légionnaires retirés."));
        }
    }

    private static int colonyOf(final EntLegionnaire troop)
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
