package fr.kodistan.interregne.colony;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * A block flying in from above to its final position, shared by every {@link Site} that shows blocks landing
 * (construction, walls, routes): launch it, then tick it until it lands.
 */
final class FlyingBlocks
{
    private static final float SCALE = 0.35F;

    record Flight(BlockPos pos, BlockState state, Display.BlockDisplay display, int launchedAt)
    {
    }

    private FlyingBlocks()
    {
    }

    /**
     * Removes the real block, replaces it with a block display drifting down from above, and returns the flight to
     * track until it lands (after flightTicks).
     */
    static Flight launch(final ServerLevel level, final BlockPos pos, final BlockState state, final int age, final float height, final RandomSource random)
    {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setBlockState(state);
        display.moveTo(pos.getX(), pos.getY(), pos.getZ());
        final float offset = (1 - SCALE) / 2;
        display.setTransformation(new Transformation(
          new Vector3f(offset + (random.nextFloat() - 0.5F) * 3, height + random.nextFloat() * 2, offset + (random.nextFloat() - 0.5F) * 3),
          new Quaternionf().rotationXYZ(random.nextFloat() * 3.14F, random.nextFloat() * 3.14F, 0),
          new Vector3f(SCALE, SCALE, SCALE),
          new Quaternionf()));
        display.addTag(ConstructionSite.FLYING_TAG);
        level.addFreshEntity(display);
        return new Flight(pos, state, display, age);
    }

    /**
     * Interpolates every flight toward its landing spot, and lands those whose time is up (or all of them, when
     * finishing the site at once).
     */
    static void tickFlights(final ServerLevel level, final List<Flight> flights, final int age, final boolean landAll, final int flightTicks)
    {
        flights.removeIf(flight -> {
            final int flown = age - flight.launchedAt();
            if (!landAll && flown == 1)
            {
                flight.display().setTransformationInterpolationDelay(0);
                flight.display().setTransformationInterpolationDuration(flightTicks);
                flight.display().setTransformation(Transformation.identity());
            }
            if (landAll || flown > flightTicks + 1)
            {
                final BlockPos pos = flight.pos();
                final BlockState current = level.getBlockState(pos);
                if (current.isAir() || !current.getFluidState().isEmpty())
                {
                    final BlockState landed = Block.updateFromNeighbourShapes(flight.state(), level, pos);
                    level.setBlock(pos, landed.isAir() ? flight.state() : landed, Block.UPDATE_ALL);
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
}
