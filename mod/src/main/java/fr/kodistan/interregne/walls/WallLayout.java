package fr.kodistan.interregne.walls;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A traced wall laid on the terrain: the columns of wall between the points, a square tower on each point and the
 * gatehouses. The wall keeps the same height above the ground everywhere; its crest is smoothed so it rises and falls
 * with the relief instead of stepping every block. Shared by the planner's ghost and the construction.
 */
public final class WallLayout
{
    public enum Status
    {
        OK,
        /**
         * A hut or a player's block is in the way, or a gate does not fit there.
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
    public record Column(int x, int z, int bottom, int top, Status status, int index)
    {
    }

    /**
     * A square tower; x and z are its north-west corner.
     */
    public record Tower(int x, int z, int size, int bottom, int top, Status status, int index)
    {
    }

    /**
     * A gatehouse across the wall, centred on (x, z): it runs GATE_HALF blocks each way along (ux, uz) and depth blocks
     * each way across. The passage is the middle PASSAGE_HALF each way; turrets stand on either side. floor is the y of
     * the passage's floor, crest the y of the lintel.
     */
    public record Gate(int x, int z, int ux, int uz, int depth, int floor, int crest, Status status, int index)
    {
        public boolean passage(final int along)
        {
            return Math.abs(along) <= PASSAGE_HALF;
        }
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
    /**
     * A gatehouse is 7 blocks along the wall: a 3-wide passage between two 2-wide turrets.
     */
    public static final int GATE_HALF = 3;
    public static final int PASSAGE_HALF = 1;

    private final List<Column> columns;
    private final List<Tower> towers;
    private final List<Gate> gates;
    private final int length;
    private final List<WallTrace.Point> center;
    private final boolean closed;
    private final Set<Long> towerCells;

    private WallLayout(final List<Column> columns, final List<Tower> towers, final List<Gate> gates, final int length,
      final List<WallTrace.Point> center, final boolean closed, final Set<Long> towerCells)
    {
        this.columns = columns;
        this.towers = towers;
        this.gates = gates;
        this.length = length;
        this.center = center;
        this.closed = closed;
        this.towerCells = towerCells;
    }

    public List<Column> columns()
    {
        return columns;
    }

    public List<Tower> towers()
    {
        return towers;
    }

    public List<Gate> gates()
    {
        return gates;
    }

