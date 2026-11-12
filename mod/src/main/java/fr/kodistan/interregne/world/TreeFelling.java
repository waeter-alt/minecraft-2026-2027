package fr.kodistan.interregne.world;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.walls.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Breaking any part of a natural tree's trunk fells the whole tree — by hand, by axe, in creative, or when our own
 * route terraforming (see routes.RoutePath) clears a log directly with setBlock. Independent of the Fell mod (which
 * only fells for an axe actually mining in survival): this covers every way a trunk block can go.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class TreeFelling
{
    private static final int MAX_LOGS = 48;
    private static final int MAX_LEAVES = 400;

    private TreeFelling()
    {
    }

    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().is(BlockTags.LOGS)
              || !Terrain.treeLog(level, event.getPos()))
        {
            return;
        }
        fell(level, event.getPos());
    }

    /**
     * Removes every log connected to the broken one (trunk and branches) and every natural leaf attached to them,
     * except the block that was already being broken — vanilla finishes that one itself.
     */
    private static void fell(final ServerLevel level, final BlockPos origin)
    {
        final Set<Long> logs = new HashSet<>();
        final Deque<BlockPos> logQueue = new ArrayDeque<>();
        logs.add(origin.asLong());
        logQueue.add(origin);
        while (!logQueue.isEmpty() && logs.size() < MAX_LOGS)
        {
            final BlockPos at = logQueue.poll();
            for (final BlockPos near : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1)))
            {
                if (near.equals(at) || !logs.add(near.asLong()))
                {
                    continue;
                }
                if (level.getBlockState(near).is(BlockTags.LOGS))
                {
                    logQueue.add(near.immutable());
                }
                else
                {
                    logs.remove(near.asLong());
                }
            }
        }

        final Set<Long> leaves = new HashSet<>();
        final Deque<BlockPos> leafQueue = new ArrayDeque<>();
        for (final long packed : logs)
        {
            queueLeaves(level, BlockPos.of(packed), leaves, leafQueue);
        }
        while (!leafQueue.isEmpty() && leaves.size() < MAX_LEAVES)
        {
            queueLeaves(level, leafQueue.poll(), leaves, leafQueue);
        }

        for (final long packed : logs)
        {
            final BlockPos pos = BlockPos.of(packed);
            if (!pos.equals(origin))
            {
                level.destroyBlock(pos, false);
            }
        }
        for (final long packed : leaves)
        {
            level.destroyBlock(BlockPos.of(packed), false);
        }
    }

    private static void queueLeaves(final ServerLevel level, final BlockPos from, final Set<Long> leaves, final Deque<BlockPos> queue)
    {
        for (final Direction dir : Direction.values())
        {
            final BlockPos near = from.relative(dir);
            final BlockState state = level.getBlockState(near);
            if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT) && leaves.add(near.asLong()))
            {
                queue.add(near.immutable());
            }
        }
    }
}
