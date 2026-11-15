package fr.kodistan.interregne.routes.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a route between two points of a surveyed relief: {@link LatticeSearch} picks the line, the corners are eased,
 * {@link ProfileSolver} sets the heights. Pure computation on a {@link HeightField}: safe on a worker thread.
 */
public final class RoutePlanner
{
    private static final int WINDOW = 10;
    private static final int EASE = 3;
    private static final float[] GREEDS = {1.0F, 2.2F};

    private RoutePlanner()
    {
    }

    /**
     * @return null when either end lies outside the field or no route was found
     */
    public static RoutePlan plan(final HeightField field, final int ax, final int az, final int bx, final int bz)
    {
        return plan(field, ax, az, bx, bz, 4000);
    }

    /**
     * @param budgetMillis how long the search may keep refining its first answer
     */
    public static RoutePlan plan(final HeightField field, final int ax, final int az, final int bx, final int bz, final long budgetMillis)
    {
        if (!usable(field, ax, az) || !usable(field, bx, bz) || (ax == bx && az == bz))
        {
            return null;
        }
        final LatticeSearch search = new LatticeSearch(field);
        // The exact search while the time budget lasts (it always does on a route between neighbours); past it, a
        // greedy one that answers fast whatever the distance.
        final long deadline = System.nanoTime() + budgetMillis * 1_000_000L;
        List<int[]> line = null;
        for (final float greed : GREEDS)
        {
            line = search.search(ax, az, bx, bz, greed, greed == 1.0F ? deadline : Long.MAX_VALUE);
            if (line != null)
            {
                break;
            }
        }
        if (line == null)
        {
            return null;
        }
        final List<int[]> eased = ease(line);
        final int n = eased.size();
        final int[] xs = new int[n];
        final int[] zs = new int[n];
        final int[] guide = new int[n];
        for (int i = 0; i < n; i++)
        {
            xs[i] = eased.get(i)[0];
            zs[i] = eased.get(i)[1];
            guide[i] = eased.get(i)[2];
        }
        final boolean[] bend = bends(xs, zs);
        final Footprint footprint = new Footprint(xs, zs);
        final ProfileSolver solver = new ProfileSolver(field, footprint, xs, zs);
        final int startY = field.ground(ax, az);
        final int endY = field.ground(bx, bz);
        ProfileSolver.Result profile = solver.solve(guide, bend, WINDOW, startY, endY);
        if (profile == null)
        {
            profile = solver.solve(guide, new boolean[n], WINDOW, startY, endY);
        }
        if (profile == null)
        {
            profile = solver.solve(guide, new boolean[n], WINDOW * 3, startY, endY);
        }
        if (profile == null)
        {
            return null;
        }
        return new RoutePlan(xs, zs, profile.y(), profile.mode(), bend, footprint, profile.cost());
    }

    private static boolean usable(final HeightField field, final int x, final int z)
    {
        return field.inside(x, z) && (field.flags(x, z) & (HeightField.UNKNOWN | HeightField.LAVA)) == 0;
    }

    /**
     * Cuts each square corner with a short diagonal (up to {@link #EASE} cells each way, less when the straight
     * runs around it are short), so a bend reads as a curve instead of a right angle.
     */
    private static List<int[]> ease(final List<int[]> line)
    {
        final int n = line.size();
        final int[] cut = new int[n];
        int previousCorner = 0;
        final List<Integer> corners = new ArrayList<>();
        for (int i = 1; i < n - 1; i++)
        {
            if (line.get(i)[3] == 1)
            {
                corners.add(i);
            }
        }
        for (int c = 0; c < corners.size(); c++)
        {
            final int at = corners.get(c);
            final int before = at - previousCorner;
            final int after = (c + 1 < corners.size() ? corners.get(c + 1) : n - 1) - at;
            cut[at] = Math.min(EASE, Math.min(before / 2, after / 2));
            previousCorner = at;
        }
        final List<int[]> out = new ArrayList<>(n);
        int i = 0;
        while (i < n)
        {
            int corner = -1;
            for (int look = i; look <= Math.min(n - 1, i + EASE); look++)
            {
                if (cut[look] > 0 && look - cut[look] == i)
                {
                    corner = look;
                    break;
                }
            }
            if (corner < 0)
            {
                out.add(line.get(i));
                i++;
                continue;
            }
            final int m = cut[corner];
            final int[] from = line.get(corner - m);
            final int[] to = line.get(corner + m);
            final int sx = Integer.signum(to[0] - from[0]);
            final int sz = Integer.signum(to[1] - from[1]);
            out.add(from);
            for (int k = 1; k < m; k++)
            {
                out.add(new int[] {from[0] + sx * k, from[1] + sz * k, line.get(corner)[2], 0});
            }
            i = corner + m;
        }
        return out;
    }

    /**
     * Cells whose step to a neighbour is diagonal or turns, and the two cells on either side of them.
     */
    private static boolean[] bends(final int[] xs, final int[] zs)
    {
        final int n = xs.length;
        final boolean[] turning = new boolean[n];
        for (int i = 1; i < n - 1; i++)
        {
            final int inX = xs[i] - xs[i - 1];
            final int inZ = zs[i] - zs[i - 1];
            final int outX = xs[i + 1] - xs[i];
            final int outZ = zs[i + 1] - zs[i];
            turning[i] = inX != outX || inZ != outZ || (inX != 0 && inZ != 0);
        }
        final boolean[] bend = new boolean[n];
        for (int i = 0; i < n; i++)
        {
            if (turning[i])
            {
                for (int k = Math.max(0, i - 2); k <= Math.min(n - 1, i + 2); k++)
                {
                    bend[k] = true;
                }
            }
        }
        return bend;
    }
}
