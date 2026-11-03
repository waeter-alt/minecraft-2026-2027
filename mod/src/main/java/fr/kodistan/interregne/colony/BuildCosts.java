package fr.kodistan.interregne.colony;

import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.economy.Couronnes;
import net.minecraft.world.item.Item;

/**
 * Single source of truth for construction prices and durations. The server charges and builds with it, and the
 * client uses it for display: NeoForge syncs server configs to every client on login, so both read the same values.
 */
public final class BuildCosts
{
    /**
     * Highest MineColonies building level.
     */
    public static final int MAX_LEVEL = 5;

    private BuildCosts()
    {
    }

    /**
     * The coin shown next to prices.
     */
    public static Item paymentItem()
    {
        return Couronnes.COURONNE.get();
    }

    public static int forLevel(final int targetLevel)
    {
        return Config.COST_PER_LEVEL.get() * Math.max(1, targetLevel);
    }

    public static int forRepair()
    {
        return Config.REPAIR_COST.get();
    }

    public static int forDecoration()
    {
        return Config.DECORATION_COST.get();
    }

    /**
     * A decoration of this many blocks: one Couronne per so many blocks, at least the flat decoration cost.
     */
    public static int forDecoration(final long blocks)
    {
        return (int) Math.max(forDecoration(), blocks / Config.DECORATION_BLOCKS_PER_COURONNE.get());
    }

    /**
     * Construction time of a building reaching this level: from the minimum at level 1 to the maximum at level 5.
     */
    public static int secondsForLevel(final int targetLevel)
    {
        final int min = Config.MIN_BUILD_SECONDS.get();
        final int max = Math.max(min, Config.MAX_BUILD_SECONDS.get());
        final int level = Math.clamp(targetLevel, 1, MAX_LEVEL);
        return min + (max - min) * (level - 1) / (MAX_LEVEL - 1);
    }

    /**
     * Construction time of a repair or a decoration.
     */
    public static int secondsForFlat()
    {
        return Config.MIN_BUILD_SECONDS.get();
    }

    /**
     * "45 s", "1 min", "1 min 30".
     */
    public static String formatSeconds(final int seconds)
    {
        if (seconds < 60)
        {
            return seconds + " s";
        }
        final int rest = seconds % 60;
        return seconds / 60 + " min" + (rest == 0 ? "" : " " + (rest < 10 ? "0" : "") + rest);
    }
}
