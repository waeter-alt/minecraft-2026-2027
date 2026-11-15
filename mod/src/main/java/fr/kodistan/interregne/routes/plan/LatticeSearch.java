package fr.kodistan.interregne.routes.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * First pass: where the road goes. An A* over (lattice node, heading) on a lattice of lines four blocks apart that
 * pass through both ends. From a node the road either follows the ground to the next node, or runs straight on at
 * the level it stands at for as long as it pays: over a hollow on a deck, through a rise in a cut or a bore, landing
 * wherever the ground comes back to that level. Every cell is priced for what it really takes at that height, so a
 * bridge or a tunnel is never decided: it is what the cheapest line turns out to be, and its deck or floor is level
 * from one foot to the other by construction. The lattice and the price of a turn give long straight runs with square
 * corners (no diagonal, no wobble); a deck or a bore never turns. Heights found here are only a guide:
 * {@link ProfileSolver} sets the real ones block by block.
 */
final class LatticeSearch
{
    static final int SPACING = 4;
    private static final int NONE = 4;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    private static final int HALF = 3;
    private static final float INF = Float.POSITIVE_INFINITY;

    private final HeightField field;
    private final int w;
    // Per travel axis (0: along x, 1: along z), the seven columns across the road at each cell
    private final short[][] lo = new short[2][];
    private final short[][] hi = new short[2][];
    private final short[][] sum = new short[2][];
    private final short[][] surface = new short[2][];
    private final byte[][] wet = new byte[2][];
    private final byte[][] blocked = new byte[2][];
    private final byte[][] hard = new byte[2][];

    /** How far a level chord looks for a structure to start, and how long it may run in all. */
    private static final int CHORD_REACH = 40;
    private static final int MAX_CHORD = 260;
    /** A deck may land this far above the ground: the rest is a ramp. */
    private static final int RAMP_MAX = 6;
    private static final int BLEND = 6;

    private int[] marchAt;
    private int[] landNode = new int[1 << 14];
    private float[] landCost = new float[1 << 14];
    private short[] landChord = new short[1 << 14];
    private int landings;

    private byte lastMode;
    /** After an infinite {@link #cell}: the deck height the water there asks for, when that was what failed. */
    private int lastClearance;
    long expanded;
    /** Price of the line the last successful search returned. */
    float lastCost;

    LatticeSearch(final HeightField field)
    {
        this.field = field;
        this.w = field.w;
        final int size = field.w * field.h;
        for (int axis = 0; axis < 2; axis++)
        {
            lo[axis] = new short[size];
            hi[axis] = new short[size];
            sum[axis] = new short[size];
            surface[axis] = new short[size];
            wet[axis] = new byte[size];
            blocked[axis] = new byte[size];
            hard[axis] = new byte[size];
            for (int z = field.z0; z < field.z0 + field.h; z++)
            {
                for (int x = field.x0; x < field.x0 + field.w; x++)
                {
                    strip(axis, x, z);
                }
            }
        }
    }

    private void strip(final int axis, final int x, final int z)
    {
        int min = Short.MAX_VALUE;
        int max = Short.MIN_VALUE;
        int total = 0;
        int water = Short.MIN_VALUE;
        int wetCount = 0;
        int blockedCount = 0;
        int hardCount = 0;
        for (int o = -HALF; o <= HALF; o++)
        {
            final int cx = axis == 0 ? x : x + o;
            final int cz = axis == 0 ? z + o : z;
            if (!field.inside(cx, cz))
            {
                hardCount++;
                continue;
            }
            final int i = field.index(cx, cz);
            final int flag = field.flags[i];
            if ((flag & (HeightField.UNKNOWN | HeightField.LAVA | HeightField.FOREIGN)) != 0)
            {
                hardCount++;
            }
            else if ((flag & (HeightField.WATER | HeightField.ICE)) != 0)
            {
                wetCount++;
                water = Math.max(water, field.ground[i]);
            }
            else
            {
                final int g = field.ground[i];
                min = Math.min(min, g);
                max = Math.max(max, g);
                total += g;
                if ((flag & HeightField.BLOCKED) != 0)
                {
                    blockedCount++;
                }
            }
        }
        final int i = field.index(x, z);
        lo[axis][i] = (short) min;
        hi[axis][i] = (short) max;
        sum[axis][i] = (short) total;
        surface[axis][i] = (short) water;
        wet[axis][i] = (byte) wetCount;
        blocked[axis][i] = (byte) blockedCount;
        hard[axis][i] = (byte) hardCount;
    }

