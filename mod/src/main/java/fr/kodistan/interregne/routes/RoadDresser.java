package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Raises a built road one level ({@link RoadLevels}), in place: every block of the road's own palette becomes the same
 * role one level up, the outer columns on the ground become a curb, the old lampposts come down and new ones go up,
 * closer together. Only blocks of the road's palette are swapped, and lampposts only go into air under the open sky,
 * so what a player built beside the road stays as it is; cells on a colony's land are skipped altogether.
 * <p>
 * Each column across the road belongs to the centre-line cell nearest it, so a column is dressed once even where the
 * road turns. A road that has been dressed before is read with the palette of its current level.
 */
public final class RoadDresser implements RouteWork
{
    private static final int REACH = 5;
    private static final int ROAD = 12;
    private static final int EDGE = 9;
    private static final int TUNNEL_WALLS = 26;
    private static final int MAX_PIER = 46;
    private static final int LAMP_OFFSET = 3;

    private final ServerLevel level;
    private final BlockPos[] path;
    private final int from;
    /** Column (x, z packed) -> d2 to its nearest cell << 32 | that cell's index. */
    private final Map<Long, Long> owner = new HashMap<>();

    private final Map<Block, Block> next = new HashMap<>();
    private final Set<Block> surfaceOnly = new HashSet<>();
    private final Set<Block> palette = new HashSet<>();
    private final Set<Block> deck = new HashSet<>();
    private final Set<Block> masonry = new HashSet<>();
    private final Set<Block> ground = new HashSet<>();
    private final Block timber;
    @Nullable
    private final Block post;
    @Nullable
    private final Block newPost;
    private final Block curb;
    private final int postHeight;
    private final int lampEvery;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /**
     * @param from the road's level now: it is dressed to from + 1
     */
    public RoadDresser(final ServerLevel level, final BlockPos[] path, final int from)
    {
        this.level = level;
        this.path = path;
        this.from = from;
        for (final Map.Entry<String, String> entry : RoadLevels.next(from).entrySet())
        {
            next.put(block(entry.getKey()), block(entry.getValue()));
        }
        for (final String id : RoadLevels.surfaceOnly(from))
        {
            surfaceOnly.add(block(id));
        }
        for (final RoadLevels.Role role : RoadLevels.Role.values())
        {
            palette.add(block(role.id(from)));
        }
        for (final RoadLevels.Role role : List.of(RoadLevels.Role.TIMBER, RoadLevels.Role.PLANKS, RoadLevels.Role.DECK_STEP))
        {
            deck.add(block(role.id(from)));
        }
        for (final RoadLevels.Role role : List.of(RoadLevels.Role.MASONRY, RoadLevels.Role.MASONRY_MOSSY, RoadLevels.Role.MASONRY_CRACKED,
          RoadLevels.Role.KEYSTONE))
        {
            masonry.add(block(role.id(from)));
        }
        for (final String id : RoadLevels.groundIds())
        {
            ground.add(block(id));
        }
        ground.add(Blocks.GRASS_BLOCK);
        ground.add(Blocks.DIRT);
        timber = block(RoadLevels.Role.TIMBER.id(from));
        post = RoadLevels.post(from) == null ? null : block(RoadLevels.post(from));
        newPost = block(RoadLevels.post(from + 1));
        curb = block(RoadLevels.curb(from + 1));
        postHeight = RoadLevels.postHeight(from + 1);
        lampEvery = RoadLevels.lampEvery(from + 1);
        for (int i = 0; i < path.length; i++)
        {
            final BlockPos cell = path[i];
            for (int dz = -REACH; dz <= REACH; dz++)
            {
                for (int dx = -REACH; dx <= REACH; dx++)
                {
                    final long d2 = dx * dx + dz * dz;
                    if (d2 <= TUNNEL_WALLS)
                    {
                        owner.merge(BlockPos.asLong(cell.getX() + dx, 0, cell.getZ() + dz), d2 << 32 | i, Math::min);
                    }
                }
            }
        }
    }

