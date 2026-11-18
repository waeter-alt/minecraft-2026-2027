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
 * Covers the wall crest bugs reported by the user on irregular terrain (holes, cliff-like jumps with no stairs),
 * fixed by reworking {@link WallLayout#of} into a per-index "local high ground" (not just the centre cell), a
 * circular-aware fill for unknown ground, and a 1-Lipschitz relaxation of the crest (a ramp, never a cliff).
 * {@link #outerCornerStaysCovered()} guards a hypothesised "corner hole" bug that was checked by hand and does NOT
 * reproduce (kept as a plain regression guard, not a fix).
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
     * A sudden 8-block cliff across the trace's run: the crest now climbs at most one block per cell (a ramp),
     * never the whole cliff in one jump.
     */
    @Test
    void cliffAcrossTheTraceBecomesARamp()
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
        assertTrue(maxJump <= 1, "a ramp should climb at most one block per cell; got max jump = " + maxJump);
        // And the ramp cells (non-face, mid-slope) are actually marked to carry a stair — on the HIGHER of each
        // pair of indices (replacing what would otherwise block the step up from the lower one), not the lower.
        for (final WallLayout.Column column : layout.columns())
        {
            if (column.riseDx() == 0 && column.riseDz() == 0)
            {
                continue;
            }
            final int neighbourIndex = column.index() - Integer.signum(column.riseDx() + column.riseDz());
            final List<WallLayout.Column> neighbourTranche = byIndex.get(neighbourIndex);
            assertTrue(neighbourTranche != null, "a rise column should have a neighbouring tranche on its low side");
            final int neighbourTop = neighbourTranche.get(0).top();
            assertEquals(column.top(), neighbourTop + 1, "the stair must sit on the HIGHER column (one block "
                                                            + "above its low-side neighbour), not the lower one");
        }
        final boolean anyRise = layout.columns().stream().anyMatch(c -> c.riseDx() != 0 || c.riseDz() != 0);
        assertTrue(anyRise, "expected at least one column marked as a stair step across the cliff");
    }

    /**
     * An isolated single-block relief blip (one block taller than its surroundings on both sides, which themselves
     * roughly agree) does not poke a bump into the crest: it is smoothed away, and the crest stays flat across it.
     */
    @Test
    void isolatedReliefBlipDoesNotBumpTheCrest()
    {
        final List<WallTrace.Point> points = List.of(new WallTrace.Point(0, 0), new WallTrace.Point(0, 30));
        // Flat at 70 except a single raised block at z = 15, well clear of the towers at both ends.
        final WallLayout.Ground groundFn = flatExcept((x, z) -> z == 15 ? 71 : 70);
        final WallLayout layout = WallLayout.of(points, false, List.of(), LEVEL, groundFn, ALWAYS);

        final Map<Integer, List<WallLayout.Column>> byIndex = byIndex(layout);
        for (int i = 11; i <= 19; i++)
        {
            final int top = byIndex.get(i).get(0).top();
            assertEquals(74, top, "the crest should ignore the single-block blip at index " + i + " and stay flat");
        }
    }

    /**
     * despikeGround directly: an isolated spike surrounded by agreeing neighbours is flattened; a genuine slope
     * (neighbours disagreeing by more than one) is left untouched.
     */
    @Test
    void despikeGroundFlattensIsolatedSpikesOnly()
    {
        final int[] spike = {70, 70, 71, 70, 70};
        WallLayout.despikeGround(spike, false);
        assertEquals(70, spike[2], "an isolated one-block spike between agreeing neighbours is noise");

        final int[] slope = {68, 69, 70, 71, 72};
        final int[] original = slope.clone();
        WallLayout.despikeGround(slope, false);
        assertEquals(original[2], slope[2], "a genuine monotonic slope must not be flattened");
    }

    /**
     * A lateral slope across the wall's 6-block width: the whole tranche now shares one flat crest (the local high
     * ground of the cross-section), instead of each cell reclamping to its own local ground.
     */
    @Test
    void lateralSlopeKeepsOneTrancheFlat()
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
        assertEquals(min, max, "one flat crest per index");
    }

    /**
     * fillMissingGround on a closed loop: an index with no ground of its own borrows its nearest neighbour,
     * wrapping around the loop — not just scanning forward, as the old per-cell fill used to (it specialcased
     * only the array's leading prefix, forward-looking, and ignored a closer neighbour reachable by wrapping).
     * Exercised directly (package-private) since the tower footprint around every trace point otherwise swallows
     * the cells right where this matters, making it hard to observe through the full WallLayout.of() pipeline.
     */
    @Test
    void fillMissingGroundWrapsAroundAClosedLoop()
    {
        final int n = 10;
        final int[] g = new int[n];
        final boolean[] hasG = new boolean[n];
        // Known: index 3 = 70 (three steps forward), index n-1 = 30 (one step back across the wrap).
        g[3] = 70;
        hasG[3] = true;
        g[n - 1] = 30;
        hasG[n - 1] = true;
        // Index 0 is unknown: its nearest known neighbour is index n-1 (one step away, via the wrap), not index 3
        // (three steps away, forward) — a forward-only fill would wrongly prefer the latter.
        WallLayout.fillMissingGround(g, hasG, true);
        assertEquals(30, g[0], "index 0 should borrow its nearest neighbour (one step back across the wrap), not "
                                + "the farther one reached by scanning forward");
    }

    /**
     * lipschitzRelax never lowers a value, always closes gaps bigger than one block, and wraps a closed loop (the
     * seam is as smooth as any other crossing, not a leftover step where the loop closes).
     */
    @Test
    void lipschitzRelaxClosesGapsWithoutLoweringAnything()
    {
        final int[] w = {70, 70, 70, 70, 40, 40, 40, 70, 70, 70};
        final int[] original = w.clone();
        WallLayout.lipschitzRelax(w, true);
        for (int i = 0; i < w.length; i++)
        {
            final int next = (i + 1) % w.length;
            assertTrue(Math.abs(w[next] - w[i]) <= 1, "index " + i + " -> " + next + " should differ by at most 1, got " + w[i] + " vs " + w[next]);
            assertTrue(w[i] >= original[i], "lipschitzRelax must never lower a value; index " + i);
        }
    }

    /**
     * A ravine deeper than the crest is willing to bridge with a ramp (PLAFOND): the cells there are shown blocked
     * ("trop profond") instead of a very tall pier, while the crest right beside it stays a normal ramp.
     */
    @Test
    void deepRavineIsCappedInsteadOfBuiltTall()
    {
        final List<WallTrace.Point> points = List.of(new WallTrace.Point(0, 0), new WallTrace.Point(0, 40));
        // A deep pit in the middle of the run: 30 blocks down for a short stretch.
        final WallLayout.Ground groundFn = flatExcept((x, z) -> z >= 18 && z <= 22 ? 40 : 70);
        final WallLayout layout = WallLayout.of(points, false, List.of(), LEVEL, groundFn, ALWAYS);

        final boolean anyBlocked = layout.columns().stream()
                                     .filter(c -> c.index() >= 18 && c.index() <= 22)
                                     .anyMatch(c -> c.status() != WallLayout.Status.OK);
        assertTrue(anyBlocked, "the pit should be too deep to bridge and shown blocked");
        final int farTop = byIndex(layout).get(5).get(0).top();
        assertEquals(74, farTop, "well away from the pit, the crest should stay the plain ground + height");
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

    /**
     * A paved road is not natural ground, so its blocks read as "a building in the way": the gate the trace gets where
     * it crosses the road must still be accepted, not shown red (playtest report: ghost shows the gate, trace refused).
     */
    @Test
    void gateOnPavedRoadIsNotAnObstacle()
    {
        final List<WallTrace.Point> points = List.of(new WallTrace.Point(-30, 0), new WallTrace.Point(30, 0));
        // A road running north-south through x = 0, paved 7 cells wide
        final Set<Long> centre = new HashSet<>();
        final Set<Long> paving = new HashSet<>();
        for (int z = -40; z <= 40; z++)
        {
            centre.add(key(0, z));
            for (int x = -3; x <= 3; x++)
            {
                paving.add(key(x, z));
            }
        }
        final WallLayout.Ground raw = ground((x, z) -> 70, Set.of(), paving);
        final List<WallTrace.Point> gates = WallLayout.crossings(points, false, List.of(), centre::contains);
        assertEquals(1, gates.size());

        final WallLayout withoutFix = WallLayout.of(points, false, gates, LEVEL, raw, ALWAYS);
        assertEquals(WallLayout.Status.BLOCKED, withoutFix.gates().get(0).status());

        final WallLayout.Ground aware = (x, z) -> WallLayout.overRoad(raw.at(x, z), x, z, centre::contains);
        final WallLayout layout = WallLayout.of(points, false, gates, LEVEL, aware, ALWAYS);
        assertEquals(WallLayout.Status.OK, layout.gates().get(0).status());
        assertEquals(0, layout.blocked());
    }
}
