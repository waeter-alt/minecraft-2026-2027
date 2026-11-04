package fr.kodistan.interregne.walls;

/**
 * What a colony wall looks like and costs at each level. Each level is a real step: taller, thicker, bigger towers.
 */
public final class WallLevels
{
    /**
     * One wall level.
     *
     * @param name       its name in the windows
     * @param height     blocks of wall above the ground, the same everywhere
     * @param thickness  blocks across
     * @param towerSize  side of the square towers on every point of the trace
     * @param towerExtra how much higher than the wall the towers rise
     * @param perColumn  Couronnes per block of wall length
     * @param perTower   Couronnes per tower
     */
    public record Level(String name, int height, int thickness, int towerSize, int towerExtra, int perColumn, int perTower)
    {
    }

    private static final Level[] LEVELS = {
      new Level("Palissade", 4, 1, 3, 2, 2, 20),
      new Level("Pierre brute", 5, 1, 3, 2, 4, 40),
      new Level("Briques de pierre", 7, 2, 5, 3, 7, 80),
      new Level("Ardoise des abîmes", 9, 3, 5, 3, 11, 140),
      new Level("Pierre noire et or", 11, 3, 7, 4, 16, 220),
    };

    public static final int MAX = LEVELS.length;

    private WallLevels()
    {
    }

    /**
     * The level, clamped to 1..MAX.
     */
    public static Level of(final int level)
    {
        return LEVELS[Math.max(1, Math.min(MAX, level)) - 1];
    }
}