    private static Block block(final String id)
    {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(id));
    }

    @Override
    public int cells()
    {
        return path.length;
    }

    @Override
    public BlockPos cell(final int i)
    {
        return path[i];
    }

    @Override
    public List<RouteStep> slice(final int i)
    {
        final List<RouteStep> out = new ArrayList<>();
        final BlockPos centre = path[i];
        if (IColonyManager.getInstance().getColonyByPosFromWorld(level, centre) != null)
        {
            return out;
        }
        final boolean tunnel = tunnel(i) || i > 0 && tunnel(i - 1) || i + 1 < path.length && tunnel(i + 1);
        final int cy = centre.getY();
        for (int dz = -REACH; dz <= REACH; dz++)
        {
            for (int dx = -REACH; dx <= REACH; dx++)
            {
                final int x = centre.getX() + dx;
                final int z = centre.getZ() + dz;
                final Long own = owner.get(BlockPos.asLong(x, 0, z));
                if (own == null || (int) (long) own != i)
                {
                    continue;
                }
                final int d2 = (int) (own >>> 32);
                if (d2 <= ROAD)
                {
                    column(out, x, z, cy, tunnel, d2 >= EDGE);
                }
                else if (tunnel)
                {
                    for (int y = cy - 1; y <= cy + 8; y++)
                    {
                        final BlockState state = level.getBlockState(cursor.set(x, y, z));
                        if (masonry.contains(state.getBlock()) && next.containsKey(state.getBlock()))
                        {
                            swap(out, x, y, z, state);
                        }
                    }
                }
            }
        }
        if (!tunnel)
        {
            lamp(out, i);
        }
        return out;
    }

    /**
     * Whether this cell runs under a roof the road builder put up: a bore's masonry or frames over the centre line.
     */
    private boolean tunnel(final int i)
    {
        final BlockPos centre = path[i];
        for (int y = centre.getY() + 3; y <= centre.getY() + 7; y++)
        {
            if (masonry.contains(level.getBlockState(cursor.set(centre.getX(), y, centre.getZ())).getBlock()))
            {
                return true;
            }
        }
        return false;
    }

    private void column(final List<RouteStep> out, final int x, final int z, final int cy, final boolean tunnel, final boolean edge)
    {
        boolean onDeck = false;
        for (int y = cy - 1; y <= cy + 1; y++)
        {
            onDeck |= deck.contains(level.getBlockState(cursor.set(x, y, z)).getBlock());
        }
        final Set<Integer> handled = new HashSet<>();
        if (edge && !onDeck)
        {
            final int cap = cap(x, z, cy);
            if (cap != Integer.MIN_VALUE)
            {
                int y = cap + 1;
                boolean removed = false;
                while (post != null && level.getBlockState(cursor.set(x, y, z)).is(post))
                {
                    out.add(new RouteStep(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState()));
                    handled.add(y);
                    removed = true;
                    y++;
                }
                if (removed && level.getBlockState(cursor.set(x, y, z)).is(Blocks.LANTERN))
                {
                    out.add(new RouteStep(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState()));
                    handled.add(y);
                }
                final BlockState top = level.getBlockState(cursor.set(x, cap, z));
                final String id = BuiltInRegistries.BLOCK.getKey(top.getBlock()).getPath();
                final boolean wild = cap == cy && (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT))
                                       && !level.getBlockState(cursor.set(x, cap + 1, z)).blocksMotion();
                if (!tunnel && (RoadLevels.flatSurface(from, id) || wild))
                {
                    out.add(new RouteStep(new BlockPos(x, cap, z), curb.defaultBlockState()));
                    handled.add(cap);
                }
            }
        }
        final int top = tunnel ? cy + 8 : cy + 3;
        for (int y = cy - 2; y <= top; y++)
        {
            if (handled.contains(y))
            {
                continue;
            }
            final BlockState state = level.getBlockState(cursor.set(x, y, z));
            final Block block = state.getBlock();
            if (!next.containsKey(block))
            {
                continue;
            }
            if (surfaceOnly.contains(block)
                  && (y > cy + 2 || level.getBlockState(cursor.set(x, y + 1, z)).isCollisionShapeFullBlock(level, cursor)))
            {
                continue;
            }
            swap(out, x, y, z, state);
        }
        if (!onDeck)
        {
            return;
        }
        // Trestles and abutments down to the ground or the river bed
        for (int y = cy - 3; y >= cy - 3 - MAX_PIER; y--)
        {
            final BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (state.isAir() || state.is(Blocks.WATER))
            {
                continue;
            }
            if (!palette.contains(state.getBlock()))
            {
                break;
            }
            if (next.containsKey(state.getBlock()))
            {
                swap(out, x, y, z, state);
            }
        }
    }

    /**
     * The top of the road in this column: the highest block one cannot walk through, lampposts and lanterns aside.
     */
    private int cap(final int x, final int z, final int cy)
    {
        for (int y = cy + 2; y >= cy - 2; y--)
        {
            final BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (state.blocksMotion() && !state.is(Blocks.LANTERN) && (post == null || !state.is(post)) && !state.is(newPost))
            {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void swap(final List<RouteStep> out, final int x, final int y, final int z, final BlockState state)
    {
        out.add(new RouteStep(new BlockPos(x, y, z), convert(state, next.get(state.getBlock()))));
    }

    /**
     * The new block, turned and shaped like the old one wherever they share a property (stairs, logs, lanterns).
     */
    private static BlockState convert(final BlockState from, final Block to)
    {
        BlockState out = to.defaultBlockState();
        for (final Property<?> property : from.getProperties())
        {
            if (out.hasProperty(property))
            {
                out = copy(from, out, property);
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(final BlockState from, final BlockState to, final Property<T> property)
    {
        return to.setValue(property, from.getValue(property));
    }

    /**
     * A lamppost beside every few cells, alternating sides, on the road's edge: only on the ground under the open sky
     * and into air, never on a bridge.
     */
    private void lamp(final List<RouteStep> out, final int i)
    {
        if (lampEvery <= 0 || i == 0 || i + 1 >= path.length || i % lampEvery != 0)
        {
            return;
        }
        final BlockPos centre = path[i];
        final int sx = path[i + 1].getX() - path[i - 1].getX();
        final int sz = path[i + 1].getZ() - path[i - 1].getZ();
        final int side = (i / lampEvery) % 2 == 0 ? 1 : -1;
        final int x = centre.getX() + (Math.abs(sx) >= Math.abs(sz) ? 0 : side * LAMP_OFFSET);
        final int z = centre.getZ() + (Math.abs(sx) >= Math.abs(sz) ? side * LAMP_OFFSET : 0);
        final int cy = centre.getY();
        for (int y = cy - 1; y <= cy + 1; y++)
        {
            final BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (deck.contains(state.getBlock()) || state.is(timber))
            {
                return;
            }
        }
        final int cap = cap(x, z, cy);
        if (cap == Integer.MIN_VALUE || !ground.contains(level.getBlockState(cursor.set(x, cap, z)).getBlock()))
        {
            return;
        }
        for (int y = cap + 1; y <= cap + postHeight + 1; y++)
        {
            if (!level.getBlockState(cursor.set(x, y, z)).isAir())
            {
                return;
            }
        }
        if (!level.canSeeSky(new BlockPos(x, cap + postHeight + 1, z)))
        {
            return;
        }
        for (int y = cap + 1; y <= cap + postHeight; y++)
        {
            out.add(new RouteStep(new BlockPos(x, y, z), newPost.defaultBlockState()));
        }
        out.add(new RouteStep(new BlockPos(x, cap + postHeight + 1, z), Blocks.LANTERN.defaultBlockState()));
    }
}
