package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.util.Tuple;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.pvp.SafeZones;
import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.walls.Walls;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        buildings.removeIf(b -> b.getBuildingLevel() <= 0);
        buildings.sort(Comparator.comparingDouble(b -> BlockPosUtil.getDistance(b.getID(), spawn)));
        final IBuilding townHall = buildings.stream().filter(b -> b.getID().equals(assault.defender.getCenter())).findFirst().orElse(null);
        if (townHall != null)
        {
            buildings.remove(townHall);
            buildings.add(townHall);
        }
        for (final IBuilding building : buildings)
        {
            assault.targets.add(building.getID());
            assault.structure.put(building.getID(), SP_BASE + SP_LEVEL * building.getBuildingLevel());
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

    /** One battering hit on the building the troop is standing at. */
    static void onHit(final EntLegionnaire troop, final BlockPos hut)
    {
        final Assault assault = BY_DEFENDER.get(colonyOf(troop));
        if (assault == null || !assault.structure.containsKey(hut))
        {
            return;
        }
        final int left = assault.structure.merge(hut, -1, Integer::sum);
        if (left <= 0)
        {
            destroyBuilding(assault, hut);
        }
    }

    /** Sweeps the structure, pays the loot, moves the troops on to their next target. */
    private static void destroyBuilding(final Assault assault, final BlockPos hut)
    {
        final IBuilding building = assault.defender.getBuildingManager().getBuilding(hut);
        final ServerLevel level = (ServerLevel) assault.defender.getWorld();
        if (building != null)
        {
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
                final BlockState state = level.getBlockState(cursor);
                if (!cursor.equals(hut) && isDesignedBlock(state))
                {
                    level.destroyBlock(cursor, false);
                    swept++;
                }
            }
            final long loot = LOOT_PER_LEVEL * Math.max(1, building.getBuildingLevel());
            RegionalTreasury.get(level.getServer()).credit(assault.attacker, loot);
            assault.destroyed++;
            assault.loot += loot;
            // One deep crash reads "building coming down" from anywhere in the colony.
            level.playSound(null, hut, net.minecraft.world.level.block.SoundType.STONE.getBreakSound(),
              net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
            announce(assault, "§c" + building.getBuildingType().getTranslationKey().toLowerCase() + "§r détruit ("
              + swept + " blocs) — butin " + loot + " couronnes.");
        }
        assault.structure.remove(hut);
        assault.targets.remove(hut);
    }

    /** Man-made only: terrain, water and plants are never touched (the user's war rule). */
    public static boolean isDesignedBlock(final BlockState state)
    {
        return !state.isAir() && !state.liquid() && !Terrain.natural(state);
    }

    /** Breakable blocks of a building, top layer first — the battering order. */
    static List<BlockPos> breakableBlocks(final ServerLevel level, final IBuilding building, final BlockPos hut)
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
        out.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()
          .thenComparingInt(pos -> Math.abs(pos.getX() - hut.getX()) + Math.abs(pos.getZ() - hut.getZ())));
        return out;
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
        if (assault.troopIds.isEmpty() && !assault.over)
        {
            assault.over = true;
            BY_DEFENDER.remove(assault.defender.getID());
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
        if (assault.commander != null && assault.defender.getWorld() instanceof ServerLevel level)
        {
            final Player player = level.getServer().getPlayerList().getPlayer(assault.commander);
            if (player != null)
            {
                player.sendSystemMessage(Component.literal(line));
            }
        }
        InterregneMod.LOGGER.info("[war] {}", line);
    }

    private WarAssaults()
    {
    }
}
