package fr.kodistan.interregne.citadel;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A weighted mix of material families picked with deterministic noise, so walls look laid by hand:
 * a per-block grain for brick variation, smooth patches for moss or copper patina.
 */
final class Palette
{
    /**
     * A full block with its matching stair, slab and wall shapes. Missing shapes fall back to the palette's first family.
     */
    record Family(Block full, @Nullable Block stair, @Nullable Block slab, @Nullable Block wall)
    {
    }

    private final long seed;
    private final List<Family> families = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();
    private double total;
    /** 0 = every block picked on its own, 1 = large smooth patches. */
    private double grain;
    @Nullable
    private Family moss;
    private double mossThreshold = 2;

    Palette(final long seed)
    {
        this.seed = seed;
    }

    Palette add(final double weight, final Block full, @Nullable final Block stair, @Nullable final Block slab, @Nullable final Block wall)
    {
        families.add(new Family(full, stair, slab, wall));
        weights.add(weight);
        total += weight;
        return this;
    }

    Palette grain(final double smoothness)
    {
        this.grain = smoothness;
        return this;
    }

    /**
     * Patches where this family replaces the mix, more often near the ground.
     */
    Palette moss(final Block full, final Block stair, final Block slab, @Nullable final Block wall, final double threshold)
    {
        this.moss = new Family(full, stair, slab, wall);
        this.mossThreshold = threshold;
        return this;
    }

    Family family(final int x, final int y, final int z)
    {
        if (moss != null)
        {
            final double patch = Noise.smooth(seed + 77, x, y, z, 5.0) - Math.min(0.25, Math.max(0, y) * 0.02);
            if (patch > mossThreshold && Noise.hash(seed + 78, x, y, z) < 0.75)
            {
                return moss;
            }
        }
        final double u = grain <= 0 ? Noise.hash(seed, x, y, z)
                            : Noise.stretch(Noise.smooth(seed + 13, x, y, z, 4.0)) * grain + Noise.hash(seed, x, y, z) * (1 - grain);
        double acc = 0;
        for (int i = 0; i < families.size(); i++)
        {
            acc += weights.get(i) / total;
            if (u < acc)
            {
                return families.get(i);
            }
        }
        return families.get(families.size() - 1);
    }

    BlockState full(final int x, final int y, final int z)
    {
        return family(x, y, z).full().defaultBlockState();
    }

    BlockState stair(final int x, final int y, final int z, final Direction facing, final boolean upsideDown)
    {
        final Family family = family(x, y, z);
        final Block block = family.stair() != null ? family.stair() : families.get(0).stair();
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
    }

