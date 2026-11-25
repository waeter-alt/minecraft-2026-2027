package fr.kodistan.interregne.routes.plan;

import org.junit.jupiter.api.Test;

import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route planner on made-up reliefs, one per thing a route must get right: a level deck from rim to rim over a
 * canyon, a deck clear of the water over a river in a plain, a bore under a ridge and none through a bump, a way
 * round a lake, and on every one of them the rules of a road a caravan can walk.
 */
class RoutePlannerTest
{
    private static final int SEA = 62;

    private static HeightField relief(final int w, final int h, final IntBinaryOperator height)
    {
        final HeightField field = new HeightField(0, 0, w, h);
        for (int z = 0; z < h; z++)
        {
            for (int x = 0; x < w; x++)
            {
                final int y = height.applyAsInt(x, z);
                if (y < SEA)
                {
                    field.set(x, z, SEA, y, HeightField.WATER);
                }
                else
                {
                    field.set(x, z, y, y, 0);
                }
            }
        }
        return field;
    }

    /** Gentle bumps, the same everywhere, so no relief is perfectly flat. */
    private static int bumps(final int x, final int z)
    {
        return (int) Math.round(1.2 * Math.sin(x / 17.0) * Math.cos(z / 23.0));
    }

    private static RoutePlan plan(final HeightField field, final int ax, final int az, final int bx, final int bz)
    {
        final RoutePlan plan = RoutePlanner.plan(field, ax, az, bx, bz, 20_000);
        assertNotNull(plan, "a route exists");
        assertEquals(ax, plan.x[0]);
        assertEquals(az, plan.z[0]);
        assertEquals(bx, plan.x[plan.length() - 1]);
        assertEquals(bz, plan.z[plan.length() - 1]);
        walkable(field, plan);
        return plan;
    }

    /**
     * What holds on every route: the line is continuous, the road changes one level at a time and never goes up
     * then straight back down, a deck stays clear of the water, a bore keeps its roof, the road never lies in water.
     */
    private static void walkable(final HeightField field, final RoutePlan plan)
    {
        int lastStep = 0;
        int lastStepAt = -10;
        for (int i = 0; i < plan.length(); i++)
        {
            final int ground = field.ground(plan.x[i], plan.z[i]);
            final boolean wet = field.wet(plan.x[i], plan.z[i]);
            if (i > 0)
            {
                assertTrue(Math.abs(plan.x[i] - plan.x[i - 1]) <= 1 && Math.abs(plan.z[i] - plan.z[i - 1]) <= 1, "continuous at " + i);
                final int step = plan.y[i] - plan.y[i - 1];
                assertTrue(Math.abs(step) <= 1, "one level at a time at " + i);
                if (step != 0)
                {
                    assertFalse(step == -lastStep && i - lastStepAt <= 2, "no hump or dip of one cell at " + i);
                    assertFalse(plan.bend[i] || plan.bend[i - 1], "no step in a bend at " + i);
                    lastStep = step;
                    lastStepAt = i;
                }
            }
            switch (plan.mode[i])
            {
                case RoutePlan.BRIDGE ->
                {
                    if (wet)
                    {
                        assertTrue(plan.y[i] >= ground + Tuning.WATER_CLEAR, "deck clear of the water at " + i);
                    }
                }
                case RoutePlan.TUNNEL -> assertTrue(ground - plan.y[i] >= Tuning.COVER, "roof over the bore at " + i);
                default ->
                {
                    assertFalse(wet && i > 0 && i < plan.length() - 1, "no road in the water at " + i);
                    assertTrue(ground - plan.y[i] <= Tuning.CUT_MAX && plan.y[i] - ground <= Tuning.FILL_MAX, "road near the ground at " + i);
                }
            }
        }
    }

    private static int longest(final RoutePlan plan, final byte kind)
    {
        int best = 0;
        int run = 0;
        for (int i = 0; i < plan.length(); i++)
        {
            run = plan.mode[i] == kind ? run + 1 : 0;
            best = Math.max(best, run);
        }
        return best;
    }

