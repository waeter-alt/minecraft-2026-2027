package fr.kodistan.interregne.routes;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * What a road looks like at each of its five levels, and as a war road: its surface, its stone, its timber. The
 * shape of the road (where it runs, cuts, bridges, bores) never changes with the level, only what {@link RouteBuilder}
 * lays along it. Validated from the concept renders of 2026-11-26 (mod/tools/render_routes_concept.py).
 */
public enum RoadStyle
{
    /** A trodden path three wide in the middle of the corridor, a log bridge on piles, a propped bore. */
    PATH,
    /** Cobbles and gravel five wide, wooden lantern posts, a plank bridge on stone piers. */
    GRAVEL,
    /** Stone setts with a curb, stone lampposts and milestones, a two-arch stone bridge, a vaulted portal. */
    PAVED,
    /** Worn setts seven wide, dry-stone walls, wooden gallows lanterns, a bridge with refuges, a guardhouse portal. */
    ROYAL,
    /** Great slabs, a quartz colonnade and hedges, a bridge with statues, a portal with a pediment. */
    IMPERIAL,
    /** Trampled deepslate, soul lanterns on gallows, stake palisades, a crenellated bridge, a portcullis. */
    WAR;

    public static RoadStyle of(final int level)
    {
        return switch (Math.max(1, Math.min(5, level)))
        {
            case 1 -> PATH;
            case 2 -> GRAVEL;
            case 3 -> PAVED;
            case 4 -> ROYAL;
            default -> IMPERIAL;
        };
    }

    public boolean timberBridge()
    {
        return this == PATH || this == GRAVEL;
    }

    /**
     * The road's top block, side columns from the centre line (0 to 3), d2 its squared distance; null leaves the
     * ground as it is (the edge of a narrow road, so it is not a ruled line).
     *
     * @param band true on a cell where a stone band crosses the road (the imperial road's great slabs)
     */
    @Nullable
    public BlockState surface(final int x, final int z, final int d2, final int side, final boolean band)
    {
        final float v = noise(x, z, 1);
        switch (this)
        {
            case PATH ->
            {
                if (d2 <= 2)
                {
                    return v < 0.78F ? state(Blocks.DIRT_PATH) : state(Blocks.COARSE_DIRT);
                }
                return d2 <= 8 && v < 0.25F ? state(Blocks.COARSE_DIRT) : null;
            }
            case GRAVEL ->
            {
                if (d2 <= 4)
                {
                    return v < 0.15F ? state(Blocks.MOSSY_COBBLESTONE) : v < 0.30F ? state(Blocks.GRAVEL) : state(Blocks.COBBLESTONE);
                }
                if (d2 <= 8)
                {
                    return v < 0.55F ? state(Blocks.GRAVEL) : state(Blocks.COARSE_DIRT);
                }
                return v < 0.25F ? state(Blocks.GRAVEL) : v < 0.4F ? state(Blocks.COARSE_DIRT) : null;
            }
            case PAVED ->
            {
                if (side >= 3)
                {
                    return state(Blocks.STONE_BRICKS);
                }
                return v < 0.08F ? state(Blocks.CRACKED_STONE_BRICKS) : ((x + z) & 1) == 0 ? state(Blocks.STONE_BRICKS) : state(Blocks.ANDESITE);
            }
            case ROYAL ->
            {
                if (side >= 3)
                {
                    return v < 0.7F ? state(Blocks.COBBLESTONE) : state(Blocks.MOSSY_COBBLESTONE);
                }
                return v < 0.15F ? state(Blocks.MOSSY_STONE_BRICKS) : v < 0.27F ? state(Blocks.CRACKED_STONE_BRICKS)
                                                                            : v < 0.4F ? state(Blocks.POLISHED_ANDESITE) : state(Blocks.STONE_BRICKS);
            }
            case IMPERIAL ->
            {
                if (side >= 3 || band)
                {
                    return state(Blocks.POLISHED_DEEPSLATE);
                }
                return state(Blocks.SMOOTH_STONE);
            }
            default ->
            {
                if (side == 0)
                {
                    return v < 0.2F ? state(Blocks.DEEPSLATE_TILES) : state(Blocks.COBBLED_DEEPSLATE);
                }
                if (side == 1)
                {
                    return v < 0.4F ? state(Blocks.COARSE_DIRT) : state(Blocks.COBBLED_DEEPSLATE);
                }
                if (side == 2)
                {
                    return v < 0.25F ? state(Blocks.SOUL_SOIL) : v < 0.5F ? state(Blocks.COARSE_DIRT) : state(Blocks.COBBLED_DEEPSLATE);
                }
                return v < 0.3F ? state(Blocks.SOUL_SOIL) : v < 0.5F ? state(Blocks.COARSE_DIRT) : null;
            }
        }
    }

    /** The surface laid where the ground must be filled under a column the style leaves bare. */
    public BlockState verge()
    {
        return this == WAR ? state(Blocks.COARSE_DIRT) : state(Blocks.GRASS_BLOCK);
    }

