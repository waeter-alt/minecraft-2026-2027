package fr.kodistan.interregne.colony;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.AbstractWorkOrder;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.mojang.math.Transformation;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One construction in progress: places its blueprint over a set duration and turns it into a small show
 * (rune outline, building sounds, dust and smoke, blocks flying into place, a timed progress bar for nearby players).
 * Everything is vanilla, sent only to players close by, and capped per tick.
 */
public final class ConstructionSite implements Site
{
    /**
     * Tag on flying block entities, so leftovers from a crash can be cleaned up when their chunk loads.
     */
    public static final String FLYING_TAG = "interregne_flying";

    private static final int    FLIGHT_TICKS       = 8;
    private static final float  FLIGHT_HEIGHT      = 6.0F;
    private static final int    MAX_CALLS_PER_TICK = 4096;
    private static final int    MAX_DUST_PER_TICK  = 6;

    /**
     * Boss bar color per target level, RPG rarity style: grey, green, blue, purple, gold.
     */
    private static final BossEvent.BossBarColor[] LEVEL_COLORS = {
      BossEvent.BossBarColor.WHITE,
      BossEvent.BossBarColor.GREEN,
      BossEvent.BossBarColor.BLUE,
      BossEvent.BossBarColor.PURPLE,
      BossEvent.BossBarColor.YELLOW};

    /**
     * A block on its way to its final position; the real block is put back when it lands.
     */
    private record Flight(BlockPos pos, BlockState state, Display.BlockDisplay display, int launchedAt)
    {
    }

    private final ServerLevel             level;
    private final IColony                 colony;
    private final IServerWorkOrder        order;
    @Nullable
    private final IBuilding               building;
    private final InstantBuildHandler     handler;
    private final PlaceStructureOperation operation;
    private final ServerBossEvent         bossBar;
    private final Component               title;
    private final AABB                    area;
    private final RandomSource            random;
    private final List<Flight>            flights = new ArrayList<>();
    private int                           expected;
    private final int                     durationTicks;

    private int        age;
    private int        placed;
    private int        dustThisTick;
    private boolean    placementDone;
    @Nullable
    private BlockState lastPlaced;
    @Nullable
    private BlockPos   lastPlacedPos;

