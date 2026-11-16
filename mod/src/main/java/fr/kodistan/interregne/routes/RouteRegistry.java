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

        public static Pair ofColonies(final int a, final int b)
        {
            return new Pair(Math.min(a, b), Math.max(a, b));
        }
    }

    /**
     * A road already built: the cells of its centre line that one walks on in the open (ground and bridge decks;
     * a tunnel is left out, the ground above it is still ground).
     */
    private record Road(int[] x, int[] y, int[] z, int colonyA, int gateA, int colonyB, int gateB, int pairA, int pairB, @Nullable Decay decay)
    {
        Road withEnds(final int a, final int gA, final int b, final int gB)
        {
            return new Road(x, y, z, a, gA, b, gB, pairA, pairB, decay);
        }
    }

    /**
     * A road vanishing after its alliance broke: the order its cells go in and which already went.
     */
    static final class Decay
    {
        final int[] order;
        final boolean[] gone;
        int remaining;

        Decay(final int[] order, final boolean[] gone)
        {
            this.order = order;
            this.gone = gone;
            int left = 0;
            for (final boolean g : gone)
            {
                left += g ? 0 : 1;
            }
            remaining = left;
        }
    }

    private final Set<Long> posts = new HashSet<>();
    private final Set<Pair> links = new HashSet<>();
    /** Colonies a road already joins, by colony id: a post looks for the nearest colony not in this set. */
    private final Set<Pair> colonyLinks = new HashSet<>();
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

    public void linkColonies(final int a, final int b)
    {
        if (colonyLinks.add(Pair.ofColonies(a, b)))
        {
            setDirty();
        }
    }

    /**
     * A post with no other colony to reach: its road goes to the colony nearest it, remembered per post.
     */
    public void linkPost(final int colony, final BlockPos post)
    {
        if (colonyLinks.add(new Pair(-1L - colony, post.asLong())))
        {
            setDirty();
        }
    }

    public boolean isPostLinked(final int colony, final BlockPos post)
    {
        return colonyLinks.contains(new Pair(-1L - colony, post.asLong()));
    }

    public boolean areLinked(final int a, final int b)
    {
        return colonyLinks.contains(Pair.ofColonies(a, b));
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
        final int colA = endA == null ? 0 : endA.colony();
        final int colB = endB == null ? 0 : endB.colony();
        final Road road = new Road(new int[count], new int[count], new int[count], colA, endA == null ? -1 : endA.gate(),
          colB, endB == null ? -1 : endB.gate(), colA, colB, null);
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
        roads.set(hook.road(), hook.first() ? road.withEnds(colony, gate, road.colonyB(), road.gateB())
                                            : road.withEnds(road.colonyA(), road.gateA(), colony, gate));
        setDirty();
    }

    /**
     * The alliance between these two colonies is over: its link is forgotten (a new route may be made) and the roads
     * built for it start to vanish, their cells in random order.
     *
     * @return how many roads started to decay
     */
    public int beginDecay(final int a, final int b)
    {
        final boolean unlinked = colonyLinks.remove(Pair.ofColonies(a, b));
        int started = 0;
        final java.util.Random random = new java.util.Random();
        final int low = Math.min(a, b);
        final int high = Math.max(a, b);
        for (int i = 0; i < roads.size(); i++)
        {
            final Road road = roads.get(i);
            final boolean ours = Math.min(road.pairA(), road.pairB()) == low && Math.max(road.pairA(), road.pairB()) == high;
            if (!ours || road.decay() != null || road.x().length == 0)
            {
                continue;
            }
            final int n = road.x().length;
            final List<Integer> shuffled = new ArrayList<>(n);
            for (int k = 0; k < n; k++)
            {
                shuffled.add(k);
            }
            java.util.Collections.shuffle(shuffled, random);
            roads.set(i, new Road(road.x(), road.y(), road.z(), road.colonyA(), road.gateA(), road.colonyB(), road.gateB(), road.pairA(), road.pairB(),
              new Decay(shuffled.stream().mapToInt(Integer::intValue).toArray(), new boolean[n])));
            started++;
        }
        if (unlinked || started > 0)
        {
            centres = null;
            setDirty();
            broadcast();
        }
        return started;
    }

    /**
     * One step of every decaying road: a fraction of its cells go, among those whose chunk is loaded. A road with
     * nothing left becomes an empty one (its index stays).
     */
    void decayStep(final net.minecraft.server.level.ServerLevel level, final double fraction)
    {
        boolean finished = false;
        boolean any = false;
        for (int i = 0; i < roads.size(); i++)
        {
            final Road road = roads.get(i);
            final Decay decay = road.decay();
            if (decay == null)
            {
                continue;
            }
            any = true;
            final int budget = Math.max(1, (int) Math.ceil(road.x().length * fraction));
            int done = 0;
            for (int k = 0; k < decay.order.length && done < budget; k++)
            {
                final int cell = decay.order[k];
                if (decay.gone[cell])
                {
                    continue;
                }
                final BlockPos at = new BlockPos(road.x()[cell], road.y()[cell], road.z()[cell]);
                if (!level.hasChunkAt(at))
                {
                    continue;
                }
                RoadDecay.erase(level, at, ROAD_D2, ROAD_REACH);
                decay.gone[cell] = true;
                decay.remaining--;
                done++;
            }
            if (decay.remaining <= 0)
            {
                roads.set(i, new Road(new int[0], new int[0], new int[0], 0, -1, 0, -1, road.pairA(), road.pairB(), null));
                finished = true;
            }
        }
        if (any)
        {
            setDirty();
        }
        if (finished)
        {
            centres = null;
            broadcast();
        }
    }

    /**
     * The road joining two colonies as cells from {@code from} to {@code to}, tunnels bridged by straight lines; null
     * when none is built or it is vanishing.
     */
    @Nullable
    public BlockPos[] pathBetween(final int from, final int to)
    {
        for (final Road road : roads)
        {
            if (road.decay() != null || road.x().length == 0)
            {
                continue;
            }
            final boolean forward = road.pairA() == from && road.pairB() == to;
            if (!forward && !(road.pairA() == to && road.pairB() == from))
            {
                continue;
            }
            final List<BlockPos> cells = new ArrayList<>();
            final int n = road.x().length;
            for (int k = 0; k < n; k++)
            {
                final int i = forward ? k : n - 1 - k;
                final BlockPos cell = new BlockPos(road.x()[i], road.y()[i], road.z()[i]);
                if (!cells.isEmpty())
                {
                    final BlockPos last = cells.getLast();
                    final int gap = Math.max(Math.abs(cell.getX() - last.getX()), Math.abs(cell.getZ() - last.getZ()));
                    for (int g = 1; g < gap; g++)
                    {
                        final double t = g / (double) gap;
                        cells.add(new BlockPos((int) Math.round(last.getX() + (cell.getX() - last.getX()) * t),
                          (int) Math.round(last.getY() + (cell.getY() - last.getY()) * t), (int) Math.round(last.getZ() + (cell.getZ() - last.getZ()) * t)));
                    }
                }
                cells.add(cell);
            }
            return cells.toArray(new BlockPos[0]);
        }
        return null;
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
                if (road.decay() != null)
                {
                    continue;
                }
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
            if (road.decay() != null || road.x().length == 0)
            {
                continue;
            }
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
            if (road.decay() != null)
            {
                continue;
            }
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
            Decay decay = null;
            if (road.contains("decayOrder"))
            {
                final int[] order = road.getIntArray("decayOrder");
                final byte[] gone = road.getByteArray("decayGone");
                final boolean[] flags = new boolean[order.length];
                for (int k = 0; k < Math.min(flags.length, gone.length); k++)
                {
                    flags[k] = gone[k] != 0;
                }
                decay = new Decay(order, flags);
            }
            registry.roads.add(new Road(road.getIntArray("x"), road.getIntArray("y"), road.getIntArray("z"), road.getInt("colonyA"),
              road.contains("gateA") ? road.getInt("gateA") : -1, road.getInt("colonyB"), road.contains("gateB") ? road.getInt("gateB") : -1,
              road.contains("pairA") ? road.getInt("pairA") : road.getInt("colonyA"), road.contains("pairB") ? road.getInt("pairB") : road.getInt("colonyB"), decay));
        }
        final long[] low = tag.getLongArray("linksLow");
        final long[] high = tag.getLongArray("linksHigh");
        for (int i = 0; i < Math.min(low.length, high.length); i++)
        {
            registry.links.add(new Pair(low[i], high[i]));
        }
        final long[] colLow = tag.getLongArray("colLinksLow");
        final long[] colHigh = tag.getLongArray("colLinksHigh");
        for (int i = 0; i < Math.min(colLow.length, colHigh.length); i++)
        {
            registry.colonyLinks.add(new Pair(colLow[i], colHigh[i]));
        }
        return registry;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        tag.putLongArray("posts", posts.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("linksLow", links.stream().mapToLong(Pair::low).toArray());
        tag.putLongArray("linksHigh", links.stream().mapToLong(Pair::high).toArray());
        tag.putLongArray("colLinksLow", colonyLinks.stream().mapToLong(Pair::low).toArray());
        tag.putLongArray("colLinksHigh", colonyLinks.stream().mapToLong(Pair::high).toArray());
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
            entry.putInt("pairA", road.pairA());
            entry.putInt("pairB", road.pairB());
            if (road.decay() != null)
            {
                entry.putIntArray("decayOrder", road.decay().order);
                final byte[] gone = new byte[road.decay().gone.length];
                for (int k = 0; k < gone.length; k++)
                {
                    gone[k] = (byte) (road.decay().gone[k] ? 1 : 0);
                }
                entry.putByteArray("decayGone", gone);
            }
            list.add(entry);
        }
        tag.put("roads", list);
        return tag;
    }
}