    /** Floor of a bore, and of a landing between two steps: never bare. */
    public BlockState paving(final int x, final int z, final int side)
    {
        final BlockState top = surface(x, z, side * side, side, false);
        if (top != null && !top.is(Blocks.GRAVEL))
        {
            return top;
        }
        return switch (this)
        {
            case PATH -> state(Blocks.DIRT_PATH);
            case GRAVEL -> noise(x, z, 3) < 0.25F ? state(Blocks.MOSSY_COBBLESTONE) : state(Blocks.COBBLESTONE);
            case WAR -> state(Blocks.COBBLED_DEEPSLATE);
            default -> state(Blocks.STONE_BRICKS);
        };
    }

    public BlockState step(final int x, final int z)
    {
        final boolean worn = noise(x, z, 3) < 0.25F;
        return switch (this)
        {
            case PATH, GRAVEL -> state(worn ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.COBBLESTONE_STAIRS);
            case PAVED, ROYAL -> state(worn ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS);
            case IMPERIAL -> state(Blocks.STONE_STAIRS);
            default -> state(Blocks.COBBLED_DEEPSLATE_STAIRS);
        };
    }

    /** Stone of abutments, piers, arches and bore linings. */
    public BlockState masonry(final int x, final int y, final int z)
    {
        final float v = noise(x * 31 + y, z, 4);
        return switch (this)
        {
            case PATH, GRAVEL -> v < 0.25F ? state(Blocks.MOSSY_COBBLESTONE) : state(Blocks.COBBLESTONE);
            case WAR -> v < 0.15F ? state(Blocks.CRACKED_DEEPSLATE_BRICKS) : state(Blocks.DEEPSLATE_BRICKS);
            default -> v < 0.12F ? state(Blocks.MOSSY_STONE_BRICKS) : v < 0.20F ? state(Blocks.CRACKED_STONE_BRICKS) : state(Blocks.STONE_BRICKS);
        };
    }

    /** The dressed stone of a bridge's face and parapet, and of a portal's facade. */
    public BlockState dressed(final int x, final int y, final int z)
    {
        return switch (this)
        {
            case IMPERIAL -> state(Blocks.QUARTZ_BLOCK);
            case WAR -> state(Blocks.POLISHED_BLACKSTONE_BRICKS);
            default -> masonry(x, y, z);
        };
    }

    public BlockState keystone()
    {
        return switch (this)
        {
            case IMPERIAL -> state(Blocks.CHISELED_QUARTZ_BLOCK);
            case WAR -> state(Blocks.CHISELED_POLISHED_BLACKSTONE);
            default -> state(Blocks.CHISELED_STONE_BRICKS);
        };
    }

    public BlockState timber()
    {
        return this == GRAVEL ? state(Blocks.DARK_OAK_LOG) : state(Blocks.SPRUCE_LOG);
    }

    public BlockState planks()
    {
        return this == GRAVEL ? state(Blocks.DARK_OAK_PLANKS) : state(Blocks.SPRUCE_PLANKS);
    }

    public BlockState deckStep()
    {
        return this == GRAVEL ? state(Blocks.DARK_OAK_STAIRS) : state(Blocks.SPRUCE_STAIRS);
    }

    public BlockState rail()
    {
        return this == GRAVEL ? state(Blocks.DARK_OAK_FENCE) : state(Blocks.SPRUCE_FENCE);
    }

    public BlockState lantern()
    {
        return this == WAR ? state(Blocks.SOUL_LANTERN) : state(Blocks.LANTERN);
    }

    private static final Set<Block> OURS = new HashSet<>();

    static
    {
        for (final Block b : new Block[] {
          Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS,
          Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE,
          Blocks.SMOOTH_STONE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.COBBLED_DEEPSLATE, Blocks.SOUL_SOIL,
          Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.CHISELED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE,
          Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_WALL,
          Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ,
          Blocks.SMOOTH_QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.IRON_BARS,
          Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
          Blocks.STONE_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.BLACKSTONE_STAIRS, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_SLAB,
          Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS,
          Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE, Blocks.SPRUCE_SLAB, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS,
          Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_SLAB, Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.SOUL_CAMPFIRE, Blocks.WHITE_TERRACOTTA,
          Blocks.GLASS_PANE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.RED_WALL_BANNER, Blocks.WHITE_WALL_BANNER,
          Blocks.OAK_LEAVES, Blocks.STONE_BRICK_SLAB, Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE_SLAB})
        {
            OURS.add(b);
        }
    }

    /**
     * Whether a block may have been laid by a road of any style: when a road is rebuilt one level up, these are its
     * own and are cleared or replaced, anything else standing on its corridor is someone's and is left alone.
     */
    public static boolean ours(final BlockState state)
    {
        return OURS.contains(state.getBlock());
    }

    static BlockState state(final Block block)
    {
        return block.defaultBlockState();
    }

    static float noise(final int x, final int z, final int salt)
    {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
