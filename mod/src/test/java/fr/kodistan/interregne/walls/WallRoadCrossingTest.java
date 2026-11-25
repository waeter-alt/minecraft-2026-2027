package fr.kodistan.interregne.walls;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wall and a road that meet, in every arrangement: the wall must never be built on a road without a gate letting it
 * through. Either the road's centre line only passes under a gate's passage, or the planner shows red and refuses the
 * trace. A silent cut (accepted trace, road under the masonry) is the bug these tests hunt.
 */
class WallRoadCrossingTest
{
    private static final WallLevels.Level LEVEL = WallLevels.of(1);
    private static final WallLayout.Claim ALWAYS = (x, z) -> true;
    /** A road is paved 7 wide: 3 each side of its centre line. */
    private static final int PAVING = 3;

    private static long key(final int x, final int z)
    {
        return WallRegistry.cell(x, z);
    }

    private static WallTrace.Point p(final int x, final int z)
    {
        return new WallTrace.Point(x, z);
    }

    /** A road's centre line and its paving, as the world holds them. */
    private static final class Road
    {
        final Set<Long> centre = new HashSet<>();
        final Set<Long> paving = new HashSet<>();

        Road line(final int x0, final int z0, final int x1, final int z1)
        {
            // Roads only go in 4 directions: a staircase hugging the straight line
            int x = x0;
            int z = z0;
            add(x, z);
            while (x != x1 || z != z1)
            {
                final long errX = Math.abs((long) (z1 - z0) * (x + Integer.signum(x1 - x) - x0) - (long) (x1 - x0) * (z - z0));
                final long errZ = Math.abs((long) (z1 - z0) * (x - x0) - (long) (x1 - x0) * (z + Integer.signum(z1 - z) - z0));
                if (x != x1 && (z == z1 || errX <= errZ))
                {
                    x += Integer.signum(x1 - x);
                }
                else
                {
                    z += Integer.signum(z1 - z);
                }
                add(x, z);
            }
            return this;
        }

        private void add(final int x, final int z)
        {
            centre.add(key(x, z));
            for (int dz = -PAVING; dz <= PAVING; dz++)
            {
                for (int dx = -PAVING; dx <= PAVING; dx++)
                {
                    paving.add(key(x + dx, z + dz));
                }
            }
        }

        /** A level-1 road is a dirt path: natural ground the probe lets through on its own. */
        boolean dirt;

        Road dirt()
        {
            dirt = true;
            return this;
        }

        WallLayout.Ground ground()
        {
            return (x, z) -> WallLayout.overRoad(new Terrain.Probe(70, !dirt && paving.contains(key(x, z)), false, true), x, z, centre::contains);
        }
    }

    private record Result(WallLayout layout, List<WallTrace.Point> gates, int cut)
    {
        boolean refused()
        {
            return layout.blocked() > 0;
        }

        @Override
        public String toString()
        {
            return "gates=" + gates + " statuses=" + layout.gates().stream().map(WallLayout.Gate::status).toList() + " blocked=" + layout.blocked() + " roadCellsUnderMasonry=" + cut;
        }
    }

    /** Plans the wall the way the planner and the server do: user gates, then automatic gates at the crossings. */
    private static Result plan(final List<WallTrace.Point> points, final boolean closed, final List<WallTrace.Point> userGates, final Road road)
    {
        final List<WallTrace.Point> gates = new ArrayList<>(userGates);
        gates.addAll(WallLayout.crossings(points, closed, userGates, road.centre::contains));
        final WallLayout layout = WallLayout.of(points, closed, gates, LEVEL, road.ground(), ALWAYS);
        return new Result(layout, gates, cut(layout, road));
    }

    /** Road centre cells the wall would stand on: under a column, a tower or a gate that does not hold. */
    private static int cut(final WallLayout layout, final Road road)
    {
        final Set<Long> under = new HashSet<>();
        for (final WallLayout.Column column : layout.columns())
        {
            under.add(key(column.x(), column.z()));
        }
        for (final WallLayout.Tower tower : layout.towers())
        {
            for (int dx = 0; dx < tower.size(); dx++)
            {
                for (int dz = 0; dz < tower.size(); dz++)
                {
                    if (tower.covers(tower.x() + dx, tower.z() + dz))
                    {
                        under.add(key(tower.x() + dx, tower.z() + dz));
                    }
                }
            }
        }
        for (final WallLayout.Gate gate : layout.gates())
        {
            for (int along = -WallLayout.GATE_HALF; along <= WallLayout.GATE_HALF; along++)
            {
                // A gatehouse's turrets may stand on an oblique road's edge: walkers step into the passage beside them
                if (gate.status() == WallLayout.Status.OK)
                {
                    continue;
                }
                for (int across = -gate.depth(); across <= gate.depth(); across++)
                {
                    under.add(key(gate.x() + along * gate.ux() - across * gate.uz(), gate.z() + along * gate.uz() + across * gate.ux()));
                }
            }
        }
        int count = 0;
        for (final long cell : road.centre)
        {
            if (under.contains(cell))
            {
                count++;
            }
        }
        return count;
    }

    /** The one rule every arrangement must keep. */
    private static void neverSilentlyCut(final Result result)
    {
        assertTrue(result.cut() == 0 || result.refused(), "road cut by an accepted wall: " + result);
    }

