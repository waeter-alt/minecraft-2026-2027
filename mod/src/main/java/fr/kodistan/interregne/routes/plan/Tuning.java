package fr.kodistan.interregne.routes.plan;

/**
 * Every price the planner weighs, in one place. The unit is "one flat cell of road walked": a route is the cheapest
 * line for a traveller who pays for distance, for every level climbed, and for the works needed to carry the road.
 * Nothing here says "a valley this deep gets a bridge": a bridge is what the cheapest line does when stepping down
 * and back up costs more than a level deck, a tunnel when climbing over costs more than a bore.
 */
final class Tuning
{
    static final byte SURFACE = 0;
    static final byte BRIDGE = 1;
    static final byte TUNNEL = 2;

    /** Columns with a squared distance to the centre line up to this are road: 7 wide on a straight run. */
    static final int ROAD_D2 = 12;

    /** Deepest open cut and highest embankment a road cell may need, at its worst column. */
    static final int CUT_MAX = 6;
    static final int FILL_MAX = 4;
    static final float W_CUT = 0.09F;
    static final float W_FILL = 0.12F;
    /** A road column standing in water (a quay along a bank): allowed for two columns of a cell at most. */
    static final int WET_MAX = 2;
    static final float WET_COLUMN = 4F;
    static final float BLOCKED_COLUMN = 30F;
    /** A cell of road that already exists: cheaper than the best new ground, so routes share a trunk and a bridge. */
    static final float EXISTING_ROAD = 0.6F;

    static final float BRIDGE_CELL = 2.4F;
    static final float BRIDGE_PIER = 0.03F;
    static final float BRIDGE_PIER_SQ = 0.0012F;
    /** Deck this far above the water surface, whatever the banks. */
    static final int WATER_CLEAR = 3;
    static final int BRIDGE_MAX = 44;

    static final float TUNNEL_CELL = 3.5F;
    /** Rock needed above the tunnel floor at the centre: five of headroom and a roof. */
    static final int COVER = 6;
    /** Rock needed above the floor at the sides, lower: the bore is arched, the builder walls what is missing. */
    static final int SIDE_COVER = 3;
    static final int DEPTH_MAX = 72;

    /** One-off prices of a structure's two ends: what keeps a ditch from getting a bridge and a bump a tunnel. */
    static final float ABUTMENT = 5F;
    static final float PORTAL = 14F;
    static final float BRIDGE_TO_TUNNEL = 10F;

    /** One level gained or lost. Dearer on a deck (a bridge wants to be level) and in a bore. */
    static final float STEP = 3F;
    static final float STEP_BRIDGE = 9F;
    static final float STEP_TUNNEL = 4F;
    /** Two steps in a row: a staircase, where one step then a landing is a slope. */
    static final float STEEP = 1.6F;

    static final float TURN = 12F;

    private Tuning()
    {
    }

    /** Piers and bracing under one cell of deck: a tall deck costs far more than a low one. */
    static float pier(final int height)
    {
        final int h = Math.max(0, height);
        return BRIDGE_PIER * h + BRIDGE_PIER_SQ * h * h;
    }

    static float stepCost(final byte mode)
    {
        return mode == BRIDGE ? STEP_BRIDGE : mode == TUNNEL ? STEP_TUNNEL : STEP;
    }

    static float switchCost(final byte from, final byte to)
    {
        if (from == to)
        {
            return 0F;
        }
        if (from != SURFACE && to != SURFACE)
        {
            return BRIDGE_TO_TUNNEL;
        }
        return from == BRIDGE || to == BRIDGE ? ABUTMENT : PORTAL;
    }
}
