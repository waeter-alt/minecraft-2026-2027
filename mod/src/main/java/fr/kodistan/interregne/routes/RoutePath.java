package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.walls.WallRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Traces a route between two points on the ground: a 2D search picks the way around what it can go around, then a
 * graded height profile turns the gentle stretches into a surface path, the steep ones into a tunnel, and the gaps
 * (water, a chasm) into a bridge. A prototype, tuned to be tested in game and adjusted from there, not a final style.
 */
public final class RoutePath
{
    /**
     * One block of the route; air clears what was growing above it.
     */
    public record Step(BlockPos pos, BlockState state)
    {
    }

    /**
     * @param steps   every block to set, in building order
     * @param length  number of ground cells the route crosses (its real walking length, not vol d'oiseau)
     * @param tunnels number of cells carved through a slope too steep to climb
     * @param bridges number of cells spanning water or a drop too steep to descend
     */
    public record Result(List<Step> steps, int length, int tunnels, int bridges)
    {
    }

    private enum Mode
    {
        SURFACE, TUNNEL, BRIDGE
    }

    private static final int MAX_SEARCH_DISTANCE = 400;
    private static final int SEARCH_MARGIN = 24;
    private static final int MAX_NODES = 60_000;
    private static final double SLOPE_WEIGHT = 2.5;
    private static final double WATER_PENALTY = 3.0;
    private static final double BLOCKED_PENALTY = 20.0;

    private static final int MAX_GRADE = 1;
    private static final int TUNNEL_MARGIN = 3;
    private static final int BRIDGE_MARGIN = 2;
    private static final int TUNNEL_HEADROOM = 2;
    private static final int LIGHT_EVERY = 6;
    private static final int PILLAR_EVERY = 6;

    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();
    private static final BlockState TUNNEL_FLOOR = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState TUNNEL_LANTERN = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState BRIDGE_FLOOR = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState BRIDGE_RAIL = Blocks.OAK_FENCE.defaultBlockState();
    private static final BlockState BRIDGE_PILLAR = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private RoutePath()
    {
    }

    /**
     * Null when the two ends are too far apart, a chunk along the way is not loaded, or no way through was found
     * within the node budget.
     */
    @Nullable
    public static Result compute(final ServerLevel level, final BlockPos a, final BlockPos b)
    {
        if (a.distSqr(b) > (long) MAX_SEARCH_DISTANCE * MAX_SEARCH_DISTANCE)
        {
            return null;
        }
        final List<long[]> cells = search(level, a.getX(), a.getZ(), b.getX(), b.getZ());
        if (cells == null || cells.isEmpty())
        {
            return null;
        }
        return build(level, cells);
    }

