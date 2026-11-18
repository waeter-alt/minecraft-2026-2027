package fr.kodistan.interregne.commerce;

import fr.kodistan.interregne.Config;

/**
 * The caravan escort tiers: 0 is no escort, 1 to 5 hire soldiers who walk with the caravan. Prices and headcounts live
 * in the config; the gear and toughness of the soldiers grow with the tier (see {@link EntEscorte}).
 */
public final class Escort
{
    public static final int MAX_TIER = 5;

    private Escort()
    {
    }

    /**
     * The highest tier a Relais of this level may hire: one tier per level.
     */
    public static int maxTier(final int relaisLevel)
    {
        return Math.clamp(relaisLevel, 0, MAX_TIER);
    }

    public static int clamp(final int tier)
    {
        return Math.clamp(tier, 0, MAX_TIER);
    }

    /**
     * What a tier costs per contract; 0 for no escort.
     */
    public static long cost(final int tier)
    {
        return tier < 1 || tier > MAX_TIER ? 0 : Config.ESCORT_COST.get(tier - 1).get();
    }

    public static int swordsmen(final int tier)
    {
        return tier < 1 || tier > MAX_TIER ? 0 : Config.ESCORT_SWORDSMEN.get(tier - 1).get();
    }

    public static int archers(final int tier)
    {
        return tier < 1 || tier > MAX_TIER ? 0 : Config.ESCORT_ARCHERS.get(tier - 1).get();
    }

    public static int headcount(final int tier)
    {
        return swordsmen(tier) + archers(tier);
    }
}