    /**
     * Blocks of wall along the trace, towers and gates excluded.
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
        for (final Gate gate : gates)
        {
            if (gate.status != Status.OK)
            {
                count++;
            }
        }
        return count;
    }

    public int cost(final WallLevels.Level level)
    {
        int cost = length * level.perColumn() + towers.size() * level.perTower();
        for (int i = 0; i < gates.size(); i++)
        {
            cost += level.gatePrice(i);
        }
        return cost;
    }

    /**
     * Where a gate would go for the mouse over hover: the nearest cell of the trace, within a few blocks, where a
     * gatehouse fits (a straight stretch, clear of the towers and of the other gates). Null when none.
     */
    @Nullable
    public WallTrace.Point gateSpot(final WallTrace.Point hover, final List<WallTrace.Point> others)
    {
        WallTrace.Point best = null;
        int bestDistance = 10;
        for (int i = 0; i < center.size(); i++)
        {
            final WallTrace.Point cell = center.get(i);
            final int distance = cell.distanceSq(hover);
            if (distance < bestDistance && direction(center, i, closed, towerCells) != null && clearOf(cell, others))
            {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The gate whose footprint holds this cell, if any.
     */
    @Nullable
    public Gate gateAt(final WallTrace.Point cell)
    {
        for (final Gate gate : gates)
        {
            final int dx = cell.x() - gate.x();
            final int dz = cell.z() - gate.z();
            final int along = dx * gate.ux() + dz * gate.uz();
            final int across = -dx * gate.uz() + dz * gate.ux();
            if (Math.abs(along) <= GATE_HALF && Math.abs(across) <= gate.depth() + 1)
            {
                return gate;
            }
        }
        return null;
    }

    private static boolean clearOf(final WallTrace.Point cell, final List<WallTrace.Point> others)
    {
        for (final WallTrace.Point other : others)
        {
            if (Math.max(Math.abs(other.x() - cell.x()), Math.abs(other.z() - cell.z())) <= GATE_HALF * 2)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Lays out the points (closed or not) and their gates at this level.
     */
    public static WallLayout of(final List<WallTrace.Point> points, final boolean closed, final List<WallTrace.Point> gatePoints,
      final WallLevels.Level level, final Ground ground, final Claim claim)
    {
        final List<WallTrace.Point> center = centerline(points, closed);
        final int half = level.towerSize() / 2;

        // Towers first: their footprint is taken out of the wall
        final List<Tower> towers = new ArrayList<>();
        final Set<Long> towerCells = new HashSet<>();
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
                    towerCells.add(key(x, z));
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
            towers.add(new Tower(point.x() - half, point.z() - half, level.towerSize(), low + 1, high + level.height() + level.towerExtra(), status,
              Math.max(0, center.indexOf(point))));
        }

        // The crest follows the ground along the centerline, averaged over a few blocks.
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
        final int[] crest = new int[center.size()];
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
            crest[i] = Math.round(sum / (float) count) + level.height();
        }

        // Gates next: their footprint is taken out of the wall too
        final int depth = level.thickness() / 2 + 1;
        final List<Gate> gates = new ArrayList<>();
        final Set<Long> gateCells = new HashSet<>();
        final Set<Integer> gateIndexes = new HashSet<>();
        for (final WallTrace.Point point : gatePoints)
        {
            final int index = center.indexOf(point);
            final int[] u = index < 0 ? null : direction(center, index, closed, towerCells);
            if (u == null)
            {
                // No longer on a straight stretch of the trace: shown red where it was
                gates.add(new Gate(point.x(), point.z(), 1, 0, depth, ground.at(point.x(), point.z()).ground(),
                  ground.at(point.x(), point.z()).ground() + level.height(), Status.BLOCKED, Math.max(0, index)));
                continue;
            }
            int floor = Integer.MAX_VALUE;
            Status status = Status.OK;
            for (int along = -GATE_HALF; along <= GATE_HALF; along++)
            {
                final int k = closed ? Math.floorMod(index + along, center.size()) : index + along;
                gateIndexes.add(k);
                for (int across = -depth; across <= depth; across++)
                {
                    final int x = point.x() + along * u[0] - across * u[1];
                    final int z = point.z() + along * u[1] + across * u[0];
                    gateCells.add(key(x, z));
                    final Terrain.Probe probe = ground.at(x, z);
                    status = worse(status, statusOf(probe, claim, x, z));
                    if (Math.abs(along) <= PASSAGE_HALF && probe.loaded() && !probe.blocked())
                    {
                        floor = Math.min(floor, probe.ground());
                    }
                }
            }
            if (floor == Integer.MAX_VALUE)
            {
                floor = groundY[index];
            }
            gates.add(new Gate(point.x(), point.z(), u[0], u[1], depth, floor, Math.max(floor + level.height(), crest[index]), status, index));
        }

        final Map<Long, Column> columns = new LinkedHashMap<>();
        int length = 0;
        final int before = (level.thickness() - 1) / 2;
        for (int i = 0; i < center.size(); i++)
        {
            final WallTrace.Point cell = center.get(i);
            if (!towerCells.contains(key(cell.x(), cell.z())) && !gateIndexes.contains(i))
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
                    if (towerCells.contains(key) || gateCells.contains(key) || columns.containsKey(key))
                    {
                        continue;
                    }
                    final Terrain.Probe probe = ground.at(x, z);
                    final Status status = statusOf(probe, claim, x, z);
                    if (status != Status.OK)
                    {
                        // Where it cannot stand, show the slice of wall that would cut through, at the crest
                        columns.put(key, new Column(x, z, crest[i] - level.height() + 1, crest[i], status, i));
                        continue;
                    }
                    // Same height everywhere, give or take the smoothing
                    final int top = Math.max(probe.ground() + level.height() - 1, Math.min(probe.ground() + level.height() + 1, crest[i]));
                    columns.put(key, new Column(x, z, probe.ground() + 1, top, status, i));
                }
            }
        }
        return new WallLayout(new ArrayList<>(columns.values()), towers, gates, length, center, closed, towerCells);
    }

    /**
     * The straight axis (unit x or z step) of the trace around cell i when a whole gatehouse fits there, clear of the
     * towers; null otherwise. Gates only go on north-south or east-west stretches.
     */
    @Nullable
    private static int[] direction(final List<WallTrace.Point> center, final int i, final boolean closed, final Set<Long> towerCells)
    {
        final int n = center.size();
        if (!closed && (i - GATE_HALF < 0 || i + GATE_HALF >= n) || n < GATE_HALF * 2 + 1)
        {
            return null;
        }
        final WallTrace.Point here = center.get(i);
        final WallTrace.Point next = center.get(Math.floorMod(i + 1, n));
        final int ux = next.x() - here.x();
        final int uz = next.z() - here.z();
        for (int along = -GATE_HALF; along <= GATE_HALF; along++)
        {
            final WallTrace.Point cell = center.get(Math.floorMod(i + along, n));
            if (cell.x() != here.x() + along * ux || cell.z() != here.z() + along * uz)
            {
                return null;
            }
            // The gatehouse's turrets may touch a tower's side, not overlap it
            if (towerCells.contains(key(cell.x(), cell.z())))
            {
                return null;
            }
        }
        return new int[] {ux, uz};
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
