package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.buildings.IBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fisher huts bring their own pond, which leaks on uneven ground and floods the surroundings with currents.
 * Once built, every gap beside or under a water source is plugged, then the stray flowing water around is cleared.
 */
public final class WaterSeal
{
    private static final int MARGIN = 8;

    private WaterSeal()
    {
    }

    public static void seal(final ServerLevel level, final IBuilding building)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final int minX = Math.min(corners.getA().getX(), corners.getB().getX());
        final int maxX = Math.max(corners.getA().getX(), corners.getB().getX());
        final int minY = Math.min(corners.getA().getY(), corners.getB().getY());
        final int maxY = Math.max(corners.getA().getY(), corners.getB().getY());
        final int minZ = Math.min(corners.getA().getZ(), corners.getB().getZ());
        final int maxZ = Math.max(corners.getA().getZ(), corners.getB().getZ());

        for (final BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ))
        {
            final BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().is(FluidTags.WATER) || !state.getFluidState().isSource())
            {
                continue;
            }
            for (final Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN})
            {
                final BlockPos next = pos.relative(side);
                if (leaks(level.getBlockState(next)))
                {
                    level.setBlockAndUpdate(next, Blocks.DIRT.defaultBlockState());
                }
            }
        }

        for (final BlockPos pos : BlockPos.betweenClosed(minX - MARGIN, minY - MARGIN, minZ - MARGIN, maxX + MARGIN, maxY, maxZ + MARGIN))
        {
            final BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER) && !state.getFluidState().isSource() && state.is(Blocks.WATER))
            {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * Air, a plant or flowing water next to a source: water escapes through it.
     */
    private static boolean leaks(final BlockState state)
    {
        if (state.getFluidState().is(FluidTags.WATER))
        {
            return !state.getFluidState().isSource() && state.is(Blocks.WATER);
        }
        return state.isAir() || state.canBeReplaced();
    }
}