    /**
     * Price of one cell of road at height y, travelling along the axis, and (in {@link #lastMode}) what the road is
     * there. Infinite where no road can be.
     */
    private float cell(final int x, final int z, final int y, final int axis)
    {
        if (!field.inside(x, z))
        {
            return INF;
        }
        final int i = field.index(x, z);
        final int flag = field.flags[i];
        if (hard[axis][i] > 0 || (flag & HeightField.BLOCKED) != 0)
        {
            return INF;
        }
        final boolean centreWet = (flag & (HeightField.WATER | HeightField.ICE)) != 0;
        final int gc = field.ground[i];
        final int nWet = wet[axis][i];
        final int nDry = 2 * HALF + 1 - nWet;
        final int nBlocked = blocked[axis][i];
        final int min = lo[axis][i];
        final int max = hi[axis][i];
        final int water = surface[axis][i];

        float best = INF;
        byte mode = Tuning.SURFACE;
        lastClearance = nWet > 0 ? water + Tuning.WATER_CLEAR : Integer.MIN_VALUE;
        if (!centreWet && nWet <= Tuning.WET_MAX && max - y <= Tuning.CUT_MAX && y - min <= Tuning.FILL_MAX && (nWet == 0 || y > water))
        {
            final float cut;
            final float fill;
            final int total = sum[axis][i];
            if (y >= max)
            {
                cut = 0;
                fill = nDry * y - total;
            }
            else if (y <= min)
            {
                cut = total - nDry * y;
                fill = 0;
            }
            else
            {
                final float range = max - min;
                cut = nDry * (max - y) * (max - y) / (2 * range);
                fill = nDry * (y - min) * (y - min) / (2 * range);
            }
            best = ((flag & HeightField.ROAD) != 0 ? Tuning.EXISTING_ROAD : 1) + Tuning.W_CUT * cut * (1 + Math.max(0, max - y) / 4F) + Tuning.W_FILL * fill * (1 + Math.max(0, y - min) / 3F)
                     + nWet * Tuning.WET_COLUMN + nBlocked * Tuning.BLOCKED_COLUMN;
        }
        if (nBlocked == 0 && (centreWet || y - gc >= 2 || (y >= gc && best == INF))
              && (nWet == 0 || y >= water + Tuning.WATER_CLEAR) && (nDry == 0 || (max - y <= 1 && y - min <= Tuning.BRIDGE_MAX)))
        {
            final int base = centreWet ? field.bed[i] : gc;
            final float deck = Tuning.BRIDGE_CELL + Tuning.pier(y - base);
            if (deck < best)
            {
                best = deck;
                mode = Tuning.BRIDGE;
            }
        }
        if (nBlocked == 0 && nWet == 0 && gc - y >= Tuning.COVER && min - y >= Tuning.SIDE_COVER)
        {
            final float bore = Tuning.TUNNEL_CELL + (min - y < Tuning.COVER ? 1F : 0F);
            if (bore < best)
            {
                best = bore;
                mode = Tuning.TUNNEL;
            }
        }
        lastMode = mode;
        return best;
    }

    /**
     * Lattice lines along one axis: through a and b exactly, {@link #SPACING} apart (give or take one between the
     * two ends), out to the bounds.
     */
    static int[] lines(final int a, final int b, final int min, final int max)
    {
        final List<Integer> out = new ArrayList<>();
        final int d = b - a;
        final int n = Math.max(1, Math.round(Math.abs(d) / (float) SPACING));
        int last = Integer.MIN_VALUE;
        for (int k = 0; k <= (d == 0 ? 0 : n); k++)
        {
            final int line = a + (int) Math.round(k * (double) d / n);
            if (line != last)
            {
                out.add(line);
                last = line;
            }
        }
        for (int line = Math.min(a, b) - SPACING; line >= min; line -= SPACING)
        {
            out.add(line);
        }
        for (int line = Math.max(a, b) + SPACING; line <= max; line += SPACING)
        {
            out.add(line);
        }
        Collections.sort(out);
        final int[] array = new int[out.size()];
        for (int i = 0; i < array.length; i++)
        {
            array[i] = out.get(i);
        }
        return array;
    }

