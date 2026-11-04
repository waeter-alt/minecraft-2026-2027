package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * An apiary yields honeycombs and honey on its own (HutProduction). Its hives are scenery set up by the hut itself:
 * one per level around it, each with bees and a few flowers, unbreakable and not for harvesting.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Apiary
{
    private static final int RADIUS     = 9;
    private static final int MIN_RADIUS = 3;
    private static final int SAMPLES    = 300;
    private static final int BEES       = 2;

    private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM};

    private Apiary()
    {
    }

    /**
     * Top the apiary up to its number of hives; hives that disappeared are forgotten and replaced.
     */
    public static void placeHives(final ServerLevel level, final IBuilding building)
    {
        if (!(building instanceof BuildingBeekeeper apiary))
        {
            return;
        }
        for (final BlockPos hive : apiary.getHives())
        {
            if (level.isLoaded(hive) && !(level.getBlockState(hive).getBlock() instanceof BeehiveBlock))
            {
                apiary.removeHive(hive);
            }
        }
        final int wanted = Math.min(apiary.getBuildingLevel(), apiary.getMaximumHives());
        int missing = wanted - apiary.getHives().size();
        if (missing <= 0)
        {
            return;
        }

        final List<Tuple<BlockPos, BlockPos>> footprints = new ArrayList<>();
        for (final IBuilding other : building.getColony().getBuildingManager().getBuildings().values())
        {
            footprints.add(other.getCorners());
        }
        final List<BlockPos> placed = new ArrayList<>(apiary.getHives());
        final BlockPos center = building.getPosition();
        for (int i = 0; i < SAMPLES && missing > 0; i++)
        {
            final double angle = level.random.nextDouble() * Math.PI * 2;
            final double distance = MIN_RADIUS + level.random.nextDouble() * (RADIUS - MIN_RADIUS);
            final int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            final int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            final BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!free(level, spot, footprints, placed))
            {
                continue;
            }
            hive(level, spot);
            apiary.addHive(spot);
            placed.add(spot);
            missing--;
        }
        building.markDirty();
    }

    private static boolean free(final ServerLevel level, final BlockPos spot, final List<Tuple<BlockPos, BlockPos>> footprints, final List<BlockPos> placed)
    {
        for (final Tuple<BlockPos, BlockPos> corners : footprints)
        {
            final BlockPos a = corners.getA();
            final BlockPos b = corners.getB();
            if (spot.getX() >= Math.min(a.getX(), b.getX()) - 1 && spot.getX() <= Math.max(a.getX(), b.getX()) + 1
                  && spot.getZ() >= Math.min(a.getZ(), b.getZ()) - 1 && spot.getZ() <= Math.max(a.getZ(), b.getZ()) + 1)
            {
                return false;
            }
        }
        for (final BlockPos other : placed)
        {
            if (other.distManhattan(spot) < 3)
            {
                return false;
            }
        }
        final BlockState ground = level.getBlockState(spot.below());
        return (ground.is(BlockTags.DIRT) || ground.is(Blocks.GRASS_BLOCK)) && level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir();
    }

    private static void hive(final ServerLevel level, final BlockPos spot)
    {
        final Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
        level.setBlockAndUpdate(spot, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, facing).setValue(BeehiveBlock.HONEY_LEVEL, 3));
        if (level.getBlockEntity(spot) instanceof BeehiveBlockEntity hive)
        {
            for (int i = 0; i < BEES; i++)
            {
                hive.storeBee(BeehiveBlockEntity.Occupant.create(0));
            }
        }
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            final BlockPos flower = spot.relative(side);
            if (side != facing && level.random.nextBoolean() && level.getBlockState(flower).isAir() && level.getBlockState(flower.below()).is(BlockTags.DIRT))
            {
                level.setBlockAndUpdate(flower, FLOWERS[level.random.nextInt(FLOWERS.length)].defaultBlockState());
            }
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 0.8, spot.getZ() + 0.5, 10, 0.4, 0.4, 0.4, 0.0);
    }

    /**
     * The apiary's hives cannot be broken (except in creative).
     */
    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event)
    {
        if (!event.getPlayer().isCreative() && event.getState().getBlock() instanceof BeehiveBlock && isApiaryHive(event.getLevel(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    /**
     * Nor sheared or bottled: the honey is the apiary's, and the bees would turn on the player.
     */
    @SubscribeEvent
    public static void onUse(final PlayerInteractEvent.RightClickBlock event)
    {
        if (!event.getEntity().isCreative() && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BeehiveBlock
              && isApiaryHive(event.getLevel(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    private static boolean isApiaryHive(final LevelAccessor accessor, final BlockPos pos)
    {
        if (!(accessor instanceof Level level))
        {
            return false;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            return false;
        }
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingBeekeeper apiary && apiary.getHives().contains(pos))
            {
                return true;
            }
        }
        return false;
    }
}
