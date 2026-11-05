package fr.kodistan.interregne.walls;

/**
 * What a colony wall looks like and costs at each level. Each level is a real step: taller, bigger towers, a new look. The width never changes (6 blocks), so an upgrade never runs into what stands beside the wall.
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
     * @param perGate    Couronnes of the second gate; each further one doubles, the first is free
     */
    public record Level(String name, int height, int thickness, int towerSize, int towerExtra, int perColumn, int perTower, int perGate)
    {
        /**
         * Price of the gate at this rank (0 for the first, which is free).
         */
        public int gatePrice(final int rank)
        {
            return rank <= 0 ? 0 : perGate << (rank - 1);
        }
    }

    /**
     * Gates per wall: at least one, at most this many.
     */
    public static final int MAX_GATES = 5;

    /**
     * Blocks across the wall, the same at every level.
     */
    public static final int WIDTH = 6;

    private static final Level[] LEVELS = {
      new Level("Palissade", 4, WIDTH, 8, 5, 2, 20, 60),
      new Level("Pierre brute", 5, WIDTH, 8, 6, 4, 40, 120),
      new Level("Briques de pierre", 7, WIDTH, 8, 6, 7, 80, 200),
      new Level("Ardoise des abîmes", 9, WIDTH, 8, 7, 11, 140, 320),
      new Level("Pierre noire et or", 11, WIDTH, 8, 8, 16, 220, 500),
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
