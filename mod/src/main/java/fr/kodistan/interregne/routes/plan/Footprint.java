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
    /** Per cell of the centre line: how far its road reaches, and how far anything of the route does. */
    private final byte[] roadCap;
    private final byte[] reachCap;

    Footprint(final int[] xs, final int[] zs)
    {
        this(xs, zs, null, null);
    }

    /**
     * @param half  per cell, the half-width of the road there (1 to 3; null: 3 everywhere). A narrow cell has no
     *              shoulders, so a road funnels into a gate without touching the wall around it.
     * @param field when given, its {@link HeightField#FOREIGN} columns belong to no cell: nothing is built there
     */
    Footprint(final int[] xs, final int[] zs, final byte[] half, final HeightField field)
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
        this.roadCap = new byte[xs.length];
        this.reachCap = new byte[xs.length];
        for (int i = 0; i < xs.length; i++)
        {
            final int k = half == null ? 3 : half[i];
            roadCap[i] = (byte) (k * k + k);
            reachCap[i] = (byte) (k <= 1 ? 2 : k == 2 ? 20 : REACH * REACH);
        }
        for (int at = 0; at < owner.length; at++)
        {
            final int i = owner[at];
            if (i < 0)
            {
                continue;
            }
            final int x = x0 + at % w;
            final int z = z0 + at / w;
            if (d2[at] > reachCap[i] || field != null && field.inside(x, z) && (field.flags(x, z) & HeightField.FOREIGN) != 0)
            {
                owner[at] = -1;
                d2[at] = Byte.MAX_VALUE;
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

    /**
     * Whether this column is part of the road itself (not a shoulder): seven wide on an open run, narrower where
     * the route funnels into a gate.
     */
    public boolean road(final int x, final int z)
    {
        final int i = owner(x, z);
        return i >= 0 && d2[(z - z0) * w + (x - x0)] <= roadCap[i];
    }
}
