package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.routes.plan.HeightField;
import fr.kodistan.interregne.routes.plan.RoutePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every Poste commercial standing in the world and the routes already built between them, saved with it. A test
 * fixture for the trade-route algorithm, not tied to colonies: any two posts can be linked, regardless of who owns
 * the land they stand on.
 */
public final class RouteRegistry extends SavedData
{
    private static final String NAME = "interregne_routes";
    /** Columns this close to a cell of a road's centre line are road (seven wide on a straight run). */
    private static final int ROAD_D2 = 12;
    private static final int ROAD_REACH = 3;

    /**
     * Two posts, whichever way round.
     */
    public record Pair(long low, long high)
    {
        public static Pair of(final BlockPos a, final BlockPos b)
        {
            final long x = a.asLong();
            final long y = b.asLong();
            return x < y ? new Pair(x, y) : new Pair(y, x);
        }
    }

    /**
     * A road already built: the cells of its centre line that one walks on in the open (ground and bridge decks;
     * a tunnel is left out, the ground above it is still ground).
     */
    private record Road(int[] x, int[] y, int[] z)
    {
    }

    private final Set<Long> posts = new HashSet<>();
    private final Set<Pair> links = new HashSet<>();
    private final List<Road> roads = new ArrayList<>();

    public static RouteRegistry get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RouteRegistry::new, RouteRegistry::load, null), NAME);
    }

    public void add(final BlockPos pos)
    {
        if (posts.add(pos.asLong()))
        {
            setDirty();
        }
    }

    /**
     * Forgets a post and the routes that ended on it (the roads themselves stay where they are).
     */
    public void remove(final BlockPos pos)
    {
        final long packed = pos.asLong();
        final boolean unlinked = links.removeIf(pair -> pair.low() == packed || pair.high() == packed);
        if (posts.remove(packed) || unlinked)
        {
            setDirty();
        }
    }

    public void link(final BlockPos a, final BlockPos b)
    {
        if (links.add(Pair.of(a, b)))
        {
            setDirty();
        }
    }

    /**
     * Remembers a route once built, so later ones can see it.
     */
    public void record(final RoutePlan plan)
    {
        int count = 0;
        for (int i = 0; i < plan.length(); i++)
        {
            count += plan.mode[i] == RoutePlan.TUNNEL ? 0 : 1;
        }
        final Road road = new Road(new int[count], new int[count], new int[count]);
        int at = 0;
        for (int i = 0; i < plan.length(); i++)
        {
            if (plan.mode[i] != RoutePlan.TUNNEL)
            {
                road.x()[at] = plan.x[i];
                road.y()[at] = plan.y[i];
                road.z()[at++] = plan.z[i];
            }
        }
        roads.add(road);
        setDirty();
    }

    /**
     * Draws the roads already built onto a surveyed relief: their columns become road at the level one walks on,
     * instead of the obstacle their steps and decks would otherwise be taken for. A new route can then leave a post
     * along the road that is already there, cross it, or share its bridge rather than build a second one beside it.
     */
    public void overlay(final HeightField field)
    {
        for (final Road road : roads)
        {
            for (int i = 0; i < road.x().length; i++)
            {
                for (int dz = -ROAD_REACH; dz <= ROAD_REACH; dz++)
                {
                    for (int dx = -ROAD_REACH; dx <= ROAD_REACH; dx++)
                    {
                        final int x = road.x()[i] + dx;
                        final int z = road.z()[i] + dz;
                        if (dx * dx + dz * dz <= ROAD_D2 && field.inside(x, z) && (field.flags(x, z) & HeightField.UNKNOWN) == 0)
                        {
                            field.set(x, z, road.y()[i], road.y()[i], HeightField.ROAD);
                        }
                    }
                }
            }
        }
    }

    /**
     * The closest other post no route links to this one yet, or null when there is none.
     */
    public BlockPos nearestUnlinked(final BlockPos from)
    {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (final long packed : posts)
        {
            final BlockPos candidate = BlockPos.of(packed);
            if (candidate.equals(from) || links.contains(Pair.of(from, candidate)))
            {
                continue;
            }
            final double distSq = from.distSqr(candidate);
            if (distSq < best)
            {
                best = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    public boolean hasOther(final BlockPos from)
    {
        return posts.size() > (posts.contains(from.asLong()) ? 1 : 0);
    }

    private static RouteRegistry load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final RouteRegistry registry = new RouteRegistry();
        for (final long packed : tag.getLongArray("posts"))
        {
            registry.posts.add(packed);
        }
        final ListTag roads = tag.getList("roads", Tag.TAG_COMPOUND);
        for (int i = 0; i < roads.size(); i++)
        {
            final CompoundTag road = roads.getCompound(i);
            registry.roads.add(new Road(road.getIntArray("x"), road.getIntArray("y"), road.getIntArray("z")));
        }
        final long[] low = tag.getLongArray("linksLow");
        final long[] high = tag.getLongArray("linksHigh");
        for (int i = 0; i < Math.min(low.length, high.length); i++)
        {
            registry.links.add(new Pair(low[i], high[i]));
        }
        return registry;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        tag.putLongArray("posts", posts.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("linksLow", links.stream().mapToLong(Pair::low).toArray());
        tag.putLongArray("linksHigh", links.stream().mapToLong(Pair::high).toArray());
        final ListTag list = new ListTag();
        for (final Road road : roads)
        {
            final CompoundTag entry = new CompoundTag();
            entry.putIntArray("x", road.x());
            entry.putIntArray("y", road.y());
            entry.putIntArray("z", road.z());
            list.add(entry);
        }
        tag.put("roads", list);
        return tag;
    }
}