    /**
     * A* over the (x, z) grid: cost per step grows with the slope, with water, and with anything already standing
     * there (a building goes around, at a price, rather than being treated as a wall).
     */
    @Nullable
    private static List<long[]> search(final ServerLevel level, final int ax, final int az, final int bx, final int bz)
    {
        final Terrain.Probe start = Terrain.probe(level, ax, az);
        final Terrain.Probe goal = Terrain.probe(level, bx, bz);
        if (!start.loaded() || !goal.loaded())
        {
            return null;
        }
        final long startCell = WallRegistry.cell(ax, az);
        final long goalCell = WallRegistry.cell(bx, bz);
        final Map<Long, Double> gScore = new HashMap<>();
        final Map<Long, Long> parent = new HashMap<>();
        final Map<Long, Terrain.Probe> probes = new HashMap<>();
        final java.util.Set<Long> closed = new java.util.HashSet<>();
        final PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingDouble(node -> Double.longBitsToDouble(node[1])));
        gScore.put(startCell, 0.0);
        probes.put(startCell, start);
        open.add(new long[] {startCell, Double.doubleToLongBits(heuristic(ax, az, bx, bz))});
        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_NODES)
        {
            final long[] node = open.poll();
            final long current = node[0];
            if (!closed.add(current))
            {
                continue;
            }
            if (current == goalCell)
            {
                return reconstruct(parent, current);
            }
            final int x = (int) (current >> 32);
            final int z = (int) current;
            final double currentG = gScore.getOrDefault(current, Double.MAX_VALUE);
            final Terrain.Probe currentProbe = probes.get(current);
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dz == 0)
                    {
                        continue;
                    }
                    final int nx = x + dx;
                    final int nz = z + dz;
                    final long neighbor = WallRegistry.cell(nx, nz);
                    Terrain.Probe probe = probes.get(neighbor);
                    if (probe == null)
                    {
                        probe = Terrain.probe(level, nx, nz);
                        if (!probe.loaded())
                        {
                            continue;
                        }
                        probes.put(neighbor, probe);
                    }
                    final double step = (dx != 0 && dz != 0) ? 1.4142 : 1.0;
                    final double slope = Math.abs(probe.ground() - currentProbe.ground()) * SLOPE_WEIGHT;
                    final double penalty = (probe.water() ? WATER_PENALTY : 0) + (probe.blocked() ? BLOCKED_PENALTY : 0);
                    final double tentative = currentG + step + slope + penalty;
                    if (tentative < gScore.getOrDefault(neighbor, Double.MAX_VALUE))
                    {
                        gScore.put(neighbor, tentative);
                        parent.put(neighbor, current);
                        open.add(new long[] {neighbor, Double.doubleToLongBits(tentative + heuristic(nx, nz, bx, bz))});
                    }
                }
            }
        }
        return null;
    }

    private static double heuristic(final int x, final int z, final int bx, final int bz)
    {
        return Math.hypot(bx - x, bz - z);
    }

    private static List<long[]> reconstruct(final Map<Long, Long> parent, final long goal)
    {
        final List<long[]> cells = new ArrayList<>();
        long current = goal;
        cells.add(new long[] {current >> 32, (int) current});
        while (parent.containsKey(current))
        {
            current = parent.get(current);
            cells.add(new long[] {current >> 32, (int) current});
        }
        java.util.Collections.reverse(cells);
        return cells;
    }

    /**
     * Turns the 2D trace into real blocks: a graded height profile decides, cell by cell, whether the route walks
     * the surface, tunnels through what rises too fast, or bridges what drops away.
     */
    private static Result build(final ServerLevel level, final List<long[]> cells)
    {
        final int[] ground = new int[cells.size()];
        final boolean[] water = new boolean[cells.size()];
        for (int i = 0; i < cells.size(); i++)
        {
            final Terrain.Probe probe = Terrain.probe(level, (int) cells.get(i)[0], (int) cells.get(i)[1]);
            ground[i] = probe.ground();
            water[i] = probe.water();
        }
        final int[] roadY = new int[cells.size()];
        roadY[0] = ground[0];
        for (int i = 1; i < cells.size(); i++)
        {
            roadY[i] = Math.max(ground[i] - TUNNEL_MARGIN, Math.min(ground[i] + BRIDGE_MARGIN,
              Math.max(roadY[i - 1] - MAX_GRADE, Math.min(roadY[i - 1] + MAX_GRADE, ground[i]))));
        }

        final List<Step> steps = new ArrayList<>();
        int tunnels = 0;
        int bridges = 0;
        for (int i = 0; i < cells.size(); i++)
        {
            final int x = (int) cells.get(i)[0];
            final int z = (int) cells.get(i)[1];
            final int y = roadY[i];
            final int diff = ground[i] - y;
            final Mode mode = water[i] || diff < -BRIDGE_MARGIN ? Mode.BRIDGE : diff > TUNNEL_MARGIN ? Mode.TUNNEL : Mode.SURFACE;
            switch (mode)
            {
                case TUNNEL ->
                {
                    tunnels++;
                    carveTunnel(level, steps, x, y, z, i);
                }
                case BRIDGE ->
                {
                    bridges++;
                    bridgeAt(level, steps, x, y, z, ground[i], i);
                }
                default -> surfaceAt(level, steps, x, y, z, ground[i]);
            }
        }
        return new Result(steps, cells.size(), tunnels, bridges);
    }

    /**
     * Gentle ground: a path block at the surface, with whatever grass or sapling stood over it cleared away.
     */
    private static void surfaceAt(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final int ground)
    {
        steps.add(new Step(new BlockPos(x, ground, z), PATH));
        for (int dy = 1; dy <= 2; dy++)
        {
            final BlockPos above = new BlockPos(x, ground + dy, z);
            final BlockState state = level.getBlockState(above);
            if (!state.isAir() && Terrain.clearable(level, above, state))
            {
                steps.add(new Step(above, AIR));
            }
        }
    }

    /**
     * A hill in the way: a one-wide corridor carved through at the road's grade, floored and lit.
     */
    private static void carveTunnel(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final int order)
    {
        steps.add(new Step(new BlockPos(x, y, z), TUNNEL_FLOOR));
        for (int dy = 1; dy <= TUNNEL_HEADROOM; dy++)
        {
            final BlockPos pos = new BlockPos(x, y + dy, z);
            if (!level.getBlockState(pos).isAir())
            {
                steps.add(new Step(pos, AIR));
            }
        }
        if (order % LIGHT_EVERY == 0)
        {
            steps.add(new Step(new BlockPos(x, y + TUNNEL_HEADROOM, z), TUNNEL_LANTERN));
        }
    }

    /**
     * Water or a drop too steep to walk down: a deck at the road's grade, a rail along its edge, and a pillar down to
     * solid ground every few blocks so it does not read as floating.
     */
    private static void bridgeAt(final ServerLevel level, final List<Step> steps, final int x, final int y, final int z, final int ground, final int order)
    {
        steps.add(new Step(new BlockPos(x, y, z), BRIDGE_FLOOR));
        if (order % 2 == 0)
        {
            steps.add(new Step(new BlockPos(x, y + 1, z), BRIDGE_RAIL));
        }
        if (order % PILLAR_EVERY == 0)
        {
            for (int py = y - 1; py > ground && y - py < 48; py--)
            {
                steps.add(new Step(new BlockPos(x, py, z), BRIDGE_PILLAR));
            }
        }
    }
}
