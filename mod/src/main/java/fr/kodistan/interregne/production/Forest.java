package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A forester always has something to cut: after each construction or upgrade it makes sure enough saplings or trees
 * stand around its hut (planting oaks on bare ground), and saplings near a staffed forester grow faster with its level.
 */
public final class Forest
{
    private static final int RADIUS  = 16;
    private static final int SPACING = 4;

    private Forest()
    {
    }

    /**
     * Plant oaks around the hut until saplings plus trunks reach the target for its level.
     */
    public static void plant(final ServerLevel level, final IBuilding building)
    {
        final BlockPos center = building.getPosition();
        final int wanted = 4 + building.getBuildingLevel() * Config.FOREST_SAPLINGS.get();
        int present = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                final int x = center.getX() + dx;
                final int z = center.getZ() + dz;
                if (topBlock(level, x, z, Heightmap.Types.WORLD_SURFACE).is(BlockTags.SAPLINGS)
                      || topBlock(level, x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).is(BlockTags.LOGS))
                {
                    present++;
                }
            }
        }
        if (present >= wanted)
        {
            return;
        }

        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final List<BlockPos> spots = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx += SPACING)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz += SPACING)
            {
                final int x = center.getX() + dx;
                final int z = center.getZ() + dz;
                if (insideFootprint(corners, x, z))
                {
                    continue;
                }
                final BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
                if (canPlant(level, pos))
                {
                    spots.add(pos);
                }
            }
        }
        Collections.shuffle(spots);
        for (int i = 0; i < Math.min(wanted - present, spots.size()); i++)
        {
            final BlockPos pos = spots.get(i);
            level.setBlockAndUpdate(pos, Blocks.OAK_SAPLING.defaultBlockState());
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /**
     * Give each sapling around the hut a chance to grow a stage, higher with the forester's level.
     */
    public static void grow(final ServerLevel level, final IBuilding building)
    {
        final double chance = Config.SAPLING_GROWTH.get() * building.getBuildingLevel();
        if (chance <= 0)
        {
            return;
        }
        final BlockPos center = building.getPosition();
        final int radius = RADIUS + 4;
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                final int x = center.getX() + dx;
                final int z = center.getZ() + dz;
                final BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                final BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof SaplingBlock sapling && level.random.nextDouble() < chance)
                {
                    sapling.advanceTree(level, pos, state, level.random);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.0);
                }
            }
        }
    }

    private static BlockState topBlock(final ServerLevel level, final int x, final int z, final Heightmap.Types type)
    {
        return level.getBlockState(new BlockPos(x, level.getHeight(type, x, z) - 1, z));
    }

    /**
     * Bare grass or dirt with room for a tree above.
     */
    private static boolean canPlant(final ServerLevel level, final BlockPos pos)
    {
        if (!level.getBlockState(pos.below()).is(BlockTags.DIRT) || !Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos))
        {
            return false;
        }
        for (int dy = 0; dy < 6; dy++)
        {
            if (!level.getBlockState(pos.above(dy)).isAir())
            {
                return false;
            }
        }
        return true;
    }

    private static boolean insideFootprint(final Tuple<BlockPos, BlockPos> corners, final int x, final int z)
    {
        final int minX = Math.min(corners.getA().getX(), corners.getB().getX()) - 2;
        final int maxX = Math.max(corners.getA().getX(), corners.getB().getX()) + 2;
        final int minZ = Math.min(corners.getA().getZ(), corners.getB().getZ()) - 2;
        final int maxZ = Math.max(corners.getA().getZ(), corners.getB().getZ()) + 2;
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
