package fr.kodistan.interregne.routes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One block of a route to set; air clears what stood there.
 */
public record RouteStep(BlockPos pos, BlockState state)
{
}
