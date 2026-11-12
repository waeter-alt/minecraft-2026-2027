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

    // Computed in one go on the server thread: the node budget bounds both the freeze and the memory (three maps of
    // boxed cells), the slightly greedy heuristic keeps long routes well under it. Further than this wants an
    // asynchronous, chunk-by-chunk search, not a bigger budget.
    private static final int MAX_SEARCH_DISTANCE = 1200;
    private static final int MAX_NODES = 250_000;
    private static final double HEURISTIC_WEIGHT = 1.3;
    private static final double SLOPE_WEIGHT = 2.5;
    private static final double WATER_PENALTY = 3.0;
    private static final double BLOCKED_PENALTY = 20.0;

    private static final int MAX_GRADE = 1;
    private static final int TUNNEL_MARGIN = 4;
    private static final int TUNNEL_MIN_RUN = 4;
    private static final int MAX_CUT = 24;
    private static final int GAP_MERGE = 2;
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
    private static final BlockState TUNNEL_STAIR = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
    private static final BlockState BRIDGE_STAIR = Blocks.OAK_STAIRS.defaultBlockState();
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
        open.add(new long[] {startCell, Double.doubleToLongBits(HEURISTIC_WEIGHT * heuristic(ax, az, bx, bz))});
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
                    open.add(new long[] {neighbor, Double.doubleToLongBits(tentative + HEURISTIC_WEIGHT * heuristic(nx, nz, bx, bz))});
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
        // A dry gap (ravine, canyon) is held like water: a cell is in a gap when the ground there sits well below what
        // the road could still reach at grade coming down from either side — a true dip, not just a cliff down or up.
        // The deck then runs straight from one rim to the other instead of sagging into the gap and climbing out.
        final int[] reachLeft = new int[n];
        final int[] reachRight = new int[n];
        reachLeft[0] = ground[0];
        for (int i = 1; i < n; i++)
        {
            reachLeft[i] = Math.max(ground[i], reachLeft[i - 1] - MAX_GRADE);
        }
        reachRight[n - 1] = ground[n - 1];
        for (int i = n - 2; i >= 0; i--)
        {
            reachRight[i] = Math.max(ground[i], reachRight[i + 1] - MAX_GRADE);
        }
        final boolean[] gap = new boolean[n];
        for (int i = 0; i < n; i++)
        {
            gap[i] = Math.min(reachLeft[i], reachRight[i]) - ground[i] > BRIDGE_MARGIN;
        }
        for (int i = 1; i < n; i++)
        {
            // A rock standing in the middle of the ravine does not split the bridge in two
            if (!gap[i] && gap[i - 1])
            {
                int j = i;
                while (j < n && !gap[j] && j - i < GAP_MERGE)
                {
                    j++;
                }
                if (j < n && gap[j])
                {
                    java.util.Arrays.fill(gap, i, j, true);
                }
            }
        }
        for (int s = 0; s < n; s++)
        {
            if (!gap[s])
            {
                continue;
            }
            int e = s;
            while (e + 1 < n && gap[e + 1])
            {
                e++;
            }
            // Never at either end (reachLeft[0] and reachRight[n-1] are the ground itself), so both rims exist
            final int rimLeft = ground[s - 1];
            final int rimRight = ground[e + 1];
            for (int i = s; i <= e; i++)
            {
                req[i] = Math.max(req[i], rimLeft + (rimRight - rimLeft) * (i - s + 1) / (e - s + 2));
            }
            s = e;
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

        // A grade-limited chase of the ground (or of the clearance water and gaps ask for): never more than one block
        // of slope per cell. Chased from both ends and averaged, so a slope steeper than the grade is half cut into,
        // half built up — chased from one end only, the road lagged a whole hillside behind the ground on every climb
        // and buried itself in it.
        final int[] target = new int[n];
        for (int i = 0; i < n; i++)
        {
            target[i] = Math.max(ground[i], req[i] == NO_REQ ? ground[i] : req[i]);
        }
        final int[] forward = new int[n];
        final int[] backward = new int[n];
        forward[0] = target[0];
        for (int i = 1; i < n; i++)
        {
            forward[i] = Math.max(forward[i - 1] - MAX_GRADE, Math.min(forward[i - 1] + MAX_GRADE, target[i]));
        }
        backward[n - 1] = target[n - 1];
        for (int i = n - 2; i >= 0; i--)
        {
            backward[i] = Math.max(backward[i + 1] - MAX_GRADE, Math.min(backward[i + 1] + MAX_GRADE, target[i]));
        }
        final int[] roadY = new int[n];
        for (int i = 0; i < n; i++)
        {
            roadY[i] = Math.floorDiv(forward[i] + backward[i], 2);
        }
        // A one-cell bump would need a stair facing both ways on the same cell: flatten it
        for (int i = 1; i + 1 < n; i++)
        {
            if (roadY[i - 1] == roadY[i + 1] && roadY[i] == roadY[i - 1] + 1)
            {
                roadY[i] = roadY[i - 1];
            }
        }

        // Bridge where the road stands well above the ground; tunnel only through a real mass of rock — deep enough
        // to leave a roof, over several cells running — everything else is walked in the open, cut into the slope
        final Mode[] modes = new Mode[n];
        for (int i = 0; i < n; i++)
        {
            modes[i] = water[i] || ground[i] - roadY[i] < -BRIDGE_MARGIN ? Mode.BRIDGE : Mode.SURFACE;
        }
        for (int i = 0; i < n; i++)
        {
            int j = i;
            while (j < n && modes[j] == Mode.SURFACE && ground[j] - roadY[j] > TUNNEL_MARGIN)
            {
                j++;
            }
            if (j - i >= TUNNEL_MIN_RUN)
            {
                java.util.Arrays.fill(modes, i, j, Mode.TUNNEL);
            }
            i = Math.max(i, j);
        }

        // Where the road changes level by one block, the stair goes on the HIGHER of the two cells, at that cell's
        // height, facing toward the climb (away from the lower cell) — see docs/GOTCHA.md: on a descent that is the
        // cell before the drop, not the one after it.
        final Direction[] stairs = new Direction[n];
        for (int i = 1; i < n; i++)
        {
            if (roadY[i] == roadY[i - 1])
            {
                continue;
            }
            final Direction along = directionOf(cells.get(i)[0] - cells.get(i - 1)[0], cells.get(i)[1] - cells.get(i - 1)[1]);
            if (roadY[i] > roadY[i - 1])
            {
                if (stairs[i] == null)
                {
                    stairs[i] = along;
                }
            }
            else if (stairs[i - 1] == null)
            {
                stairs[i - 1] = along.getOpposite();
            }
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
            final int[] from = i + 1 < n ? cells.get(i) : cells.get(Math.max(0, i - 1));
            final int[] to = i + 1 < n ? cells.get(i + 1) : cells.get(i);
            final Direction dir = directionOf(to[0] - from[0], to[1] - from[1]);
            final Direction perp = dir.getClockWise();
            switch (modes[i])
            {
                case TUNNEL ->
                {
                    tunnels++;
                    carveTunnel(level, steps, x, y, z, perp, i, floorOf(TUNNEL_FLOOR, TUNNEL_STAIR, stairs[i]));
                }
                case BRIDGE ->
                {
                    bridges++;
                    bridgeAt(level, steps, x, y, z, perp, i, floorOf(BRIDGE_FLOOR, BRIDGE_STAIR, stairs[i]));
                }
                default -> surfaceAt(level, steps, x, y, z, perp, floorOf(PATH, STAIR, stairs[i]));
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

    /**
     * @param climb the stair's FACING (the side it climbs toward), null for a flat cell
     */
    private static BlockState floorOf(final BlockState flat, final BlockState stair, @Nullable final Direction climb)
    {
        return climb == null ? flat : stair.setValue(StairBlock.FACING, climb);
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
      final BlockState cap)
    {
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
      final int order, final BlockState floor)
    {
        for (int o = -HALF; o <= HALF; o++)
        {
            final int lx = x + perp.getStepX() * o;
            final int lz = z + perp.getStepZ() * o;
            steps.add(new Step(new BlockPos(lx, y, lz), floor));
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
    private static void bridgeAt(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final Direction perp,
      final int order, final BlockState deck)
    {
        for (int o = -HALF; o <= HALF; o++)
        {
            final int lx = x + perp.getStepX() * o;
            final int lz = z + perp.getStepZ() * o;
            steps.add(new Step(new BlockPos(lx, y, lz), deck));
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
        // Cut into the slope, open to the sky: clearAbove() only takes plants and trees, so the earth and rock above
        // the shelf would otherwise stay put and the road would run under the hillside it was meant to climb
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, target, z);
        for (int y = target + 1; y <= Math.min(ground, target + MAX_CUT); y++)
        {
            pos.setY(y);
            final BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && Terrain.natural(state) && !Terrain.clearable(level, pos, state))
            {
                steps.add(new Step(pos.immutable(), AIR));
            }
        }
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
