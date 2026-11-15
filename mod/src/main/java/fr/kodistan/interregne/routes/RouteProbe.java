package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.routes.plan.HeightField;
import fr.kodistan.interregne.walls.Terrain;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Reads one column of terrain the way a route sees it: where the ground is once trees and plants are set aside,
 * whether it is water (and how deep), whether a player built there. The survey fills a {@link HeightField} with it,
 * the builder reads the live world with it, so both agree on what "the ground" is.
 */
public final class RouteProbe
{
    /**
     * @param ground y of the highest ground block; the surface block on water, lava or ice
     * @param bed    y of the first solid block under the water (same as ground on dry land)
     * @param flags  {@link HeightField#WATER}, {@link HeightField#LAVA}, {@link HeightField#ICE},
     *               {@link HeightField#BLOCKED}
     */
    public record Column(int ground, int bed, int flags)
    {
        public boolean wet()
        {
            return (flags & (HeightField.WATER | HeightField.ICE)) != 0;
        }

        public boolean lava()
        {
            return (flags & HeightField.LAVA) != 0;
        }

        public boolean blocked()
        {
            return (flags & HeightField.BLOCKED) != 0;
        }

        /** Where a pile or an embankment under this column stands. */
        public int base()
        {
            return bed;
        }
    }

    /**
     * The 256 columns of one chunk, x + 16 z.
     */
    public record Columns(short[] ground, short[] bed, byte[] flags)
    {
        void copyTo(final HeightField field, final int chunkX, final int chunkZ)
        {
            for (int i = 0; i < 256; i++)
            {
                final int x = (chunkX << 4) + (i & 15);
                final int z = (chunkZ << 4) + (i >> 4);
                if (field.inside(x, z))
                {
                    field.set(x, z, ground[i], bed[i], flags[i]);
                }
            }
        }
    }

    private RouteProbe()
    {
    }

