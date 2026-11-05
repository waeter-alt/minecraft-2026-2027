package fr.kodistan.interregne.walls;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallBannerBlock;
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
    private record Style(BlockState body, BlockState cap, BlockState spike, BlockState tower, BlockState towerTop, BlockState rail, BlockState pile,
                         BlockState floor, BlockState portcullis)
    {
    }

    private static final Style[] STYLES = {
      new Style(Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(),
        Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(),
        Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState()),
      new Style(Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE_WALL.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
        Blocks.GRAVEL.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState()),
      new Style(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(),
        Blocks.STONE_BRICKS.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
        Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState()),
      new Style(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(),
        Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(),
        Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState()),
      new Style(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.CHAIN.defaultBlockState()),
    };

    private static final int CLEAR_ABOVE = 24;

    /**
     * Whether a block is one a wall is made of, at any level (to see through a colony's own wall when retracing it).
     */
    public static boolean isWallBlock(final BlockState state)
    {
        if (state.is(Blocks.LANTERN) || state.is(Blocks.WHITE_WALL_BANNER))
        {
            return true;
        }
        for (final Style style : STYLES)
        {
            for (final BlockState part : new BlockState[] {style.body(), style.cap(), style.spike(), style.tower(), style.towerTop(), style.rail(),
              style.pile(), style.floor(), style.portcullis()})
            {
                if (state.is(part.getBlock()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private WallBlocks()
    {
    }

    public static List<Step> of(final LevelReader level, final WallLayout layout, final int wallLevel)
    {
        return of(level, layout, wallLevel, (x, z) -> Terrain.probe(level, x, z));
    }

    /**
     * Same, reading the ground through this function (the ground remembered under an existing wall).
     */
    public static List<Step> of(final LevelReader level, final WallLayout layout, final int wallLevel, final WallLayout.Ground ground)
    {
        final Style style = STYLES[Math.max(1, Math.min(STYLES.length, wallLevel)) - 1];
        final List<Step> steps = new ArrayList<>();
        final java.util.Set<Long> body = footprint(layout);
        for (final WallLayout.Column column : layout.columns())
        {
            final int order = column.index() * 2;
            final int x = column.x();
            final int z = column.z();
            clear(level, steps, x, z, column.bottom(), column.top() + CLEAR_ABOVE, order);
            piles(level, steps, x, z, style, order);
            // The two faces carry the parapet; between them the crest is a walkway
            final boolean face = !body.contains(WallRegistry.cell(x + 1, z)) || !body.contains(WallRegistry.cell(x - 1, z))
                                   || !body.contains(WallRegistry.cell(x, z + 1)) || !body.contains(WallRegistry.cell(x, z - 1));
            for (int y = column.bottom(); y <= column.top(); y++)
            {
                steps.add(new Step(new BlockPos(x, y, z), y < column.top() ? style.body() : face ? style.cap() : style.floor(), order));
            }
            if (face)
            {
                steps.add(new Step(new BlockPos(x, column.top() + 1, z), ((x + z) & 1) == 0 ? style.spike() : style.cap(), order));
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
                    final Terrain.Probe probe = ground.at(x, z);
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
        for (final WallLayout.Gate gate : layout.gates())
        {
            gate(level, steps, gate, style, ground);
        }
        steps.sort(Comparator.comparingInt(Step::order).thenComparingInt(step -> step.state().isAir() ? 0 : 1).thenComparingInt(step -> step.pos().getY()));
        return steps;
    }

    /**
     * Every cell the wall stands on: columns, towers and gatehouses.
     */
    private static java.util.Set<Long> footprint(final WallLayout layout)
    {
        final java.util.Set<Long> cells = new java.util.HashSet<>();
        for (final WallLayout.Column column : layout.columns())
        {
            cells.add(WallRegistry.cell(column.x(), column.z()));
        }
        for (final WallLayout.Tower tower : layout.towers())
        {
            for (int dx = 0; dx < tower.size(); dx++)
            {
                for (int dz = 0; dz < tower.size(); dz++)
                {
                    cells.add(WallRegistry.cell(tower.x() + dx, tower.z() + dz));
                }
            }
        }
        for (final WallLayout.Gate gate : layout.gates())
        {
            for (int along = -WallLayout.GATE_HALF; along <= WallLayout.GATE_HALF; along++)
            {
                for (int across = -gate.depth(); across <= gate.depth(); across++)
                {
                    cells.add(WallRegistry.cell(gate.x() + along * gate.ux() - across * gate.uz(), gate.z() + along * gate.uz() + across * gate.ux()));
                }
            }
        }
        return cells;
    }

    /**
     * A gatehouse: a passage floored and cleared to the lintel, its portcullis raised under it, and a turret on each
     * side with the colony's banners on both faces and a lantern on top.
     */
    private static void gate(final LevelReader level, final List<Step> steps, final WallLayout.Gate gate, final Style style, final WallLayout.Ground ground)
    {
        final int order = gate.index() * 2;
        final int depth = gate.depth();
        final int turretTop = gate.crest() + 2;
        for (int along = -WallLayout.GATE_HALF; along <= WallLayout.GATE_HALF; along++)
        {
            for (int across = -depth; across <= depth; across++)
            {
                final int x = gate.x() + along * gate.ux() - across * gate.uz();
                final int z = gate.z() + along * gate.uz() + across * gate.ux();
                final Terrain.Probe probe = ground.at(x, z);
                final int below = probe.loaded() ? probe.ground() : gate.floor();
                final boolean edge = Math.abs(across) == depth || Math.abs(along) == WallLayout.GATE_HALF;
                if (gate.passage(along))
                {
                    clear(level, steps, x, z, gate.floor() + 1, gate.crest() + CLEAR_ABOVE, order);
                    piles(level, steps, x, z, style, order);
                    for (int y = below + 1; y < gate.floor(); y++)
                    {
                        steps.add(new Step(new BlockPos(x, y, z), style.body(), order));
                    }
                    steps.add(new Step(new BlockPos(x, gate.floor(), z), style.floor(), order));
                    // Natural ground above the floor (a slope) is dug out to make the way through
                    for (int y = gate.floor() + 1; y < gate.crest(); y++)
                    {
                        final BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).isAir() && !Terrain.clearable(level, pos, level.getBlockState(pos)))
                        {
                            steps.add(new Step(pos, Blocks.AIR.defaultBlockState(), order));
                        }
                    }
                    if (across == 0 && gate.crest() - gate.floor() >= 4)
                    {
                        // The portcullis, raised: only its teeth show under the lintel
                        steps.add(new Step(new BlockPos(x, gate.crest() - 1, z), style.portcullis(), order));
                    }
                    steps.add(new Step(new BlockPos(x, gate.crest(), z), style.cap(), order));
                    steps.add(new Step(new BlockPos(x, gate.crest() + 1, z), style.body(), order));
                    if (Math.abs(across) == depth && ((along + across) & 1) == 0)
                    {
                        steps.add(new Step(new BlockPos(x, gate.crest() + 2, z), style.spike(), order));
                    }
                    continue;
                }
                clear(level, steps, x, z, below + 1, turretTop + CLEAR_ABOVE, order);
                piles(level, steps, x, z, style, order);
                for (int y = below + 1; y <= turretTop; y++)
                {
                    steps.add(new Step(new BlockPos(x, y, z), y == turretTop ? style.towerTop() : style.tower(), order));
                }
                if (edge)
                {
                    steps.add(new Step(new BlockPos(x, turretTop + 1, z), style.rail(), order));
                }
                else if (across == 0 && Math.abs(along) == WallLayout.PASSAGE_HALF + 1)
                {
                    steps.add(new Step(new BlockPos(x, turretTop + 1, z), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), order));
                }
                if (Math.abs(across) == depth && Math.abs(along) == WallLayout.PASSAGE_HALF + 1)
                {
                    // The colony's banner hangs on the turret, facing out of the gatehouse (outside and inside the walls)
                    final int sign = Integer.signum(across);
                    final Direction facing = Direction.fromDelta(-gate.uz() * sign, 0, gate.ux() * sign);
                    if (facing != null)
                    {
                        steps.add(new Step(new BlockPos(x, gate.crest(), z).relative(facing),
                          Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, facing), order + 1));
                    }
                }
            }
        }
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