    /**
     * @return the centre line cell by cell as {x, z, guide height, 1 when the road turns there}, start to end, or
     * null when no way was found in time
     */
    List<int[]> search(final int ax, final int az, final int bx, final int bz, final float greed, final long deadlineNanos)
    {
        final int[] xs = lines(ax, bx, field.x0 + HALF + 1, field.x0 + field.w - HALF - 2);
        final int[] zs = lines(az, bz, field.z0 + HALF + 1, field.z0 + field.h - HALF - 2);
        final int nx = xs.length;
        final int nz = zs.length;
        final int ai = Arrays.binarySearch(xs, ax);
        final int aj = Arrays.binarySearch(zs, az);
        final int bi = Arrays.binarySearch(xs, bx);
        final int bj = Arrays.binarySearch(zs, bz);
        if (ai < 0 || aj < 0 || bi < 0 || bj < 0)
        {
            return null;
        }
        final int startNode = aj * nx + ai;
        final int goalNode = bj * nx + bi;
        // Height of the road at a node: the ground there (middle value of the nine columns around it, so one
        // boulder or one hole does not make a level)
        final short[] level = new short[nx * nz];
        final int[] around = new int[9];
        for (int j = 0; j < nz; j++)
        {
            for (int i = 0; i < nx; i++)
            {
                int count = 0;
                for (int dz = -1; dz <= 1; dz++)
                {
                    for (int dx = -1; dx <= 1; dx++)
                    {
                        final int x = xs[i] + dx;
                        final int z = zs[j] + dz;
                        if (field.inside(x, z) && (field.flags[field.index(x, z)] & (HeightField.WATER | HeightField.ICE | HeightField.UNKNOWN)) == 0)
                        {
                            around[count++] = field.ground(x, z);
                        }
                    }
                }
                Arrays.sort(around, 0, count);
                level[j * nx + i] = (short) (count == 0 ? field.ground(xs[i], zs[j]) : around[count / 2]);
            }
        }
        level[startNode] = (short) field.ground(ax, az);
        level[goalNode] = (short) field.ground(bx, bz);
        final int goalY = level[goalNode];

        final int total = nx * nz * 5;
        final float[] g = new float[total];
        final int[] parent = new int[total];
        final short[] via = new short[total];
        final boolean[] closed = new boolean[total];
        Arrays.fill(g, INF);
        marchAt = new int[nx * nz * 4];
        Arrays.fill(marchAt, -1);
        landings = 0;
        final Heap open = new Heap();
        final int start = startNode * 5 + NONE;
        g[start] = 0;
        parent[start] = -1;
        open.push(start, 0F);
        expanded = 0;
        while (open.size > 0)
        {
            final int current = open.pop();
            if (closed[current])
            {
                continue;
            }
            closed[current] = true;
            expanded++;
            if ((expanded & 0x3FF) == 0 && System.nanoTime() > deadlineNanos)
            {
                return null;
            }
            final int node = current / 5;
            final int dir = current % 5;
            if (node == goalNode)
            {
                lastCost = g[current];
                return unroll(current, parent, via, level, xs, zs, nx);
            }
            final int i = node % nx;
            final int j = node / nx;
            final int x = xs[i];
            final int z = zs[j];
            final int y = level[node];
            for (int nd = 0; nd < 4; nd++)
            {
                final boolean turning = dir != NONE && nd != dir;
                float base = g[current];
                if (turning)
                {
                    if ((nd ^ 1) == dir || cell(x, z, y, nd >> 1) == INF || lastMode != Tuning.SURFACE)
                    {
                        continue;
                    }
                    base += Tuning.TURN;
                }
                final int[] line = (nd >> 1) == 0 ? xs : zs;
                final int at = (nd >> 1) == 0 ? i : j;
                final int sign = (nd & 1) == 0 ? 1 : -1;
                final int stride = (nd >> 1) == 0 ? 1 : nx;
                if (at + sign < 0 || at + sign >= line.length)
                {
                    continue;
                }

                // The road on the ground to the next node, following its level
                final int next = node + sign * stride;
                final int length = Math.abs(line[at + sign] - line[at]);
                final int rise = level[next] - y;
                if (Math.abs(rise) <= length)
                {
                    final float cost = edge(x, z, y, nd, length, rise, turning);
                    if (cost != INF)
                    {
                        relax(open, g, parent, via, closed, next * 5 + nd, current, base + cost, Short.MIN_VALUE, greed, xs[next % nx], zs[next / nx], level[next], bx, bz, goalY);
                    }
                }

                // Straight on at one level, whatever the ground does: over a hollow on a deck, through a rise in a
                // cut or a bore. Lands on every node where the ground comes back to that level.
                final int slot = node * 4 + nd;
                if (marchAt[slot] < 0)
                {
                    march(slot, x, z, y, nd, node, next, at, sign, stride, line, level);
                }
                final int from = marchAt[slot];
                final int count = landNode[from];
                if (turning && landChord[from] != 0)
                {
                    continue;
                }
                for (int m = from + 1; m <= from + count; m++)
                {
                    final int to = landNode[m];
                    relax(open, g, parent, via, closed, to * 5 + nd, current, base + landCost[m], landChord[m], greed, xs[to % nx], zs[to / nx], level[to], bx, bz, goalY);
                }
            }
        }
        return null;
    }

