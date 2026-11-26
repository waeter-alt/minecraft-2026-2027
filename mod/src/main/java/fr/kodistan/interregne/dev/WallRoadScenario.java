package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import com.mojang.authlib.GameProfile;
import fr.kodistan.interregne.network.WallBuildPayload;
import fr.kodistan.interregne.routes.RouteEnds;
import fr.kodistan.interregne.routes.RoutePlanning;
import fr.kodistan.interregne.routes.RouteRegistry;
import fr.kodistan.interregne.routes.plan.RoutePlan;
import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.walls.WallBlocks;
import fr.kodistan.interregne.walls.WallLayout;
import fr.kodistan.interregne.walls.WallLevels;
import fr.kodistan.interregne.walls.WallRegistry;
import fr.kodistan.interregne.walls.WallTrace;
import fr.kodistan.interregne.walls.Walls;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A wall and a trade road meeting on real terrain, end to end: a road is laid across the colony's land, walls are
 * traced over it (across, along it, with the player's gate beside it), the crossing one is built for real by the
 * server's own handler, then a road is planned to the walled colony from its inner side and must go round to the gate.
 * Run with ticks=24000, every=20: the steps chain from one snapshot to the next.
 */
public final class WallRoadScenario implements Scenario
{
    private static final int HALF = 24;
    private static final int ROAD_HALF = 32;

    private final List<String> checks = new ArrayList<>();
    private int step;
    private int rx;
    private int rz;
    private CompletableFuture<String> road;
    private ServerPlayer player;
    private List<WallTrace.Point> trace;
    private WallLayout built;
    private RoutePlan approach;
    private boolean approachDone;

    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        if (WallRegistry.get(level.getServer()).of(colony) != null)
        {
            // The scenario's wall must be a new one, not a retrace of whatever the save holds
            WallRegistry.get(level.getServer()).remove(colony);
            lines.add("existing wall unregistered (its blocks stay as obstacles)");
        }
        final BlockPos c = colony.getCenter();
        // No player on a test server: keep the whole area loaded for the probes, the road and the wall site
        for (int cz = (c.getZ() - 150) >> 4; cz <= (c.getZ() + 150) >> 4; cz++)
        {
            for (int cx = (c.getX() - 150) >> 4; cx <= (c.getX() + 150) >> 4; cx++)
            {
                level.setChunkForced(cx, cz, true);
            }
        }
        int best = Integer.MAX_VALUE;
        for (int dz = -100; dz <= 100; dz += 4)
        {
            for (int dx = -100; dx <= 100; dx += 4)
            {
                final int found = obstacles(level, colony, c.getX() + dx, c.getZ() + dz, best);
                if (found < best)
                {
                    best = found;
                    rx = c.getX() + dx;
                    rz = c.getZ() + dz;
                }
            }
        }
        if (best > 0)
        {
            throw new IllegalStateException("no clear stretch of land for the test wall in this colony (best spot " + rx + "," + rz + " has " + best + " obstacles)");
        }
        trace = List.of(new WallTrace.Point(rx - HALF, rz), new WallTrace.Point(rx + HALF, rz));
        lines.add("test wall E-W at z=" + rz + " from x=" + (rx - HALF) + " to " + (rx + HALF) + ", road N-S at x~" + rx + " (centre " + c.toShortString() + ")");
        road = RouteDev.build(level, new BlockPos(rx, 0, rz - ROAD_HALF), new BlockPos(rx, 0, rz + ROAD_HALF), false);
        player = FakePlayerFactory.get(level, new GameProfile(colony.getPermissions().getOwner(), colony.getPermissions().getOwnerName()));
        player.setGameMode(GameType.CREATIVE);
        return lines;
    }

    /** Cells in the way of a wall across here and a road through its middle, counted up to limit. */
    private static int obstacles(final ServerLevel level, final IColony colony, final int x, final int z, final int limit)
    {
        int count = 0;
        for (int dz = -ROAD_HALF; dz <= ROAD_HALF && count < limit; dz++)
        {
            for (int dx = -4; dx <= 4; dx++)
            {
                count += blockedAt(level, colony, x + dx, z + dz) ? 1 : 0;
            }
        }
        for (int dx = -HALF - 5; dx <= HALF + 5 && count < limit; dx++)
        {
            for (int dz = -5; dz <= 5; dz++)
            {
                count += Math.abs(dx) > 4 && blockedAt(level, colony, x + dx, z + dz) ? 1 : 0;
            }
        }
        return count;
    }

    private static boolean blockedAt(final ServerLevel level, final IColony colony, final int x, final int z)
    {
        final Terrain.Probe probe = Terrain.probe(level, x, z);
        return !probe.loaded() || probe.blocked() || probe.water() || !colony.isCoordInColony(level, new BlockPos(x, 64, z));
    }

    private WallLayout.Ground reader(final ServerLevel level)
    {
        final RouteRegistry roads = RouteRegistry.get(level.getServer());
        return (x, z) -> WallLayout.overRoad(Terrain.probe(level, x, z), x, z, roads::onRoad);
    }

    private WallLayout layout(final ServerLevel level, final IColony colony, final List<WallTrace.Point> points, final List<WallTrace.Point> gates)
    {
        return WallLayout.of(points, false, gates, WallLevels.of(1), reader(level), (x, z) -> colony.isCoordInColony(level, new BlockPos(x, 64, z)));
    }

    private List<WallTrace.Point> withCrossings(final ServerLevel level, final List<WallTrace.Point> points, final List<WallTrace.Point> userGates)
    {
        final List<WallTrace.Point> gates = new ArrayList<>(userGates);
        gates.addAll(WallLayout.crossings(points, false, userGates, RouteRegistry.get(level.getServer())::onRoad));
        return gates;
    }

    @Override
    public String snapshot(final IColony colony, final ServerLevel level)
    {
        switch (step)
        {
            case 0 ->
            {
                if (!road.isDone())
                {
                    return "road being studied";
                }
                final String line = road.join();
                checks.add(check("road laid across the colony: " + line, !line.startsWith("ERROR")));
                if (line.startsWith("ERROR"))
                {
                    step = 9;
                    return line;
                }
                traceChecks(colony, level);
                final List<WallTrace.Point> gates = withCrossings(level, trace, List.of());
                built = layout(level, colony, trace, gates);
                Walls.onBuild(new WallBuildPayload(colony.getID(), WallTrace.flat(trace), WallTrace.flat(gates), false, 1), context(player));
                final boolean started = Walls.building(colony);
                checks.add(check("server accepts the wall across the road and starts its site", started));
                step = started ? 1 : 9;
                return "wall site " + (started ? "started" : "REFUSED");
            }
            case 1 ->
            {
                if (Walls.building(colony))
                {
                    return "wall site running";
                }
                builtChecks(level);
                startApproach(colony, level);
                step = 2;
                return "wall built, approach road being studied";
            }
            case 2 ->
            {
                if (!approachDone)
                {
                    return "approach road being studied";
                }
                approachChecks(level);
                step = 9;
                return "done";
            }
            default ->
            {
                return "done";
            }
        }
    }

    /** The three ways a trace can meet the road, judged on the real ground before anything is built. */
    private void traceChecks(final IColony colony, final ServerLevel level)
    {
        final List<WallTrace.Point> across = withCrossings(level, trace, List.of());
        final WallLayout crossing = layout(level, colony, trace, across);
        checks.add(check("wall across the road gets exactly one automatic gate (" + across + ")", across.size() == 1));
        checks.add(check("wall across the road has no red cell (blocked=" + crossing.blocked() + ")", crossing.blocked() == 0));

        // The road's straight run at this x, south of the crossing: a wall laid along it
        final RouteRegistry roads = RouteRegistry.get(level.getServer());
        int along = Integer.MIN_VALUE;
        for (int x = rx - 2; x <= rx + 2 && along == Integer.MIN_VALUE; x++)
        {
            boolean straight = true;
            for (int z = rz + 8; z <= rz + 24; z++)
            {
                straight &= roads.onRoad(WallRegistry.cell(x, z));
            }
            if (straight)
            {
                along = x;
            }
        }
        if (along == Integer.MIN_VALUE)
        {
            checks.add("SKIP wall along the road: the road has no straight run of 17 cells south of the crossing");
        }
        else
        {
            final List<WallTrace.Point> lengthwise = List.of(new WallTrace.Point(along, rz + 8), new WallTrace.Point(along, rz + 24));
            final WallLayout onTop = layout(level, colony, lengthwise, withCrossings(level, lengthwise, List.of()));
            checks.add(check("wall laid along the road is refused (blocked=" + onTop.blocked() + ")", onTop.blocked() > 0));
            final List<WallTrace.Point> edge = List.of(new WallTrace.Point(along + 3, rz + 8), new WallTrace.Point(along + 3, rz + 24));
            final WallLayout onEdge = layout(level, colony, edge, withCrossings(level, edge, List.of()));
            checks.add(check("wall laid on the road's edge is refused (blocked=" + onEdge.blocked() + ")", onEdge.blocked() > 0));
        }

        final List<WallTrace.Point> playerGate = List.of(new WallTrace.Point(rx + 5, rz));
        final WallLayout offset = layout(level, colony, trace, withCrossings(level, trace, playerGate));
        checks.add(check("player's gate 5 blocks beside the road: road cells shown red (blocked=" + offset.blocked() + ")", offset.blocked() > 0));
    }

    /** The built crossing: the road still runs through the gate's passage, the wall stands on both sides. */
    private void builtChecks(final ServerLevel level)
    {
        final WallLayout.Gate gate = built.gates().isEmpty() ? null : built.gates().get(0);
        if (gate == null)
        {
            checks.add("FAIL no gate in the built layout");
            return;
        }
        final RouteRegistry roads = RouteRegistry.get(level.getServer());
        int roadCells = 0;
        int open = 0;
        for (int z = gate.z() - gate.depth(); z <= gate.z() + gate.depth(); z++)
        {
            for (int x = gate.x() - WallLayout.PASSAGE_HALF; x <= gate.x() + WallLayout.PASSAGE_HALF; x++)
            {
                if (!roads.onRoad(WallRegistry.cell(x, z)))
                {
                    continue;
                }
                roadCells++;
                // The lintel closes the column far above: walk up from the road's surface instead
                int y = gate.floor() - 3;
                while (y < gate.floor() + 4 && !level.getBlockState(new BlockPos(x, y + 1, z)).getCollisionShape(level, new BlockPos(x, y + 1, z)).isEmpty())
                {
                    y++;
                }
                final BlockPos feet = new BlockPos(x, y + 1, z);
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                      && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty() && y <= gate.floor() + 1)
                {
                    open++;
                }
                else if (open == 0 && roadCells == 1)
                {
                    checks.add("  first road cell in the passage " + feet.toShortString() + ": below=" + level.getBlockState(feet.below()) + " feet=" + level.getBlockState(feet)
                                 + " head=" + level.getBlockState(feet.above()) + " floor=" + gate.floor());
                }
            }
        }
        checks.add(check("road runs through the built gate passage: " + open + "/" + roadCells + " road cells open, 2 blocks of headroom", roadCells > 0 && open == roadCells));
        int masonry = 0;
        for (final int side : new int[] {-1, 1})
        {
            final int x = gate.x() + side * (WallLayout.GATE_HALF + 3);
            final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, gate.z()) - 1;
            if (WallBlocks.isWallBlock(level.getBlockState(new BlockPos(x, y, gate.z()))))
            {
                masonry++;
            }
        }
        checks.add(check("wall stands on both sides of the gate (" + masonry + "/2)", masonry == 2));
        int cut = 0;
        for (final WallLayout.Column column : built.columns())
        {
            if (roads.onRoad(WallRegistry.cell(column.x(), column.z())))
            {
                cut++;
            }
        }
        checks.add(check("no wall column on the road's centre line (" + cut + ")", cut == 0));
    }

    /**
     * A road planned afterwards from inside the wall's side to the colony: it is hooked to the gate's outer threshold and
     * must go round the wall's end, never through it.
     */
    private void startApproach(final IColony colony, final ServerLevel level)
    {
        final BlockPos c = colony.getCenter();
        final int sx = Integer.signum(c.getX() - rx);
        final int sz = Integer.signum(c.getZ() - rz);
        // A point on the colony's side of the wall, off the first road
        final BlockPos far = new BlockPos(rx + 12 + sx * 4, 0, rz + (sz == 0 ? 1 : sz) * 20);
        final RouteEnds.End end = RouteEnds.of(level, colony, new BlockPos(rx, 64, rz), far);
        checks.add(check("approach hooks to the wall's gate (gate index " + end.gate() + ", at " + end.at().toShortString() + ")", end.gate() >= 0));
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far.getX(), far.getZ()) - 1;
        final RouteEnds.End from = new RouteEnds.End(new BlockPos(far.getX(), y, far.getZ()), null, 0, -1, false);
        final boolean started = RoutePlanning.study(level, from, end, null, plan -> {
            approach = plan;
            approachDone = true;
        });
        if (!started)
        {
            checks.add("FAIL approach study not started");
            approachDone = true;
        }
    }

    private void approachChecks(final ServerLevel level)
    {
        if (approach == null)
        {
            checks.add("FAIL no approach road found to the walled colony's gate");
            return;
        }
        final Set<Long> wall = new HashSet<>();
        for (final WallLayout.Column column : built.columns())
        {
            wall.add(WallRegistry.cell(column.x(), column.z()));
        }
        for (final WallLayout.Tower tower : built.towers())
        {
            for (int dx = 0; dx < tower.size(); dx++)
            {
                for (int dz = 0; dz < tower.size(); dz++)
                {
                    if (tower.covers(tower.x() + dx, tower.z() + dz))
                    {
                        wall.add(WallRegistry.cell(tower.x() + dx, tower.z() + dz));
                    }
                }
            }
        }
        int through = 0;
        for (int i = 0; i < approach.length(); i++)
        {
            if (wall.contains(WallRegistry.cell(approach.x[i], approach.z[i])))
            {
                through++;
            }
        }
        checks.add(check("approach road (" + approach.length() + " cells) never crosses the wall's masonry (" + through + " cells on it)", through == 0));
    }

    @Override
    public List<String> verdict(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>(checks);
        if (step != 9)
        {
            lines.add("FAIL scenario stopped at step " + step + " (raise ticks)");
        }
        return lines;
    }

    private static IPayloadContext context(final ServerPlayer player)
    {
        return (IPayloadContext) Proxy.newProxyInstance(WallRoadScenario.class.getClassLoader(), new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> {
            if ("player".equals(method.getName()))
            {
                return player;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static String check(final String what, final boolean pass)
    {
        return (pass ? "PASS " : "FAIL ") + what;
    }
}
