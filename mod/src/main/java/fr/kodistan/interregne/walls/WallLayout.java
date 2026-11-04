package fr.kodistan.interregne.walls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A traced wall laid on the terrain: the columns of wall between the points and a square tower on each point. The
 * wall keeps the same height above the ground everywhere; its crest is smoothed so it rises and falls with the
 * relief instead of stepping every block. Shared by the planner's ghost and, later, the construction.
 */
public final class WallLayout
{
    public enum Status
    {
        OK,
        /**
         * A hut or a player's block is in the way.
         */
        BLOCKED,
        /**
         * Outside the colony, or terrain not loaded.
         */
        OUTSIDE
    }

    /**
     * One column of wall, from bottom to top inclusive.
     */
    public record Column(int x, int z, int bottom, int top, Status status)
    {
    }

    /**
     * A square tower; x and z are its north-west corner.
     */
    public record Tower(int x, int z, int size, int bottom, int top, Status status)
    {
    }

    /**
     * Terrain for one column.
     */
    @FunctionalInterface
    public interface Ground
    {
        Terrain.Probe at(int x, int z);
    }

    /**
     * Whether a column belongs to the colony.
     */
    @FunctionalInterface
    public interface Claim
    {
        boolean contains(int x, int z);
    }

    private static final int SMOOTH = 2;

    private final List<Column> columns;
    private final List<Tower> towers;
    private final int length;

    private WallLayout(final List<Column> columns, final List<Tower> towers, final int length)
    {
        this.columns = columns;
        this.towers = towers;
        this.length = length;
    }

    public List<Column> columns()
    {
        return columns;
    }

    public List<Tower> towers()
    {
        return towers;
    }

    /**
     * Blocks of wall along the trace, towers excluded.
     */
    public int length()
    {
        return length;
    }

    public int blocked()
    {
        int count = 0;
        for (final Column column : columns)
        {
            if (column.status != Status.OK)
            {
                count++;
            }
        }
        for (final Tower tower : towers)
        {
            if (tower.status != Status.OK)
            {
                count++;
            }
        }
        return count;
    }

    public int cost(final WallLevels.Level level)
    {
        return length * level.perColumn() + towers.size() * level.perTower();
    }

    /**
     * Lays out the points (closed or not) at this level.
     */
    public static WallLayout of(final List<WallTrace.Point> points, final boolean closed, final WallLevels.Level level, final Ground ground,
      final Claim claim)
    {
        final List<WallTrace.Point> center = centerline(points, closed);
        final int half = level.towerSize() / 2;

        // Towers first: their footprint is taken out of the wall
        final List<Tower> towers = new ArrayList<>();
        final Map<Long, Boolean> towerCells = new LinkedHashMap<>();
        for (final WallTrace.Point point : points)
        {
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            int roof = Integer.MAX_VALUE;
            Status status = Status.OK;
            for (int dx = -half; dx < level.towerSize() - half; dx++)
            {
                for (int dz = -half; dz < level.towerSize() - half; dz++)
                {
                    final int x = point.x() + dx;
                    final int z = point.z() + dz;
                    towerCells.put(key(x, z), true);
                    final Terrain.Probe probe = ground.at(x, z);
                    final Status cell = statusOf(probe, claim, x, z);
                    if (cell == Status.OK)
                    {
                        low = Math.min(low, probe.ground());
                        high = Math.max(high, probe.ground());
                    }
                    else
                    {
                        roof = Math.min(roof, probe.ground());
                    }
                    status = worse(status, cell);
                }
            }
            if (low == Integer.MAX_VALUE)
            {
                // Nothing free under it: shown where the roof is
                low = roof;
                high = roof;
            }
            towers.add(new Tower(point.x() - half, point.z() - half, level.towerSize(), low + 1, high + level.height() + level.towerExtra(), status));
        }

        // The crest follows the ground along the centerline, averaged over a few blocks
        // A roof is not ground: blocked cells take the height of the nearest free ground before them (after them at the start)
        final int[] groundY = new int[center.size()];
        final boolean[] free = new boolean[center.size()];
        for (int i = 0; i < center.size(); i++)
        {
            final Terrain.Probe probe = ground.at(center.get(i).x(), center.get(i).z());
            groundY[i] = probe.ground();
            free[i] = probe.loaded() && !probe.blocked();
        }
        int last = -1;
        for (int i = 0; i < center.size(); i++)
        {
            if (free[i])
            {
                last = groundY[i];
            }
            else if (last != -1)
            {
                groundY[i] = last;
            }
        }
        int firstFree = 0;
        while (firstFree < center.size() && !free[firstFree])
        {
            firstFree++;
        }
        for (int i = 0; i < firstFree && firstFree < center.size(); i++)
        {
            groundY[i] = groundY[firstFree];
        }
        final Map<Long, Column> columns = new LinkedHashMap<>();
        int length = 0;
        final int before = (level.thickness() - 1) / 2;
        for (int i = 0; i < center.size(); i++)
        {
            int sum = 0;
            int count = 0;
            for (int j = i - SMOOTH; j <= i + SMOOTH; j++)
            {
                final int k = closed ? Math.floorMod(j, center.size()) : j;
                if (k >= 0 && k < center.size())
                {
                    sum += groundY[k];
                    count++;
                }
            }
            final int crest = Math.round(sum / (float) count) + level.height();
            final WallTrace.Point cell = center.get(i);
            if (!towerCells.containsKey(key(cell.x(), cell.z())))
            {
                length++;
            }
            for (int dx = -before; dx < level.thickness() - before; dx++)
            {
                for (int dz = -before; dz < level.thickness() - before; dz++)
                {
                    final int x = cell.x() + dx;
                    final int z = cell.z() + dz;
                    final long key = key(x, z);
                    if (towerCells.containsKey(key) || columns.containsKey(key))
                    {
                        continue;
                    }
                    final Terrain.Probe probe = ground.at(x, z);
                    final Status status = statusOf(probe, claim, x, z);
                    if (status != Status.OK)
                    {
                        // Where it cannot stand, show the slice of wall that would cut through, at the crest
                        columns.put(key, new Column(x, z, crest - level.height() + 1, crest, status));
                        continue;
                    }
                    // Same height everywhere, give or take the smoothing
                    final int top = Math.max(probe.ground() + level.height() - 1, Math.min(probe.ground() + level.height() + 1, crest));
                    columns.put(key, new Column(x, z, probe.ground() + 1, top, status));
                }
            }
        }
        return new WallLayout(new ArrayList<>(columns.values()), towers, length);
    }

    /**
     * Every cell along the segments, in order, without repeating the shared ends.
     */
    private static List<WallTrace.Point> centerline(final List<WallTrace.Point> points, final boolean closed)
    {
        final List<WallTrace.Point> cells = new ArrayList<>();
        final int segments = closed ? points.size() : points.size() - 1;
        for (int i = 0; i < segments; i++)
        {
            final List<WallTrace.Point> line = WallTrace.line(points.get(i), points.get((i + 1) % points.size()));
            cells.addAll(cells.isEmpty() ? line : line.subList(1, line.size()));
        }
        if (closed && cells.size() > 1)
        {
            cells.removeLast();
        }
        return cells;
    }

    private static Status statusOf(final Terrain.Probe probe, final Claim claim, final int x, final int z)
    {
        if (!probe.loaded() || !claim.contains(x, z))
        {
            return Status.OUTSIDE;
        }
        return probe.blocked() ? Status.BLOCKED : Status.OK;
    }

    private static Status worse(final Status a, final Status b)
    {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static long key(final int x, final int z)
    {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