    @Test
    void canyonGetsALevelDeckFromRimToRim()
    {
        // Two plateaus at 92, a canyon 26 wide and about 30 deep between them, a stream at the bottom
        final HeightField field = relief(320, 200, (x, z) -> {
            final int c = Math.abs(x - 160);
            return c < 13 ? (c < 3 ? 60 : 64 + (int) ((c - 3) * 2.8)) : 92 + bumps(x, z);
        });
        final RoutePlan plan = plan(field, 20, 100, 300, 100);
        assertEquals(1, plan.count(RoutePlan.BRIDGE), "one bridge");
        assertEquals(0, plan.count(RoutePlan.TUNNEL));
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < plan.length(); i++)
        {
            if (plan.mode[i] == RoutePlan.BRIDGE)
            {
                low = Math.min(low, plan.y[i]);
                high = Math.max(high, plan.y[i]);
            }
        }
        assertEquals(low, high, "the deck is level");
        assertTrue(low >= 90, "the deck is at the height of the rims, not down in the canyon: " + low);
        assertTrue(longest(plan, RoutePlan.BRIDGE) <= 34, "the deck spans the canyon and no more");
    }

    @Test
    void riverInAPlainGetsADeckClearOfTheWater()
    {
        final HeightField field = relief(360, 200, (x, z) -> {
            final int river = Math.abs(x - 180);
            return river < 7 ? 58 : Math.min(66 + bumps(x, z), 63 + (river - 7));
        });
        final RoutePlan plan = plan(field, 20, 100, 340, 100);
        assertEquals(1, plan.count(RoutePlan.BRIDGE));
        assertEquals(0, plan.count(RoutePlan.TUNNEL));
        assertTrue(longest(plan, RoutePlan.BRIDGE) <= 26, "the deck spans the river, not the plain");
    }

    @Test
    void highRidgeGetsABoreAndABumpDoesNot()
    {
        final HeightField ridge = relief(360, 240, (x, z) -> 68 + (int) (44 * Math.exp(-(x - 180) * (x - 180) / (2 * 22 * 22.0))) + bumps(x, z));
        final RoutePlan through = plan(ridge, 20, 120, 340, 120);
        assertEquals(1, through.count(RoutePlan.TUNNEL), "a ridge 44 high with no way round is bored through");
        assertTrue(longest(through, RoutePlan.TUNNEL) >= 20);

        final HeightField bump = relief(360, 240, (x, z) -> 68 + (int) (7 * Math.exp(-(x - 180) * (x - 180) / (2 * 22 * 22.0))) + bumps(x, z));
        final RoutePlan over = plan(bump, 20, 120, 340, 120);
        assertEquals(0, over.count(RoutePlan.TUNNEL), "a rise of 7 is walked over");
        assertEquals(0, over.count(RoutePlan.BRIDGE));
    }

    @Test
    void lakeIsGoneRound()
    {
        final HeightField field = relief(420, 300, (x, z) -> {
            final double d = Math.hypot(x - 210, (z - 150) * 1.3);
            return d < 60 ? 50 : 66 + bumps(x, z);
        });
        final RoutePlan plan = plan(field, 20, 150, 400, 150);
        assertEquals(0, plan.count(RoutePlan.BRIDGE), "a lake 120 across is not bridged");
        assertTrue(plan.length() > 400, "the road goes round");
    }

    @Test
    void diagonalTripRunsInLongStraightLegs()
    {
        final HeightField field = relief(420, 420, (x, z) -> 70 + bumps(x, z));
        final RoutePlan plan = plan(field, 30, 40, 380, 370);
        int turns = 0;
        for (int i = 1; i < plan.length() - 1; i++)
        {
            final boolean straight = plan.x[i] - plan.x[i - 1] == plan.x[i + 1] - plan.x[i] && plan.z[i] - plan.z[i - 1] == plan.z[i + 1] - plan.z[i];
            if (!straight && (i < 2 || plan.x[i - 1] - plan.x[i - 2] == plan.x[i] - plan.x[i - 1] && plan.z[i - 1] - plan.z[i - 2] == plan.z[i] - plan.z[i - 1]))
            {
                turns++;
            }
        }
        assertTrue(turns <= 6, "a few bends, not a staircase of them: " + turns);
        assertEquals(0, plan.count(RoutePlan.BRIDGE) + plan.count(RoutePlan.TUNNEL));
    }

    @Test
    void unknownCountryIsNeverCrossed()
    {
        final HeightField field = relief(300, 200, (x, z) -> 70 + bumps(x, z));
        for (int z = 0; z < 140; z++)
        {
            for (int x = 140; x < 160; x++)
            {
                field.flags[field.index(x, z)] = (byte) HeightField.UNKNOWN;
            }
        }
        final RoutePlan plan = plan(field, 20, 60, 280, 60);
        for (int i = 0; i < plan.length(); i++)
        {
            assertEquals(0, field.flags(plan.x[i], plan.z[i]) & HeightField.UNKNOWN, "known ground at " + i);
        }
    }

    @Test
    void existingBridgeIsSharedRatherThanDoubled()
    {
        // A river 40 wide with one bridge already across it, eight blocks off the straight line of the new route
        final HeightField field = relief(360, 200, (x, z) -> {
            final int river = Math.abs(x - 180);
            return river < 20 ? 58 : Math.min(66 + bumps(x, z), 63 + (river - 20));
        });
        for (int x = 150; x <= 210; x++)
        {
            for (int z = 105; z <= 111; z++)
            {
                field.set(x, z, 66, 66, HeightField.ROAD);
            }
        }
        final RoutePlan plan = plan(field, 20, 100, 340, 100);
        assertEquals(0, plan.count(RoutePlan.BRIDGE), "the route crosses on the bridge that is already there");
        boolean onDeck = false;
        for (int i = 0; i < plan.length(); i++)
        {
            onDeck |= plan.x[i] == 180 && plan.z[i] == 108;
        }
        assertTrue(onDeck, "the route goes over the existing deck");
    }

    @Test
    void roadToTheSameColonyIsMetSquareOnAndNotRunAlong()
    {
        // A road along x at z 103, off the search's lattice, already leading to B; B's gate lies beyond it, well off
        // to the side, so going there would mean running along it
        final HeightField field = relief(320, 260, (x, z) -> 70 + bumps(x, z));
        final int roadZ = 103;
        final int[] xs = new int[201];
        final int[] zs = new int[201];
        for (int k = 0; k <= 200; k++)
        {
            xs[k] = 100 + k;
            zs[k] = roadZ;
            for (int dz = -3; dz <= 3; dz++)
            {
                field.set(xs[k], roadZ + dz, 70, 70, HeightField.ROAD);
            }
        }
        final byte[] join = RoutePlanner.joinMask(field, java.util.List.<int[][]>of(new int[][] {xs, zs}));
        final RoutePlan plan = RoutePlanner.plan(field, 200, 20, 60, 240, 20_000, null, null, join);
        assertNotNull(plan, "a route exists");
        assertTrue(plan.joined, "the route ends on the road already there");
        final int n = plan.length();
        assertEquals(roadZ, plan.z[n - 1], "it stops on that road's centre line, not a block or two off it");
        assertTrue(plan.x[n - 1] >= 100 && plan.x[n - 1] <= 300);
        for (int i = n - 8; i < n; i++)
        {
            assertEquals(plan.x[n - 1], plan.x[i], "the last stretch runs square to the road at " + i);
        }
        int inBand = 0;
        for (int i = 0; i < n; i++)
        {
            if (Math.abs(plan.z[i] - roadZ) <= 3 && plan.x[i] >= 97 && plan.x[i] <= 303)
            {
                assertTrue(i >= n - 4, "only the very end of the route lies on the old road's band, at " + i);
                inBand++;
            }
        }
        assertEquals(4, inBand, "it does not run along the old road");
        walkable(field, plan);
    }
}
