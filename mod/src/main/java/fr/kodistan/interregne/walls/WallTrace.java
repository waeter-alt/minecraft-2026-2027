package fr.kodistan.interregne.walls;

import java.util.ArrayList;
import java.util.List;

/**
 * The points a player places to trace a wall, open or closed. Segments run straight between points; by default each
 * one snaps to a multiple of 45°, since free diagonals read as jagged stairs.
 */
public final class WallTrace
{
    /**
     * A point of the trace, on the block grid.
     */
    public record Point(int x, int z)
    {
        public int distanceSq(final Point other)
        {
            final int dx = x - other.x;
            final int dz = z - other.z;
            return dx * dx + dz * dz;
        }
    }

    private final List<Point> points = new ArrayList<>();
    private boolean closed;
    private boolean finished;
    private int version;

    public List<Point> points()
    {
        return points;
    }

    public boolean closed()
    {
        return closed;
    }

    public boolean isEmpty()
    {
        return points.isEmpty();
    }

    /**
     * Bumped on every change, so callers know when to lay the wall out again.
     */
    public int version()
    {
        return version;
    }

    public void add(final Point point)
    {
        if (done() || (!points.isEmpty() && points.getLast().equals(point)))
        {
            return;
        }
        points.add(point);
        version++;
    }

    public void close()
    {
        if (points.size() >= 3)
        {
            closed = true;
            version++;
        }
    }

    /**
     * Ends an open trace on its last point: no more points follow the mouse.
     */
    public void finish()
    {
        if (points.size() >= 2 && !closed)
        {
            finished = true;
            version++;
        }
    }

    /**
     * No more points can be added: closed into a loop, or ended open on its last tower.
     */
    public boolean done()
    {
        return closed || finished;
    }

    /**
     * Undoes the last action: reopens a closed or ended trace, otherwise removes the last point.
     */
    public void undo()
    {
        if (closed || finished)
        {
            closed = false;
            finished = false;
        }
        else if (!points.isEmpty())
        {
            points.removeLast();
        }
        version++;
    }

    public void clear()
    {
        points.clear();
        finished = false;
        closed = false;
        version++;
    }

    /**
     * Where the next point goes when the mouse is over target: snapped to 0/45/90°… from the last point unless free.
     */
    public Point snap(final Point target, final boolean free)
    {
        if (points.isEmpty() || free)
        {
            return target;
        }
        final Point from = points.getLast();
        final int dx = target.x - from.x;
        final int dz = target.z - from.z;
        if (dx == 0 && dz == 0)
        {
            return target;
        }
        final double angle = Math.round(Math.atan2(dz, dx) / (Math.PI / 4)) * (Math.PI / 4);
        final int ux = (int) Math.round(Math.cos(angle));
        final int uz = (int) Math.round(Math.sin(angle));
        // Length along the snapped direction: the projection, in whole steps of (ux, uz)
        final double along = (dx * ux + dz * uz) / (double) (ux * ux + uz * uz);
        final int steps = (int) Math.max(0, Math.round(along));
        return new Point(from.x + ux * steps, from.z + uz * steps);
    }

    /**
     * The cells of a straight line from a to b, 4-connected (never a diagonal step) so the wall has no gap a player
     * could slip through. Includes both ends.
     */
    public static List<Point> line(final Point a, final Point b)
    {
        final List<Point> cells = new ArrayList<>();
        int x = a.x;
        int z = a.z;
        final int dx = Math.abs(b.x - a.x);
        final int dz = Math.abs(b.z - a.z);
        final int sx = Integer.signum(b.x - a.x);
        final int sz = Integer.signum(b.z - a.z);
        cells.add(new Point(x, z));
        int ix = 0;
        int iz = 0;
        while (ix < dx || iz < dz)
        {
            // Step along the axis whose next crossing comes first
            if ((0.5 + ix) * dz < (0.5 + iz) * dx)
            {
                x += sx;
                ix++;
            }
            else
            {
                z += sz;
                iz++;
            }
            cells.add(new Point(x, z));
        }
        return cells;
    }
}
