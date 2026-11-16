package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.routes.plan.HeightField;
import fr.kodistan.interregne.routes.plan.RoutePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import fr.kodistan.interregne.network.RoadLinesPayload;
import fr.kodistan.interregne.walls.WallRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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
    private record Road(int[] x, int[] y, int[] z, int colonyA, int gateA, int colonyB, int gateB)
    {
    }

    private final Set<Long> posts = new HashSet<>();
    private final Set<Pair> links = new HashSet<>();
    private final List<Road> roads = new ArrayList<>();
    /** The cells of every road's centre line, rebuilt when a road is added or changed. */
    private Set<Long> centres;

    /**
     * One end of a road hooked to a colony.
     *
     * @param road  the road's index
     * @param first the road's first cell (its A end), else its last
     */
    public record Hook(int road, boolean first, BlockPos at, int gate)
    {
    }

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
    public void record(final RoutePlan plan, @Nullable final RouteEnds.End endA, @Nullable final RouteEnds.End endB)
    {
        int count = 0;
        for (int i = 0; i < plan.length(); i++)
        {
            count += plan.mode[i] == RoutePlan.TUNNEL ? 0 : 1;
        }
        final Road road = new Road(new int[count], new int[count], new int[count], endA == null ? 0 : endA.colony(), endA == null ? -1 : endA.gate(),
          endB == null ? 0 : endB.colony(), endB == null ? -1 : endB.gate());
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
        centres = null;
        setDirty();
    }

    /**
     * Every road end hooked to this colony.
     */
    public List<Hook> hooks(final int colony)
    {
        final List<Hook> out = new ArrayList<>();
        for (int i = 0; i < roads.size(); i++)
        {
            final Road road = roads.get(i);
            final int last = road.x().length - 1;
            if (last < 0)
            {
                continue;
            }
            if (road.colonyA() == colony)
            {
                out.add(new Hook(i, true, new BlockPos(road.x()[0], road.y()[0], road.z()[0]), road.gateA()));
            }
            if (road.colonyB() == colony)
            {
                out.add(new Hook(i, false, new BlockPos(road.x()[last], road.y()[last], road.z()[last]), road.gateB()));
            }
        }
        return out;
    }

    /**
     * Moves a road end to another gate of the same colony, or unhooks it (colony 0) once a connector has taken over.
     */
    public void rehook(final Hook hook, final int colony, final int gate)
    {
        final Road road = roads.get(hook.road());
        roads.set(hook.road(), hook.first() ? new Road(road.x(), road.y(), road.z(), colony, gate, road.colonyB(), road.gateB())
                                            : new Road(road.x(), road.y(), road.z(), road.colonyA(), road.gateA(), colony, gate));
        setDirty();
    }

    /**
     * The centre-line cells of a road, as {x, z} pairs.
     */
    public int[][] cells(final int road)
    {
        final Road r = roads.get(road);
        return new int[][] {r.x(), r.z()};
    }

    /**
     * Whether a road's centre line passes through this cell ({@link WallRegistry#cell}).
     */
    public boolean onRoad(final long cell)
    {
        if (centres == null)
        {
            final Set<Long> next = new HashSet<>();
            for (final Road road : roads)
            {
                for (int i = 0; i < road.x().length; i++)
                {
                    next.add(WallRegistry.cell(road.x()[i], road.z()[i]));
                }
            }
            centres = next;
        }
        return centres.contains(cell);
    }

    private RoadLinesPayload payload()
    {
        final List<Integer> data = new ArrayList<>();
        for (final Road road : roads)
        {
            data.add(road.colonyA());
            data.add(road.colonyB());
            data.add(road.x().length);
            for (int i = 0; i < road.x().length; i++)
            {
                data.add(road.x()[i]);
                data.add(road.z()[i]);
            }
        }
        return new RoadLinesPayload(data);
    }

    /**
     * Tells every client where the roads run, for the wall planner.
     */
    public void broadcast()
    {
        PacketDistributor.sendToAllPlayers(payload());
    }

    public void send(final ServerPlayer player)
    {
        PacketDistributor.sendToPlayer(player, payload());
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
            registry.roads.add(new Road(road.getIntArray("x"), road.getIntArray("y"), road.getIntArray("z"), road.getInt("colonyA"),
              road.contains("gateA") ? road.getInt("gateA") : -1, road.getInt("colonyB"), road.contains("gateB") ? road.getInt("gateB") : -1));
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
            entry.putInt("colonyA", road.colonyA());
            entry.putInt("gateA", road.gateA());
            entry.putInt("colonyB", road.colonyB());
            entry.putInt("gateB", road.gateB());
            list.add(entry);
        }
        tag.put("roads", list);
        return tag;
    }
}
