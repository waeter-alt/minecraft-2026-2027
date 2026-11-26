package fr.kodistan.interregne.war;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The taking of a town hall left in ruins: once the assault reaches it, the attackers must stay around its centre for a
 * cumulated time. The progress only moves while at least one troop is in the zone, pauses when it empties and never
 * goes back to zero. Plain numbers, no world: see {@link WarAssaults} for who stands where.
 */
public final class RuinCapture
{
    /** Blocks around the ruin's centre (horizontally) where a troop counts as present. */
    public static final double RADIUS = 6.0D;
    /** Blocks above or below the centre where a troop still counts as present. */
    public static final double HEIGHT = 12.0D;
    /** Where the troops gather: well inside the zone, so none of them waits at its edge. */
    public static final int STAND = 3;

    private final long neededTicks;
    private long doneTicks;

    public RuinCapture(final long neededTicks)
    {
        this.neededTicks = Math.max(1L, neededTicks);
    }

    /**
     * Lets {@code ticks} pass with {@code troopsInZone} troops around the ruin.
     *
     * @return whether the ruin is taken
     */
    public boolean advance(final int troopsInZone, final long ticks)
    {
        if (troopsInZone > 0 && ticks > 0)
        {
            doneTicks = Math.min(neededTicks, doneTicks + ticks);
        }
        return complete();
    }

    public boolean complete()
    {
        return doneTicks >= neededTicks;
    }

    public long doneTicks()
    {
        return doneTicks;
    }

    public long neededTicks()
    {
        return neededTicks;
    }

    /** 0 to 1. */
    public float progress()
    {
        return (float) doneTicks / neededTicks;
    }

    /** Whether a troop at this offset from the ruin's centre counts as present. */
    public static boolean inZone(final double dx, final double dy, final double dz)
    {
        return dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(dy) <= HEIGHT;
    }

    /**
     * Plans the taking of the town hall: when it is in ruins it stays the LAST target, taken instead of battered.
     * Appends {@code hall} to {@code targets} then.
     *
     * @return the capture to run, or null when the town hall stands (it is a normal target then) or does not count
     */
    @Nullable
    public static RuinCapture plan(final List<BlockPos> targets, final BlockPos hall, final boolean hallRuined, final long neededTicks)
    {
        if (!hallRuined || targets.contains(hall))
        {
            return null;
        }
        targets.add(hall);
        return new RuinCapture(neededTicks);
    }
}
