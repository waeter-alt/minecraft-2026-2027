package fr.kodistan.interregne.routes.plan;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The relief a route is planned on: one surveyed column per block of a rectangle. Plain arrays and no Minecraft
 * class, so the planner runs (and is tested) outside the game, on a worker thread, on terrain dumped to a file.
 */
public final class HeightField
{
    /** The column's top is water (its {@link #ground} is the water surface, its {@link #bed} the bottom). */
    public static final int WATER = 1;
    /** Something a player built stands there. */
    public static final int BLOCKED = 2;
    public static final int LAVA = 4;
    /** Frozen water: crossed like water, never walked on. */
    public static final int ICE = 8;
    /** Not surveyed: nothing is known, nothing may be built. */
    public static final int UNKNOWN = 16;
    /** An existing road or bridge deck, its {@link #ground} the level one walks on: a new route may follow it. */
    public static final int ROAD = 32;
    /** Another colony's land, or a town's own wall and streets: a route goes around, never through. */
    public static final int FOREIGN = 64;
    /** The land of an end colony without a wall: the road goes in to its town hall's door, two blocks wide. */
    public static final int TOWN = 128;

    private static final int MAGIC = 0x52544552;

    public final int x0;
    public final int z0;
    public final int w;
    public final int h;
    public final short[] ground;
    public final short[] bed;
    public final byte[] flags;

    public HeightField(final int x0, final int z0, final int w, final int h)
    {
        this(x0, z0, w, h, new short[w * h], new short[w * h], new byte[w * h]);
    }

    public HeightField(final int x0, final int z0, final int w, final int h, final short[] ground, final short[] bed, final byte[] flags)
    {
        this.x0 = x0;
        this.z0 = z0;
        this.w = w;
        this.h = h;
        this.ground = ground;
        this.bed = bed;
        this.flags = flags;
    }

    public boolean inside(final int x, final int z)
    {
        return x >= x0 && z >= z0 && x < x0 + w && z < z0 + h;
    }

    public int index(final int x, final int z)
    {
        return (z - z0) * w + (x - x0);
    }

    public void set(final int x, final int z, final int groundY, final int bedY, final int flag)
    {
        final int i = index(x, z);
        ground[i] = (short) groundY;
        bed[i] = (short) bedY;
        flags[i] = (byte) flag;
    }

    /**
     * Adds a flag to a column, leaving its heights as they are.
     */
    public void mark(final int x, final int z, final int flag)
    {
        flags[index(x, z)] |= (byte) flag;
    }

    public int ground(final int x, final int z)
    {
        return ground[index(x, z)];
    }

    public int flags(final int x, final int z)
    {
        return inside(x, z) ? flags[index(x, z)] : UNKNOWN;
    }

    public boolean wet(final int x, final int z)
    {
        return (flags(x, z) & (WATER | ICE)) != 0;
    }

    /**
     * A copy of the part of this field inside the given bounds (clamped to the field).
     */
    public HeightField crop(final int minX, final int minZ, final int maxX, final int maxZ)
    {
        final int cx0 = Math.max(x0, minX);
        final int cz0 = Math.max(z0, minZ);
        final int cx1 = Math.min(x0 + w - 1, maxX);
        final int cz1 = Math.min(z0 + h - 1, maxZ);
        final HeightField out = new HeightField(cx0, cz0, cx1 - cx0 + 1, cz1 - cz0 + 1);
        for (int z = cz0; z <= cz1; z++)
        {
            final int from = index(cx0, z);
            final int to = out.index(cx0, z);
            System.arraycopy(ground, from, out.ground, to, out.w);
            System.arraycopy(bed, from, out.bed, to, out.w);
            System.arraycopy(flags, from, out.flags, to, out.w);
        }
        return out;
    }

    public static HeightField read(final InputStream stream) throws IOException
    {
        final DataInputStream in = new DataInputStream(stream);
        if (in.readInt() != MAGIC || in.readInt() != 1)
        {
            throw new IOException("Not a terrain dump");
        }
        final int x0 = in.readInt();
        final int z0 = in.readInt();
        final int w = in.readInt();
        final int h = in.readInt();
        final HeightField field = new HeightField(x0, z0, w, h);
        final byte[] buffer = new byte[w * 2];
        for (final short[] target : new short[][] {field.ground, field.bed})
        {
            for (int row = 0; row < h; row++)
            {
                in.readFully(buffer);
                for (int i = 0; i < w; i++)
                {
                    target[row * w + i] = (short) ((buffer[2 * i] << 8) | (buffer[2 * i + 1] & 0xFF));
                }
            }
        }
        in.readFully(field.flags);
        return field;
    }

    public void write(final OutputStream stream) throws IOException
    {
        final DataOutputStream out = new DataOutputStream(stream);
        out.writeInt(MAGIC);
        out.writeInt(1);
        out.writeInt(x0);
        out.writeInt(z0);
        out.writeInt(w);
        out.writeInt(h);
        final byte[] buffer = new byte[w * 2];
        for (final short[] source : new short[][] {ground, bed})
        {
            for (int row = 0; row < h; row++)
            {
                for (int i = 0; i < w; i++)
                {
                    final short value = source[row * w + i];
                    buffer[2 * i] = (byte) (value >> 8);
                    buffer[2 * i + 1] = (byte) value;
                }
                out.write(buffer);
            }
        }
        out.write(flags);
        out.flush();
    }
}
