package fr.kodistan.interregne.walls;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the wall crest bugs reported by the user on irregular terrain (holes, cliff-like jumps with no
 * stairs): most tests below currently assert the BUGGY behaviour of {@link WallLayout#of} so the regression is
 * pinned down before the crest algorithm is reworked. Once the rework (paliers + Lipschitz smoothing + rise/stairs,
 * see the walls-system plan) lands, those assertions flip to the opposite (no jump, uniform tranche).
 * {@link #outerCornerStaysCovered()} is the exception: a hypothesised "corner hole" bug was checked by hand here
 * and does NOT reproduce, so it is kept as a plain regression guard instead.
 */
class WallLayoutTest
{
    private static final WallLevels.Level LEVEL = WallLevels.of(1);
    private static final WallLayout.Claim ALWAYS = (x, z) -> true;

    private static long key(final int x, final int z)
    {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static WallLayout.Ground ground(final IntBinaryOperator height, final Set<Long> unloaded, final Set<Long> blocked)
    {
        return (x, z) -> {
            final long k = key(x, z);
            if (unloaded.contains(k))
            {
                return Terrain.Probe.UNKNOWN;
            }
            return new Terrain.Probe(height.applyAsInt(x, z), blocked.contains(k), false, true);
        };
    }

    private static WallLayout.Ground flatExcept(final IntBinaryOperator height)
    {
        return ground(height, Set.of(), Set.of());
    }

    private static Map<Integer, List<WallLayout.Column>> byIndex(final WallLayout layout)
    {
        final Map<Integer, List<WallLayout.Column>> map = new HashMap<>();
        for (final WallLayout.Column column : layout.columns())
        {
            map.computeIfAbsent(column.index(), i -> new ArrayList<>()).add(column);
        }
        return map;
    }

    /**
     * A sudden 8-block cliff across the trace's run (WallLayout.java ~L290-334): the crest is a 5-cell moving
     * average of the raw ground, then each column is reclamped to its OWN local ground ± 1 (~L424). The wall
     * therefore steps straight down the cliff instead of ramping with stairs.
     */
    @Test
    void cliffAcrossTheTraceProducesABruteJumpInsteadOfARamp()
    {
        final List<WallTrace.Point> points = List.of(new WallTrace.Point(0, 0), new WallTrace.Point(0, 30));
        final WallLayout.Ground groundFn = flatExcept((x, z) -> z <= 12 ? 70 : 62);
        final WallLayout layout = WallLayout.of(points, false, List.of(), LEVEL, groundFn, ALWAYS);

        final Map<Integer, List<WallLayout.Column>> byIndex = byIndex(layout);
        int maxJump = 0;
        for (int i = 6; i < 24; i++)
        {
            final List<WallLayout.Column> here = byIndex.get(i);
            final List<WallLayout.Column> next = byIndex.get(i + 1);
            if (here == null || next == null)
            {
                continue;
            }
            final int top = here.get(0).top();
            final int nextTop = next.get(0).top();
            maxJump = Math.max(maxJump, Math.abs(nextTop - top));
        }
        // Desired once fixed: maxJump <= 1 (a ramp, one stair step per cell). Today it jumps the whole cliff at once.
        assertTrue(maxJump > 1, "expected today's bug (a brute jump); got max jump = " + maxJump);
    }

    /**
     * A lateral slope across the wall's 6-block width: every cell of a single tranche reclamps to its own local
     * ground (~L424), so the walkway is not flat across the wall even though it is meant to be a single uniform
     * crest per index. This is the main source of the "holes" the user sees (WallLayout.java diagnostic A2).
     */
    @Test
    void lateralSlopeMakesOneTrancheUneven()
    {
        final List<WallTrace.Point> points = List.of(new WallTrace.Point(0, 0), new WallTrace.Point(20, 0));
        // Height rises 2 blocks per step away from the centerline (z = 0), across the wall's width.
        final WallLayout.Ground groundFn = flatExcept((x, z) -> 70 + 2 * z);
        final WallLayout layout = WallLayout.of(points, false, List.of(), LEVEL, groundFn, ALWAYS);

        final List<WallLayout.Column> tranche = byIndex(layout).get(10);
        assertTrue(tranche != null && tranche.size() > 1, "tranche 10 should have several cells across the width");
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (final WallLayout.Column column : tranche)
        {
            min = Math.min(min, column.top());
            max = Math.max(max, column.top());
        }
        // Desired once fixed: min == max (one flat crest per index). Today it varies with the local slope.
        assertTrue(max - min > 1, "expected today's bug (uneven tranche); got spread = " + (max - min));
    }

    /**
     * On a closed loop, unloaded cells near index 0 are forward-filled from the first free index instead of
     * wrapping to the end of the loop (WallLayout.java ~L298-318, non-circular fill). Combined with a real
     * elevation difference right at the seam, the crest disagrees with itself where the loop closes.
     */
    @Test
    void closedLoopSeamIsNotFilledCircularly()
    {
        final List<WallTrace.Point> points = List.of(
          new WallTrace.Point(0, 0), new WallTrace.Point(20, 0),
          new WallTrace.Point(20, 20), new WallTrace.Point(0, 20));
        final Set<Long> unloaded = new HashSet<>();
        // The point where the loop closes (index 0, x=0,z=0) and its very next cell (index 1, x=1,z=0) are unloaded
        // chunks: nothing is known there yet. Both truly sit in the basin below (see groundFn), exactly like their
        // wrap-around neighbour (the last centerline cell, index n-1, x=0,z=1 — loaded and known).
        unloaded.add(key(0, 0));
        unloaded.add(key(1, 0));
        // A basin along the whole x<=1 strip (all of segment "(0,20)->(0,0)", plus the first two cells of
        // "(0,0)->(20,0)"): 40 blocks lower than the rest. Index 2 (x=2,z=0, loaded) is the first cell back on the
        // high plateau.
        final WallLayout.Ground groundFn = ground((x, z) -> x <= 1 ? 30 : 70, unloaded, Set.of());
        final WallLayout layout = WallLayout.of(points, true, List.of(), LEVEL, groundFn, ALWAYS);

        // Desired once fixed: filling the unloaded corner should lean on its nearest KNOWN neighbour, which is the
        // wrap-around one (index n-1, basin, 30) rather than the far one reached by scanning forward past the
        // unloaded run (index 2, plateau, 70) — so the tower's walkway should land near the basin floor (~34).
        // Today's forward-only fill (WallLayout.java ~L310-318) ignores the wrap-around neighbour entirely and
        // fills the corner from the plateau side instead, pulling the walkway up well above the basin (~58).
        final int walk = layout.towers().get(0).walk();
        assertTrue(walk > 50, "expected today's bug (walkway pulled toward the far plateau, ignoring the near "
                                 + "wrap-around basin neighbour); got tower.walk() = " + walk);
    }

    /**
     * A regression guard, not a bug reproduction: WallLayout attributes each cell to whichever centerline index's
     * OWN segment distance test passes first (~L397-428), not necessarily the nearest one. On paper that reads as
     * a risk of uncovered corner cells, but both a 90° and a 45° bend were checked by hand (with an oracle using
     * WallLayout's own coordinate convention, see {@link #distanceToPolylineShifted}) and neither shows a hole:
     * each segment's own distance test already clamps to its shared endpoint, which rounds the corner correctly
     * regardless of which adjacent segment "owns" that endpoint's index. Kept so a future rework of the crest
     * algorithm (nearest-index attribution, paliers) does not regress this.
     */
    @Test
    void outerCornerStaysCovered()
    {
        final List<WallTrace.Point> points = List.of(
          new WallTrace.Point(0, 0), new WallTrace.Point(20, 20), new WallTrace.Point(40, 20));
        final WallLayout.Ground groundFn = flatExcept((x, z) -> 70);
        final WallLayout layout = WallLayout.of(points, false, List.of(), LEVEL, groundFn, ALWAYS);

        final Set<Long> actual = new HashSet<>();
        for (final WallLayout.Column column : layout.columns())
        {
            actual.add(key(column.x(), column.z()));
        }
        final double halfWidth = LEVEL.thickness() / 2.0;
        int missing = 0;
        for (int x = 14; x <= 26; x++)
        {
            for (int z = 16; z <= 24; z++)
            {
                final int cx = x;
                final int cz = z;
                final boolean nearPolyline = distanceToPolylineShifted(cx, cz, points) <= halfWidth;
                final boolean covered = actual.contains(key(cx, cz))
                                           || layout.towers().stream().anyMatch(t -> t.covers(cx, cz));
                if (nearPolyline && !covered)
                {
                    missing++;
                }
            }
        }
        assertEquals(0, missing, "no cell near the bend should go uncovered");
    }

    /**
     * Ground-truth oracle independent of WallLayout's own (buggy) per-index attribution: distance from a cell's
     * centre to the NEAREST of the whole polyline's segments, in WallLayout's own coordinate convention (the
     * segment's endpoints shifted by +1.0, ~L411) — not just the single segment the closest centerline index
     * happens to own.
     */
    private static double distanceToPolylineShifted(final int x, final int z, final List<WallTrace.Point> points)
    {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < points.size(); i++)
        {
            best = Math.min(best, distanceToSegment(x + 0.5, z + 0.5, points.get(i), points.get(i + 1)));
        }
        return best;
    }

    private static double distanceToSegment(final double px, final double pz, final WallTrace.Point a, final WallTrace.Point b)
    {
        final double ax = a.x() + 1.0;
        final double az = a.z() + 1.0;
        final double dx = b.x() + 1.0 - ax;
        final double dz = b.z() + 1.0 - az;
        final double length = dx * dx + dz * dz;
        final double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / length));
        final double ex = px - (ax + t * dx);
        final double ez = pz - (az + t * dz);
        return Math.sqrt(ex * ex + ez * ez);
    }
}