    /**
     * Every landing of the level chord leaving a node along nd, computed once per (node, heading) and kept: the
     * node reached, the price from the take-off, the level of the chord. The header entry holds the count, and
     * whether the chord leaves the ground within two cells (then it cannot follow a turn).
     */
    private void march(final int slot, final int x, final int z, final int y, final int nd, final int node, final int next, final int at,
      final int sign, final int stride, final int[] line, final short[] level)
    {
        final int header = landings;
        marchAt[slot] = header;
        push(0, 0F, (short) 0);
        int chord = y;
        float ramp = 0;
        boolean raised = false;
        march:
        while (true)
        {
            landings = header + 1;
            landChord[header] = 0;
            float sum = ramp;
            byte mode = Tuning.SURFACE;
            boolean structure = false;
            int node2 = node;
            int index = at;
            for (int k = 1; k <= MAX_CHORD; k++)
            {
                final int cx = x + DX[nd] * k;
                final int cz = z + DZ[nd] * k;
                final float c = cell(cx, cz, chord, nd >> 1);
                if (c == INF)
                {
                    if (!raised && lastClearance > chord && lastClearance - y <= Tuning.FILL_MAX)
                    {
                        // Water under a deck too low: take off higher, the bank ramps up to it
                        raised = true;
                        ramp = (lastClearance - y) * Tuning.STEP;
                        chord = lastClearance;
                        continue march;
                    }
                    break march;
                }
                final byte now = lastMode;
                if (now != mode)
                {
                    if (k <= 2)
                    {
                        landChord[header] = 1;
                    }
                    sum += Tuning.switchCost(mode, now);
                    mode = now;
                }
                sum += c;
                structure |= now != Tuning.SURFACE;
                if (!structure && k > CHORD_REACH)
                {
                    break march;
                }
                if ((nd >> 1) == 0 ? cx == line[index + sign] : cz == line[index + sign])
                {
                    index += sign;
                    node2 += sign * stride;
                    final int gap = chord - level[node2];
                    if (structure && node2 != next)
                    {
                        float landing = INF;
                        if (now == Tuning.SURFACE)
                        {
                            landing = Math.abs(gap) * Tuning.STEP;
                        }
                        else if (now == Tuning.BRIDGE && gap <= RAMP_MAX && !wetCentre(cx, cz))
                        {
                            landing = Tuning.ABUTMENT + gap * Tuning.STEP_BRIDGE;
                        }
                        if (landing != INF)
                        {
                            push(node2, sum + landing, (short) chord);
                        }
                    }
                    if (index + sign < 0 || index + sign >= line.length)
                    {
                        break march;
                    }
                }
            }
            break;
        }
        landNode[header] = landings - header - 1;
    }

