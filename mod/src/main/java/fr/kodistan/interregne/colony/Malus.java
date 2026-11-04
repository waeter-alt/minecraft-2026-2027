package fr.kodistan.interregne.colony;

import com.minecolonies.api.util.constant.CitizenConstants;
import fr.kodistan.interregne.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * What neglect costs: a hungry, starving or sick villager keeps working, but slower, and so does the hut's own
 * production. The slowdowns multiply, so a sick villager left to starve is all but useless. Feeding (restaurant or
 * Intendance) and curing (Intendance only) are the way out. Shared by the server and the windows, which explain it.
 */
public final class Malus
{
    /** Under this saturation a villager is hungry: the same limit the Intendance feeds at. */
    public static final double HUNGRY = CitizenConstants.LOW_SATURATION;

    public enum Kind
    {
        HUNGRY, STARVING, SICK
    }

    private Malus()
    {
    }

    public static boolean hungry(final double saturation)
    {
        return saturation < HUNGRY;
    }

    public static boolean starving(final double saturation)
    {
        return saturation <= 0;
    }

    /**
     * How many times slower this kind makes a villager: 1 when it does not apply or the settings are not loaded yet.
     */
    public static double of(final Kind kind)
    {
        try
        {
            return switch (kind)
            {
                case HUNGRY -> Config.HUNGRY_SLOWDOWN.get();
                case STARVING -> Config.STARVING_SLOWDOWN.get();
                case SICK -> Config.SICK_SLOWDOWN.get();
            };
        }
        catch (final IllegalStateException e)
        {
            return 1.0;
        }
    }

    /**
     * The kinds that apply. Starving replaces hungry, it does not add to it.
     */
    public static List<Kind> kinds(final double saturation, final boolean sick)
    {
        final List<Kind> kinds = new ArrayList<>();
        if (starving(saturation))
        {
            kinds.add(Kind.STARVING);
        }
        else if (hungry(saturation))
        {
            kinds.add(Kind.HUNGRY);
        }
        if (sick)
        {
            kinds.add(Kind.SICK);
        }
        return kinds;
    }

    /**
     * How many times slower this villager works, all its maluses multiplied: 1 when fed and healthy.
     */
    public static double factor(final double saturation, final boolean sick)
    {
        double factor = 1.0;
        for (final Kind kind : kinds(saturation, sick))
        {
            factor *= of(kind);
        }
        return factor;
    }

    /**
     * "−67 %": the share of work lost at this slowdown.
     */
    public static String percent(final double factor)
    {
        return "−" + Math.round((1.0 - 1.0 / factor) * 100) + " %";
    }

    /**
     * "3×", "1,5×".
     */
    public static String times(final double factor)
    {
        return (factor == Math.rint(factor) ? String.valueOf((long) factor) : String.valueOf(Math.round(factor * 10) / 10.0).replace('.', ',')) + "×";
    }
}
