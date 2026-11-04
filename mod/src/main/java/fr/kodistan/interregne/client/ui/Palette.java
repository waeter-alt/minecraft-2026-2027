package fr.kodistan.interregne.client.ui;

/**
 * The parchment colours shared by every Interrègne screen.
 */
public final class Palette
{
    public static final int PAPER         = 0xFFE9D8A6;
    public static final int PAPER_DARK    = 0xFFDCC48E;
    public static final int PAPER_LIGHT   = 0xFFF8EED6;
    public static final int PANEL         = 0xFFF1E3BD;
    public static final int CARD_HOVER    = 0xFFFFF6DD;
    public static final int EDGE          = 0xFF4A2E16;
    public static final int RULE          = 0xFFB9976A;
    public static final int LEATHER       = 0xFF5C3A21;
    public static final int LEATHER_LIGHT = 0xFF7A5232;
    public static final int GOLD_TEXT     = 0xFFFFE3A3;
    public static final int INFLUENCE     = 0xFFC9A0FF;
    public static final int INK           = 0xFF4A2A10;
    public static final int MUTED         = 0xFF7A5232;
    public static final int SELECTED      = 0xFFC08A1E;
    public static final int WHITE         = 0xFFFFFFFF;

    /**
     * State colours: green = fine, amber = waiting, red = blocked. Nothing else uses them.
     */
    public static final int GREEN       = 0xFF3B6D11;
    public static final int GREEN_HOVER = 0xFF4A8516;
    public static final int GREEN_LIGHT = 0xFF639922;
    public static final int GREEN_TEXT  = 0xFFEAF3DE;
    public static final int GREEN_SOFT  = 0xFFDCE8C5;
    public static final int AMBER       = 0xFFC08A1E;
    public static final int AMBER_TEXT  = 0xFF7A5410;
    public static final int AMBER_SOFT  = 0xFFF3DFA5;
    public static final int RED         = 0xFFA32D2D;
    public static final int RED_SOFT    = 0xFFF0CFC4;

    /**
     * Sickness: a pale, sickly green, never used for anything good.
     */
    public static final int SICK_SOFT = 0xFFD3D98E;
    public static final int SICK_TEXT = 0xFF5B6216;

    /**
     * Building level colours, the same as the construction site bars: 1 white, 2 green, 3 blue, 4 purple, 5 gold.
     * Index 0 is a building not built yet.
     */
    private static final int[] LEVEL_FILL = {0xFFB8A888, 0xFFF4F1EA, 0xFF4CA83A, 0xFF3A7BD5, 0xFF9B4DCA, 0xFFE8B021};
    private static final int[] LEVEL_EDGE = {0xFF7A6A4E, 0xFF8E8778, 0xFF2B6620, 0xFF1F4C8C, 0xFF5E2A80, 0xFF8A6410};
    private static final int[] LEVEL_TEXT = {0xFF3A2A18, 0xFF3A3A3A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF3A2A08};

    /**
     * Pale tints of the level colours, for a whole panel's background: about a tenth of the colour over white, like
     * the "50" shades of design palettes, so dark ink stays readable on them.
     */
    private static final int[] LEVEL_SOFT = {0xFFEDE3CC, 0xFFFAF8F3, 0xFFE6F3E0, 0xFFE3EDFA, 0xFFF1E6F8, 0xFFFBF1D3};

    public static int levelSoft(final int level)
    {
        return LEVEL_SOFT[clampLevel(level)];
    }

    private Palette()
    {
    }

    private static int clampLevel(final int level)
    {
        return Math.max(0, Math.min(LEVEL_FILL.length - 1, level));
    }

    public static int levelFill(final int level)
    {
        return LEVEL_FILL[clampLevel(level)];
    }

    public static int levelEdge(final int level)
    {
        return LEVEL_EDGE[clampLevel(level)];
    }

    /**
     * Text drawn on the level's fill: dark on white and gold, white on the others.
     */
    public static int levelText(final int level)
    {
        return LEVEL_TEXT[clampLevel(level)];
    }
}
