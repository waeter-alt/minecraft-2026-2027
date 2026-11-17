package fr.kodistan.interregne.routes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a road is made of at each of its five levels, by role: level 1 is the road the route builder lays (earth,
 * spruce, stone bricks), level 5 a Roman road (smooth stone, deepslate curbs, quartz and iron bridges, lampposts).
 * Raising a road one level swaps each block for the same role one level up, so a block that serves two roles at a
 * level must lead to the same block at the next (checked by the tests). Pure block ids, so the tests need no game.
 */
public final class RoadLevels
{
    public static final int MAX = 5;

    /**
     * @param surface walked on: only swapped where it is the top of the road, never deeper (fill, water bed)
     */
    public enum Role
    {
        PATH(true, "dirt_path", "packed_mud", "andesite", "polished_andesite", "smooth_stone"),
        COARSE(true, "coarse_dirt", "mud_bricks", "tuff", "polished_tuff", "polished_deepslate"),
        PAVE(true, "cobblestone", "cobblestone", "tuff_bricks", "polished_tuff", "polished_deepslate"),
        PAVE_MOSSY(true, "mossy_cobblestone", "mossy_cobblestone", "tuff_bricks", "polished_tuff", "polished_deepslate"),
        STEP(true, "cobblestone_stairs", "cobblestone_stairs", "andesite_stairs", "polished_andesite_stairs", "stone_stairs"),
        STEP_MOSSY(true, "mossy_cobblestone_stairs", "mossy_cobblestone_stairs", "andesite_stairs", "polished_andesite_stairs", "stone_stairs"),
        MASONRY(false, "stone_bricks", "stone_bricks", "stone_bricks", "polished_andesite", "smooth_stone"),
        MASONRY_MOSSY(false, "mossy_stone_bricks", "mossy_stone_bricks", "stone_bricks", "polished_andesite", "smooth_stone"),
        MASONRY_CRACKED(false, "cracked_stone_bricks", "cracked_stone_bricks", "stone_bricks", "polished_andesite", "smooth_stone"),
        KEYSTONE(false, "chiseled_stone_bricks", "chiseled_stone_bricks", "chiseled_tuff", "chiseled_tuff_bricks", "chiseled_stone_bricks"),
        PLANKS(false, "spruce_planks", "dark_oak_planks", "stone_bricks", "polished_andesite", "smooth_stone"),
        TIMBER(false, "spruce_log", "dark_oak_log", "stripped_dark_oak_log", "polished_basalt", "quartz_pillar"),
        DECK_STEP(false, "spruce_stairs", "dark_oak_stairs", "stone_brick_stairs", "polished_andesite_stairs", "stone_stairs"),
        RAIL(false, "spruce_fence", "dark_oak_fence", "stone_brick_wall", "andesite_wall", "iron_bars"),
        LANTERN(false, "lantern", "lantern", "lantern", "lantern", "lantern");

        public final boolean surface;
        private final String[] ids;

        Role(final boolean surface, final String... ids)
        {
            this.surface = surface;
            this.ids = ids;
        }

        public String id(final int level)
        {
            return ids[level - 1];
        }
    }

    /** The outer columns of a road on the ground, from level 2 on: a curb framing the paving. */
    private static final String[] CURB = {null, "mud_bricks", "stone_bricks", "deepslate_bricks", "polished_deepslate"};
    /** Lampposts along the road on the ground: what the post is made of, how tall, and every how many cells. */
    private static final String[] POST = {null, "dark_oak_fence", "stone_brick_wall", "polished_deepslate_wall", "polished_blackstone_wall"};
    private static final int[] POST_HEIGHT = {0, 1, 2, 2, 3};
    private static final int[] LAMP_EVERY = {0, 24, 18, 14, 10};

    private RoadLevels()
    {
    }