    BlockState slab(final int x, final int y, final int z, final SlabType type)
    {
        final Family family = family(x, y, z);
        final Block block = family.slab() != null ? family.slab() : families.get(0).slab();
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    BlockState wall(final int x, final int y, final int z)
    {
        final Family family = family(x, y, z);
        final Block block = family.wall() != null ? family.wall() : families.get(0).wall();
        return block.defaultBlockState();
    }

    // ---- The citadel's palettes ----

    /** Ramparts and city stonework: stone bricks, cracked, andesite, tuff, moss near the ground. */
    static Palette rampart(final long seed)
    {
        return new Palette(seed)
                 .add(52, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL)
                 .add(14, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL)
                 .add(8, Blocks.ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB, Blocks.ANDESITE_WALL)
                 .add(6, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB, Blocks.ANDESITE_WALL)
                 .add(6, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL)
                 .add(7, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_WALL)
                 .add(4, Blocks.STONE, Blocks.STONE_STAIRS, Blocks.STONE_SLAB, null)
                 .moss(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_WALL, 0.62);
    }

    /** The palace: deepslate bricks and tiles, a few cracks. */
    static Palette deepslate(final long seed)
    {
        return new Palette(seed)
                 .add(45, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL)
                 .add(10, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL)
                 .add(25, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL)
                 .add(6, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL)
                 .add(14, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.POLISHED_DEEPSLATE_WALL);
    }

    /** Slate roofs of the palace and the towers. */
    static Palette slateRoof(final long seed)
    {
        return new Palette(seed)
                 .add(70, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL)
                 .add(18, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL)
                 .add(12, Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_WALL)
                 .grain(0.4);
    }

    /** Copper roofs in patches of patina, waxed so they keep their colour. */
    static Palette copperRoof(final long seed)
    {
        return new Palette(seed)
                 .add(22, Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_SLAB, null)
                 .add(28, Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, null)
                 .add(30, Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, null)
                 .add(20, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, null)
                 .grain(0.8);
    }

    /** Paved streets: a worn stone mix with gravel spots. */
    static Palette street(final long seed)
    {
        return new Palette(seed)
                 .add(26, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL)
                 .add(20, Blocks.COBBLESTONE, null, null, null)
                 .add(14, Blocks.ANDESITE, null, null, null)
                 .add(14, Blocks.POLISHED_ANDESITE, null, null, null)
                 .add(10, Blocks.CRACKED_STONE_BRICKS, null, null, null)
                 .add(6, Blocks.GRAVEL, null, null, null)
                 .add(6, Blocks.TUFF, null, null, null)
                 .add(4, Blocks.COBBLED_DEEPSLATE, null, null, null)
                 .moss(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL, 0.66);
    }

    /** Lower storeys of the houses: rough stone. */
    static Palette houseStone(final long seed)
    {
        return new Palette(seed)
                 .add(30, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL)
                 .add(28, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL)
                 .add(14, Blocks.ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB, Blocks.ANDESITE_WALL)
                 .add(16, Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_WALL)
                 .add(12, Blocks.TUFF, Blocks.TUFF_STAIRS, Blocks.TUFF_SLAB, Blocks.TUFF_WALL)
                 .moss(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL, 0.6);
    }

    /** Blackstone of the Porte des Profondeurs. */
    static Palette blackstone(final long seed)
    {
        return new Palette(seed)
                 .add(60, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
                   Blocks.POLISHED_BLACKSTONE_BRICK_WALL)
                 .add(25, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
                   Blocks.POLISHED_BLACKSTONE_BRICK_WALL)
                 .add(15, Blocks.BLACKSTONE, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL);
    }

    /**
     * Deterministic hash and value noise, the same for a given seed and position.
     */
    static final class Noise
    {
        private Noise()
        {
        }

        static double hash(final long seed, final int x, final int y, final int z)
        {
            long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + z * 0x27D4EB2F165667C5L;
            h ^= h >>> 31;
            h *= 0x7FB5D329728EA185L;
            h ^= h >>> 27;
            h *= 0x81DADEF4BC2DD44DL;
            h ^= h >>> 33;
            return (h >>> 11) * 0x1.0p-53;
        }

        /**
         * Trilinear value noise in [0, 1) with features about {@code scale} blocks wide.
         */
        static double smooth(final long seed, final int x, final int y, final int z, final double scale)
        {
            final double fx = x / scale;
            final double fy = y / scale;
            final double fz = z / scale;
            final int x0 = (int) Math.floor(fx);
            final int y0 = (int) Math.floor(fy);
            final int z0 = (int) Math.floor(fz);
            final double tx = fade(fx - x0);
            final double ty = fade(fy - y0);
            final double tz = fade(fz - z0);
            final double c00 = lerp(tx, hash(seed, x0, y0, z0), hash(seed, x0 + 1, y0, z0));
            final double c10 = lerp(tx, hash(seed, x0, y0 + 1, z0), hash(seed, x0 + 1, y0 + 1, z0));
            final double c01 = lerp(tx, hash(seed, x0, y0, z0 + 1), hash(seed, x0 + 1, y0, z0 + 1));
            final double c11 = lerp(tx, hash(seed, x0, y0 + 1, z0 + 1), hash(seed, x0 + 1, y0 + 1, z0 + 1));
            return lerp(tz, lerp(ty, c00, c10), lerp(ty, c01, c11));
        }

        /**
         * Spread smooth noise, which bunches around 0.5, back over [0, 1).
         */
        static double stretch(final double value)
        {
            return Math.min(0.999, Math.max(0, (value - 0.5) * 2.2 + 0.5));
        }

        private static double fade(final double t)
        {
            return t * t * (3 - 2 * t);
        }

        private static double lerp(final double t, final double a, final double b)
        {
            return a + (b - a) * t;
        }
    }
}
