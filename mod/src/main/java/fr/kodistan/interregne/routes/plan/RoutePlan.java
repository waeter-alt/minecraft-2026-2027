package fr.kodistan.interregne.routes.plan;

/**
 * A planned route: its centre line cell by cell (start to end), the height of the road at each cell and what the
 * road is there, plus the map of the columns around it.
 */
public final class RoutePlan
{
    public static final byte SURFACE = Tuning.SURFACE;
    public static final byte BRIDGE = Tuning.BRIDGE;
    public static final byte TUNNEL = Tuning.TUNNEL;

    public final int[] x;
    public final int[] z;
    public final int[] y;
    public final byte[] mode;
    /** Cells in or right next to a bend: the road is level there. */
    public final boolean[] bend;
    public final Footprint footprint;
    public final float cost;
    /** The route ends on a road already built (its last cell is on that road's centre line), not at its B end. */
    public final boolean joined;

    RoutePlan(final int[] x, final int[] z, final int[] y, final byte[] mode, final boolean[] bend, final Footprint footprint, final float cost,
      final boolean joined)
    {
        this.joined = joined;
        this.x = x;
        this.z = z;
        this.y = y;
        this.mode = mode;
        this.bend = bend;
        this.footprint = footprint;
        this.cost = cost;
    }

    public int length()
    {
        return x.length;
    }

    /**
     * How many separate structures of this kind the route has (a run of cells counts once).
     */
    public int count(final byte kind)
    {
        int runs = 0;
        for (int i = 0; i < mode.length; i++)
        {
            if (mode[i] == kind && (i == 0 || mode[i - 1] != kind))
            {
                runs++;
            }
        }
        return runs;
    }
}
