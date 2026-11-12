package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.walls.Terrain;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Traces a route between two points on the ground. Everything hangs on one reference: the relief smoothed over a wide
 * window (the baseline, see {@link Relief}). The 2D search pays for every cell that stands out of it, so it slips
 * through a pass rather than over the hump next to it; then, along the chosen line, the stretches where the ground
 * still stands well above or sinks well below that baseline are measured as whole features — a hill, a ravine, a
 * river — and only the deep and long enough ones become a tunnel or a bridge, held straight between their two real
 * edges. Everything else is walked on the surface, graded, with stairs where it climbs. Laid out {@link #WIDTH}
 * blocks wide, shoulders terraformed to match, trees cleared a little further out. A prototype, tuned in game.
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
     * @param tunnels    number of centre cells carved through a hill
     * @param bridges    number of centre cells spanning water or a ravine
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

    // Computed in one go on the server thread. The search runs on flat arrays over a box around the two ends (no boxed
    // maps), the node budget bounds the freeze, the slightly greedy heuristic keeps long routes under it. Further than
    // this wants an asynchronous, chunk-by-chunk search, not a bigger budget.
    private static final int MAX_SEARCH_DISTANCE = 1200;
    private static final int MAX_NODES = 400_000;
    private static final int SEARCH_PAD = 64;
    private static final double SEARCH_PAD_RATIO = 0.25;
    private static final double HEURISTIC_WEIGHT = 1.3;
    private static final double SLOPE_WEIGHT = 2.5;
    private static final double WATER_PENALTY = 3.0;
    private static final double BLOCKED_PENALTY = 20.0;

    // The baseline: terrain sampled every SAMPLE_STEP blocks, averaged over a square of BASELINE_RADIUS around each
    // sample. Wide enough that a hill or a ravine is a bump or a hole in it, not part of it.
    private static final int SAMPLE_STEP = 4;
    private static final int BASELINE_RADIUS = 16;
    // Search cost of standing out of the baseline: free within ROUGH_FREE, then per block above (a hump to go around)
    // and, cheaper, below (a hollow — crossing it is often unavoidable, the search should only pick a narrow spot).
    private static final double ROUGH_FREE = 2.0;
    private static final double ROUGH_UP_WEIGHT = 0.6;
    private static final double ROUGH_DOWN_WEIGHT = 0.3;

    private static final int MAX_GRADE = 1;
    // A feature along the line: its cells stand at least DIP_DEV / BUMP_DEV off the baseline AND inside a real trough /
    // crest of the route's own profile (the "rope" stretched over ROPE_SPAN cells either side) — a valley walked along
    // its floor sits below the baseline but is no ravine to bridge.
    private static final int ROPE_SPAN = 40;
    private static final int DIP_DEV = 3;
    private static final int BUMP_DEV = 4;
    private static final int MERGE_GAP = 3;
    private static final int EXPAND_MAX = 24;
    private static final int BRIDGE_MIN_DEPTH = 4;
    private static final int BRIDGE_MIN_LEN = 4;
    private static final int WATER_CLEARANCE = 4;
    // Free height above the tunnel floor: on a road 7 wide, 2 read as a slot; 3 reads as a tunnel
    private static final int TUNNEL_HEADROOM = 3;
    private static final int TUNNEL_MIN_ROOF = 3;
    private static final int TUNNEL_COVER = TUNNEL_HEADROOM + TUNNEL_MIN_ROOF;
    private static final int TUNNEL_MIN_RUN = 4;
    private static final int MAX_CUT = 24;
    private static final int LIGHT_EVERY = 6;
    private static final int PILLAR_EVERY = 6;
    private static final int EMBANKMENT_LIMIT = 6;
    private static final int NO_REQ = Integer.MIN_VALUE;

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

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
        final int pad = SEARCH_PAD + (int) (Math.sqrt(a.distSqr(b)) * SEARCH_PAD_RATIO);
        final Relief relief = new Relief(level, Math.min(a.getX(), b.getX()) - pad, Math.min(a.getZ(), b.getZ()) - pad,
          Math.max(a.getX(), b.getX()) + pad, Math.max(a.getZ(), b.getZ()) + pad);
        final List<int[]> cells = search(relief, a.getX(), a.getZ(), b.getX(), b.getZ());
        if (cells == null || cells.isEmpty())
        {
            return null;
        }
        return build(level, relief, cells);
    }

    /**
     * The ground over the search box: every cell's probe, cached as the search reaches it, and the baseline — the
     * relief averaged over a window {@link #BASELINE_RADIUS} wide. The baseline is computed on a coarse grid (one
     * probe every {@link #SAMPLE_STEP} blocks) with a summed-area table, so each window average costs four lookups
     * whatever its size, then read back bilinearly: a box over a 1200-block route stays around a hundred thousand
     * probes, not millions.
     */
    private static final class Relief
    {
        private static final byte PROBED = 1;
        private static final byte LOADED = 2;
        private static final byte WATER = 4;
        private static final byte BLOCKED = 8;

        private final ServerLevel level;
        private final int x0;
        private final int z0;
        private final int w;
        private final int h;
        private final int[] ground;
        private final byte[] info;
        private final int sw;
        private final int sh;
        private final double[] base;

        Relief(final ServerLevel level, final int minX, final int minZ, final int maxX, final int maxZ)
        {
            this.level = level;
            this.x0 = minX;
            this.z0 = minZ;
            this.w = maxX - minX + 1;
            this.h = maxZ - minZ + 1;
            this.ground = new int[w * h];
            this.info = new byte[w * h];
            this.sw = (w - 1) / SAMPLE_STEP + 1;
            this.sh = (h - 1) / SAMPLE_STEP + 1;
            this.base = new double[sw * sh];

            final int stride = sw + 1;
            final double[] sum = new double[stride * (sh + 1)];
            final int[] count = new int[stride * (sh + 1)];
            for (int sj = 0; sj < sh; sj++)
            {
                for (int si = 0; si < sw; si++)
                {
                    final int x = x0 + si * SAMPLE_STEP;
                    final int z = z0 + sj * SAMPLE_STEP;
                    final boolean loaded = probe(x, z);
                    final int at = (sj + 1) * stride + si + 1;
                    sum[at] = (loaded ? groundAt(x, z) : 0) + sum[at - 1] + sum[at - stride] - sum[at - stride - 1];
                    count[at] = (loaded ? 1 : 0) + count[at - 1] + count[at - stride] - count[at - stride - 1];
                }
            }
            final int r = Math.max(1, BASELINE_RADIUS / SAMPLE_STEP);
            for (int sj = 0; sj < sh; sj++)
            {
                for (int si = 0; si < sw; si++)
                {
                    final int i0 = Math.max(0, si - r);
                    final int i1 = Math.min(sw, si + r + 1);
                    final int j0 = Math.max(0, sj - r);
                    final int j1 = Math.min(sh, sj + r + 1);
                    final int c = count[j1 * stride + i1] - count[j0 * stride + i1] - count[j1 * stride + i0] + count[j0 * stride + i0];
                    final double s = sum[j1 * stride + i1] - sum[j0 * stride + i1] - sum[j1 * stride + i0] + sum[j0 * stride + i0];
                    base[sj * sw + si] = c == 0 ? Double.NaN : s / c;
                }
            }
        }

        boolean inside(final int x, final int z)
        {
            return x >= x0 && z >= z0 && x < x0 + w && z < z0 + h;
        }

        int index(final int x, final int z)
        {
            return (z - z0) * w + (x - x0);
        }

        /**
         * Probes the cell once (cached); false when its chunk is not loaded.
         */
        boolean probe(final int x, final int z)
        {
            final int i = index(x, z);
            if (info[i] == 0)
            {
                final Terrain.Probe probe = Terrain.probe(level, x, z);
                ground[i] = probe.ground();
                info[i] = (byte) (PROBED | (probe.loaded() ? LOADED : 0) | (probe.water() ? WATER : 0) | (probe.blocked() ? BLOCKED : 0));
            }
            return (info[i] & LOADED) != 0;
        }

        int groundAt(final int x, final int z)
        {
            return ground[index(x, z)];
        }

        boolean water(final int x, final int z)
        {
            return (info[index(x, z)] & WATER) != 0;
        }

        boolean blocked(final int x, final int z)
        {
            return (info[index(x, z)] & BLOCKED) != 0;
        }

        /**
         * The smoothed relief at this cell, NaN where nothing around it is loaded.
         */
        double baseline(final int x, final int z)
        {
            final double fx = (double) (x - x0) / SAMPLE_STEP;
            final double fz = (double) (z - z0) / SAMPLE_STEP;
            final int i0 = Math.min(sw - 1, (int) Math.floor(fx));
            final int j0 = Math.min(sh - 1, (int) Math.floor(fz));
            final int i1 = Math.min(sw - 1, i0 + 1);
            final int j1 = Math.min(sh - 1, j0 + 1);
            final double tx = Math.min(1, Math.max(0, fx - i0));
            final double tz = Math.min(1, Math.max(0, fz - j0));
            double acc = 0;
            double weight = 0;
            final int[][] corners = {{i0, j0}, {i1, j0}, {i0, j1}, {i1, j1}};
            final double[] weights = {(1 - tx) * (1 - tz), tx * (1 - tz), (1 - tx) * tz, tx * tz};
            for (int k = 0; k < 4; k++)
            {
                final double v = base[corners[k][1] * sw + corners[k][0]];
                if (!Double.isNaN(v) && weights[k] > 0)
                {
                    acc += v * weights[k];
                    weight += weights[k];
                }
            }
            return weight > 0 ? acc / weight : Double.NaN;
        }

        /**
         * How far the cell stands above (positive) or below (negative) the baseline; 0 when unknown.
         */
        double deviation(final int x, final int z)
        {
            final double b = baseline(x, z);
            return Double.isNaN(b) ? 0 : groundAt(x, z) - b;
        }
    }

    /**
     * A* over the (x, z) grid inside the relief's box, 4-connected only (no diagonal step — a route that cuts corners
     * looks wrong). Cost per step grows with the slope to the next cell, with water, with anything already standing
     * there (a building goes around, at a price, rather than being treated as a wall), and with how far the next cell
     * stands out of the baseline: a crest costs for every cell of it, so a pass a little to the side wins over going
     * straight over the hump — the slope alone only sees one cell ahead and could not tell.
     */
    @Nullable
    private static List<int[]> search(final Relief relief, final int ax, final int az, final int bx, final int bz)
    {
        if (!relief.probe(ax, az) || !relief.probe(bx, bz))
        {
            return null;
        }
        final int size = relief.w * relief.h;
        final float[] g = new float[size];
        Arrays.fill(g, Float.POSITIVE_INFINITY);
        final byte[] via = new byte[size];
        final boolean[] closed = new boolean[size];
        final int startIdx = relief.index(ax, az);
        final int goalIdx = relief.index(bx, bz);
        final PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingDouble(node -> Double.longBitsToDouble(node[1])));
        g[startIdx] = 0;
        open.add(new long[] {startIdx, Double.doubleToLongBits(HEURISTIC_WEIGHT * heuristic(ax, az, bx, bz))});
        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_NODES)
        {
            final int current = (int) open.poll()[0];
            if (closed[current])
            {
                continue;
            }
            closed[current] = true;
            final int x = relief.x0 + current % relief.w;
            final int z = relief.z0 + current / relief.w;
            if (current == goalIdx)
            {
                return reconstruct(relief, via, x, z, startIdx);
            }
            final int currentGround = relief.groundAt(x, z);
            for (int k = 0; k < 4; k++)
            {
                final int nx = x + DX[k];
                final int nz = z + DZ[k];
                if (!relief.inside(nx, nz))
                {
                    continue;
                }
                final int neighbor = relief.index(nx, nz);
                if (closed[neighbor] || !relief.probe(nx, nz))
                {
                    continue;
                }
                final double slope = Math.abs(relief.groundAt(nx, nz) - currentGround) * SLOPE_WEIGHT;
                final double penalty = (relief.water(nx, nz) ? WATER_PENALTY : 0) + (relief.blocked(nx, nz) ? BLOCKED_PENALTY : 0);
                final double dev = relief.deviation(nx, nz);
                final double rough = dev > ROUGH_FREE ? (dev - ROUGH_FREE) * ROUGH_UP_WEIGHT
                                       : dev < -ROUGH_FREE ? (-dev - ROUGH_FREE) * ROUGH_DOWN_WEIGHT : 0;
                final double tentative = g[current] + 1.0 + slope + penalty + rough;
                if (tentative < g[neighbor])
                {
                    g[neighbor] = (float) tentative;
                    via[neighbor] = (byte) (k + 1);
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

    private static List<int[]> reconstruct(final Relief relief, final byte[] via, final int gx, final int gz, final int startIdx)
    {
        final List<int[]> cells = new ArrayList<>();
        int x = gx;
        int z = gz;
        cells.add(new int[] {x, z});
        int idx = relief.index(x, z);
        while (idx != startIdx && via[idx] != 0)
        {
            final int k = via[idx] - 1;
            x -= DX[k];
            z -= DZ[k];
            cells.add(new int[] {x, z});
            idx = relief.index(x, z);
        }
        Collections.reverse(cells);
        return cells;
    }

    /**
     * Turns the 2D centre line into real blocks, {@link #WIDTH} wide. The height profile is decided feature by
     * feature, not cell by cell: a hollow (water, a ravine) found along the line is bridged with a deck held on the
     * straight line between its two rims, a crest is cut through on the straight line between its two feet — in a
     * tunnel where the rock above leaves a real roof, in an open cut elsewhere. Only then is the whole profile
     * graded (one block per cell at most) and stepped with stairs.
     */
    private static Result build(final ServerLevel level, final Relief relief, final List<int[]> cells)
    {
        final int n = cells.size();
        final int[] ground = new int[n];
        final boolean[] water = new boolean[n];
        final double[] dev = new double[n];
        for (int i = 0; i < n; i++)
        {
            final int x = cells.get(i)[0];
            final int z = cells.get(i)[1];
            relief.probe(x, z);
            ground[i] = relief.groundAt(x, z);
            water[i] = relief.water(x, z);
            dev[i] = relief.deviation(x, z);
        }

        // The rope over the route's own profile: upper is the level a hollow would fill to between the highest ground
        // on either side (rain trapped in a trough), lower the level a crest would be shaved down to. Unlike the
        // baseline, both follow the line itself: they tell a trough crossed from one walked along.
        final int[] upper = new int[n];
        final int[] lower = new int[n];
        for (int i = 0; i < n; i++)
        {
            int leftMax = ground[i];
            int leftMin = ground[i];
            for (int j = Math.max(0, i - ROPE_SPAN); j < i; j++)
            {
                leftMax = Math.max(leftMax, ground[j]);
                leftMin = Math.min(leftMin, ground[j]);
            }
            int rightMax = ground[i];
            int rightMin = ground[i];
            for (int j = i + 1; j <= Math.min(n - 1, i + ROPE_SPAN); j++)
            {
                rightMax = Math.max(rightMax, ground[j]);
                rightMin = Math.min(rightMin, ground[j]);
            }
            upper[i] = Math.min(leftMax, rightMax);
            lower[i] = Math.max(leftMin, rightMin);
        }

        final Mode[] modes = new Mode[n];
        final int[] target = ground.clone();
        final int[] floorReq = new int[n];
        Arrays.fill(floorReq, NO_REQ);

        // Hollows: bridged rim to rim, or simply filled when shallow and short
        final boolean[] dipCandidate = new boolean[n];
        final boolean[] inDip = new boolean[n];
        final boolean[] dipInside = new boolean[n];
        for (int i = 0; i < n; i++)
        {
            dipCandidate[i] = water[i] || (dev[i] < -DIP_DEV && upper[i] - ground[i] >= DIP_DEV);
            dipInside[i] = water[i] || upper[i] > ground[i];
        }
        for (final int[] span : spans(dipCandidate, dipInside, null, n))
        {
            final int a = span[0];
            final int b = span[1];
            boolean wet = false;
            int maxDepth = 0;
            for (int i = a + 1; i < b; i++)
            {
                wet |= water[i];
                maxDepth = Math.max(maxDepth, lineAt(ground, a, b, i) - ground[i]);
            }
            final boolean bridge = wet || maxDepth > EMBANKMENT_LIMIT || (maxDepth >= BRIDGE_MIN_DEPTH && b - a - 1 >= BRIDGE_MIN_LEN);
            for (int i = a + 1; i < b; i++)
            {
                final int line = lineAt(ground, a, b, i);
                inDip[i] = true;
                target[i] = line;
                floorReq[i] = water[i] ? Math.max(line, ground[i] + WATER_CLEARANCE) : line;
                modes[i] = bridge ? Mode.BRIDGE : Mode.SURFACE;
            }
        }
        for (int i = 0; i < n; i++)
        {
            if (water[i] && !inDip[i])
            {
                floorReq[i] = ground[i] + WATER_CLEARANCE;
                modes[i] = Mode.BRIDGE;
            }
        }

        // Crests: cut through foot to foot, as a tunnel where the rock leaves a real roof
        final boolean[] bumpCandidate = new boolean[n];
        final boolean[] bumpInside = new boolean[n];
        for (int i = 0; i < n; i++)
        {
            bumpCandidate[i] = !inDip[i] && dev[i] > BUMP_DEV && ground[i] - lower[i] >= BUMP_DEV;
            bumpInside[i] = ground[i] > lower[i];
        }
        for (final int[] span : spans(bumpCandidate, bumpInside, inDip, n))
        {
            final int a = span[0];
            final int b = span[1];
            final boolean[] deep = new boolean[n];
            for (int i = a + 1; i < b; i++)
            {
                final int line = lineAt(ground, a, b, i);
                target[i] = line;
                modes[i] = Mode.SURFACE;
                deep[i] = ground[i] - line >= TUNNEL_COVER;
            }
            mergeGaps(deep, null, a + 1, b);
            for (int s = a + 1; s < b; s++)
            {
                if (!deep[s])
                {
                    continue;
                }
                int e = s;
                int maxCover = 0;
                while (e < b && deep[e])
                {
                    maxCover = Math.max(maxCover, ground[e] - target[e]);
                    e++;
                }
                if (e - s >= TUNNEL_MIN_RUN || maxCover > MAX_CUT)
                {
                    Arrays.fill(modes, s, e, Mode.TUNNEL);
                }
                s = e;
            }
        }

        // The deck of a bridge never sinks below its line, and reaches it at grade from either side
        for (int i = n - 2; i >= 0; i--)
        {
            if (floorReq[i + 1] != NO_REQ)
            {
                floorReq[i] = Math.max(floorReq[i], floorReq[i + 1] - MAX_GRADE);
            }
        }
        for (int i = 1; i < n; i++)
        {
            if (floorReq[i - 1] != NO_REQ)
            {
                floorReq[i] = Math.max(floorReq[i], floorReq[i - 1] - MAX_GRADE);
            }
        }

        // Grade: never more than one block per cell. Features are already straightened, so this only shapes the
        // open stretches between them; chased from both ends and averaged, so a climb steeper than the grade is half
        // cut into, half built up instead of lagging a hillside behind.
        final int[] forward = new int[n];
        final int[] backward = new int[n];
        forward[0] = Math.max(target[0], floorReq[0]);
        for (int i = 1; i < n; i++)
        {
            forward[i] = Math.max(forward[i - 1] - MAX_GRADE, Math.min(forward[i - 1] + MAX_GRADE, Math.max(target[i], floorReq[i])));
        }
        backward[n - 1] = Math.max(target[n - 1], floorReq[n - 1]);
        for (int i = n - 2; i >= 0; i--)
        {
            backward[i] = Math.max(backward[i + 1] - MAX_GRADE, Math.min(backward[i + 1] + MAX_GRADE, Math.max(target[i], floorReq[i])));
        }
        final int[] roadY = new int[n];
        for (int i = 0; i < n; i++)
        {
            roadY[i] = Math.max(floorReq[i], Math.floorDiv(forward[i] + backward[i], 2));
        }
        // A one-cell bump would need a stair facing both ways on the same cell: flatten it
        for (int i = 1; i + 1 < n; i++)
        {
            if (roadY[i - 1] == roadY[i + 1] && roadY[i] == roadY[i - 1] + 1 && roadY[i - 1] >= floorReq[i])
            {
                roadY[i] = roadY[i - 1];
            }
        }

        // What no feature claimed: open ground, except a ramp too high to embank (the approach of a high bridge) or a
        // long climb steep enough to leave a roof over the graded road
        for (int i = 0; i < n; i++)
        {
            if (modes[i] == null && roadY[i] - ground[i] > EMBANKMENT_LIMIT)
            {
                modes[i] = Mode.BRIDGE;
            }
        }
        for (int i = 0; i < n; i++)
        {
            int j = i;
            while (j < n && modes[j] == null && ground[j] - roadY[j] >= TUNNEL_COVER)
            {
                j++;
            }
            if (j - i >= TUNNEL_MIN_RUN)
            {
                Arrays.fill(modes, i, j, Mode.TUNNEL);
            }
            i = Math.max(i, j);
        }
        for (int i = 0; i < n; i++)
        {
            if (modes[i] == null)
            {
                modes[i] = Mode.SURFACE;
            }
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
     * The features along the line, as {anchor before, anchor after} pairs (the anchors themselves stay outside): the
     * candidate cells, joined across gaps of {@link #MERGE_GAP} or less (a rock in the middle of a ravine), then
     * widened while still inside the trough or crest, up to {@link #EXPAND_MAX} cells either way, so the anchors land on
     * the real rims or feet. Overlapping features become one.
     */
    private static List<int[]> spans(final boolean[] candidate, final boolean[] inside, @Nullable final boolean[] forbidden, final int n)
    {
        final boolean[] cand = candidate.clone();
        mergeGaps(cand, forbidden, 0, n);
        final List<int[]> spans = new ArrayList<>();
        for (int s = 0; s < n; s++)
        {
            if (!cand[s])
            {
                continue;
            }
            int e = s;
            while (e + 1 < n && cand[e + 1])
            {
                e++;
            }
            int left = s;
            while (left - 1 >= 0 && s - (left - 1) <= EXPAND_MAX && inside[left - 1] && (forbidden == null || !forbidden[left - 1]))
            {
                left--;
            }
            int right = e;
            while (right + 1 < n && (right + 1) - e <= EXPAND_MAX && inside[right + 1] && (forbidden == null || !forbidden[right + 1]))
            {
                right++;
            }
            int a = Math.max(left - 1, 0);
            int b = Math.min(right + 1, n - 1);
            // Never anchor on a cell another feature already holds (its rim is the next cell in)
            if (forbidden != null && forbidden[a])
            {
                a++;
            }
            if (forbidden != null && forbidden[b])
            {
                b--;
            }
            if (b - a >= 2)
            {
                final int[] last = spans.isEmpty() ? null : spans.get(spans.size() - 1);
                if (last != null && a < last[1])
                {
                    last[1] = Math.max(last[1], b);
                }
                else
                {
                    spans.add(new int[] {a, b});
                }
            }
            s = e;
        }
        return spans;
    }

    /**
     * Fills the gaps of {@link #MERGE_GAP} cells or less between two runs of the mask, within [from, to), unless a
     * forbidden cell lies in the gap.
     */
    private static void mergeGaps(final boolean[] mask, @Nullable final boolean[] forbidden, final int from, final int to)
    {
        for (int i = from + 1; i < to; i++)
        {
            if (mask[i] || !mask[i - 1])
            {
                continue;
            }
            int j = i;
            boolean clean = true;
            while (j < to && !mask[j] && j - i < MERGE_GAP)
            {
                clean &= forbidden == null || !forbidden[j];
                j++;
            }
            if (j < to && mask[j] && clean)
            {
                Arrays.fill(mask, i, j, true);
            }
            i = j;
        }
    }

    /**
     * Height at i of the straight line from cell a's ground to cell b's ground.
     */
    private static int lineAt(final int[] ground, final int a, final int b, final int i)
    {
        return (int) Math.round(ground[a] + (ground[b] - ground[a]) * (double) (i - a) / (b - a));
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
     * Water or a ravine: a deck {@link #WIDTH} wide at the road's grade, a rail along both edges, and pillars down to
     * solid ground every few blocks so it does not read as floating. A rock standing up through the deck is cut away.
     */
    private static void bridgeAt(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final Direction perp,
      final int order, final BlockState deck)
    {
        for (int o = -HALF; o <= HALF; o++)
        {
            final int lx = x + perp.getStepX() * o;
            final int lz = z + perp.getStepZ() * o;
            steps.add(new Step(new BlockPos(lx, y, lz), deck));
            final Terrain.Probe probe = Terrain.probe(level, lx, lz);
            if (probe.loaded() && probe.ground() > y)
            {
                cutAbove(level, steps, lx, lz, y, probe.ground());
            }
            if (Math.abs(o) == HALF)
            {
                steps.add(new Step(new BlockPos(lx, y + 1, lz), BRIDGE_RAIL));
            }
            if ((o == -HALF || o == 0 || o == HALF) && order % PILLAR_EVERY == 0)
            {
                final int bed = probe.loaded() ? probe.ground() : y;
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
        cutAbove(level, steps, x, z, target, ground);
        clearAbove(level, steps, x, z, target);
    }

    /**
     * Clears the natural earth and rock of one column from just above fromY up to toY ({@link #MAX_CUT} at most).
     */
    private static void cutAbove(final ServerLevel level, final List<Step> steps, final int x, final int z, final int fromY, final int toY)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
        for (int y = fromY + 1; y <= Math.min(toY, fromY + MAX_CUT); y++)
        {
            pos.setY(y);
            final BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && Terrain.natural(state) && !Terrain.clearable(level, pos, state))
            {
                steps.add(new Step(pos.immutable(), AIR));
            }
        }
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