    /**
     * A column of the live world; its chunk gets loaded if it is not.
     */
    public static Column of(final ServerLevel level, final int x, final int z)
    {
        final ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
        return scan(level::getBlockState, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15), level.getMinBuildHeight(), x, z);
    }

    /**
     * Every column of a loaded chunk, without ever loading a neighbour: a tree leaning over the chunk border is
     * judged on what is loaded, and taken for a natural tree when that is not enough to tell.
     */
    public static Columns sample(final ServerLevel level, final ChunkAccess chunk)
    {
        final int cx = chunk.getPos().x;
        final int cz = chunk.getPos().z;
        final Function<BlockPos, BlockState> blocks = pos -> {
            if (pos.getX() >> 4 == cx && pos.getZ() >> 4 == cz)
            {
                return chunk.getBlockState(pos);
            }
            final LevelChunk other = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return other == null ? null : other.getBlockState(pos);
        };
        final Columns columns = new Columns(new short[256], new short[256], new byte[256]);
        final int min = level.getMinBuildHeight();
        for (int i = 0; i < 256; i++)
        {
            store(columns, i, scan(blocks, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, i & 15, i >> 4), min, (cx << 4) + (i & 15), (cz << 4) + (i >> 4)));
        }
        return columns;
    }

    /**
     * Every column of a chunk straight from its saved data, without loading it into the world — safe on any
     * thread, and some ten times cheaper than a load. Null when the data is not a fully generated chunk of this
     * version of the game: that one must be loaded (or generated) for real.
     *
     * @param tag    the chunk as stored in its region file
     * @param blocks the block registry, to read the palettes
     */
    @Nullable
    public static Columns read(@Nullable final CompoundTag tag, final HolderGetter<Block> blocks, final int minY, final int height)
    {
        if (tag == null || !"minecraft:full".equals(tag.getString("Status"))
              || tag.getInt("DataVersion") != SharedConstants.getCurrentVersion().getDataVersion().getVersion())
        {
            return null;
        }
        final int cx = tag.getInt("xPos");
        final int cz = tag.getInt("zPos");
        final int sections = height >> 4;
        final BlockState[][] palettes = new BlockState[sections][];
        final long[][] data = new long[sections][];
        final ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        int highest = -1;
        for (int s = 0; s < list.size(); s++)
        {
            final CompoundTag section = list.getCompound(s);
            final int index = section.getByte("Y") - (minY >> 4);
            if (index < 0 || index >= sections || !section.contains("block_states", Tag.TAG_COMPOUND))
            {
                continue;
            }
            final CompoundTag states = section.getCompound("block_states");
            final ListTag palette = states.getList("palette", Tag.TAG_COMPOUND);
            final BlockState[] entries = new BlockState[palette.size()];
            boolean empty = true;
            for (int p = 0; p < entries.length; p++)
            {
                entries[p] = NbtUtils.readBlockState(blocks, palette.getCompound(p));
                empty &= entries[p].isAir();
            }
            if (entries.length == 0 || empty)
            {
                continue;
            }
            palettes[index] = entries;
            data[index] = entries.length > 1 ? states.getLongArray("data") : null;
            highest = Math.max(highest, index);
        }
        final BlockState air = Blocks.AIR.defaultBlockState();
        final Function<BlockPos, BlockState> reader = pos -> {
            if (pos.getX() >> 4 != cx || pos.getZ() >> 4 != cz)
            {
                return null;
            }
            final int index = (pos.getY() - minY) >> 4;
            if (index < 0 || index >= sections || palettes[index] == null)
            {
                return air;
            }
            final BlockState[] palette = palettes[index];
            final long[] packed = data[index];
            if (packed == null || packed.length == 0)
            {
                return palette[0];
            }
            final int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            final int perLong = 64 / bits;
            final int cell = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
            final int id = (int) ((packed[cell / perLong] >>> ((cell % perLong) * bits)) & ((1L << bits) - 1));
            return id < palette.length ? palette[id] : air;
        };
        final Columns columns = new Columns(new short[256], new short[256], new byte[256]);
        final int top = highest < 0 ? minY : minY + (highest << 4) + 15;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++)
        {
            final int x = (cx << 4) + (i & 15);
            final int z = (cz << 4) + (i >> 4);
            // Down to the first block that is not air: where the saved height map would point, without trusting it
            int y = top;
            while (y > minY && reader.apply(pos.set(x, y, z)).isAir())
            {
                y--;
            }
            store(columns, i, scan(reader, y, minY, x, z));
        }
        return columns;
    }

    private static void store(final Columns columns, final int i, final Column column)
    {
        columns.ground()[i] = (short) column.ground();
        columns.bed()[i] = (short) column.bed();
        columns.flags()[i] = (byte) column.flags();
    }

    private static Column scan(final Function<BlockPos, BlockState> blocks, final int top, final int min, final int x, final int z)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        int y = top;
        BlockState state = blocks.apply(pos);
        while (y > min && state.getFluidState().isEmpty() && vegetation(blocks, pos, state))
        {
            pos.setY(--y);
            state = blocks.apply(pos);
        }
        final boolean fluid = !state.getFluidState().isEmpty();
        if (!fluid && !state.is(BlockTags.ICE))
        {
            return new Column(y, y, Terrain.natural(state) ? 0 : HeightField.BLOCKED);
        }
        final int flag = !fluid ? HeightField.ICE : state.getFluidState().is(FluidTags.WATER) ? HeightField.WATER : HeightField.LAVA;
        final int surface = y;
        while (y > min && (!state.getFluidState().isEmpty() || state.is(BlockTags.ICE) || vegetation(blocks, pos, state)))
        {
            pos.setY(--y);
            state = blocks.apply(pos);
        }
        return new Column(surface, y, flag);
    }

    private static boolean vegetation(final Function<BlockPos, BlockState> blocks, final BlockPos pos, final BlockState state)
    {
        return Terrain.clearable(state, () -> Terrain.treeLog(blocks, pos));
    }
}