    private static final List<WallTrace.Point> RING = List.of(p(-30, -30), p(30, -30), p(30, 30), p(-30, 30));

    // --- A wall traced over an existing road ---

    @Test
    void straightWallAcrossRoad()
    {
        final Result r = plan(List.of(p(-30, 0), p(30, 0)), false, List.of(), new Road().line(0, -40, 0, 40));
        assertEquals(1, r.gates().size(), r.toString());
        assertTrue(!r.refused() && r.cut() == 0, r.toString());
    }

    @Test
    void ringRoadPassesThrough()
    {
        final Result r = plan(RING, true, List.of(), new Road().line(0, -50, 0, 50));
        assertEquals(2, r.gates().size(), r.toString());
        assertTrue(!r.refused() && r.cut() == 0, r.toString());
    }

    @Test
    void ringRoadStopsInside()
    {
        final Result r = plan(RING, true, List.of(), new Road().line(0, -50, 0, 0));
        assertEquals(1, r.gates().size(), r.toString());
        assertTrue(!r.refused() && r.cut() == 0, r.toString());
    }

    @Test
    void ringRoadAcrossTheSeam()
    {
        // The ring's first point is (-30, -30): a road through its first stretch, just past the seam
        final Result r = plan(RING, true, List.of(), new Road().line(-22, -50, -22, 50));
        assertEquals(2, r.gates().size(), r.toString());
        assertTrue(!r.refused() && r.cut() == 0, r.toString());
    }

    @Test
    void obliqueRoadAcrossStraightWall()
    {
        final Result r = plan(List.of(p(-40, 0), p(40, 0)), false, List.of(), new Road().line(-20, -40, 20, 40));
        neverSilentlyCut(r);
        assertTrue(!r.refused(), "an oblique road should still get a usable gate: " + r);
    }

    @Test
    void roadThroughACornerIsRefused()
    {
        final Result r = plan(RING, true, List.of(), new Road().line(30, -60, 30, 0));
        neverSilentlyCut(r);
    }

    @Test
    void roadAcrossADiagonalStretch()
    {
        final Result r = plan(List.of(p(-30, -30), p(30, 30)), false, List.of(), new Road().line(0, -40, 0, 40));
        neverSilentlyCut(r);
    }

    @Test
    void roadNearTheOpenEnd()
    {
        final Result r = plan(List.of(p(0, 0), p(40, 0)), false, List.of(), new Road().line(2, -30, 2, 30));
        neverSilentlyCut(r);
    }

    @Test
    void twoRoadsCloseTogether()
    {
        final Road road = new Road().line(0, -40, 0, 40).line(9, -40, 9, 40);
        final Result r = plan(List.of(p(-40, 0), p(40, 0)), false, List.of(), road);
        neverSilentlyCut(r);
    }

    @Test
    void playerGateBesideTheRoad()
    {
        final Result r = plan(List.of(p(-40, 0), p(40, 0)), false, List.of(p(5, 0)), new Road().line(0, -40, 0, 40));
        neverSilentlyCut(r);
    }

    @Test
    void wallAlongTheRoad()
    {
        final Result r = plan(List.of(p(-40, 0), p(40, 0)), false, List.of(), new Road().line(-60, 0, 60, 0));
        neverSilentlyCut(r);
    }

    @Test
    void wallAlongTheRoadsEdge()
    {
        final Result r = plan(List.of(p(-40, 0), p(40, 0)), false, List.of(), new Road().line(-60, 3, 60, 3));
        neverSilentlyCut(r);
    }

    @Test
    void wallAlongADirtRoad()
    {
        final Result r = plan(List.of(p(-40, 0), p(40, 0)), false, List.of(), new Road().line(-60, 1, 60, 1).dirt());
        assertTrue(r.refused(), "a dirt road is a road too: " + r);
    }

    @Test
    void dirtRoadAcrossWall()
    {
        final Result r = plan(List.of(p(-30, 0), p(30, 0)), false, List.of(), new Road().line(0, -40, 0, 40).dirt());
        assertEquals(1, r.gates().size(), r.toString());
        assertTrue(!r.refused() && r.cut() == 0, r.toString());
    }

    @Test
    void roadAtFortyFiveDegrees()
    {
        // Too slanted to go through a straight gatehouse: red, the player bends the wall square to the road there
        final Result r = plan(List.of(p(-40, 0), p(40, 0)), false, List.of(), new Road().line(-30, -30, 30, 30));
        neverSilentlyCut(r);
    }

    @Test
    void sixRoadsThroughARing()
    {
        final Road road = new Road().line(-15, -50, -15, 50).line(0, -50, 0, 50).line(15, -50, 15, 50);
        final Result r = plan(RING, true, List.of(), road);
        assertEquals(6, r.gates().size(), r.toString());
        neverSilentlyCut(r);
        assertTrue(r.refused(), "a 6th gate is past the limit: " + r);
    }

    @Test
    void roadInsideTheRingNeverTouchesIt()
    {
        final Result r = plan(RING, true, List.of(), new Road().line(-10, 0, 10, 0));
        assertEquals(0, r.gates().size(), r.toString());
        assertTrue(!r.refused() && r.cut() == 0, r.toString());
    }
}
