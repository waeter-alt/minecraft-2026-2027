package fr.kodistan.interregne.walls;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
     * One column of wall, from bottom to top inclusive. riseDx/riseDz point towards the neighbouring index when
     * the crest climbs exactly one block that way (a stair goes here instead of a flat floor); (0, 0) on the flat.
     */
    public record Column(int x, int z, int bottom, int top, Status status, int index, int riseDx, int riseDz)
    {
    }

    /**
     * A round tower inside the size x size square whose north-west corner is (x, z). walk is the y of its floor at the
     * level of the wall's walkway, top the y of its roof.
     */
    public record Tower(int x, int z, int size, int bottom, int top, int walk, Status status, int index)
    {
        public boolean covers(final int cx, final int cz)
        {
            final int dx = cx - x;
            final int dz = cz - z;
            return dx >= 0 && dz >= 0 && dx < size && dz < size && round(dx + 0.5 - size / 2.0, dz + 0.5 - size / 2.0, size);
        }
    }

    /**
     * Whether a cell at this offset from a tower's centre is inside its round footprint.
     */
    static boolean round(final double ox, final double oz, final int size)
    {
        final double r = size / 2.0;
        return ox * ox + oz * oz <= r * r + 0.5;
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

    /**
     * A gatehouse is 7 blocks along the wall: a 3-wide passage between two 2-wide turrets.
     */
    public static final int GATE_HALF = 3;
    public static final int PASSAGE_HALF = 1;

    /**
     * How much taller than the wall's nominal height the crest may climb above its own local ground to bridge a
     * ravine with a ramp. Beyond that it is not worth it: the cells there are shown blocked ("trop profond")
     * instead of building a very tall pier.
     */
    private static final int PLAFOND = 12;

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
            // Centred on the wall's axis, which runs half a block past the trace's cells
            for (int dx = 1 - half; dx <= level.towerSize() - half; dx++)
            {
                for (int dz = 1 - half; dz <= level.towerSize() - half; dz++)
                {
                    if (!round(dx - 0.5, dz - 0.5, level.towerSize()))
                    {
                        continue;
                    }
                    final int x = point.x() + dx;
                    final int z = point.z() + dz;
                    towerCells.add(key(x, z));
                    final Terrain.Probe probe = ground.at(x, z);
                    final Status cell = statusOf(probe, ground, claim, x, z);
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
            towers.add(new Tower(point.x() + 1 - half, point.z() + 1 - half, level.towerSize(), low + 1, high + level.height() + level.towerExtra(), high + level.height(),
              status, Math.max(0, center.indexOf(point))));
        }

        // Geometric assignment: every candidate cell within the wall's width of a centerline index's own segment,
        // excluding tower footprints, attributed to the first index that claims it. Proven to fully cover corners
        // (90° and 45°, see WallLayoutTest#outerCornerStaysCovered) since each segment's own distance test already
        // clamps to its shared endpoint.
        final int[] segmentOf = segments(points, closed, center.size());
        final double halfWidth = level.thickness() / 2.0;
        final int reach = (int) Math.ceil(halfWidth) + 1;
        final Map<Long, Integer> assignedIndex = new LinkedHashMap<>();
        final Map<Long, Terrain.Probe> assignedProbe = new HashMap<>();
        for (int i = 0; i < center.size(); i++)
        {
            final WallTrace.Point cell = center.get(i);
            final WallTrace.Point a = points.get(segmentOf[i]);
            final WallTrace.Point b = points.get((segmentOf[i] + 1) % points.size());
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    final int x = cell.x() + dx;
                    final int z = cell.z() + dz;
                    final long cellKey = key(x, z);
                    if (towerCells.contains(cellKey) || assignedIndex.containsKey(cellKey)
                          || distance(x + 0.5, z + 0.5, a.x() + 1.0, a.z() + 1.0, b.x() + 1.0, b.z() + 1.0) > halfWidth)
                    {
                        continue;
                    }
                    assignedIndex.put(cellKey, i);
                    assignedProbe.put(cellKey, ground.at(x, z));
                }
            }
        }

        // The local high ground of each index's whole tranche (not just its centre cell), so every cell across the
        // wall's width shares one crest instead of each reclamping to its own local ground (the main cause of the
        // "holes" on an uneven cross-section, see WallLayoutTest#lateralSlopeMakesOneTrancheUneven).
        final int[] g = new int[center.size()];
        final boolean[] hasG = new boolean[center.size()];
        Arrays.fill(g, Integer.MIN_VALUE);
        for (final Map.Entry<Long, Integer> entry : assignedIndex.entrySet())
        {
            final Terrain.Probe probe = assignedProbe.get(entry.getKey());
            if (probe.loaded() && !probe.blocked())
            {
                final int i = entry.getValue();
                g[i] = Math.max(g[i], probe.ground());
                hasG[i] = true;
            }
        }
        // An index with no ground of its own (every assigned cell blocked or unloaded, or none assigned at all)
        // borrows its nearest neighbour's, wrapping a closed loop instead of only scanning forward (the old fix
        // for the leading unloaded cells ignored the wrap-around neighbour entirely, see
        // WallLayoutTest#closedLoopSeamIsNotFilledCircularly).
        fillMissingGround(g, hasG, closed);
        // A single-cell relief blip (one isolated block of noise, surrounded by otherwise consistent ground)
        // should not read as a step in the crest: the walkway passes over it smoothly instead of bumping up/down.
        despikeGround(g, closed);

        // The crest: this index's own ground plus the level's nominal height (same resting height as on flat
        // ground before), raised only where a neighbour's cliff forces it, so it climbs or descends at most one
        // block per cell — a ramp, never a cliff — wrapping a closed loop.
        final int[] w = new int[center.size()];
        for (int i = 0; i < center.size(); i++)
        {
            w[i] = g[i] + level.height();
        }
        lipschitzRelax(w, closed);

        // Each tower's walkway reads straight off the crest, so it always agrees with the wall beside it.
        // A single point has no centerline yet: its tower keeps its own heights.
        towers.replaceAll(tower -> {
            if (w.length == 0)
            {
                return tower;
            }
            final int walk = w[Math.min(tower.index(), w.length - 1)];
            return new Tower(tower.x(), tower.z(), tower.size(), tower.bottom(), Math.max(tower.top(), walk + level.towerExtra()), walk, tower.status(), tower.index());
        });

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
                    status = worse(status, statusOf(probe, ground, claim, x, z));
                    if (Math.abs(along) <= PASSAGE_HALF && probe.loaded() && !probe.blocked())
                    {
                        floor = Math.min(floor, probe.ground());
                    }
                }
            }
            if (floor == Integer.MAX_VALUE)
            {
                floor = g[index];
            }
            gates.add(new Gate(point.x(), point.z(), u[0], u[1], depth, floor, Math.max(floor + level.height(), w[index]), status, index));
        }

        // Columns: one uniform top per index (the crest), built from the cells assigned earlier, minus whatever a
        // gate has since claimed. A cell too deep below the crest (a ravine the ramp would have to bridge for too
        // long, see PLAFOND) is shown blocked instead of built impossibly tall.
        final Map<Long, Column> columns = new LinkedHashMap<>();
        int length = 0;
        for (int i = 0; i < center.size(); i++)
        {
            final WallTrace.Point cell = center.get(i);
            if (!towerCells.contains(key(cell.x(), cell.z())) && !gateIndexes.contains(i))
            {
                length++;
            }
        }
        for (final Map.Entry<Long, Integer> entry : assignedIndex.entrySet())
        {
            final long cellKey = entry.getKey();
            if (gateCells.contains(cellKey))
            {
                continue;
            }
            final int i = entry.getValue();
            final int x = (int) (cellKey >> 32);
            final int z = (int) cellKey;
            final Terrain.Probe probe = assignedProbe.get(cellKey);
            final Status status = statusOf(probe, ground, claim, x, z);
            if (status != Status.OK)
            {
                // Where it cannot stand, show the slice of wall that would cut through, at the crest
                columns.put(cellKey, new Column(x, z, w[i] - level.height() + 1, w[i], status, i, 0, 0));
                continue;
            }
            if (w[i] - g[i] > level.height() + PLAFOND)
            {
                columns.put(cellKey, new Column(x, z, probe.ground() + 1, w[i], Status.BLOCKED, i, 0, 0));
                continue;
            }
            columns.put(cellKey, new Column(x, z, probe.ground() + 1, w[i], status, i, 0, 0));
        }
        return new WallLayout(markRises(new ArrayList<>(columns.values()), w, center, closed), towers, gates, length, center, closed, towerCells);
    }

    /**
     * Fills the indices with no ground of their own from the nearest index that has one (forward or backward,
     * whichever is closer), wrapping around a closed loop.
     */
    static void fillMissingGround(final int[] g, final boolean[] hasG, final boolean closed)
    {
        final int n = g.length;
        boolean anyKnown = false;
        for (final boolean known : hasG)
        {
            anyKnown |= known;
        }
        if (!anyKnown)
        {
            return;
        }
        final int[] filled = g.clone();
        for (int i = 0; i < n; i++)
        {
            if (hasG[i])
            {
                continue;
            }
            for (int d = 1; d < n; d++)
            {
                final int forward = closed ? Math.floorMod(i + d, n) : i + d;
                if (forward >= 0 && forward < n && hasG[forward])
                {
                    filled[i] = g[forward];
                    break;
                }
                final int backward = closed ? Math.floorMod(i - d, n) : i - d;
                if (backward >= 0 && backward < n && hasG[backward])
                {
                    filled[i] = g[backward];
                    break;
                }
            }
        }
        System.arraycopy(filled, 0, g, 0, n);
    }

    /**
     * Smooths away an isolated single-cell relief blip: an index whose ground differs from BOTH its neighbours by
     * exactly one block, while those neighbours themselves roughly agree with each other, is noise (a lone rock, a
     * single raised dirt block) rather than a real feature — it is flattened to its neighbours' level instead of
     * poking a one-block bump or dip into the crest. A genuine slope (neighbours disagreeing by more than one) is
     * left untouched.
     */
    static void despikeGround(final int[] g, final boolean closed)
    {
        final int n = g.length;
        if (n < 3)
        {
            return;
        }
        final int[] smoothed = g.clone();
        for (int i = 0; i < n; i++)
        {
            final int prev = closed ? Math.floorMod(i - 1, n) : Math.max(0, i - 1);
            final int next = closed ? Math.floorMod(i + 1, n) : Math.min(n - 1, i + 1);
            if (prev == i || next == i || prev == next)
            {
                continue;
            }
            if (Math.abs(g[prev] - g[next]) <= 1 && Math.abs(g[i] - g[prev]) == 1 && Math.abs(g[i] - g[next]) == 1)
            {
                smoothed[i] = Math.round((g[prev] + g[next]) / 2.0f);
            }
        }
        System.arraycopy(smoothed, 0, g, 0, n);
    }

    /**
     * Relaxes the crest so it never differs by more than one block between neighbours, by raising (never
     * lowering) whichever side is low until it is consistent — a ramp instead of a cliff, wrapping a closed loop.
     * Converges because every step only raises a value, bounded by the array's own maximum.
     */
    static void lipschitzRelax(final int[] w, final boolean closed)
    {
        final int n = w.length;
        if (n < 2)
        {
            return;
        }
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (int i = 0; i < n; i++)
            {
                final int prev = closed ? Math.floorMod(i - 1, n) : Math.max(0, i - 1);
                final int next = closed ? Math.floorMod(i + 1, n) : Math.min(n - 1, i + 1);
                final int raised = Math.max(w[i], Math.max(w[prev] - 1, w[next] - 1));
                if (raised != w[i])
                {
                    w[i] = raised;
                    changed = true;
                }
            }
        }
    }

    /**
     * Marks, for every column, the direction (if any) in which its index's crest climbs exactly one block FROM a
     * neighbour: a stair goes on the HIGHER of the two indices (replacing what would otherwise be a one-block-tall
     * wall blocking the step up from the lower one), facing the direction of ascent. A local one-cell dip between
     * two higher neighbours is a known rare edge case where only one of its two directions is kept.
     */
    private static List<Column> markRises(final List<Column> columns, final int[] w, final List<WallTrace.Point> center, final boolean closed)
    {
        final int n = w.length;
        if (n < 2)
        {
            return columns;
        }
        final int[] riseDx = new int[n];
        final int[] riseDz = new int[n];
        for (int i = 0; i < n; i++)
        {
            final int next = closed ? Math.floorMod(i + 1, n) : i + 1;
            if (!closed && next >= n)
            {
                continue;
            }
            final WallTrace.Point here = center.get(i);
            final WallTrace.Point there = center.get(next);
            if (w[next] == w[i] + 1)
            {
                // "there" (next) is higher: it gets the stair, facing the way you climb up to reach it.
                riseDx[next] = Integer.signum(there.x() - here.x());
                riseDz[next] = Integer.signum(there.z() - here.z());
            }
            else if (w[next] == w[i] - 1)
            {
                // "here" (i) is higher: it gets the stair, facing the way you climb up to reach it.
                riseDx[i] = Integer.signum(here.x() - there.x());
                riseDz[i] = Integer.signum(here.z() - there.z());
            }
        }
        final List<Column> marked = new ArrayList<>(columns.size());
        for (final Column column : columns)
        {
            marked.add(new Column(column.x(), column.z(), column.bottom(), column.top(), column.status(), column.index(),
              riseDx[column.index()], riseDz[column.index()]));
        }
        return marked;
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
    /**
     * For each centerline cell, the index of the trace segment it lies on.
     */
    private static int[] segments(final List<WallTrace.Point> points, final boolean closed, final int size)
    {
        final int[] of = new int[size];
        int k = 0;
        final int segments = closed ? points.size() : points.size() - 1;
        for (int s = 0; s < segments && k < size; s++)
        {
            final List<WallTrace.Point> line = WallTrace.line(points.get(s), points.get((s + 1) % points.size()));
            for (int j = k == 0 ? 0 : 1; j < line.size() && k < size; j++)
            {
                of[k++] = s;
            }
        }
        return of;
    }

    /**
     * Distance from (px, pz) to the segment (ax, az)-(bx, bz).
     */
    private static double distance(final double px, final double pz, final double ax, final double az, final double bx, final double bz)
    {
        final double dx = bx - ax;
        final double dz = bz - az;
        final double length = dx * dx + dz * dz;
        final double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / length));
        final double ex = px - (ax + t * dx);
        final double ez = pz - (az + t * dz);
        return Math.sqrt(ex * ex + ez * ez);
    }

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

    /**
     * Blocks kept clear between the wall and any building, so villagers can still walk round their huts.
     */
    public static final int MARGIN = 2;

    /**
     * The cells around a trace where its own wall may stand: the ground there is read through the wall's blocks, the
     * same way in the planner and on the server.
     */
    public static java.util.Set<Long> ownCells(final List<WallTrace.Point> points, final boolean closed)
    {
        final java.util.Set<Long> cells = new java.util.HashSet<>();
        final int reach = GATE_HALF + 3;
        for (int i = 0; i < points.size(); i++)
        {
            if (i + 1 < points.size() || closed && points.size() > 2)
            {
                for (final WallTrace.Point cell : WallTrace.line(points.get(i), points.get((i + 1) % points.size())))
                {
                    for (int dx = -reach; dx <= reach; dx++)
                    {
                        for (int dz = -reach; dz <= reach; dz++)
                        {
                            cells.add(key(cell.x() + dx, cell.z() + dz));
                        }
                    }
                }
            }
        }
        return cells;
    }

    private static Status statusOf(final Terrain.Probe probe, final Ground ground, final Claim claim, final int x, final int z)
    {
        if (!probe.loaded() || !claim.contains(x, z))
        {
            return Status.OUTSIDE;
        }
        if (probe.blocked())
        {
            return Status.BLOCKED;
        }
        for (int dx = -MARGIN; dx <= MARGIN; dx++)
        {
            for (int dz = -MARGIN; dz <= MARGIN; dz++)
            {
                if ((dx != 0 || dz != 0) && ground.at(x + dx, z + dz).blocked() && ground.at(x + dx, z + dz).loaded())
                {
                    return Status.BLOCKED;
                }
            }
        }
        return Status.OK;
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
