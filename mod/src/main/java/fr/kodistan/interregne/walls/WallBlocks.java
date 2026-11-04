package fr.kodistan.interregne.walls;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a laid-out wall into the blocks to set, in building order: along the trace, bottom to top, so the wall rises
 * as a wave following the path. Trees and grass in its way are cleared first (set to air); a wall on water stands on
 * piles down to the bottom.
 */
public final class WallBlocks
{
    /**
     * One block to set; air clears.
     */
    public record Step(BlockPos pos, BlockState state, int order)
    {
    }

    /**
     * The blocks of one level's look.
     */
    private record Style(BlockState body, BlockState cap, BlockState spike, BlockState tower, BlockState towerTop, BlockState rail, BlockState pile)
    {
    }

    private static final Style[] STYLES = {
      new Style(Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(),
        Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState()),
      new Style(Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE_WALL.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()),
      new Style(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(),
        Blocks.STONE_BRICKS.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState()),
      new Style(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(),
        Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(),
        Blocks.COBBLED_DEEPSLATE.defaultBlockState()),
      new Style(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()),
    };

    private static final int CLEAR_ABOVE = 24;

    private WallBlocks()
    {
    }

    public static List<Step> of(final LevelReader level, final WallLayout layout, final int wallLevel)
    {
        final Style style = STYLES[Math.max(1, Math.min(STYLES.length, wallLevel)) - 1];
        final List<Step> steps = new ArrayList<>();
        for (final WallLayout.Column column : layout.columns())
        {
            final int order = column.index() * 2;
            clear(level, steps, column.x(), column.z(), column.bottom(), column.top() + CLEAR_ABOVE, order);
            piles(level, steps, column.x(), column.z(), style, order);
            for (int y = column.bottom(); y <= column.top(); y++)
            {
                steps.add(new Step(new BlockPos(column.x(), y, column.z()), y == column.top() ? style.cap() : style.body(), order));
            }
            // Spikes or merlons on every other block of the crest
            if (((column.x() + column.z()) & 1) == 0)
            {
                steps.add(new Step(new BlockPos(column.x(), column.top() + 1, column.z()), style.spike(), order));
            }
        }
        for (final WallLayout.Tower tower : layout.towers())
        {
            final int order = tower.index() * 2 + 1;
            final int last = tower.size() - 1;
            for (int dx = 0; dx <= last; dx++)
            {
                for (int dz = 0; dz <= last; dz++)
                {
                    final int x = tower.x() + dx;
                    final int z = tower.z() + dz;
                    final Terrain.Probe probe = Terrain.probe(level, x, z);
                    final int bottom = probe.loaded() ? probe.ground() + 1 : tower.bottom();
                    clear(level, steps, x, z, bottom, tower.top() + CLEAR_ABOVE, order);
                    piles(level, steps, x, z, style, order);
                    for (int y = bottom; y <= tower.top(); y++)
                    {
                        steps.add(new Step(new BlockPos(x, y, z), y == tower.top() ? style.towerTop() : style.tower(), order));
                    }
                    final boolean edge = dx == 0 || dz == 0 || dx == last || dz == last;
                    if (edge)
                    {
                        steps.add(new Step(new BlockPos(x, tower.top() + 1, z), style.rail(), order));
                    }
                }
            }
            // A lantern in the middle of the tower top: the wall glows at night
            final int mid = tower.size() / 2;
            steps.add(new Step(new BlockPos(tower.x() + mid, tower.top() + 1, tower.z() + mid),
              Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), order));
        }
        steps.sort(Comparator.comparingInt(Step::order).thenComparingInt(step -> step.state().isAir() ? 0 : 1).thenComparingInt(step -> step.pos().getY()));
        return steps;
    }

    /**
     * Air over what the wall clears: grass, flowers, the trunk and leaves of natural trees, from its foot up.
     */
    private static void clear(final LevelReader level, final List<Step> steps, final int x, final int z, final int from, final int to, final int order)
    {
        final int top = Math.max(to, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, from, z);
        for (int y = from; y <= top; y++)
        {
            pos.setY(y);
            final BlockState state = level.getBlockState(pos);
            if (!state.isAir() && Terrain.clearable(level, pos, state))
            {
                steps.add(new Step(pos.immutable(), Blocks.AIR.defaultBlockState(), order));
            }
        }
    }

    /**
     * On water, piles from the bottom up to the surface, so the wall stands instead of floating.
     */
    private static void piles(final LevelReader level, final List<Step> steps, final int x, final int z, final WallBlocks.Style style, final int order)
    {
        final Terrain.Probe probe = Terrain.probe(level, x, z);
        if (!probe.water())
        {
            return;
        }
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, probe.ground(), z);
        while (pos.getY() > level.getMinBuildHeight() && !level.getFluidState(pos).isEmpty())
        {
            steps.add(new Step(pos.immutable(), style.pile(), order));
            pos.move(0, -1, 0);
        }
    }
}