    /**
     * What each block of a road at this level becomes one level up (only the blocks that change), or a conflict
     * reported as an exception when one block would have to become two different ones.
     */
    public static Map<String, String> next(final int level)
    {
        if (level < 1 || level >= MAX)
        {
            throw new IllegalArgumentException("level " + level);
        }
        final Map<String, String> out = new HashMap<>();
        for (final Role role : Role.values())
        {
            final String from = role.id(level);
            final String to = role.id(level + 1);
            final String before = out.put(from, to);
            if (before != null && !before.equals(to))
            {
                throw new IllegalStateException(from + " -> " + before + " and " + to + " at level " + level);
            }
        }
        out.entrySet().removeIf(entry -> entry.getKey().equals(entry.getValue()));
        return out;
    }

    /**
     * Blocks of this level that are only ever the top of the road.
     */
    public static Set<String> surfaceOnly(final int level)
    {
        final Set<String> out = new LinkedHashSet<>();
        for (final Role role : Role.values())
        {
            if (role.surface)
            {
                out.add(role.id(level));
            }
        }
        for (final Role role : Role.values())
        {
            if (!role.surface)
            {
                out.remove(role.id(level));
            }
        }
        return out;
    }

    /**
     * Whether this block is one of the road's full surface blocks at this level (no stairs): what a curb replaces.
     */
    public static boolean flatSurface(final int level, final String id)
    {
        return id.equals(Role.PATH.id(level)) || id.equals(Role.COARSE.id(level)) || id.equals(Role.PAVE.id(level))
                 || id.equals(Role.PAVE_MOSSY.id(level)) || id.equals(curb(level));
    }

    /** Null at level 1. */
    public static String curb(final int level)
    {
        return CURB[level - 1];
    }

    /** Null at level 1. */
    public static String post(final int level)
    {
        return POST[level - 1];
    }

    public static int postHeight(final int level)
    {
        return POST_HEIGHT[level - 1];
    }

    public static int lampEvery(final int level)
    {
        return LAMP_EVERY[level - 1];
    }

    /**
     * The full blocks a road of any level lies on (paving, curbs, masonry), which its decay turns back to grass.
     */
    public static Set<String> groundIds()
    {
        final Set<String> out = new LinkedHashSet<>();
        for (int level = 1; level <= MAX; level++)
        {
            for (final Role role : List.of(Role.PATH, Role.COARSE, Role.PAVE, Role.PAVE_MOSSY, Role.MASONRY, Role.MASONRY_MOSSY, Role.MASONRY_CRACKED, Role.KEYSTONE))
            {
                out.add(role.id(level));
            }
            if (curb(level) != null)
            {
                out.add(curb(level));
            }
        }
        return out;
    }

    /**
     * What stands on or above a road of any level (steps, decks, rails, lampposts), which its decay simply removes;
     * a block that is also ground is left to {@link #groundIds()}.
     */
    public static Set<String> raisedIds()
    {
        final Set<String> out = new LinkedHashSet<>();
        for (int level = 1; level <= MAX; level++)
        {
            for (final Role role : List.of(Role.STEP, Role.STEP_MOSSY, Role.PLANKS, Role.TIMBER, Role.DECK_STEP, Role.RAIL, Role.LANTERN))
            {
                out.add(role.id(level));
            }
            if (post(level) != null)
            {
                out.add(post(level));
            }
        }
        out.removeAll(groundIds());
        return out;
    }

    /**
     * The cost of raising a road from this level to the next, read from a list of MAX - 1 costs ("500,1500,...").
     *
     * @return -1 at the top level, or when the list does not have a cost for this step
     */
    public static long upgradeCost(final String costs, final int from)
    {
        if (from < 1 || from >= MAX)
        {
            return -1;
        }
        final List<Double> values = parse(costs);
        return from - 1 < values.size() ? Math.max(0, Math.round(values.get(from - 1))) : -1;
    }

    /**
     * The value for this level from a list of MAX numbers ("0.10,0.20,..."), 0 when missing or unreadable.
     */
    public static double perLevel(final String values, final int level)
    {
        final List<Double> parsed = parse(values);
        return level >= 1 && level - 1 < parsed.size() ? parsed.get(level - 1) : 0;
    }

    private static List<Double> parse(final String text)
    {
        final List<Double> out = new ArrayList<>();
        for (final String part : text.split(","))
        {
            try
            {
                out.add(Double.parseDouble(part.trim()));
            }
            catch (final NumberFormatException e)
            {
                return out;
            }
        }
        return out;
    }
}
