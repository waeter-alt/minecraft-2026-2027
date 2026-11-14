package fr.kodistan.interregne.routes.plan;

import java.util.Arrays;

/**
 * Second pass: how high the road runs, cell by cell, along the line the search chose. A dynamic program over (cell,
 * height, what the road is there, how it last changed level) that prices the exact columns each cell covers, so the
 * profile it returns is the cheapest one that obeys the rules a caravan and a stair block impose: one level at most
 * per cell, no up-then-down within two cells, no step in a bend. Decks come out level from rim to rim because a step
 * on a deck is dear and its piers are cheap; cuts and embankments stay shallow because their price grows with their
 * depth; a bore or a deck only starts where its two ends are worth paying for.
 */
final class ProfileSolver
{
    private static final float INF = Float.POSITIVE_INFINITY;
    private static final int TRENDS = 5;
    private static final int MODES = 3;
    // Trend of a cell: level for two cells or more, level once after a step up / down, just stepped up / down
    private static final int LEVEL = 0;
    private static final int AFTER_UP = 1;
    private static final int AFTER_DOWN = 2;
    private static final int UP = 3;
    private static final int DOWN = 4;
    private static final float BEND_STRUCTURE = 60F;

    record Result(int[] y, byte[] mode, float cost)
    {
    }

    private final HeightField field;
    private final int n;
    // Per cell: the dry columns the road covers there, and what else it meets
    private final int[][] dry;
    private final int[] wet;
    private final int[] water;
    private final int[] blocked;
    private final boolean[] hard;
    private final int[] centre;
    private final int[] centreBed;
    private final boolean[] centreWet;
    private final boolean[] centreBlocked;

    ProfileSolver(final HeightField field, final Footprint footprint, final int[] xs, final int[] zs)
    {
        this.field = field;
        this.n = xs.length;
        this.dry = new int[n][];
        this.wet = new int[n];
        this.water = new int[n];
        this.blocked = new int[n];
        this.hard = new boolean[n];
        this.centre = new int[n];
        this.centreBed = new int[n];
        this.centreWet = new boolean[n];
        this.centreBlocked = new boolean[n];
        final int[] buffer = new int[81];
        for (int i = 0; i < n; i++)
        {
            int count = 0;
            water[i] = Short.MIN_VALUE;
            for (int dz = -4; dz <= 4; dz++)
            {
                for (int dx = -4; dx <= 4; dx++)
                {
                    final int x = xs[i] + dx;
                    final int z = zs[i] + dz;
                    if (footprint.owner(x, z) != i || !Footprint.road(footprint.distanceSq(x, z)))
                    {
                        continue;
                    }
                    final int flag = field.flags(x, z);
                    if ((flag & (HeightField.UNKNOWN | HeightField.LAVA)) != 0)
                    {
                        hard[i] = true;
                    }
                    else if ((flag & (HeightField.WATER | HeightField.ICE)) != 0)
                    {
                        wet[i]++;
                        water[i] = Math.max(water[i], field.ground(x, z));
                    }
                    else if ((flag & HeightField.BLOCKED) != 0)
                    {
                        blocked[i]++;
                    }
                    else
                    {
                        buffer[count++] = field.ground(x, z);
                    }
                }
            }
            dry[i] = Arrays.copyOf(buffer, count);
            Arrays.sort(dry[i]);
            final int flag = field.flags(xs[i], zs[i]);
            centre[i] = field.inside(xs[i], zs[i]) ? field.ground(xs[i], zs[i]) : 0;
            centreBed[i] = field.inside(xs[i], zs[i]) ? field.bed[field.index(xs[i], zs[i])] : 0;
            centreWet[i] = (flag & (HeightField.WATER | HeightField.ICE)) != 0;
            centreBlocked[i] = (flag & HeightField.BLOCKED) != 0;
        }
    }

    float surface(final int i, final int y)
    {
        final int[] g = dry[i];
        if (hard[i] || centreWet[i] || centreBlocked[i] || g.length == 0 || wet[i] > Tuning.WET_MAX || (wet[i] > 0 && y <= water[i]))
        {
            return INF;
        }
        final int deepest = g[g.length - 1] - y;
        final int highest = y - g[0];
        if (deepest > Tuning.CUT_MAX || highest > Tuning.FILL_MAX)
        {
            return INF;
        }
        float cut = 0;
        float fill = 0;
        for (final int ground : g)
        {
            if (ground > y)
            {
                cut += ground - y;
            }
            else
            {
                fill += y - ground;
            }
        }
        final float scale = 7F / g.length;
        return 1 + Tuning.W_CUT * cut * scale * (1 + Math.max(0, deepest) / 4F) + Tuning.W_FILL * fill * scale * (1 + Math.max(0, highest) / 3F)
                 + wet[i] * Tuning.WET_COLUMN + blocked[i] * Tuning.BLOCKED_COLUMN;
    }

    float bridge(final int i, final int y, final float surfaceCost)
    {
        final int[] g = dry[i];
        if (hard[i] || blocked[i] > 0 || centreBlocked[i])
        {
            return INF;
        }
        if (!(centreWet[i] || y - centre[i] >= 2 || (y >= centre[i] && surfaceCost == INF)))
        {
            return INF;
        }
        if (wet[i] > 0 && y < water[i] + Tuning.WATER_CLEAR)
        {
            return INF;
        }
        if (g.length > 0 && (g[g.length - 1] - y > 1 || y - g[0] > Tuning.BRIDGE_MAX))
        {
            return INF;
        }
        return Tuning.BRIDGE_CELL + Tuning.pier(y - (centreWet[i] ? centreBed[i] : centre[i]));
    }

