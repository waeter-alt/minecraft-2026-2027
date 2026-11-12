package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.walls.WallRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Traces a route between two points on the ground: a 2D search picks the way around what it can go around (4
 * directions only — a route that cuts diagonally reads as jagged), then a graded height profile turns the gentle
 * stretches into a surface path (with stairs where the grade steps up), the steep ones into a tunnel, and the gaps
 * (water, a chasm) into a bridge. Laid out {@link #WIDTH} blocks wide, with the shoulders terraformed to match and
 * trees cleared a little further out still. A prototype, tuned to be tested in game and adjusted from there.
 */
public final class RoutePath
{
    /**
     * One block of the route; air clears what was growing above it.
     */
    public record Step(BlockPos pos, BlockState state)
    {
    }

    /**
     * @param steps      every block to set, in the order the route's centre line runs (start to end) — see
     *                   {@link #interleaved} for the order the site actually builds in
     * @param chunkSizes how many of {@code steps} belong to each centre-line cell, in the same order, so the two
     *                   ends can be built toward the middle without re-running the whole layout
     * @param length     number of ground cells the route's centre line crosses (its walking length, not vol d'oiseau)
     * @param tunnels    number of centre cells carved through a slope too steep to climb
     * @param bridges    number of centre cells spanning water or a drop too steep to descend
     */
    public record Result(List<Step> steps, List<Integer> chunkSizes, int length, int tunnels, int bridges)
    {
    }

    private enum Mode
    {
        SURFACE, TUNNEL, BRIDGE
    }

    public static final int WIDTH = 7;
    private static final int HALF = WIDTH / 2;
    private static final int TREE_MARGIN = 3;

    private static final int MAX_SEARCH_DISTANCE = 400;
    private static final int MAX_NODES = 60_000;
    private static final double SLOPE_WEIGHT = 2.5;
    private static final double WATER_PENALTY = 3.0;
    private static final double BLOCKED_PENALTY = 20.0;

    private static final int MAX_GRADE = 1;
    private static final int TUNNEL_MARGIN = 3;
    private static final int BRIDGE_MARGIN = 2;
    private static final int WATER_CLEARANCE = 4;
    private static final int TUNNEL_HEADROOM = 2;
    private static final int LIGHT_EVERY = 6;
    private static final int PILLAR_EVERY = 6;
    private static final int EMBANKMENT_LIMIT = 6;

    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();
    private static final BlockState EMBANKMENT = Blocks.DIRT.defaultBlockState();
    private static final BlockState STAIR = Blocks.COBBLESTONE_STAIRS.defaultBlockState();
    private static final BlockState TUNNEL_FLOOR = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState TUNNEL_LANTERN = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState BRIDGE_FLOOR = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState BRIDGE_RAIL = Blocks.OAK_FENCE.defaultBlockState();
    private static final BlockState BRIDGE_PILLAR = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private RoutePath()
    {
    }

    /**
     * Null when the two ends are too far apart, a chunk along the way is not loaded, or no way through was found
     * within the node budget.
     */
    @Nullable
    public static Result compute(final ServerLevel level, final BlockPos a, final BlockPos b)
    {
        if (a.distSqr(b) > (long) MAX_SEARCH_DISTANCE * MAX_SEARCH_DISTANCE)
        {
            return null;
        }
        final List<int[]> cells = search(level, a.getX(), a.getZ(), b.getX(), b.getZ());
        if (cells == null || cells.isEmpty())
        {
            return null;
        }
        return build(level, cells);
    }

    /**
     * A* over the (x, z) grid, 4-connected only (no diagonal step — a route that cuts corners looks wrong). Cost per
     * step grows with the slope, with water, and with anything already standing there (a building goes around, at a
     * price, rather than being treated as a wall).
     */
    @Nullable
    private static List<int[]> search(final ServerLevel level, final int ax, final int az, final int bx, final int bz)
    {
        final Terrain.Probe start = Terrain.probe(level, ax, az);
        final Terrain.Probe goal = Terrain.probe(level, bx, bz);
        if (!start.loaded() || !goal.loaded())
        {
            return null;
        }
        final long startCell = WallRegistry.cell(ax, az);
        final long goalCell = WallRegistry.cell(bx, bz);
        final Map<Long, Double> gScore = new HashMap<>();
        final Map<Long, Long> parent = new HashMap<>();
        final Map<Long, Terrain.Probe> probes = new HashMap<>();
        final java.util.Set<Long> closed = new java.util.HashSet<>();
        final PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingDouble(node -> Double.longBitsToDouble(node[1])));
        gScore.put(startCell, 0.0);
        probes.put(startCell, start);
        open.add(new long[] {startCell, Double.doubleToLongBits(heuristic(ax, az, bx, bz))});
        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_NODES)
        {
            final long[] node = open.poll();
            final long current = node[0];
            if (!closed.add(current))
            {
                continue;
            }
            if (current == goalCell)
            {
                return reconstruct(parent, current);
            }
            final int x = (int) (current >> 32);
            final int z = (int) current;
            final double currentG = gScore.getOrDefault(current, Double.MAX_VALUE);
            final Terrain.Probe currentProbe = probes.get(current);
            for (final Direction dir : Direction.Plane.HORIZONTAL)
            {
                final int nx = x + dir.getStepX();
                final int nz = z + dir.getStepZ();
                final long neighbor = WallRegistry.cell(nx, nz);
                Terrain.Probe probe = probes.get(neighbor);
                if (probe == null)
                {
                    probe = Terrain.probe(level, nx, nz);
                    if (!probe.loaded())
                    {
                        continue;
                    }
                    probes.put(neighbor, probe);
                }
                final double slope = Math.abs(probe.ground() - currentProbe.ground()) * SLOPE_WEIGHT;
                final double penalty = (probe.water() ? WATER_PENALTY : 0) + (probe.blocked() ? BLOCKED_PENALTY : 0);
                final double tentative = currentG + 1.0 + slope + penalty;
                if (tentative < gScore.getOrDefault(neighbor, Double.MAX_VALUE))
                {
                    gScore.put(neighbor, tentative);
                    parent.put(neighbor, current);
                    open.add(new long[] {neighbor, Double.doubleToLongBits(tentative + heuristic(nx, nz, bx, bz))});
                }
            }
        }
        return null;
    }

    private static double heuristic(final int x, final int z, final int bx, final int bz)
    {
        return Math.abs(bx - x) + Math.abs(bz - z);
    }

    private static List<int[]> reconstruct(final Map<Long, Long> parent, final long goal)
    {
        final List<int[]> cells = new ArrayList<>();
        long current = goal;
        cells.add(new int[] {(int) (current >> 32), (int) current});
        while (parent.containsKey(current))
        {
            current = parent.get(current);
            cells.add(new int[] {(int) (current >> 32), (int) current});
        }
        java.util.Collections.reverse(cells);
        return cells;
    }

    /**
     * Turns the 2D centre line into real blocks, {@link #WIDTH} wide: a graded height profile decides, cell by
     * cell, whether the route walks the surface (flat, or stepped where the grade climbs), tunnels through what
     * rises too fast, or bridges what drops away.
     */
    private static Result build(final ServerLevel level, final List<int[]> cells)
    {
        final int n = cells.size();
        final int[] ground = new int[n];
        final boolean[] water = new boolean[n];
        for (int i = 0; i < n; i++)
        {
            final Terrain.Probe probe = Terrain.probe(level, cells.get(i)[0], cells.get(i)[1]);
            ground[i] = probe.ground();
            water[i] = probe.water();
        }
        // How high the road must be to clear the water, spread out over the distance the grade needs to climb there
        // and back down, so the climb starts well before the bank and the deck never comes near the surface.
        final int NO_REQ = Integer.MIN_VALUE;
        final int[] req = new int[n];
        java.util.Arrays.fill(req, NO_REQ);
        for (int i = 0; i < n; i++)
        {
            if (water[i])
            {
                req[i] = ground[i] + WATER_CLEARANCE;
            }
        }
        for (int i = n - 2; i >= 0; i--)
        {
            if (req[i + 1] != NO_REQ)
            {
                req[i] = Math.max(req[i], req[i + 1] - MAX_GRADE);
            }
        }
        for (int i = 1; i < n; i++)
        {
            if (req[i - 1] != NO_REQ)
            {
                req[i] = Math.max(req[i], req[i - 1] - MAX_GRADE);
            }
        }

        // A grade-limited chase of the ground (or the water clearance where that asks for more height): never more
        // than one block of slope per cell, so a cliff the grade cannot keep up with becomes a bridge on its own,
        // rather than the road diving to meet it.
        final int[] roadY = new int[n];
        roadY[0] = Math.max(ground[0], req[0] == NO_REQ ? ground[0] : req[0]);
        for (int i = 1; i < n; i++)
        {
            final int target = Math.max(ground[i], req[i] == NO_REQ ? ground[i] : req[i]);
            roadY[i] = Math.max(roadY[i - 1] - MAX_GRADE, Math.min(roadY[i - 1] + MAX_GRADE, target));
        }

        final List<Step> steps = new ArrayList<>();
        final List<Integer> chunkSizes = new ArrayList<>(n);
        int tunnels = 0;
        int bridges = 0;
        for (int i = 0; i < n; i++)
        {
            final int before = steps.size();
            final int x = cells.get(i)[0];
            final int z = cells.get(i)[1];
            final int y = roadY[i];
            final int diff = ground[i] - y;
            final Mode mode = water[i] || diff < -BRIDGE_MARGIN ? Mode.BRIDGE : diff > TUNNEL_MARGIN ? Mode.TUNNEL : Mode.SURFACE;
            final int[] from = i + 1 < n ? cells.get(i) : cells.get(Math.max(0, i - 1));
            final int[] to = i + 1 < n ? cells.get(i + 1) : cells.get(i);
            final Direction dir = directionOf(to[0] - from[0], to[1] - from[1]);
            final Direction perp = dir.getClockWise();
            final boolean climbing = mode == Mode.SURFACE && i > 0 && roadY[i] != roadY[i - 1];
            switch (mode)
            {
                case TUNNEL ->
                {
                    tunnels++;
                    carveTunnel(level, steps, x, y, z, perp, i);
                }
                case BRIDGE ->
                {
                    bridges++;
                    bridgeAt(level, steps, x, y, z, ground[i], perp, i);
                }
                default -> surfaceAt(level, steps, x, y, z, perp, climbing ? (roadY[i] > roadY[i - 1] ? dir : dir.getOpposite()) : null);
            }
            clearTreesAround(level, steps, x, y, z, perp);
            chunkSizes.add(steps.size() - before);
        }
        return new Result(steps, chunkSizes, n, tunnels, bridges);
    }

    /**
     * The blocks of a route, reordered so the site builds outward from both ends toward the middle instead of one
     * sweep from start to end.
     */
    public static List<Step> interleaved(final Result result)
    {
        final List<Step> steps = result.steps();
        final List<Integer> sizes = result.chunkSizes();
        final int[] starts = new int[sizes.size()];
        int offset = 0;
        for (int i = 0; i < sizes.size(); i++)
        {
            starts[i] = offset;
            offset += sizes.get(i);
        }
        final List<Step> ordered = new ArrayList<>(steps.size());
        int lo = 0;
        int hi = sizes.size() - 1;
        while (lo <= hi)
        {
            ordered.addAll(steps.subList(starts[lo], starts[lo] + sizes.get(lo)));
            if (hi != lo)
            {
                ordered.addAll(steps.subList(starts[hi], starts[hi] + sizes.get(hi)));
            }
            lo++;
            hi--;
        }
        return ordered;
    }

    private static Direction directionOf(final int dx, final int dz)
    {
        final Direction found = Direction.fromDelta(dx, 0, dz);
        return found == null ? Direction.NORTH : found;
    }

    /**
     * Gentle ground: a flat shelf {@link #WIDTH} wide at the road's grade (an embankment where the ground is lower,
     * cut back where it is higher), a stair step across the whole width where the grade itself climbs.
     */
    private static void surfaceAt(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final Direction perp,
      @Nullable final Direction climb)
    {
        final BlockState cap = climb == null ? PATH : STAIR.setValue(StairBlock.FACING, climb);
        for (int o = -HALF; o <= HALF; o++)
        {
            final int lx = x + perp.getStepX() * o;
            final int lz = z + perp.getStepZ() * o;
            levelColumn(level, steps, lx, lz, y, cap);
        }
    }

    /**
     * A hill in the way: a corridor {@link #WIDTH} wide carved through at the road's grade, floored and lit from
     * its walls.
     */
    private static void carveTunnel(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final Direction perp,
      final int order)
    {
        for (int o = -HALF; o <= HALF; o++)
        {
            final int lx = x + perp.getStepX() * o;
            final int lz = z + perp.getStepZ() * o;
            steps.add(new Step(new BlockPos(lx, y, lz), TUNNEL_FLOOR));
            for (int dy = 1; dy <= TUNNEL_HEADROOM; dy++)
            {
                final BlockPos pos = new BlockPos(lx, y + dy, lz);
                if (!level.getBlockState(pos).isAir())
                {
                    steps.add(new Step(pos, AIR));
                }
            }
            if (Math.abs(o) == HALF && order % LIGHT_EVERY == 0)
            {
                steps.add(new Step(new BlockPos(lx, y + TUNNEL_HEADROOM, lz), TUNNEL_LANTERN));
            }
        }
    }

    /**
     * Water or a drop too steep to walk down: a deck {@link #WIDTH} wide at the road's grade, a rail along both
     * edges, and pillars down to solid ground every few blocks so it does not read as floating.
     */
    private static void bridgeAt(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final int ground,
      final Direction perp, final int order)
    {
        for (int o = -HALF; o <= HALF; o++)
        {
            final int lx = x + perp.getStepX() * o;
            final int lz = z + perp.getStepZ() * o;
            steps.add(new Step(new BlockPos(lx, y, lz), BRIDGE_FLOOR));
            if (Math.abs(o) == HALF)
            {
                steps.add(new Step(new BlockPos(lx, y + 1, lz), BRIDGE_RAIL));
            }
            if ((o == -HALF || o == 0 || o == HALF) && order % PILLAR_EVERY == 0)
            {
                final int bed = Terrain.probe(level, lx, lz).ground();
                for (int py = y - 1; py > bed && y - py < 48; py--)
                {
                    steps.add(new Step(new BlockPos(lx, py, lz), BRIDGE_PILLAR));
                }
            }
        }
    }

    /**
     * Brings one column of the shelf to the target height: fills the gap with an embankment where the ground sits
     * lower, then caps it and clears whatever stood above — terrain, grass, a whole tree's trunk and canopy — up to
     * the heightmap, so nothing from a tree that stood in the road's way is left floating.
     */
    private static void levelColumn(final ServerLevel level, final List<Step> steps, final int x, final int z, final int target, final BlockState cap)
    {
        final Terrain.Probe probe = Terrain.probe(level, x, z);
        final int ground = probe.loaded() ? probe.ground() : target;
        if (ground < target && target - ground <= EMBANKMENT_LIMIT)
        {
            for (int y = ground + 1; y < target; y++)
            {
                steps.add(new Step(new BlockPos(x, y, z), EMBANKMENT));
            }
        }
        steps.add(new Step(new BlockPos(x, target, z), cap));
        clearAbove(level, steps, x, z, target);
    }

    /**
     * Trees a little further out than the shelf itself: cleared from the shelf's edge to the heightmap, like a
     * wall's own clearing, so a route through a forest does not leave half a canopy floating over it.
     */
    private static void clearTreesAround(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final Direction perp)
    {
        for (int o = HALF + 1; o <= HALF + TREE_MARGIN; o++)
        {
            for (final int sign : new int[] {1, -1})
            {
                clearAbove(level, steps, x + perp.getStepX() * o * sign, z + perp.getStepZ() * o * sign, y);
            }
        }
    }

    /**
     * Clears everything clearable from just above fromY up to the heightmap: grass, a sapling, a whole natural
     * tree's trunk and canopy, whatever is in the way — never leaves a floating remnant above the route.
     */
    private static void clearAbove(final ServerLevel level, final List<Step> steps, final int x, final int z, final int fromY)
    {
        final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY + 1, z);
        for (int y = fromY + 1; y <= Math.max(top, fromY + 2); y++)
        {
            pos.setY(y);
            final BlockState state = level.getBlockState(pos);
            if (!state.isAir() && Terrain.clearable(level, pos, state))
            {
                steps.add(new Step(pos.immutable(), AIR));
            }
        }
    }
}
