package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import fr.kodistan.interregne.routes.plan.HeightField;
import fr.kodistan.interregne.routes.plan.Lead;
import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.walls.WallBlocks;
import fr.kodistan.interregne.walls.WallLayout;
import fr.kodistan.interregne.walls.WallLevels;
import fr.kodistan.interregne.walls.WallRegistry;
import fr.kodistan.interregne.walls.WallTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a route ends in a colony: the outer threshold of one of its wall gates (the one the post was set by, or
 * else the one facing the other end). A walled town is never entered, so its streets and decoration are never
 * touched. A colony without a wall gets the road in to its town hall's door, narrowed to a street two blocks wide
 * that goes around every building; with no town hall either, the road stops just past the edge of its land. A route
 * never crosses a third colony's land.
 */
public final class RouteEnds
{
    /** Cells of straight approach out of a gate, over which the road widens from the passage to its full width. */
    public static final int LEAD = 8;
    /** A post set this close to a gate picks that gate. */
    private static final int PICK = 24;
    /** Columns this close to a wall's trace are the wall's: the road and its shoulders keep off them. */
    private static final int WALL_MARGIN = 4;
    /** How far past the edge of an unwalled colony's land the road stops. */
    private static final int BEYOND_BORDER = 2;
    /** Columns this close to a building of an unwalled town stay clear of the street going through it. */
    private static final int BUILDING_MARGIN = 1;
    /** How far the straight way out of the town hall's door reaches past the building. */
    private static final int DOOR_OUT = 3;

    private RouteEnds()
    {
    }

    /**
     * @param at     where the route ends (y: the ground block)
     * @param lead   the straight approach out of a gate, null when there is none
     * @param colony the colony this end belongs to, 0 for none
     * @param gate   the index of its gate in the wall's gate list, -1 for none
     * @param border the end stops at the edge of an unwalled colony: a marker is set there once the road is built
     */
    public record End(BlockPos at, @Nullable Lead lead, int colony, int gate, boolean border)
    {
    }

    /**
     * @param post   the Poste commercial standing for this end
     * @param toward the other end, for a colony to pick the gate facing it
     */
    public static End of(final ServerLevel level, final BlockPos post, final BlockPos toward)
    {
        return of(level, IColonyManager.getInstance().getColonyByPosFromWorld(level, post), post, toward);
    }

    /**
     * Same, for the given colony whether or not the post stands on its land; a bare end at the post when null.
     */
    public static End of(final ServerLevel level, @Nullable final IColony colony, final BlockPos post, final BlockPos toward)
    {
        if (colony == null)
        {
            return new End(post.below(), null, 0, -1, false);
        }
        final WallRegistry.Wall wall = WallRegistry.get(level.getServer()).of(colony);
        if (wall != null && !wall.gates().isEmpty())
        {
            final List<WallLayout.Gate> gates = layout(level, colony, wall).gates();
            if (!gates.isEmpty())
            {
                return atGate(colony, wall, gates, post, toward);
            }
        }
        if (wall == null)
        {
            final End door = atDoor(level, colony, toward);
            if (door != null)
            {
                return door;
            }
        }
        return atBorder(level, colony, toward);
    }

