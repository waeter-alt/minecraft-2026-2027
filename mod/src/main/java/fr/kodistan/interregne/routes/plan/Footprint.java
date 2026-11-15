package fr.kodistan.interregne.routes.plan;

import java.util.Arrays;

/**
 * Which cell of the centre line each column near the route belongs to (the nearest one), and how far it lies from
 * it. The road, its shoulders and the tree clearing are all read from this one map, so a corner is filled without a
 * gap or an overlap whatever the shape of the line.
 */
public final class Footprint
{
    /** How far from the centre line columns are mapped: road, three rings of shoulder, tree clearing. */
    public static final int REACH = 8;

    private final int x0;
    private final int z0;
    private final int w;
    private final int h;
    private final int[] owner;
    private final byte[] d2;

    Footprint(final int[] xs, final int[] zs)
    {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < xs.length; i++)
        {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        this.x0 = minX - REACH;
        this.z0 = minZ - REACH;
        this.w = maxX - minX + 1 + 2 * REACH;
        this.h = maxZ - minZ + 1 + 2 * REACH;
        this.owner = new int[w * h];
        this.d2 = new byte[w * h];
        Arrays.fill(owner, -1);
        Arrays.fill(d2, Byte.MAX_VALUE);
        for (int i = 0; i < xs.length; i++)
        {
            for (int dz = -REACH; dz <= REACH; dz++)
            {
                for (int dx = -REACH; dx <= REACH; dx++)
                {
                    final int d = dx * dx + dz * dz;
                    if (d > REACH * REACH)
                    {
                        continue;
                    }
                    final int at = (zs[i] + dz - z0) * w + (xs[i] + dx - x0);
                    if (d < d2[at])
                    {
                        d2[at] = (byte) d;
                        owner[at] = i;
                    }
                }
            }
        }
    }

    /**
     * Index of the centre-line cell this column belongs to, -1 when it lies out of reach of the route.
     */
    public int owner(final int x, final int z)
    {
        return x < x0 || z < z0 || x >= x0 + w || z >= z0 + h ? -1 : owner[(z - z0) * w + (x - x0)];
    }

    /**
     * Squared distance from this column to its cell of the centre line.
     */
    public int distanceSq(final int x, final int z)
    {
        return x < x0 || z < z0 || x >= x0 + w || z >= z0 + h ? Integer.MAX_VALUE : d2[(z - z0) * w + (x - x0)];
    }

    public static boolean road(final int distanceSq)
    {
        return distanceSq <= Tuning.ROAD_D2;
    }
}
