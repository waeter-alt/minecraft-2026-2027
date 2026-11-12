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
import java.util.List;

/**
 * Traces a route between two points on the ground, the way Baritone finds its paths: no smoothed relief, no feature
 * detection, no tunnel/bridge decision taken ahead of time. The A* state is (x, z, road height), so the height of the
 * road is known at every step of the search instead of being rebuilt afterwards; a step goes to one of the four
 * neighbours at the same height or one block up or down, which bounds the grade by construction. Each step costs the
 * real work of laying the road {@link #WIDTH} blocks wide at that height: earth and rock to dig, embankment to fill,
 * or a bridge deck or a tunnel bore when either is cheaper than the open cut or the fill. A tunnel or a bridge is
 * nothing but what the cheapest path turned out to be; the block style of each cell is read back on the chosen path
 * with the very same cost function. Shoulders terraformed to match, trees cleared a little further out.
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

    // Computed in one go on the server thread. The state is (x, z, y): only the states actually reached are stored
    // (open-addressed hash, primitive arrays), the expansion and node budgets bound the freeze, the slightly greedy
    // horizontal heuristic keeps long routes under them. Further than this wants an asynchronous search.
    private static final int MAX_SEARCH_DISTANCE = 1200;
    private static final int MAX_EXPANSIONS = 700_000;
    private static final int MAX_NODES = 2_500_000;
    private static final int SEARCH_PAD = 64;
    private static final double SEARCH_PAD_RATIO = 0.25;
    private static final double HEURISTIC_WEIGHT = 1.3;

    // Costs, in blocks of work (one block dug or placed = 1). STEP is the deck or path of one cell plus the walk;
    // CLIMB is a level gained or lost — stairs to lay and, mostly, a caravan's effort, so a pass beside a hump beats
    // the hump and a deck already level across a valley does not sag into it to save a few pillar blocks.
    private static final double STEP = WIDTH + 1;
    private static final double CLIMB = 20;
    private static final double DIG = 1;
    private static final double FILL = 1;
    // A bore lined and lit, over its headroom only: dearer per cell than digging those blocks, so a tunnel only wins
    // where the open cut would be much deeper — never on an open slope where the road only lags a few blocks.
    private static final double TUNNEL_PREMIUM = 35;
    // A deck with its rails, plus its pillars (every PILLAR_EVERY cells, three of them, cheap cobblestone): beats a
    // full embankment past three or four blocks of drop, so a real gap gets a bridge and a ditch gets filled.
    private static final double BRIDGE_PREMIUM = 20;
    private static final double PILLAR_WEIGHT = 0.2;
    private static final double WATER_PENALTY = 3 * STEP;
    private static final double BLOCKED_PENALTY = 20 * STEP;

    // The heights the search may try at a cell: between the lowest and highest ground within RANGE_RADIUS of it
    // (sampled every SAMPLE_STEP blocks), so a deck can stay at rim level across a ravine and a bore at foot level
    // under a hill, without exploring heights the local relief gives no reason for.
    private static final int SAMPLE_STEP = 4;
    private static final int RANGE_RADIUS = 32;
    private static final int MAX_DEPTH = 48;
    private static final int MAX_LIFT = 40;

    private static final int WATER_CLEARANCE = 4;
    // Free height above the tunnel floor: on a road 7 wide, 2 read as a slot; 3 reads as a tunnel
    private static final int TUNNEL_HEADROOM = 3;
    private static final int TUNNEL_MIN_ROOF = 3;
    private static final int TUNNEL_COVER = TUNNEL_HEADROOM + TUNNEL_MIN_ROOF;
    private static final int MAX_CUT = 24;
    private static final int LIGHT_EVERY = 6;
    private static final int PILLAR_EVERY = 6;
    private static final int EMBANKMENT_LIMIT = 6;

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
     * within the search budget.
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
     * The ground over the search box: every cell's probe, cached as the search reaches it, and the band of heights
     * worth trying at each cell (lowest and highest ground around it). The band comes from a coarse grid, one probe
     * every {@link #SAMPLE_STEP} blocks, its min and max taken over a square window with a separable pass.
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
        private final int minY;
        private final int maxY;
        private final int[] ground;
        private final byte[] info;
        private final int sw;
        private final int sh;
        private final int[] low;
        private final int[] high;

        Relief(final ServerLevel level, final int minX, final int minZ, final int maxX, final int maxZ)
        {
            this.level = level;
            this.x0 = minX;
            this.z0 = minZ;
            this.w = maxX - minX + 1;
            this.h = maxZ - minZ + 1;
            this.minY = level.getMinBuildHeight();
            this.maxY = level.getMaxBuildHeight() - 1;
            this.ground = new int[w * h];
            this.info = new byte[w * h];
            this.sw = (w - 1) / SAMPLE_STEP + 1;
            this.sh = (h - 1) / SAMPLE_STEP + 1;

            final int[] sampleLow = new int[sw * sh];
            final int[] sampleHigh = new int[sw * sh];
            for (int sj = 0; sj < sh; sj++)
            {
                for (int si = 0; si < sw; si++)
                {
                    final int x = x0 + si * SAMPLE_STEP;
                    final int z = z0 + sj * SAMPLE_STEP;
                    final boolean loaded = probe(x, z);
                    sampleLow[sj * sw + si] = loaded ? groundAt(x, z) : Integer.MAX_VALUE;
                    sampleHigh[sj * sw + si] = loaded ? groundAt(x, z) : Integer.MIN_VALUE;
                }
            }
            final int r = Math.max(1, RANGE_RADIUS / SAMPLE_STEP);
            final int[] rowLow = new int[sw * sh];
            final int[] rowHigh = new int[sw * sh];
            for (int sj = 0; sj < sh; sj++)
            {
                for (int si = 0; si < sw; si++)
                {
                    int lo = Integer.MAX_VALUE;
                    int hi = Integer.MIN_VALUE;
                    for (int i = Math.max(0, si - r); i <= Math.min(sw - 1, si + r); i++)
                    {
                        lo = Math.min(lo, sampleLow[sj * sw + i]);
                        hi = Math.max(hi, sampleHigh[sj * sw + i]);
                    }
                    rowLow[sj * sw + si] = lo;
                    rowHigh[sj * sw + si] = hi;
                }
            }
            this.low = new int[sw * sh];
            this.high = new int[sw * sh];
            for (int sj = 0; sj < sh; sj++)
            {
                for (int si = 0; si < sw; si++)
                {
                    int lo = Integer.MAX_VALUE;
                    int hi = Integer.MIN_VALUE;
                    for (int j = Math.max(0, sj - r); j <= Math.min(sh - 1, sj + r); j++)
                    {
                        lo = Math.min(lo, rowLow[j * sw + si]);
                        hi = Math.max(hi, rowHigh[j * sw + si]);
                    }
                    low[sj * sw + si] = lo;
                    high[sj * sw + si] = hi;
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
         * Probes the cell once (cached); false when it lies outside the box or its chunk is not loaded.
         */
        boolean probe(final int x, final int z)
        {
            if (!inside(x, z))
            {
                return false;
            }
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

        private int sample(final int x, final int z)
        {
            final int si = Math.min(sw - 1, Math.max(0, Math.round((float) (x - x0) / SAMPLE_STEP)));
            final int sj = Math.min(sh - 1, Math.max(0, Math.round((float) (z - z0) / SAMPLE_STEP)));
            return sj * sw + si;
        }

        /**
         * Lowest road height worth trying at a probed cell.
         */
        int lowest(final int x, final int z)
        {
            final int g = groundAt(x, z);
            final int around = low[sample(x, z)];
            return Math.max(minY + 1, Math.max(g - MAX_DEPTH, Math.min(g, around) - 1));
        }

        /**
         * Highest road height worth trying at a probed cell.
         */
        int highest(final int x, final int z)
        {
            final int g = groundAt(x, z);
            final int around = high[sample(x, z)];
            return Math.min(maxY - TUNNEL_HEADROOM - 1, Math.min(g + MAX_LIFT, Math.max(g, around) + WATER_CLEARANCE));
        }
    }

    /**
     * The real work of laying the road across one cell at height y, its {@link #WIDTH} columns spread along (px, pz):
     * the cheapest of an open shelf (dig what stands above, embank what lies below), a tunnel bore (only where every
     * column leaves a roof) and a bridge deck (only where something lies below it). Infinite when nothing fits —
     * water under a deck too low, a fill too high to embank with no bridge reason. The same function labels the
     * cells of the chosen path, so the block style is exactly what the search paid for.
     *
     * @param mode receives the cheapest mode when not null
     */
    private static double work(final Relief relief, final int x, final int z, final int y, final int px, final int pz, @Nullable final Mode[] mode)
    {
        double cut = 0;
        double fill = 0;
        int maxFill = 0;
        int maxCut = 0;
        int minCover = Integer.MAX_VALUE;
        boolean water = false;
        for (int o = -HALF; o <= HALF; o++)
        {
            final int cx = x + px * o;
            final int cz = z + pz * o;
            if (!relief.probe(cx, cz))
            {
                continue;
            }
            final int g = relief.groundAt(cx, cz);
            if (relief.water(cx, cz))
            {
                if (y < g + WATER_CLEARANCE)
                {
                    return Double.POSITIVE_INFINITY;
                }
                water = true;
                continue;
            }
            final int d = y - g;
            if (d > 0)
            {
                fill += d;
                maxFill = Math.max(maxFill, d);
            }
            else if (d < 0)
            {
                cut += -d;
                maxCut = Math.max(maxCut, -d);
            }
            minCover = Math.min(minCover, -d);
        }

        double best = Double.POSITIVE_INFINITY;
        Mode bestMode = Mode.SURFACE;
        if (!water && maxFill <= EMBANKMENT_LIMIT && maxCut <= MAX_CUT)
        {
            best = cut * DIG + fill * FILL;
        }
        if (!water && minCover != Integer.MAX_VALUE && minCover >= TUNNEL_COVER)
        {
            final double bore = TUNNEL_HEADROOM * WIDTH * DIG + TUNNEL_PREMIUM;
            if (bore < best)
            {
                best = bore;
                bestMode = Mode.TUNNEL;
            }
        }
        if ((water || maxFill > 0) && maxCut <= MAX_CUT)
        {
            final int centre = relief.probe(x, z) ? relief.groundAt(x, z) : y;
            final double deck = BRIDGE_PREMIUM + 2 + PILLAR_WEIGHT * Math.max(0, y - centre) + cut * DIG + (water ? WATER_PENALTY : 0);
            if (deck < best)
            {
                best = deck;
                bestMode = Mode.BRIDGE;
            }
        }
        if (mode != null)
        {
            mode[0] = bestMode;
        }
        return best;
    }

    /**
     * The states reached so far, keyed by (cell, height, "just climbed"): an open-addressed hash over primitive
     * arrays, so a long route's millions of states stay a few tens of megabytes, not boxed maps.
     */
    private static final class Nodes
    {
        private final int minY;
        private int[] table = new int[1 << 16];
        long[] key = new long[1 << 15];
        float[] g = new float[1 << 15];
        int[] parent = new int[1 << 15];
        boolean[] closed = new boolean[1 << 15];
        int count;

        Nodes(final int minY)
        {
            this.minY = minY;
        }

        long key(final int cell, final int y, final boolean climbed)
        {
            return ((long) cell << 11) | ((long) (y - minY) << 1) | (climbed ? 1 : 0);
        }

        static int cell(final long key)
        {
            return (int) (key >>> 11);
        }

        int y(final long key)
        {
            return (int) ((key >>> 1) & 0x3FF) + minY;
        }

        static boolean climbed(final long key)
        {
            return (key & 1) != 0;
        }

        private static int hash(final long k)
        {
            final long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32));
        }

        int find(final long k)
        {
            final int mask = table.length - 1;
            int slot = hash(k) & mask;
            while (table[slot] != 0)
            {
                final int id = table[slot] - 1;
                if (key[id] == k)
                {
                    return id;
                }
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        int add(final long k, final int parentId, final float cost)
        {
            if (count == key.length)
            {
                final int cap = key.length * 2;
                key = Arrays.copyOf(key, cap);
                g = Arrays.copyOf(g, cap);
                parent = Arrays.copyOf(parent, cap);
                closed = Arrays.copyOf(closed, cap);
            }
            if ((count + 1) * 2 > table.length)
            {
                rehash(table.length * 2);
            }
            final int id = count++;
            key[id] = k;
            g[id] = cost;
            parent[id] = parentId;
            insert(id);
            return id;
        }

        private void insert(final int id)
        {
            final int mask = table.length - 1;
            int slot = hash(key[id]) & mask;
            while (table[slot] != 0)
            {
                slot = (slot + 1) & mask;
            }
            table[slot] = id + 1;
        }

        private void rehash(final int size)
        {
            table = new int[size];
            for (int id = 0; id < count; id++)
            {
                insert(id);
            }
        }
    }

    /**
     * Binary min-heap of node ids by priority; stale entries are skipped when popped (the node is already closed).
     */
    private static final class Heap
    {
        private int[] ids = new int[1 << 15];
        private float[] keys = new float[1 << 15];
        int size;

        void push(final int id, final float priority)
        {
            if (size == ids.length)
            {
                ids = Arrays.copyOf(ids, size * 2);
                keys = Arrays.copyOf(keys, size * 2);
            }
            int i = size++;
            while (i > 0)
            {
                final int up = (i - 1) >>> 1;
                if (keys[up] <= priority)
                {
                    break;
                }
                ids[i] = ids[up];
                keys[i] = keys[up];
                i = up;
            }
            ids[i] = id;
            keys[i] = priority;
        }

        int pop()
        {
            final int top = ids[0];
            final int lastId = ids[--size];
            final float last = keys[size];
            int i = 0;
            while (true)
            {
                int child = 2 * i + 1;
                if (child >= size)
                {
                    break;
                }
                if (child + 1 < size && keys[child + 1] < keys[child])
                {
                    child++;
                }
                if (keys[child] >= last)
                {
                    break;
                }
                ids[i] = ids[child];
                keys[i] = keys[child];
                i = child;
            }
            ids[i] = lastId;
            keys[i] = last;
            return top;
        }
    }

    /**
     * A* over (x, z, road height), 4-connected (no diagonal: a route that cuts corners looks wrong). From height y the
     * next cell is entered at y - 1, y or y + 1 — staying at y however far the ground drops or rises is what carries a
     * deck across a ravine or a bore through a hill. A one-cell hump (up then straight down) is never generated: its
     * middle cell would need a stair facing both ways (docs/GOTCHA.md), hence the "just climbed" bit in the state.
     * Each step pays {@link #STEP}, {@link #CLIMB} if it changes level, the {@link #work} of laying that cell and a
     * penalty for anything a player built there (gone around, at a price, rather than treated as a wall).
     *
     * @return the centre line as {x, z, y} cells, start to end
     */
    @Nullable
    private static List<int[]> search(final Relief relief, final int ax, final int az, final int bx, final int bz)
    {
        if (!relief.probe(ax, az) || !relief.probe(bx, bz))
        {
            return null;
        }
        final int startY = relief.groundAt(ax, az);
        final int goalY = relief.groundAt(bx, bz);
        final int goalCell = relief.index(bx, bz);
        final Nodes nodes = new Nodes(relief.minY);
        final Heap open = new Heap();
        final int start = nodes.add(nodes.key(relief.index(ax, az), startY, false), -1, 0f);
        open.push(start, (float) heuristic(ax, az, startY, bx, bz, goalY));
        int expanded = 0;
        while (open.size > 0 && expanded < MAX_EXPANSIONS && nodes.count < MAX_NODES)
        {
            final int current = open.pop();
            if (nodes.closed[current])
            {
                continue;
            }
            nodes.closed[current] = true;
            expanded++;
            final long key = nodes.key[current];
            final int cell = Nodes.cell(key);
            final int y = nodes.y(key);
            if (cell == goalCell && y == goalY)
            {
                return reconstruct(relief, nodes, current);
            }
            final int x = relief.x0 + cell % relief.w;
            final int z = relief.z0 + cell / relief.w;
            final boolean climbed = Nodes.climbed(key);
            final int parent = nodes.parent[current];
            final int parentCell = parent < 0 ? -1 : Nodes.cell(nodes.key[parent]);
            final float base = nodes.g[current];
            for (int k = 0; k < 4; k++)
            {
                final int nx = x + DX[k];
                final int nz = z + DZ[k];
                if (!relief.probe(nx, nz))
                {
                    continue;
                }
                final int neighbour = relief.index(nx, nz);
                if (neighbour == parentCell)
                {
                    continue;
                }
                final int lo = relief.lowest(nx, nz);
                final int hi = relief.highest(nx, nz);
                // Columns spread across the direction of travel
                final int px = DZ[k] != 0 ? 1 : 0;
                final int pz = DX[k] != 0 ? 1 : 0;
                final double penalty = relief.blocked(nx, nz) ? BLOCKED_PENALTY : 0;
                for (int dy = -1; dy <= 1; dy++)
                {
                    if (dy < 0 && climbed)
                    {
                        continue;
                    }
                    final int ny = y + dy;
                    if (ny < lo || ny > hi)
                    {
                        continue;
                    }
                    final long nkey = nodes.key(neighbour, ny, dy > 0);
                    final int existing = nodes.find(nkey);
                    if (existing >= 0 && nodes.closed[existing])
                    {
                        continue;
                    }
                    final double work = work(relief, nx, nz, ny, px, pz, null);
                    if (work == Double.POSITIVE_INFINITY)
                    {
                        continue;
                    }
                    final float tentative = (float) (base + STEP + (dy != 0 ? CLIMB : 0) + work + penalty);
                    final int id;
                    if (existing < 0)
                    {
                        id = nodes.add(nkey, current, tentative);
                    }
                    else if (tentative < nodes.g[existing])
                    {
                        id = existing;
                        nodes.g[id] = tentative;
                        nodes.parent[id] = current;
                    }
                    else
                    {
                        continue;
                    }
                    open.push(id, (float) (tentative + heuristic(nx, nz, ny, bx, bz, goalY)));
                }
            }
        }
        return null;
    }

    /**
     * At least one {@link #STEP} per cell left and one {@link #CLIMB} per level left; the horizontal part is
     * weighted a little so long routes do not flood the search box.
     */
    private static double heuristic(final int x, final int z, final int y, final int bx, final int bz, final int by)
    {
        return HEURISTIC_WEIGHT * STEP * (Math.abs(bx - x) + Math.abs(bz - z)) + CLIMB * Math.abs(by - y);
    }

    private static List<int[]> reconstruct(final Relief relief, final Nodes nodes, final int goal)
    {
        final List<int[]> cells = new ArrayList<>();
        for (int id = goal; id >= 0; id = nodes.parent[id])
        {
            final long key = nodes.key[id];
            final int cell = Nodes.cell(key);
            cells.add(new int[] {relief.x0 + cell % relief.w, relief.z0 + cell / relief.w, nodes.y(key)});
        }
        Collections.reverse(cells);
        return cells;
    }

    /**
     * Turns the centre line, heights included, into real blocks {@link #WIDTH} wide. Each cell's style is the mode
     * {@link #work} finds cheapest at the height the search chose, across the direction the search entered it from —
     * the same columns it paid for.
     */
    private static Result build(final ServerLevel level, final Relief relief, final List<int[]> cells)
    {
        final int n = cells.size();
        final int[] roadY = new int[n];
        final Direction[] along = new Direction[n];
        final Mode[] modes = new Mode[n];
        final Mode[] out = new Mode[1];
        for (int i = 0; i < n; i++)
        {
            roadY[i] = cells.get(i)[2];
            final int[] from = i > 0 ? cells.get(i - 1) : cells.get(i);
            final int[] to = i > 0 ? cells.get(i) : cells.get(Math.min(n - 1, 1));
            along[i] = directionOf(to[0] - from[0], to[1] - from[1]);
            final Direction perp = along[i].getClockWise();
            out[0] = Mode.SURFACE;
            final double cost = work(relief, cells.get(i)[0], cells.get(i)[1], roadY[i], Math.abs(perp.getStepX()), Math.abs(perp.getStepZ()), out);
            modes[i] = cost == Double.POSITIVE_INFINITY ? Mode.SURFACE : out[0];
        }

        // Where the road changes level by one block, the stair goes on the HIGHER of the two cells, at that cell's
        // height, facing toward the climb (away from the lower cell) — see docs/GOTCHA.md: on a descent that is the
        // cell before the drop, not the one after it. The search never makes a one-cell hump, so no cell needs two.
        final Direction[] stairs = new Direction[n];
        for (int i = 1; i < n; i++)
        {
            if (roadY[i] == roadY[i - 1])
            {
                continue;
            }
            if (roadY[i] > roadY[i - 1])
            {
                if (stairs[i] == null)
                {
                    stairs[i] = along[i];
                }
            }
            else if (stairs[i - 1] == null)
            {
                stairs[i - 1] = along[i].getOpposite();
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
            final Direction perp = along[i].getClockWise();
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
