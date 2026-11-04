package fr.kodistan.interregne.colony;

import org.jetbrains.annotations.Nullable;

/**
 * No building may rise above the town hall: to bring a hut to level N, the town hall must be at level N first.
 * The town hall itself is not capped.
 */
public final class TownHallCap
{
    private TownHallCap()
    {
    }

    /**
     * Why this level is out of reach, or null when it is allowed.
     */
    @Nullable
    public static String refusal(final boolean isTownHall, final int targetLevel, final int townHallLevel)
    {
        if (isTownHall || targetLevel <= townHallLevel)
        {
            return null;
        }
        return "L'Hôtel de ville est au niveau " + townHallLevel + " : améliorez-le d'abord pour passer ce bâtiment au niveau " + targetLevel + ".";
    }
}
