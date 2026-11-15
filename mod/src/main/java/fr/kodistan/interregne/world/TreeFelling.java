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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Breaking any part of a natural tree's trunk fells the whole tree — by hand, by axe, in creative, or when our own
 * route terraforming (see routes.RouteBuilder) clears a log directly with setBlock. Independent of the Fell mod (which
 * only fells for an axe actually mining in survival): this covers every way a trunk block can go.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class TreeFelling
{
    private static final int MAX_LOGS = 48;
    private static final int MAX_LEAVES = 1200;
    /** A leaf lives within this many leaves of a log (vanilla). */
    private static final int LEAF_DISTANCE = 6;

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
     * Removes every log connected to the broken one (trunk and branches) and every leaf left with no tree to hang
     * from, except the block that was already being broken — vanilla finishes that one itself.
     */
    private static void fell(final ServerLevel level, final BlockPos origin)
    {
        final Set<Long> logs = new HashSet<>();
        final Set<Long> leaves = new HashSet<>();
        trunk(level, origin, logs);
        orphans(level, logs, leaves);
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

    /**
     * Adds to logs the whole tree a log belongs to: every log connected to it, trunk and branches.
     */
    public static void trunk(final ServerLevel level, final BlockPos origin, final Set<Long> logs)
    {
        final Deque<BlockPos> queue = new ArrayDeque<>();
        int found = 0;
        if (logs.add(origin.asLong()))
        {
            queue.add(origin.immutable());
        }
        while (!queue.isEmpty() && found < MAX_LOGS)
        {
            final BlockPos at = queue.poll();
            found++;
            for (final BlockPos near : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1)))
            {
                if (!logs.contains(near.asLong()) && level.getBlockState(near).is(BlockTags.LOGS))
                {
                    logs.add(near.asLong());
                    queue.add(near.immutable());
                }
            }
        }
    }

    /**
     * Adds to leaves every natural leaf that the removal of these logs leaves hanging from nothing: the game's own
     * rule (a leaf lives within six leaves of a log), worked out at once instead of waiting for the leaves to decay.
     * A leaf that another tree still holds stays, so felling one tree never strips its neighbours.
     */
    public static void orphans(final ServerLevel level, final Set<Long> logs, final Set<Long> leaves)
    {
        // Every leaf the felled logs could have been holding
        final Map<Long, Integer> distance = new HashMap<>();
        Deque<BlockPos> ring = new ArrayDeque<>();
        for (final long packed : logs)
        {
            ring.add(BlockPos.of(packed));
        }
        for (int reach = 0; reach < LEAF_DISTANCE && !ring.isEmpty() && distance.size() < MAX_LEAVES; reach++)
        {
            final Deque<BlockPos> next = new ArrayDeque<>();
            for (final BlockPos from : ring)
            {
                for (final Direction dir : Direction.values())
                {
                    final BlockPos near = from.relative(dir);
                    final BlockState state = level.getBlockState(near);
                    if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT) && distance.putIfAbsent(near.asLong(), LEAF_DISTANCE + 1) == null)
                    {
                        next.add(near);
                    }
                }
            }
            ring = next;
        }
        // How far each one now is from a log that stays, directly or through leaves outside the set
        for (final Map.Entry<Long, Integer> entry : distance.entrySet())
        {
            final BlockPos pos = BlockPos.of(entry.getKey());
            int best = LEAF_DISTANCE + 1;
            for (final Direction dir : Direction.values())
            {
                final BlockPos near = pos.relative(dir);
                final long packed = near.asLong();
                if (logs.contains(packed) || distance.containsKey(packed))
                {
                    continue;
                }
                final BlockState state = level.getBlockState(near);
                if (state.is(BlockTags.LOGS))
                {
                    best = 1;
                }
                else if (state.getBlock() instanceof LeavesBlock)
                {
                    best = Math.min(best, state.getValue(LeavesBlock.DISTANCE) + 1);
                }
            }
            entry.setValue(best);
        }
        for (int d = 1; d < LEAF_DISTANCE; d++)
        {
            for (final Map.Entry<Long, Integer> entry : distance.entrySet())
            {
                if (entry.getValue() != d)
                {
                    continue;
                }
                final BlockPos pos = BlockPos.of(entry.getKey());
                for (final Direction dir : Direction.values())
                {
                    distance.computeIfPresent(pos.relative(dir).asLong(), (key, old) -> Math.min(old, entry.getValue() + 1));
                }
            }
        }
        for (final Map.Entry<Long, Integer> entry : distance.entrySet())
        {
            if (entry.getValue() > LEAF_DISTANCE)
            {
                leaves.add(entry.getKey());
            }
        }
    }
}
