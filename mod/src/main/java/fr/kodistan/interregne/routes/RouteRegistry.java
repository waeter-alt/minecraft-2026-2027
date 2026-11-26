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
     * a tunnel is left out, the ground above it is still ground). A road that ends on another one instead of at its
     * B colony's gate (a T junction) has colony B 0, and joinRoad/joinAt say which road and which of its cells.
     */
    private record Road(int[] x, int[] y, int[] z, int colonyA, int gateA, int colonyB, int gateB, int pairA, int pairB, @Nullable Decay decay, int level,
                        int joinRoad, int joinAt, @Nullable Shape shape, boolean war)
    {
        Road withEnds(final int a, final int gA, final int b, final int gB)
        {
            return new Road(x, y, z, a, gA, b, gB, pairA, pairB, decay, level, joinRoad, joinAt, shape, war);
        }

        Road withLevel(final int next)
        {
            return new Road(x, y, z, colonyA, gateA, colonyB, gateB, pairA, pairB, decay, next, joinRoad, joinAt, shape, war);
        }

        Road atPeace()
        {
            return new Road(x, y, z, colonyA, gateA, colonyB, gateB, pairA, pairB, decay, 1, joinRoad, joinAt, shape, false);
        }
    }

    /**
     * The whole plan a road was built from, tunnels included, so an upgrade can lay it again in its next style.
     * Roads recorded before 2026-11-26 have none and are dressed in place instead.
     */
    private record Shape(int[] x, int[] y, int[] z, byte[] mode, byte[] half, byte[] bend)
    {
        static Shape of(final RoutePlan plan)
        {
            final int n = plan.length();
            final byte[] half = new byte[n];
            final byte[] bend = new byte[n];
            for (int i = 0; i < n; i++)
            {
                half[i] = (byte) plan.footprint.half(i);
                bend[i] = (byte) (plan.bend[i] ? 1 : 0);
            }
            return new Shape(plan.x.clone(), plan.y.clone(), plan.z.clone(), plan.mode.clone(), half, bend);
        }

        RoutePlan plan()
        {
            final boolean[] flags = new boolean[bend.length];
            for (int i = 0; i < flags.length; i++)
            {
                flags[i] = bend[i] != 0;
            }
            return RoutePlan.restore(x, z, y, mode, flags, half);
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
    /** Every column a standing road's slabs cover, rebuilt like {@link #centres}. */
    private Set<Long> band;
    private Set<Long> exceptBand;
    private int exceptRoad = -1;
    /** The level of the road under every column of every road (the highest where two roads meet), rebuilt like {@link #centres}. */
    private java.util.Map<Long, Integer> levels;

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

    public boolean war(final int road)
    {
        return roads.get(road).war();
    }

    /**
     * The standing war road between these two colonies, -1 when there is none.
     */
    public int warRoad(final int a, final int b)
    {
        for (int i = 0; i < roads.size(); i++)
        {
            final Road road = roads.get(i);
            if (road.war() && standing(i) && (road.pairA() == a && road.pairB() == b || road.pairA() == b && road.pairB() == a))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * A war road the two colonies have turned into their trade road: level 1, and raised like any other from now on.
     */
    public void makePeace(final int road)
    {
        roads.set(road, roads.get(road).atPeace());
        levels = null;
        setDirty();
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
        record(plan, endA, endB, false);
    }

    /**
     * @param war a war road: it links the two colonies for the troops, but is never raised and gives way to their
     *            first trade road once they are allies
     */
    public void record(final RoutePlan plan, @Nullable final RouteEnds.End endA, @Nullable final RouteEnds.End endB, final boolean war)
    {
        int count = 0;
        for (int i = 0; i < plan.length(); i++)
        {
            count += plan.mode[i] == RoutePlan.TUNNEL ? 0 : 1;
        }
        final int colA = endA == null ? 0 : endA.colony();
        final int colB = endB == null ? 0 : endB.colony();
        int joinRoad = -1;
        int joinAt = -1;
        if (plan.joined)
        {
            final int last = plan.length() - 1;
            for (int r = 0; r < roads.size() && joinRoad < 0; r++)
            {
                final Road other = roads.get(r);
                if (other.decay() != null || !joinable(other, colA, colB))
                {
                    continue;
                }
                for (int k = 0; k < other.x().length; k++)
                {
                    if (other.x()[k] == plan.x[last] && other.z()[k] == plan.z[last])
                    {
                        joinRoad = r;
                        joinAt = k;
                        break;
                    }
                }
            }
        }
        final boolean hooked = joinRoad < 0;
        final Road road = new Road(new int[count], new int[count], new int[count], colA, endA == null ? -1 : endA.gate(),
          hooked ? colB : 0, hooked && endB != null ? endB.gate() : -1, colA, colB, null, 1, joinRoad, joinAt, Shape.of(plan), war);
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
        band = null;
        exceptBand = null;
        levels = null;
        setDirty();
    }

    /**
     * Whether a new road from colony {@code from} (0 for a post) to colony {@code to} may end on this one: it is
     * standing and already leads to {@code to}, and not from {@code from} (that pair would then have two roads).
     */
    private static boolean joinable(final Road road, final int from, final int to)
    {
        if (road.decay() != null || road.x().length == 0 || to <= 0 || (road.pairA() != to && road.pairB() != to))
        {
            return false;
        }
        return from <= 0 || (road.pairA() != from && road.pairB() != from);
    }

    /**
     * The centre lines, as {xs, zs}, of the roads a new route from {@code from} to {@code to} may end on ({@link
     * RoutePlanner#joinMask}).
     */
    public List<int[][]> joinRoads(final int from, final int to)
    {
        final List<int[][]> out = new ArrayList<>();
        for (final Road road : roads)
        {
            if (joinable(road, from, to))
            {
                out.add(new int[][] {road.x(), road.z()});
            }
        }
        return out;
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
                if (!nearJunction(road, cell))
                {
                    RoadDecay.erase(level, at, ROAD_D2, ROAD_REACH);
                }
                decay.gone[cell] = true;
                decay.remaining--;
                done++;
            }
            if (decay.remaining <= 0)
            {
                roads.set(i, new Road(new int[0], new int[0], new int[0], 0, -1, 0, -1, road.pairA(), road.pairB(), null, 1, -1, -1, null, false));
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
            band = null;
            exceptBand = null;
            levels = null;
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
            if (road.joinRoad() < 0)
            {
                return path(road, forward);
            }
            // A branch: its own cells up to the junction, then the road it ends on as far as their B colony
            final List<BlockPos> cells = new ArrayList<>();
            append(cells, road, 0, road.x().length - 1);
            if (!extend(cells, road, 0))
            {
                continue;
            }
            if (!forward)
            {
                java.util.Collections.reverse(cells);
            }
            return cells.toArray(new BlockPos[0]);
        }
        return null;
    }

    /**
     * Follows a branch's junction onto the road it ends on, toward the branch's B colony.
     *
     * @return false when that road is gone, vanishing or does not lead there
     */
    private boolean extend(final List<BlockPos> cells, final Road branch, final int depth)
    {
        if (branch.joinRoad() >= roads.size() || depth > 8)
        {
            return false;
        }
        final Road joined = roads.get(branch.joinRoad());
        final int n = joined.x().length;
        if (joined.decay() != null || branch.joinAt() >= n)
        {
            return false;
        }
        if (joined.pairB() == branch.pairB())
        {
            append(cells, joined, branch.joinAt(), n - 1);
            return joined.joinRoad() < 0 || extend(cells, joined, depth + 1);
        }
        if (joined.pairA() == branch.pairB())
        {
            append(cells, joined, branch.joinAt(), 0);
            return true;
        }
        return false;
    }

    /**
     * Whether this cell of a branch lies so close to its junction that erasing its slabs would bite into the road it
     * ends on.
     */
    private static boolean nearJunction(final Road road, final int cell)
    {
        if (road.joinRoad() < 0)
        {
            return false;
        }
        final int last = road.x().length - 1;
        return Math.max(Math.abs(road.x()[cell] - road.x()[last]), Math.abs(road.z()[cell] - road.z()[last])) <= 2 * ROAD_REACH;
    }

    private static BlockPos[] path(final Road road, final boolean forward)
    {
        final List<BlockPos> cells = new ArrayList<>();
        final int n = road.x().length;
        if (n > 0)
        {
            append(cells, road, forward ? 0 : n - 1, forward ? n - 1 : 0);
        }
        return cells.toArray(new BlockPos[0]);
    }

    /**
     * Adds a road's cells from index {@code from} to {@code to} (either way), tunnels bridged by straight lines, the
     * first one left out when it is the cell already last.
     */
    private static void append(final List<BlockPos> cells, final Road road, final int from, final int to)
    {
        final int step = to >= from ? 1 : -1;
        for (int i = from; i != to + step; i += step)
        {
            final BlockPos cell = new BlockPos(road.x()[i], road.y()[i], road.z()[i]);
            if (!cells.isEmpty())
            {
                final BlockPos last = cells.getLast();
                if (last.getX() == cell.getX() && last.getZ() == cell.getZ())
                {
                    continue;
                }
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
    }

    /**
     * A road's centre line from its A end to its B end, tunnels bridged by straight lines (empty once it has vanished).
     */
    public BlockPos[] path(final int road)
    {
        return path(roads.get(road), true);
    }

    public int roadCount()
    {
        return roads.size();
    }

    /**
     * The standing road (not vanishing) joining these two colonies, or -1.
     */
    public int roadBetween(final int a, final int b)
    {
        for (int i = 0; i < roads.size(); i++)
        {
            final Road road = roads.get(i);
            if (road.decay() == null && road.x().length > 0
                  && (road.pairA() == a && road.pairB() == b || road.pairA() == b && road.pairB() == a))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * The standing road of this colony passing closest to pos, within maxDist blocks across, or -1.
     */
    public int nearestRoad(final int colony, final BlockPos pos, final int maxDist)
    {
        int best = -1;
        long bestD2 = (long) maxDist * maxDist + 1;
        for (int i = 0; i < roads.size(); i++)
        {
            final Road road = roads.get(i);
            if (road.decay() != null || road.pairA() != colony && road.pairB() != colony)
            {
                continue;
            }
            for (int k = 0; k < road.x().length; k++)
            {
                final long dx = road.x()[k] - pos.getX();
                final long dz = road.z()[k] - pos.getZ();
                if (dx * dx + dz * dz < bestD2)
                {
                    bestD2 = dx * dx + dz * dz;
                    best = i;
                }
            }
        }
        return best;
    }

    /**
     * Whether this road still stands (built, not vanishing).
     */
    public boolean standing(final int road)
    {
        return road >= 0 && road < roads.size() && roads.get(road).decay() == null && roads.get(road).x().length > 0;
    }

    public int level(final int road)
    {
        return roads.get(road).level();
    }

    /**
     * The two colonies a road was built between.
     */
    public int[] colonies(final int road)
    {
        return new int[] {roads.get(road).pairA(), roads.get(road).pairB()};
    }

    public void setLevel(final int road, final int level)
    {
        final Road current = roads.get(road);
        final int next = Math.clamp(level, 1, RoadLevels.MAX);
        if (current.level() != next)
        {
            roads.set(road, current.withLevel(next));
            levels = null;
            setDirty();
        }
    }

    /**
     * The level of the road one stands on here: 0 off every standing road, or a column more than a few blocks above or
     * below its deck (under a bridge, over a tunnel).
     */
    public int levelAt(final BlockPos pos)
    {
        if (levels == null)
        {
            final java.util.Map<Long, Integer> next = new java.util.HashMap<>();
            for (final Road road : roads)
            {
                if (road.decay() != null)
                {
                    continue;
                }
                for (final BlockPos cell : path(road, true))
                {
                    for (int dz = -ROAD_REACH; dz <= ROAD_REACH; dz++)
                    {
                        for (int dx = -ROAD_REACH; dx <= ROAD_REACH; dx++)
                        {
                            if (dx * dx + dz * dz <= ROAD_D2)
                            {
                                final long key = BlockPos.asLong(cell.getX() + dx, 0, cell.getZ() + dz);
                                final int packed = road.level() << 16 | (cell.getY() & 0xFFFF);
                                next.merge(key, packed, Math::max);
                            }
                        }
                    }
                }
            }
            levels = next;
        }
        final Integer packed = levels.get(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
        if (packed == null)
        {
            return 0;
        }
        final int y = (short) (packed & 0xFFFF);
        return Math.abs(pos.getY() - y - 1) <= 3 ? packed >>> 16 : 0;
    }

    /**
     * The centre-line cells of a road, as {x, z} pairs.
     */
    /**
     * The plan a road was built from, to rebuild it whole; null for a road recorded before plans were kept.
     */
    @Nullable
    public RoutePlan plan(final int road)
    {
        final Shape shape = roads.get(road).shape();
        return shape == null ? null : shape.plan();
    }

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

    /**
     * Whether a standing road's slabs already cover this column: a new road joining it lays nothing there.
     */
    public boolean inBand(final int x, final int z)
    {
        if (band == null)
        {
            band = band(-1);
        }
        return band.contains(WallRegistry.cell(x, z));
    }

    /**
     * Same, leaving one road out: that road being rebuilt over its own slabs.
     */
    public boolean inBand(final int x, final int z, final int except)
    {
        if (exceptBand == null || exceptRoad != except)
        {
            exceptBand = band(except);
            exceptRoad = except;
        }
        return exceptBand.contains(WallRegistry.cell(x, z));
    }

    private Set<Long> band(final int except)
    {
        final Set<Long> next = new HashSet<>();
        for (int r = 0; r < roads.size(); r++)
        {
            final Road road = roads.get(r);
            if (road.decay() != null || r == except)
            {
                continue;
            }
            for (int i = 0; i < road.x().length; i++)
            {
                for (int dz = -ROAD_REACH; dz <= ROAD_REACH; dz++)
                {
                    for (int dx = -ROAD_REACH; dx <= ROAD_REACH; dx++)
                    {
                        if (dx * dx + dz * dz <= ROAD_D2)
                        {
                            next.add(WallRegistry.cell(road.x()[i] + dx, road.z()[i] + dz));
                        }
                    }
                }
            }
        }
        return next;
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
              road.contains("pairA") ? road.getInt("pairA") : road.getInt("colonyA"), road.contains("pairB") ? road.getInt("pairB") : road.getInt("colonyB"), decay,
              road.contains("level") ? Math.max(1, road.getInt("level")) : 1, road.contains("joinRoad") ? road.getInt("joinRoad") : -1,
              road.contains("joinAt") ? road.getInt("joinAt") : -1, road.contains("shapeMode") ? new Shape(road.getIntArray("shapeX"),
              road.getIntArray("shapeY"), road.getIntArray("shapeZ"), road.getByteArray("shapeMode"), road.getByteArray("shapeHalf"),
              road.getByteArray("shapeBend")) : null, road.getBoolean("war")));
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
            entry.putInt("level", road.level());
            entry.putInt("joinRoad", road.joinRoad());
            entry.putInt("joinAt", road.joinAt());
            if (road.war())
            {
                entry.putBoolean("war", true);
            }
            if (road.shape() != null)
            {
                entry.putIntArray("shapeX", road.shape().x());
                entry.putIntArray("shapeY", road.shape().y());
                entry.putIntArray("shapeZ", road.shape().z());
                entry.putByteArray("shapeMode", road.shape().mode());
                entry.putByteArray("shapeHalf", road.shape().half());
                entry.putByteArray("shapeBend", road.shape().bend());
            }
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