    private void push(final int node, final float cost, final short chord)
    {
        if (landings == landNode.length)
        {
            landNode = Arrays.copyOf(landNode, landings * 2);
            landCost = Arrays.copyOf(landCost, landings * 2);
            landChord = Arrays.copyOf(landChord, landings * 2);
        }
        landNode[landings] = node;
        landCost[landings] = cost;
        landChord[landings] = chord;
        landings++;
    }

    private boolean wetCentre(final int x, final int z)
    {
        return (field.flags[field.index(x, z)] & (HeightField.WATER | HeightField.ICE)) != 0;
    }

    private void relax(final Heap open, final float[] g, final int[] parent, final short[] via, final boolean[] closed, final int state,
      final int from, final float cost, final short chord, final float greed, final int x, final int z, final int y, final int bx, final int bz,
      final int goalY)
    {
        if (closed[state] || cost >= g[state])
        {
            return;
        }
        g[state] = cost;
        parent[state] = from;
        via[state] = chord;
        open.push(state, cost + greed * (Math.abs(bx - x) + Math.abs(bz - z)) + Tuning.STEP * Math.abs(goalY - y));
    }

    /**
     * Price of the cells between a node and the next along dir, the road changing level by rise over them.
     */
    private float edge(final int x, final int z, final int y, final int dir, final int length, final int rise, final boolean turning)
    {
        final float steep = Math.abs(rise) * 4 > length * 3 ? 2.2F : Math.abs(rise) * 2 > length ? 1.5F : 1F;
        float total = 0;
        byte mode = Tuning.SURFACE;
        int level = y;
        for (int k = 1; k <= length; k++)
        {
            final int ky = y + Math.round(k * rise / (float) length);
            final float c = cell(x + DX[dir] * k, z + DZ[dir] * k, ky, dir >> 1);
            if (c == INF)
            {
                return INF;
            }
            total += c;
            final byte now = lastMode;
            // A corner stays level and on the ground a little way out, so the turn itself is never a ramp or a deck end
            if (turning && k <= 2 && (now != Tuning.SURFACE || ky != level))
            {
                return INF;
            }
            total += Tuning.switchCost(mode, now);
            if (ky != level)
            {
                total += Math.max(Tuning.stepCost(mode), Tuning.stepCost(now)) * steep;
            }
            mode = now;
            level = ky;
        }
        return mode == Tuning.SURFACE ? total : INF;
    }

    private List<int[]> unroll(final int goal, final int[] parent, final short[] via, final short[] level, final int[] xs, final int[] zs, final int nx)
    {
        final List<Integer> states = new ArrayList<>();
        for (int s = goal; s >= 0; s = parent[s])
        {
            states.add(s);
        }
        Collections.reverse(states);
        final List<int[]> cells = new ArrayList<>();
        final int first = states.get(0) / 5;
        cells.add(new int[] {xs[first % nx], zs[first / nx], level[first], 0});
        int lastDir = NONE;
        for (int n = 1; n < states.size(); n++)
        {
            final int from = states.get(n - 1) / 5;
            final int to = states.get(n) / 5;
            final int dir = states.get(n) % 5;
            final int fx = xs[from % nx];
            final int fz = zs[from / nx];
            final int length = Math.abs(xs[to % nx] - fx) + Math.abs(zs[to / nx] - fz);
            if (lastDir != NONE && lastDir != dir)
            {
                cells.get(cells.size() - 1)[3] = 1;
            }
            lastDir = dir;
            final int chord = via[states.get(n)];
            final int blend = Math.min(BLEND, length / 2);
            for (int k = 1; k <= length; k++)
            {
                final int y;
                if (chord == Short.MIN_VALUE)
                {
                    y = level[from] + Math.round(k * (level[to] - level[from]) / (float) length);
                }
                else if (k <= blend)
                {
                    y = level[from] + Math.round(k * (chord - level[from]) / (float) (blend + 1));
                }
                else if (k > length - blend)
                {
                    y = level[to] + Math.round((length - k) * (chord - level[to]) / (float) (blend + 1));
                }
                else
                {
                    y = chord;
                }
                cells.add(new int[] {fx + DX[dir] * k, fz + DZ[dir] * k, y, 0});
            }
        }
        return cells;
    }

    /**
     * Binary min-heap of state ids by priority; stale entries are skipped when popped (the state is already closed).
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
}