    float tunnel(final int i, final int y)
    {
        final int[] g = dry[i];
        if (hard[i] || blocked[i] > 0 || centreBlocked[i] || wet[i] > 0 || g.length == 0)
        {
            return INF;
        }
        if (centre[i] - y < Tuning.COVER || g[0] - y < Tuning.SIDE_COVER)
        {
            return INF;
        }
        return Tuning.TUNNEL_CELL + (g[0] - y < Tuning.COVER ? 1F : 0F);
    }

    /**
     * @param guide  the height the search had in mind at each cell: the profile stays within window of it
     * @param bend   cells in or next to a bend: no step there, and no structure if it can be helped
     * @return null when no profile obeys the rules within the window
     */
    Result solve(final int[] guide, final boolean[] bend, final int window, final int startY, final int endY)
    {
        final int heights = 2 * window + 1;
        final int stride = heights * TRENDS * MODES;
        final float[] cost = new float[n * stride];
        final int[] back = new int[n * stride];
        Arrays.fill(cost, INF);
        final float[] here = new float[heights * MODES];

        final int startIndex = startY - (guide[0] - window);
        if (startIndex < 0 || startIndex >= heights)
        {
            return null;
        }
        cost[(startIndex * TRENDS + LEVEL) * MODES + Tuning.SURFACE] = 0;

        for (int i = 1; i < n; i++)
        {
            final int low = guide[i] - window;
            final int previousLow = guide[i - 1] - window;
            final boolean last = i == n - 1;
            for (int yi = 0; yi < heights; yi++)
            {
                final int y = low + yi;
                final float s = last ? (y == endY ? 0F : INF) : surface(i, y);
                here[yi * MODES + Tuning.SURFACE] = s;
                here[yi * MODES + Tuning.BRIDGE] = last ? INF : bridge(i, y, s);
                here[yi * MODES + Tuning.TUNNEL] = last ? INF : tunnel(i, y);
            }
            final boolean still = bend[i] || bend[i - 1];
            final int base = i * stride;
            final int previousBase = (i - 1) * stride;
            for (int yi = 0; yi < heights; yi++)
            {
                final int y = low + yi;
                for (byte m = 0; m < MODES; m++)
                {
                    float c = here[yi * MODES + m];
                    if (c == INF)
                    {
                        continue;
                    }
                    if (bend[i] && m != Tuning.SURFACE)
                    {
                        c += BEND_STRUCTURE;
                    }
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        if (still && dy != 0)
                        {
                            continue;
                        }
                        final int pyi = y - dy - previousLow;
                        if (pyi < 0 || pyi >= heights)
                        {
                            continue;
                        }
                        for (int pt = 0; pt < TRENDS; pt++)
                        {
                            final int trend;
                            boolean flight = false;
                            if (dy == 0)
                            {
                                trend = pt == UP ? AFTER_UP : pt == DOWN ? AFTER_DOWN : LEVEL;
                            }
                            else if (dy > 0)
                            {
                                if (pt == AFTER_DOWN || pt == DOWN)
                                {
                                    continue;
                                }
                                trend = UP;
                                flight = pt == UP;
                            }
                            else
                            {
                                if (pt == AFTER_UP || pt == UP)
                                {
                                    continue;
                                }
                                trend = DOWN;
                                flight = pt == DOWN;
                            }
                            final int target = base + (yi * TRENDS + trend) * MODES + m;
                            for (byte pm = 0; pm < MODES; pm++)
                            {
                                final int source = previousBase + (pyi * TRENDS + pt) * MODES + pm;
                                final float before = cost[source];
                                if (before == INF)
                                {
                                    continue;
                                }
                                float total = before + c + Tuning.switchCost(pm, m);
                                if (dy != 0)
                                {
                                    total += Math.max(Tuning.stepCost(pm), Tuning.stepCost(m)) * (flight ? Tuning.STEEP : 1F);
                                }
                                if (total < cost[target])
                                {
                                    cost[target] = total;
                                    back[target] = source - previousBase;
                                }
                            }
                        }
                    }
                }
            }
        }

        final int endIndex = endY - (guide[n - 1] - window);
        if (endIndex < 0 || endIndex >= heights)
        {
            return null;
        }
        int best = -1;
        for (int t = 0; t < TRENDS; t++)
        {
            final int state = (endIndex * TRENDS + t) * MODES + Tuning.SURFACE;
            if (cost[(n - 1) * stride + state] < (best < 0 ? INF : cost[(n - 1) * stride + best]))
            {
                best = state;
            }
        }
        if (best < 0)
        {
            return null;
        }
        final int[] y = new int[n];
        final byte[] mode = new byte[n];
        final float total = cost[(n - 1) * stride + best];
        int state = best;
        for (int i = n - 1; i >= 0; i--)
        {
            y[i] = guide[i] - window + state / (TRENDS * MODES);
            mode[i] = (byte) (state % MODES);
            state = back[i * stride + state];
        }
        return new Result(y, mode, total);
    }
}
