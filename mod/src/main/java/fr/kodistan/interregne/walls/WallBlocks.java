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
        Blocks.ANDESITE.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState()),
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
        if (state.is(Blocks.LANTERN) || state.is(Blocks.WHITE_WALL_BANNER) || state.is(Blocks.LADDER) || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE_BRICK_STAIRS)
              || state.is(Blocks.DEEPSLATE_BRICK_STAIRS) || state.is(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS))
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
        return of(level, layout, wallLevel, ground, null);
    }

    /**
     * Same, knowing which side the colony lies on (towards inside): the ways up from the gates are on that side.
     */
    public static List<Step> of(final LevelReader level, final WallLayout layout, final int wallLevel, final WallLayout.Ground ground,
      @org.jetbrains.annotations.Nullable final BlockPos inside)
    {
        final Style style = STYLES[Math.max(1, Math.min(STYLES.length, wallLevel)) - 1];
        final List<Step> steps = new ArrayList<>();
        final java.util.Set<Long> body = footprint(layout);
        final java.util.Map<Long, WallLayout.Column> columns = new java.util.HashMap<>();
        final java.util.Set<Long> faces = new java.util.HashSet<>();
        for (final WallLayout.Column column : layout.columns())
        {
            columns.put(WallRegistry.cell(column.x(), column.z()), column);
        }
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
                faces.add(WallRegistry.cell(x, z));
                steps.add(new Step(new BlockPos(x, column.top() + 1, z), ((x + z) & 1) == 0 ? style.spike() : style.cap(), order));
            }
        }
        for (final WallLayout.Tower tower : layout.towers())
        {
            tower(level, steps, tower, style, ground, columns, faces);
        }
        final java.util.Map<Long, Step> overrides = new java.util.HashMap<>();
        for (final WallLayout.Gate gate : layout.gates())
        {
            gate(level, steps, gate, style, ground);
            wayUp(overrides, gate, wallLevel, columns, faces, inside);
        }
        // The ways up are carved into the wall: they replace what was laid there
        for (int i = 0; i < steps.size(); i++)
        {
            final Step carved = overrides.remove(steps.get(i).pos().asLong());
            if (carved != null)
            {
                steps.set(i, new Step(carved.pos(), carved.state(), steps.get(i).order()));
            }
        }
        steps.addAll(overrides.values());
        steps.sort(Comparator.comparingInt(Step::order).thenComparingInt(step -> step.state().isAir() ? 0 : 1).thenComparingInt(step -> step.pos().getY()));
        // Ladders go up last, once every block they hang on stands: set earlier, a later neighbour update would knock them off
        final List<Step> ladders = new ArrayList<>();
        steps.removeIf(step -> step.state().is(Blocks.LADDER) && ladders.add(step));
        ladders.sort(Comparator.comparingInt(step -> step.pos().getY()));
        steps.addAll(ladders);
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
                    if (tower.covers(tower.x() + dx, tower.z() + dz))
                    {
                        cells.add(WallRegistry.cell(tower.x() + dx, tower.z() + dz));
                    }
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
     * A round tower: solid up to the wall's walkway, then a room the walkway runs into through openings where the wall
     * meets it, with arrow slits, a lantern and a ladder up to the crenellated roof.
     */
    private static void tower(final LevelReader level, final List<Step> steps, final WallLayout.Tower tower, final Style style, final WallLayout.Ground ground,
      final java.util.Map<Long, WallLayout.Column> columns, final java.util.Set<Long> faces)
    {
        final int order = tower.index() * 2 + 1;
        final int walk = tower.walk();
        final int top = tower.top();
        final int cx = tower.x() + tower.size() / 2 - 1;
        final int cz = tower.z() + tower.size() / 2 - 1;
        // The ladder: against the first side of the room whose wall has no opening
        BlockPos ladder = null;
        Direction ladderFacing = Direction.NORTH;
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            int x = cx;
            int z = cz;
            while (tower.covers(x + side.getStepX() * 2, z + side.getStepZ() * 2))
            {
                x += side.getStepX();
                z += side.getStepZ();
            }
            final int rx = x + side.getStepX();
            final int rz = z + side.getStepZ();
            if (!door(tower, rx, rz, columns, faces))
            {
                ladder = new BlockPos(x, 0, z);
                ladderFacing = side.getOpposite();
                break;
            }
        }
        for (int dx = 0; dx < tower.size(); dx++)
        {
            for (int dz = 0; dz < tower.size(); dz++)
            {
                final int x = tower.x() + dx;
                final int z = tower.z() + dz;
                if (!tower.covers(x, z))
                {
                    continue;
                }
                final boolean rim = !tower.covers(x + 1, z) || !tower.covers(x - 1, z) || !tower.covers(x, z + 1) || !tower.covers(x, z - 1);
                final boolean door = rim && door(tower, x, z, columns, faces);
                final boolean slit = rim && !door && Math.floorMod(x * 3 + z * 5, 4) == 0;
                final boolean ladderCell = ladder != null && ladder.getX() == x && ladder.getZ() == z;
                final Terrain.Probe probe = ground.at(x, z);
                final int bottom = probe.loaded() ? probe.ground() + 1 : tower.bottom();
                clear(level, steps, x, z, bottom, top + CLEAR_ABOVE, order);
                piles(level, steps, x, z, style, order);
                for (int y = bottom; y <= top; y++)
                {
                    final BlockState state;
                    if (y < walk)
                    {
                        state = style.tower();
                    }
                    else if (y == walk)
                    {
                        // A band of the roof's stone marks the walkway's level all round
                        state = rim ? style.towerTop() : style.floor();
                    }
                    else if (y < top)
                    {
                        if (ladderCell)
                        {
                            state = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, ladderFacing);
                        }
                        else if (!rim || door && y <= walk + 3 || slit && y == walk + 2)
                        {
                            continue;
                        }
                        else
                        {
                            state = style.tower();
                        }
                    }
                    else
                    {
                        state = ladderCell ? Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, ladderFacing)
                                  : rim ? style.towerTop() : style.floor();
                    }
                    steps.add(new Step(new BlockPos(x, y, z), state, order));
                }
                if (rim && ((x + z) & 1) == 0)
                {
                    // Merlons on every other block of the roof's edge
                    steps.add(new Step(new BlockPos(x, top + 1, z), style.tower(), order));
                }
            }
        }
        final BlockPos lanternAt = new BlockPos(cx + 1, 0, cz + 1);
        final boolean onLadder = ladder != null && ladder.getX() == lanternAt.getX() && ladder.getZ() == lanternAt.getZ();
        if (!onLadder)
        {
            // Light inside the room (no mobs in the towers) and a lantern on the roof: the wall glows at night
            steps.add(new Step(lanternAt.atY(walk + 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), order));
            steps.add(new Step(lanternAt.atY(top + 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), order));
        }
    }

    /**
     * A tower's edge cell opens onto the walkway when a walkway (not parapet) cell of the wall touches it.
     */
    private static boolean door(final WallLayout.Tower tower, final int x, final int z, final java.util.Map<Long, WallLayout.Column> columns,
      final java.util.Set<Long> faces)
    {
        for (final Direction side : Direction.Plane.HORIZONTAL)
        {
            final long next = WallRegistry.cell(x + side.getStepX(), z + side.getStepZ());
            if (!tower.covers(x + side.getStepX(), z + side.getStepZ()) && columns.containsKey(next) && !faces.contains(next))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The way up to the walkway beside a gate, on the colony's side of the wall: a ladder in the wall's inner face at the
     * first levels, a flight of stairs carved into it from level 3.
     */
    private static void wayUp(final java.util.Map<Long, Step> overrides, final WallLayout.Gate gate, final int wallLevel,
      final java.util.Map<Long, WallLayout.Column> columns, final java.util.Set<Long> faces, @org.jetbrains.annotations.Nullable final BlockPos inside)
    {
        final int ax = -gate.uz();
        final int az = gate.ux();
        int in = 1;
        if (inside != null && (long) (inside.getX() - gate.x()) * ax + (long) (inside.getZ() - gate.z()) * az < 0)
        {
            in = -1;
        }
        final Direction outward = Direction.fromDelta(ax * in, 0, az * in);
        if (outward == null)
        {
            return;
        }
        final int start = WallLayout.GATE_HALF + 1;
        for (final int side : new int[] {1, -1})
        {
            // The inner face of the wall just past the gatehouse
            int face = Integer.MIN_VALUE;
            for (int across = in * (gate.depth() + 1); across != 0; across -= in)
            {
                final long cell = WallRegistry.cell(gate.x() + side * start * gate.ux() + across * ax, gate.z() + side * start * gate.uz() + across * az);
                if (faces.contains(cell))
                {
                    face = across;
                    break;
                }
            }
            if (face == Integer.MIN_VALUE)
            {
                continue;
            }
            final Direction up = Direction.fromDelta(side * gate.ux(), 0, side * gate.uz());
            if (wallLevel >= 3 && up != null && stairs(overrides, gate, wallLevel, side, face, ax, az, up, columns, faces))
            {
                return;
            }
            final int x = gate.x() + side * start * gate.ux() + face * ax;
            final int z = gate.z() + side * start * gate.uz() + face * az;
            final WallLayout.Column column = columns.get(WallRegistry.cell(x, z));
            if (column == null)
            {
                continue;
            }
            // A ladder up the inner face, against the wall behind it, and a gap in the parapet at the top
            for (int y = column.bottom(); y <= column.top(); y++)
            {
                final BlockPos pos = new BlockPos(x, y, z);
                overrides.put(pos.asLong(), new Step(pos, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, outward),
                  gate.index() * 2 + 1));
            }
            final BlockPos gap = new BlockPos(x, column.top() + 1, z);
            overrides.put(gap.asLong(), new Step(gap, Blocks.AIR.defaultBlockState(), gate.index() * 2 + 1));
            return;
        }
    }

    /**
     * A flight of stairs carved into the wall's inner face, climbing away from the gate to the walkway. False (nothing
     * carved) when the wall there is too short for it.
     */
    private static boolean stairs(final java.util.Map<Long, Step> overrides, final WallLayout.Gate gate, final int wallLevel, final int side, final int face,
      final int ax, final int az, final Direction up, final java.util.Map<Long, WallLayout.Column> columns, final java.util.Set<Long> faces)
    {
        final int start = WallLayout.GATE_HALF + 1;
        final java.util.Map<Long, Step> flight = new java.util.HashMap<>();
        final WallLayout.Column first = columns.get(WallRegistry.cell(gate.x() + side * start * gate.ux() + face * ax, gate.z() + side * start * gate.uz() + face * az));
        if (first == null)
        {
            return false;
        }
        final BlockState stair = (wallLevel >= 5 ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS : wallLevel == 4 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS).defaultBlockState();
        for (int k = 0; k < 24; k++)
        {
            final int x = gate.x() + side * (start + k) * gate.ux() + face * ax;
            final int z = gate.z() + side * (start + k) * gate.uz() + face * az;
            final long cell = WallRegistry.cell(x, z);
            final WallLayout.Column column = columns.get(cell);
            if (column == null || !faces.contains(cell))
            {
                return false;
            }
            final int y = first.bottom() + k;
            if (y > column.top())
            {
                break;
            }
            final BlockPos at = new BlockPos(x, y, z);
            flight.put(at.asLong(), new Step(at, stair.setValue(net.minecraft.world.level.block.StairBlock.FACING, up), gate.index() * 2 + 1));
            for (int air = y + 1; air <= column.top() + 1; air++)
            {
                final BlockPos pos = new BlockPos(x, air, z);
                flight.put(pos.asLong(), new Step(pos, Blocks.AIR.defaultBlockState(), gate.index() * 2 + 1));
            }
            if (y == column.top())
            {
                overrides.putAll(flight);
                return true;
            }
        }
        return false;
    }

    /**
     * A gatehouse: a passage floored and cleared to the lintel, its portcullis raised under it, and a turret on each
     * side with the colony's banners on both faces and a lantern on top.
     */
    private static void gate(final LevelReader level, final List<Step> steps, final WallLayout.Gate gate, final Style style, final WallLayout.Ground ground)
    {
        final int order = gate.index() * 2;
        final int depth = gate.depth();
        final int turretTop = gate.crest() + 4;
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
                    // The lintel is the walkway's floor over the passage, a parapet on both faces
                    steps.add(new Step(new BlockPos(x, gate.crest(), z), Math.abs(across) == depth ? style.cap() : style.floor(), order));
                    if (Math.abs(across) == depth)
                    {
                        steps.add(new Step(new BlockPos(x, gate.crest() + 1, z), ((along + across) & 1) == 0 ? style.spike() : style.cap(), order));
                    }
                    continue;
                }
                clear(level, steps, x, z, below + 1, turretTop + CLEAR_ABOVE, order);
                piles(level, steps, x, z, style, order);
                // The walkway goes through the turrets: a floored corridor at its level, three blocks high
                final boolean corridor = Math.abs(across) <= 1;
                for (int y = below + 1; y <= turretTop; y++)
                {
                    if (corridor && y > gate.crest() && y <= gate.crest() + 3)
                    {
                        continue;
                    }
                    steps.add(new Step(new BlockPos(x, y, z), corridor && y == gate.crest() ? style.floor() : y == turretTop ? style.towerTop() : style.tower(), order));
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