    /**
     * @return the colony of this dimension whose centre lies nearest the position, null when there is none
     */
    @Nullable
    public static IColony nearest(final ServerLevel level, final BlockPos pos)
    {
        IColony best = null;
        double bestD = Double.MAX_VALUE;
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            final double d = colony.getCenter().distSqr(pos);
            if (d < bestD)
            {
                bestD = d;
                best = colony;
            }
        }
        return best;
    }

    /**
     * @return the colony of this dimension nearest the given one that is not the colony itself, is allied to it and that no road joins
     * to it yet, null when there is none
     */
    @Nullable
    public static IColony nearestUnlinked(final ServerLevel level, final IColony from, final RouteRegistry registry)
    {
        IColony best = null;
        double bestD = Double.MAX_VALUE;
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            if (colony.getID() == from.getID() || registry.areLinked(from.getID(), colony.getID())
                  || !fr.kodistan.interregne.politics.Alliances.get(level.getServer()).areAllied(from.getID(), colony.getID()))
            {
                continue;
            }
            final double d = colony.getCenter().distSqr(from.getCenter());
            if (d < bestD)
            {
                bestD = d;
                best = colony;
            }
        }
        return best;
    }

    private static WallLayout layout(final ServerLevel level, final IColony colony, final WallRegistry.Wall wall)
    {
        final Set<Long> own = WallLayout.ownCells(wall.points(), wall.closed());
        final java.util.function.LongPredicate roads = RouteRegistry.get(level.getServer())::onRoad;
        final WallLayout.Ground reader = (x, z) -> {
            final long cell = WallRegistry.cell(x, z);
            final Terrain.Probe known = wall.ground().get(cell);
            return known != null ? known : WallLayout.overRoad(Terrain.probe(level, x, z, own.contains(cell) ? WallBlocks::isWallBlock : null), x, z, roads);
        };
        final WallLayout.Claim claim = (x, z) -> colony.isCoordInColony(level, new BlockPos(x, 64, z));
        return WallLayout.of(wall.points(), wall.closed(), wall.gates(), WallLevels.of(wall.level()), reader, claim);
    }

    private static End atGate(final IColony colony, final WallRegistry.Wall wall, final List<WallLayout.Gate> gates, final BlockPos post,
                              final BlockPos toward)
    {
        final BlockPos centre = colony.getCenter();
        WallLayout.Gate best = null;
        double bestScore = Double.MAX_VALUE;
        // A post set by a gate picks that gate
        for (final WallLayout.Gate gate : gates)
        {
            final double d = sq(gate.x() - post.getX(), gate.z() - post.getZ());
            if (d <= PICK * PICK && d < bestScore)
            {
                best = gate;
                bestScore = d;
            }
        }
        // Otherwise the gate whose threshold lies nearest the other end
        if (best == null)
        {
            for (final WallLayout.Gate gate : gates)
            {
                final int[] n = outward(wall, gate, centre);
                final double d = sq(gate.x() + n[0] * (gate.depth() + 1) - toward.getX(), gate.z() + n[1] * (gate.depth() + 1) - toward.getZ());
                if (d < bestScore)
                {
                    bestScore = d;
                    best = gate;
                }
            }
        }
        final int[] n = outward(wall, best, centre);
        final int tx = best.x() + n[0] * (best.depth() + 1);
        final int tz = best.z() + n[1] * (best.depth() + 1);
        final Lead lead = new Lead(tx, tz, n[0], n[1], LEAD, best.floor());
        return new End(new BlockPos(tx, best.floor(), tz), lead, colony.getID(), best.index(), false);
    }

    /**
     * The side of a gate that faces out of the town: out of the wall's ring when it is closed, away from the
     * colony's centre otherwise.
     */
    private static int[] outward(final WallRegistry.Wall wall, final WallLayout.Gate gate, final BlockPos centre)
    {
        final int nx = -gate.uz();
        final int nz = gate.ux();
        final boolean plus;
        if (wall.closed() && wall.points().size() > 2)
        {
            plus = !inside(wall.points(), gate.x() + nx * (gate.depth() + 2), gate.z() + nz * (gate.depth() + 2));
        }
        else
        {
            plus = nx * (gate.x() - centre.getX()) + nz * (gate.z() - centre.getZ()) >= 0;
        }
        return plus ? new int[] {nx, nz} : new int[] {-nx, -nz};
    }

    /**
     * Just outside the town hall's front door: a door in its footprint whose outer side faces away from the building
     * (the one nearest the other end, the outer ones first), else the middle of the side facing the other end. Null
     * without a town hall.
     */
    @Nullable
    private static End atDoor(final ServerLevel level, final IColony colony, final BlockPos toward)
    {
        final IBuilding hall = colony.getBuildingManager().getTownHall();
        if (hall == null)
        {
            return null;
        }
        final Tuple<BlockPos, BlockPos> corners = hall.getCorners();
        final int[] box = box(corners);
        final double cx = (box[0] + box[2]) / 2.0;
        final double cz = (box[1] + box[3]) / 2.0;
        final int minY = Math.min(corners.getA().getY(), corners.getB().getY());
        final int maxY = Math.max(corners.getA().getY(), corners.getB().getY());
        BlockPos door = null;
        int nx = 0;
        int nz = 0;
        double best = Double.MAX_VALUE;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++)
        {
            for (int z = box[1]; z <= box[3]; z++)
            {
                for (int x = box[0]; x <= box[2]; x++)
                {
                    final BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER)
                    {
                        continue;
                    }
                    final Direction facing = state.getValue(DoorBlock.FACING);
                    int dx = facing.getStepX();
                    int dz = facing.getStepZ();
                    if (dx * (x - cx) + dz * (z - cz) < 0)
                    {
                        dx = -dx;
                        dz = -dz;
                    }
                    // An inner door counts as much farther: the way in is the one on the outside of the building
                    final double score = Math.sqrt(sq(x - toward.getX(), z - toward.getZ())) + 16 * inset(box, x, z, dx, dz);
                    if (score < best)
                    {
                        best = score;
                        door = new BlockPos(x, y, z);
                        nx = dx;
                        nz = dz;
                    }
                }
            }
        }
        final int sx;
        final int sz;
        final int floor;
        if (door != null)
        {
            sx = door.getX() + nx;
            sz = door.getZ() + nz;
            floor = door.getY() - 1;
        }
        else
        {
            final double dx = toward.getX() - cx;
            final double dz = toward.getZ() - cz;
            if (Math.abs(dx) >= Math.abs(dz))
            {
                nx = dx >= 0 ? 1 : -1;
                sx = (nx > 0 ? box[2] : box[0]) + nx;
                sz = (int) Math.round(cz);
            }
            else
            {
                nz = dz >= 0 ? 1 : -1;
                sx = (int) Math.round(cx);
                sz = (nz > 0 ? box[3] : box[1]) + nz;
            }
            floor = Terrain.probe(level, sx, sz).ground();
        }
        final Lead lead = new Lead(sx, sz, nx, nz, inset(box, sx, sz, nx, nz) + DOOR_OUT, floor);
        return new End(new BlockPos(sx, floor, sz), lead, colony.getID(), -1, false);
    }

    /**
     * Min x, min z, max x, max z of a building's footprint.
     */
    private static int[] box(final Tuple<BlockPos, BlockPos> corners)
    {
        return new int[] {
          Math.min(corners.getA().getX(), corners.getB().getX()), Math.min(corners.getA().getZ(), corners.getB().getZ()),
          Math.max(corners.getA().getX(), corners.getB().getX()), Math.max(corners.getA().getZ(), corners.getB().getZ())};
    }

    /**
     * Steps from this column, along the direction, to the first one out of the box.
     */
    private static int inset(final int[] box, final int x, final int z, final int nx, final int nz)
    {
        int steps = 0;
        while (x + nx * steps >= box[0] && x + nx * steps <= box[2] && z + nz * steps >= box[1] && z + nz * steps <= box[3])
        {
            steps++;
        }
        return steps;
    }

    /**
     * Whether this end goes in to the town hall's door of a colony without a wall.
     */
    private static boolean door(final End end)
    {
        return end.colony() > 0 && end.gate() < 0 && end.lead() != null;
    }

    /**
     * Walks from the colony's centre toward the other end until past the edge of its land.
     */
    private static End atBorder(final ServerLevel level, final IColony colony, final BlockPos toward)
    {
        final BlockPos centre = colony.getCenter();
        final double dx = toward.getX() - centre.getX();
        final double dz = toward.getZ() - centre.getZ();
        final double length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        int x = centre.getX();
        int z = centre.getZ();
        for (int step = 0; step < length; step++)
        {
            x = centre.getX() + (int) Math.round(dx * step / length);
            z = centre.getZ() + (int) Math.round(dz * step / length);
            if (!colony.isCoordInColony(level, new BlockPos(x, 64, z)))
            {
                break;
            }
        }
        x += (int) Math.round(dx / length * BEYOND_BORDER);
        z += (int) Math.round(dz / length * BEYOND_BORDER);
        final int y = Terrain.probe(level, x, z).ground();
        return new End(new BlockPos(x, y, z), null, colony.getID(), -1, true);
    }

    /**
     * Marks out of bounds, on a surveyed relief, what a route between these two ends must not touch: every third
     * colony's land, each end colony's wall and what it encloses (its whole land when the road stops at its edge).
     * An unwalled end colony's land is marked {@link HeightField#TOWN} instead, and only its buildings are out of
     * bounds. Then opens the straight approaches out of the gates and doors again, at their floor. Server thread
     * only.
     */
    public static void exclude(final ServerLevel level, final HeightField field, final End a, final End b)
    {
        final Map<ChunkPos, IChunkClaimData> claims = IColonyManager.getInstance().getClaimData(level.dimension());
        if (claims != null)
        {
            for (final Map.Entry<ChunkPos, IChunkClaimData> entry : claims.entrySet())
            {
                final int owner = entry.getValue().getOwningColony();
                if (owner <= 0)
                {
                    continue;
                }
                final boolean third = owner != a.colony() && owner != b.colony();
                final boolean bare = owner == a.colony() && a.lead() == null || owner == b.colony() && b.lead() == null;
                final boolean town = owner == a.colony() && door(a) || owner == b.colony() && door(b);
                final int flag = third || bare ? HeightField.FOREIGN : town ? HeightField.TOWN : 0;
                if (flag != 0)
                {
                    final ChunkPos chunk = entry.getKey();
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++)
                    {
                        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
                        {
                            mark(field, x, z, flag);
                        }
                    }
                }
            }
        }
        for (final End end : List.of(a, b))
        {
            if (end.lead() == null)
            {
                continue;
            }
            final IColony colony = IColonyManager.getInstance().getColonyByWorld(end.colony(), level);
            if (colony != null && door(end))
            {
                for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
                {
                    final int[] box = box(building.getCorners());
                    for (int z = box[1] - BUILDING_MARGIN; z <= box[3] + BUILDING_MARGIN; z++)
                    {
                        for (int x = box[0] - BUILDING_MARGIN; x <= box[2] + BUILDING_MARGIN; x++)
                        {
                            foreign(field, x, z);
                        }
                    }
                }
                continue;
            }
            final WallRegistry.Wall wall = colony == null ? null : WallRegistry.get(level.getServer()).of(colony);
            if (wall != null)
            {
                fence(field, wall);
            }
        }
        for (final End end : List.of(a, b))
        {
            final Lead lead = end.lead();
            if (lead == null)
            {
                continue;
            }
            final boolean street = door(end);
            for (int k = 0; k <= lead.length(); k++)
            {
                final int half = street || k <= 2 ? 1 : k <= 4 ? 2 : 3;
                for (int o = -half; o <= half; o++)
                {
                    final int x = lead.x() + lead.nx() * k - lead.nz() * o;
                    final int z = lead.z() + lead.nz() * k + lead.nx() * o;
                    if (field.inside(x, z))
                    {
                        field.set(x, z, lead.y(), lead.y(), street ? HeightField.TOWN : 0);
                    }
                }
            }
        }
    }

    /**
     * The wall's trace widened by {@link #WALL_MARGIN}, and the inside of its ring when it is closed.
     */
    private static void fence(final HeightField field, final WallRegistry.Wall wall)
    {
        final List<WallTrace.Point> points = wall.points();
        final boolean ring = wall.closed() && points.size() > 2;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < points.size(); i++)
        {
            final WallTrace.Point p = points.get(i);
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
            if (i + 1 < points.size() || ring)
            {
                for (final WallTrace.Point cell : WallTrace.line(p, points.get((i + 1) % points.size())))
                {
                    for (int dz = -WALL_MARGIN; dz <= WALL_MARGIN; dz++)
                    {
                        for (int dx = -WALL_MARGIN; dx <= WALL_MARGIN; dx++)
                        {
                            foreign(field, cell.x() + dx, cell.z() + dz);
                        }
                    }
                }
            }
        }
        if (ring)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                for (int x = minX; x <= maxX; x++)
                {
                    if (inside(points, x, z))
                    {
                        foreign(field, x, z);
                    }
                }
            }
        }
    }

    private static void foreign(final HeightField field, final int x, final int z)
    {
        mark(field, x, z, HeightField.FOREIGN);
    }

    private static void mark(final HeightField field, final int x, final int z, final int flag)
    {
        if (field.inside(x, z))
        {
            field.mark(x, z, flag);
        }
    }

    /**
     * Even-odd test against the wall's ring.
     */
    static boolean inside(final List<WallTrace.Point> ring, final int x, final int z)
    {
        boolean in = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++)
        {
            final WallTrace.Point p = ring.get(i);
            final WallTrace.Point q = ring.get(j);
            if ((p.z() > z) != (q.z() > z) && x < (double) (q.x() - p.x()) * (z - p.z()) / (q.z() - p.z()) + p.x())
            {
                in = !in;
            }
        }
        return in;
    }

    private static double sq(final double dx, final double dz)
    {
        return dx * dx + dz * dz;
    }
}
