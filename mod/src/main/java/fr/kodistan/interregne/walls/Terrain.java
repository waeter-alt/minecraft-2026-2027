package fr.kodistan.interregne.walls;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Reads the ground a wall stands on. Trees, grass and flowers are not ground: the wall clears them. Anything else
 * that is not natural terrain (a hut, a path of planks, a farm, a fence) blocks the wall there.
 */
public final class Terrain
{
    /**
     * One column of terrain.
     *
     * @param ground  y of the highest ground block (water surface on water)
     * @param blocked a building or a player's block stands there
     * @param water   the ground is water: the wall will stand on piles
     * @param loaded  false when the chunk is not loaded: nothing is known
     */
    /**
     * @param road the cell is in the walkable core of a trade road: only a gate's passage may stand there
     */
    public record Probe(int ground, boolean blocked, boolean water, boolean loaded, boolean road)
    {
        public static final Probe UNKNOWN = new Probe(0, true, false, false);

        public Probe(final int ground, final boolean blocked, final boolean water, final boolean loaded)
        {
            this(ground, blocked, water, loaded, false);
        }
    }

    private static final int TREE_HEIGHT = 32;

    private Terrain()
    {
    }

    public static Probe probe(final LevelReader level, final int x, final int z)
    {
        return probe(level, x, z, null);
    }

    /**
     * Same, seeing through the blocks ignore accepts as well (the colony's own wall, when it is being retraced).
     */
    public static Probe probe(final LevelReader level, final int x, final int z, @Nullable final Predicate<BlockState> ignore)
    {
        if (!level.hasChunk(x >> 4, z >> 4))
        {
            return Probe.UNKNOWN;
        }
        final int min = level.getMinBuildHeight();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > min)
        {
            pos.setY(y);
            final BlockState state = level.getBlockState(pos);
            if (!clearable(level, pos, state) && (ignore == null || !ignore.test(state)))
            {
                break;
            }
            y--;
        }
        pos.setY(y);
        final BlockState ground = level.getBlockState(pos);
        final boolean water = ground.getBlock() instanceof LiquidBlock;
        return new Probe(y, !water && !natural(ground), water, true);
    }

    /**
     * Natural ground: soil, sand, stone, ice, snow, ores, fluids.
     */
    public static boolean natural(final BlockState state)
    {
        return state.getBlock() instanceof LiquidBlock
                 || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                 || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.ICE)
                 || state.is(BlockTags.SNOW) || state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.GRAVELS)
                 || state.is(Tags.Blocks.SANDSTONE_BLOCKS) || state.is(Tags.Blocks.STONES)
                 || state.is(Blocks.CLAY) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK)
                 || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    /**
     * What the wall clears on its way: air, grass, flowers, saplings, natural trees (logs under natural leaves, never
     * leaves a player placed).
     */
    public static boolean clearable(final LevelReader level, final BlockPos pos, final BlockState state)
    {
        return clearable(state, () -> treeLog(level, pos));
    }

    /**
     * Same, the caller saying how to tell whether a log belongs to a natural tree (only asked for a log).
     */
    public static boolean clearable(final BlockState state, final BooleanSupplier naturalLog)
    {
        if (state.isAir())
        {
            return true;
        }
        if (state.getBlock() instanceof LiquidBlock)
        {
            return false;
        }
        if (state.getBlock() instanceof LeavesBlock)
        {
            return !state.getValue(LeavesBlock.PERSISTENT);
        }
        if (state.is(BlockTags.LOGS))
        {
            return naturalLog.getAsBoolean();
        }
        return state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                 || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                 || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM)
                 || state.is(Blocks.BEE_NEST) || state.is(Blocks.SNOW);
    }

    /**
     * A log belongs to a natural tree when natural (non persistent) leaves grow around the top of its trunk.
     */
    public static boolean treeLog(final LevelReader level, final BlockPos pos)
    {
        return treeLog(level::getBlockState, pos);
    }

    /**
     * Same, reading blocks through the given function; a block it cannot tell (null) counts as natural leaves, so
     * a tree at the edge of what is known is still a tree.
     */
    public static boolean treeLog(final Function<BlockPos, BlockState> blocks, final BlockPos pos)
    {
        final BlockPos.MutableBlockPos cursor = pos.mutable();
        int climbed = 0;
        while (climbed < TREE_HEIGHT)
        {
            final BlockState above = blocks.apply(cursor.above());
            if (above == null || !above.is(BlockTags.LOGS))
            {
                break;
            }
            cursor.move(0, 1, 0);
            climbed++;
        }
        final BlockPos top = cursor.immutable();
        for (final BlockPos near : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2)))
        {
            final BlockState state = blocks.apply(near);
            if (state == null || (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)))
            {
                return true;
            }
        }
        return false;
    }
}