    public ConstructionSite(
      final ServerLevel level,
      final IColony colony,
      final IServerWorkOrder order,
      @Nullable final IBuilding building,
      final Blueprint blueprint,
      @Nullable final ServerPlayer player)
    {
        this.level = level;
        this.colony = colony;
        this.order = order;
        this.building = building;
        this.random = level.getRandom();
        this.handler = new InstantBuildHandler(level, blueprint, colony, order, building, this);
        this.operation = new PlaceStructureOperation(new StructurePlacer(handler), player);
        this.expected = Math.max(1, countChanges(level, order.getLocation(), handler.getBluePrint()));
        this.durationTicks = durationSeconds(order) * 20;
        this.area = areaOf(order, blueprint);

        final boolean leveled = order instanceof WorkOrderBuilding && order.getWorkOrderType() != WorkOrderType.REPAIR;
        this.title = leveled
                       ? Component.literal("⚒ ").append(order.getDisplayName()).append(" · niveau " + order.getTargetLevel())
                       : Component.literal("⚒ ").append(order.getDisplayName());
        final BossEvent.BossBarColor color = leveled
                                               ? LEVEL_COLORS[Mth.clamp(order.getTargetLevel(), 1, LEVEL_COLORS.length) - 1]
                                               : BossEvent.BossBarColor.WHITE;
        this.bossBar = new ServerBossEvent(title, color, BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setProgress(0);
    }

    /**
     * Construction time of an order, shared with the hut window through {@link BuildCosts}.
     */
    public static int durationSeconds(final IServerWorkOrder order)
    {
        if (order instanceof WorkOrderBuilding && order.getWorkOrderType() != WorkOrderType.REPAIR)
        {
            return BuildCosts.secondsForLevel(order.getTargetLevel());
        }
        return BuildCosts.secondsForFlat();
    }

    /**
     * Opening of the show: clear the ground of players, turn them toward the site, and announce it.
     */
    @Override
    public void start()
    {
        for (final ServerPlayer player : level.players())
        {
            if (player.getBoundingBox().intersects(area))
            {
                pushOut(player);
            }
        }
        final BlockPos center = BlockPos.containing(area.getCenter());
        level.playSound(null, center, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.6F);
        level.playSound(null, center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.8F);
        SiteEffects.runeOutline(level, area, 3);
        updateBossBar(true);
    }

    /**
     * @return true once the construction is over and the site can be dropped.
     */
    @Override
    public boolean tick()
    {
        age++;
        if (!placementDone)
        {
            placeOnSchedule();
            buildingEffects();
            if (age % 5 == 0)
            {
                updateBossBar(age % 20 == 0);
            }
        }

        tickFlights(false);

        if (placementDone && flights.isEmpty())
        {
            finish();
            return true;
        }
        return false;
    }

    /**
     * Complete the site at once, used when the server stops so nothing is left half built.
     */
    @Override
    public void finishNow()
    {
        int calls = 0;
        while (!placementDone && calls++ < 1_000_000)
        {
            if (operation.apply(level))
            {
                placementDone = true;
            }
        }
        tickFlights(true);
        finish();
    }

    /**
     * Called by the handler each time a block is put in the world.
     */
    void onBlockPlaced(final BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        if (state.isAir())
        {
            return;
        }
        // Structurize put back a block that was still flying: the flight is over, and the block was already counted
        if (flights.removeIf(flight -> {
            if (flight.pos().equals(pos))
            {
                flight.display().discard();
                return true;
            }
            return false;
        }))
        {
            return;
        }
        placed++;
        lastPlaced = state;
        lastPlacedPos = pos;

        if (dustThisTick < MAX_DUST_PER_TICK)
        {
            dustThisTick++;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
              pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
        }

        final int share = Config.FLYING_SHARE.get();
        if (share > 0 && placed % share == 0 && flights.size() < Config.MAX_FLYING.get() && canFly(state, pos))
        {
            launch(pos, state);
        }
    }

    /**
     * Called by the handler when the whole blueprint has been placed.
     */
    void onPlacementComplete()
    {
        placementDone = true;
    }

    /**
     * Place blocks so that the share placed follows the share of time elapsed.
     */
    private void placeOnSchedule()
    {
        dustThisTick = 0;
        // The estimate of blocks to place can be short (terrain, blocks placed twice): stretch it rather than overrun
        if (placed >= expected * 0.95)
        {
            expected = (int) (placed * 1.15) + 10;
        }
        // Past the deadline, finish as fast as the per-tick budget allows
        final int target = age >= durationTicks ? Integer.MAX_VALUE : (int) Math.ceil((double) expected * age / durationTicks);
        int calls = 0;
        while (placed < target && calls++ < MAX_CALLS_PER_TICK && !placementDone)
        {
            if (operation.apply(level))
            {
                placementDone = true;
            }
        }
    }

    /**
     * Ambient effects while building: material sounds, smoke puffs, chimney smoke, the odd blast, the rune outline.
     */
    private void buildingEffects()
    {
        if (lastPlaced != null && age % 3 == 0)
        {
            final SoundType sound = lastPlaced.getSoundType();
            level.playSound(null, lastPlacedPos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.7F, 0.8F + random.nextFloat() * 0.4F);
        }
        if (lastPlacedPos != null && age % 4 == 0)
        {
            level.sendParticles(ParticleTypes.POOF, lastPlacedPos.getX() + 0.5, lastPlacedPos.getY() + 0.8, lastPlacedPos.getZ() + 0.5, 3, 0.4, 0.2, 0.4, 0.02);
        }
        if (lastPlacedPos != null && age % 25 == 0)
        {
            SiteEffects.blastPuff(level, area, lastPlacedPos.getY() + 0.5, random);
        }
        if (age % 20 == 0)
        {
            SiteEffects.chimneySmoke(level, area);
        }
        if (age % 10 == 0)
        {
            SiteEffects.runeOutline(level, area, 1);
        }
    }

    private boolean canFly(final BlockState state, final BlockPos pos)
    {
        // Only plain full blocks: anything with data, support needs or gravity stays put,
        // and nothing holding back water, which would rush into the gap while the block flies
        if (state.hasBlockEntity() || state.getBlock() instanceof FallingBlock || !state.isCollisionShapeFullBlock(level, pos))
        {
            return false;
        }
        for (final Direction direction : Direction.values())
        {
            if (!level.getFluidState(pos.relative(direction)).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    private void launch(final BlockPos pos, final BlockState state)
    {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setBlockState(state);
        display.moveTo(pos.getX(), pos.getY(), pos.getZ());
        final float scale = 0.35F;
        final float offset = (1 - scale) / 2;
        display.setTransformation(new Transformation(
          new Vector3f(offset + (random.nextFloat() - 0.5F) * 3, FLIGHT_HEIGHT + random.nextFloat() * 2, offset + (random.nextFloat() - 0.5F) * 3),
          new Quaternionf().rotationXYZ(random.nextFloat() * 3.14F, random.nextFloat() * 3.14F, 0),
          new Vector3f(scale, scale, scale),
          new Quaternionf()));
        display.addTag(FLYING_TAG);
        level.addFreshEntity(display);
        flights.add(new Flight(pos, state, display, age));
    }

    private void tickFlights(final boolean landAll)
    {
        flights.removeIf(flight -> {
            final int flown = age - flight.launchedAt();
            if (!landAll && flown == 1)
            {
                // Clients have the starting pose now: interpolate to the block's real place
                flight.display().setTransformationInterpolationDelay(0);
                flight.display().setTransformationInterpolationDuration(FLIGHT_TICKS);
                flight.display().setTransformation(Transformation.identity());
            }
            if (landAll || flown > FLIGHT_TICKS + 1)
            {
                final BlockPos pos = flight.pos();
                final BlockState current = level.getBlockState(pos);
                if (current.isAir() || !current.getFluidState().isEmpty())
                {
                    level.setBlock(pos, flight.state(), Block.UPDATE_ALL);
                    if (!landAll)
                    {
                        level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
                    }
                }
                flight.display().discard();
                return true;
            }
            return false;
        });
    }

    private void finish()
    {
        bossBar.setProgress(1);
        bossBar.removeAllPlayers();
        restoreFluids();
        InterregneMod.LOGGER.info("Construction {} done: {} blocks placed (estimate {}), {} ticks for {} planned",
          order.getStructurePath(), placed, expected, age, durationTicks);
        final Vec3 top = new Vec3(area.getCenter().x, area.maxY, area.getCenter().z);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, top.x, top.y, top.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, 80, area.getXsize() / 4, 1.0, area.getZsize() / 4, 0.4);
        level.playSound(null, top.x, top.y, top.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
        InstantBuilder.complete(colony, order, building, handler.getBluePrint());
    }

    /**
     * Make the water of the plan right: sources where the plan has water, and no stray flowing water inside it.
     * Fishermen need still water, and blocks placed out of order can let it run.
     */
    private void restoreFluids()
    {
        final Blueprint blueprint = handler.getBluePrint();
        final BlockState[] palette = blueprint.getPalette();
        final short[][][] structure = blueprint.getStructure();
        final BlockPos origin = order.getLocation().subtract(blueprint.getPrimaryBlockOffset());
        for (int y = 0; y < structure.length; y++)
        {
            for (int z = 0; z < structure[y].length; z++)
            {
                for (int x = 0; x < structure[y][z].length; x++)
                {
                    final BlockState planned = palette[structure[y][z][x]];
                    if (BuiltInRegistries.BLOCK.getKey(planned.getBlock()).getNamespace().equals("structurize"))
                    {
                        continue;
                    }
                    final BlockPos pos = origin.offset(x, y, z);
                    final BlockState current = level.getBlockState(pos);
                    final boolean wantsSource = planned.getFluidState().isSource();
                    final boolean strayFlow = !current.getFluidState().isEmpty() && !current.getFluidState().isSource() && planned.getFluidState().isEmpty();
                    if ((wantsSource && !current.getFluidState().isSource()) || strayFlow)
                    {
                        level.setBlock(pos, planned, Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /**
     * Keep the bar's progress and countdown in time, and show it to players nearby only.
     */
    private void updateBossBar(final boolean refreshViewers)
    {
        bossBar.setProgress(Math.min(1.0F, (float) age / durationTicks));
        if (age % 20 == 0 || age <= 1)
        {
            bossBar.setName(SiteEffects.withCountdown(title, durationTicks - age));
        }
        if (refreshViewers)
        {
            SiteEffects.showToNearby(bossBar, level, area);
        }
    }

    /**
     * Move a player standing on the site to the nearest side, on the ground, looking at the construction.
     */
    private void pushOut(final ServerPlayer player)
    {
        final double toWest = player.getX() - area.minX;
        final double toEast = area.maxX - player.getX();
        final double toNorth = player.getZ() - area.minZ;
        final double toSouth = area.maxZ - player.getZ();
        final double min = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));

        double x = player.getX();
        double z = player.getZ();
        if (min == toWest)
        {
            x = area.minX - 1.5;
        }
        else if (min == toEast)
        {
            x = area.maxX + 1.5;
        }
        else if (min == toNorth)
        {
            z = area.minZ - 1.5;
        }
        else
        {
            z = area.maxZ + 1.5;
        }
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));

        // Face the middle of the site, a third of the way up
        final Vec3 eye = new Vec3(x, y + player.getEyeHeight(), z);
        final Vec3 look = new Vec3(area.getCenter().x, area.minY + area.getYsize() / 3, area.getCenter().z).subtract(eye);
        final float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        final float pitch = (float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);

        player.teleportTo(level, x, y, z, yaw, pitch);
        player.sendSystemMessage(Component.literal("Écarte-toi du chantier !"));
    }

    /**
     * Blocks the placement will actually change: an upgrade leaves most of the building as it is.
     */
    private static int countChanges(final ServerLevel level, final BlockPos anchor, final Blueprint blueprint)
    {
        final BlockState[] palette = blueprint.getPalette();
        final short[][][] structure = blueprint.getStructure();
        final BlockPos origin = anchor.subtract(blueprint.getPrimaryBlockOffset());
        int count = 0;
        for (int y = 0; y < structure.length; y++)
        {
            for (int z = 0; z < structure[y].length; z++)
            {
                for (int x = 0; x < structure[y][z].length; x++)
                {
                    final BlockState state = palette[structure[y][z][x]];
                    // Structurize placeholders are never placed
                    if (state.isAir() || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("structurize"))
                    {
                        continue;
                    }
                    if (level.getBlockState(origin.offset(x, y, z)).getBlock() != state.getBlock())
                    {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static AABB areaOf(final IServerWorkOrder order, final Blueprint blueprint)
    {
        if (order instanceof AbstractWorkOrder abstractOrder && abstractOrder.getBoundingBox() != null)
        {
            return abstractOrder.getBoundingBox();
        }
        final int half = Math.max(blueprint.getSizeX(), blueprint.getSizeZ()) / 2;
        final BlockPos pos = order.getLocation();
        return new AABB(pos.getX() - half, pos.getY(), pos.getZ() - half, pos.getX() + half + 1, pos.getY() + blueprint.getSizeY(), pos.getZ() + half + 1);
    }
}
