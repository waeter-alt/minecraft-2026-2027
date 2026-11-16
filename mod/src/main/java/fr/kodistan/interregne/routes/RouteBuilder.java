package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.routes.plan.RoutePlan;
import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.world.TreeFelling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a planned route into blocks, one cell of its centre line at a time ({@link #slice}), reading the world as it
 * is at that moment — so the site can lay the road as it goes, with only the chunks around its two fronts loaded.
 * <ul>
 * <li>On the ground: a trodden path seven wide, ragged at the edges, cobble steps where it changes level (see
 * docs/GOTCHA.md), the ground cut or banked up to it, and three columns of shoulder each side eased toward the
 * natural slope so the road never runs in a trench nor on a wall. Trees rooted on the road or its shoulders are felled
 * whole.</li>
 * <li>Over a hollow or water: a timber deck between two rails, on trestles every few blocks standing on the ground
 * or the river bed, stone under its two ends where the bank is near.</li>
 * <li>Through a rise: an arched bore five high at the crown, a stone portal at each end, timber frames and lanterns
 * along it, and stone wherever the rock is missing or would not hold (sand, gravel, water, a cave).</li>
 * </ul>
 * Nothing a player built is ever touched: such a column is left as it stands.
 */
public final class RouteBuilder
{
    private static final int REACH = 7;
    private static final int ROAD = 12;
    private static final int RING_1 = 20;
    private static final int RING_2 = 30;
    private static final int RING_3 = 42;

    private static final int PIER_EVERY = 6;
    private static final int TIE_EVERY = 8;
    private static final int MAX_PIER = 46;
    private static final int ABUTMENT_MAX = 6;
    private static final int SUPPORT_EVERY = 6;
    private static final int MAX_FILL = 10;
    private static final int FLIGHT_GAP = 3;

    private static final int PIER = 1;
    private static final int BRACE_TO_NEXT = 2;
    private static final int BRACE_TO_PREVIOUS = 4;
    private static final int END = 8;
    private static final int PORTAL = 16;
    private static final int LINED = 32;
    private static final int SUPPORT = 64;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();
    private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW.defaultBlockState();
    private static final BlockState STEP = Blocks.COBBLESTONE_STAIRS.defaultBlockState();
    private static final BlockState MOSSY_STEP = Blocks.MOSSY_COBBLESTONE_STAIRS.defaultBlockState();
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState TIMBER = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState DECK_STEP = Blocks.SPRUCE_STAIRS.defaultBlockState();
    private static final BlockState RAIL = Blocks.SPRUCE_FENCE.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
    private static final BlockState HANGING_LANTERN = LANTERN.setValue(LanternBlock.HANGING, true);
    private static final BlockState MASONRY = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY_MASONRY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED_MASONRY = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState KEYSTONE = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();

    private final ServerLevel level;
    private final RoutePlan plan;
    private final int n;
    private final Direction[] travel;
    private final Direction[] stairs;
    private final boolean[] paved;
    private final int[] roles;
    private final Set<Long> felled = new HashSet<>();
    private final Set<Long> falling = new HashSet<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public RouteBuilder(final ServerLevel level, final RoutePlan plan)
    {
        this.level = level;
        this.plan = plan;
        this.n = plan.length();
        this.travel = new Direction[n];
        this.stairs = new Direction[n];
        this.paved = new boolean[n];
        this.roles = new int[n];
        for (int i = 0; i < n; i++)
        {
            final int from = Math.max(1, i);
            travel[i] = n < 2 ? Direction.EAST : Direction.fromDelta(plan.x[from] - plan.x[from - 1], 0, plan.z[from] - plan.z[from - 1]);
        }
        // Between two cells one level apart, the stair goes on the HIGHER one, at its height, facing the climb
        // (docs/GOTCHA.md): going down, that is the cell before the drop, facing back.
        for (int i = 1; i < n; i++)
        {
            if (travel[i] == null || plan.y[i] == plan.y[i - 1])
            {
                continue;
            }
            if (plan.y[i] > plan.y[i - 1])
            {
                stairs[i] = travel[i];
            }
            else
            {
                stairs[i - 1] = travel[i].getOpposite();
            }
        }
        // A landing between two steps close together is paved like them: a climb reads as one stone stairway, not as
        // stripes of stone across a dirt road
        int lastStair = -FLIGHT_GAP - 2;
        for (int k = 0; k < n; k++)
        {
            if (stairs[k] == null)
            {
                continue;
            }
            if (k - lastStair <= FLIGHT_GAP + 1)
            {
                for (int between = lastStair + 1; between < k; between++)
                {
                    paved[between] = plan.mode[between] == RoutePlan.SURFACE;
                }
            }
            lastStair = k;
        }
        int i = 0;
        while (i < n)
        {
            int end = i;
            while (end + 1 < n && plan.mode[end + 1] == plan.mode[i])
            {
                end++;
            }
            if (plan.mode[i] == RoutePlan.BRIDGE)
            {
                bridge(i, end);
            }
            else if (plan.mode[i] == RoutePlan.TUNNEL)
            {
                tunnel(i, end);
            }
            i = end + 1;
        }
    }

    private void bridge(final int first, final int last)
    {
        roles[first] |= END;
        roles[last] |= END;
        final int length = last - first + 1;
        final int piers = (length - 1) / PIER_EVERY;
        for (int k = 1; k <= piers; k++)
        {
            final int at = first + Math.round(k * length / (float) (piers + 1));
            roles[at] |= PIER;
            if (at - 1 > first)
            {
                roles[at - 1] |= BRACE_TO_NEXT;
            }
            if (at + 1 < last)
            {
                roles[at + 1] |= BRACE_TO_PREVIOUS;
            }
        }
    }

    private void tunnel(final int first, final int last)
    {
        roles[first] |= PORTAL;
        roles[last] |= PORTAL;
        if (last - first >= 2)
        {
            roles[first + 1] |= LINED;
            roles[last - 1] |= LINED;
        }
        for (int at = first + 4; at <= last - 4; at += SUPPORT_EVERY)
        {
            roles[at] |= SUPPORT;
        }
    }

    public int cells()
    {
        return n;
    }

    public RoutePlan plan()
    {
        return plan;
    }

    /**
     * Every block to set for one cell of the centre line: the columns of road, shoulder and structure nearest to it.
     */
    public List<RouteStep> slice(final int i)
    {
        final List<RouteStep> out = new ArrayList<>(256);
        final Direction dir = travel[i];
        // A cell riding a road that is already there (its steps, a deck) adds nothing beside it: no bank is raised
        // along an existing bridge, no shoulder cut twice
        final boolean riding = plan.mode[i] == RoutePlan.SURFACE && RouteProbe.of(level, plan.x[i], plan.z[i]).blocked();
        for (int dz = -REACH; dz <= REACH; dz++)
        {
            for (int dx = -REACH; dx <= REACH; dx++)
            {
                final int x = plan.x[i] + dx;
                final int z = plan.z[i] + dz;
                if (plan.footprint.owner(x, z) != i)
                {
                    continue;
                }
                final int d2 = plan.footprint.distanceSq(x, z);
                if (d2 > RING_3)
                {
                    continue;
                }
                final int along = dir == null ? 1 : dx * dir.getStepX() + dz * dir.getStepZ();
                final int side = along == 0 ? Math.abs(dx * dir.getStepZ() - dz * dir.getStepX()) : (int) Math.round(Math.sqrt(d2));
                switch (plan.mode[i])
                {
                    case RoutePlan.BRIDGE -> deckColumn(out, i, x, z, d2);
                    case RoutePlan.TUNNEL -> boreColumn(out, i, x, z, d2, side, along == 0);
                    default ->
                    {
                        if (plan.footprint.road(x, z))
                        {
                            roadColumn(out, i, x, z, d2);
                        }
                        else if (!riding)
                        {
                            shoulderColumn(out, x, z, plan.y[i], d2 <= RING_1 ? 1 : d2 <= RING_2 ? 2 : 3);
                        }
                    }
                }
            }
        }
        fellMarked(out);
        return out;
    }

    private void roadColumn(final List<RouteStep> out, final int i, final int x, final int z, final int d2)
    {
        final RouteProbe.Column column = RouteProbe.of(level, x, z);
        if (column.blocked() || column.lava())
        {
            return;
        }
        final int y = plan.y[i];
        if (!clearAbove(out, x, z, y, top(x, z)))
        {
            return;
        }
        BlockState cap = stairs[i] != null ? step(x, z).setValue(StairBlock.FACING, stairs[i]) : paved[i] ? paving(x, z) : surface(x, z, d2);
        if (cap == null)
        {
            if (column.ground() == y && !column.wet())
            {
                // Ragged edge: the ground is already at the road's level, it stays as it is
                return;
            }
            cap = COARSE;
        }
        for (int yy = Math.max(column.base() + 1, y - MAX_FILL); yy < y; yy++)
        {
            out.add(new RouteStep(new BlockPos(x, yy, z), column.wet() && yy <= column.ground() ? COBBLE : DIRT));
        }
        if (column.base() >= y && !sound(x, y - 1, z))
        {
            out.add(new RouteStep(new BlockPos(x, y - 1, z), DIRT));
        }
        out.add(new RouteStep(new BlockPos(x, y, z), cap));
    }

    /**
     * A column beside the road, k columns out: brought within k levels of the road, so a cut opens up and an
     * embankment slopes down instead of standing as a wall. The new surface takes the skin the old one had.
     */
    private void shoulderColumn(final List<RouteStep> out, final int x, final int z, final int y, final int k)
    {
        final RouteProbe.Column column = RouteProbe.of(level, x, z);
        if (column.blocked() || column.lava())
        {
            return;
        }
        if (column.wet())
        {
            if (k == 1 && column.ground() > y)
            {
                // Water standing higher than the road right beside it: dammed, or it would run onto the path
                for (int yy = column.bed() + 1; yy <= column.ground(); yy++)
                {
                    out.add(new RouteStep(new BlockPos(x, yy, z), DIRT));
                }
            }
            return;
        }
        final int ground = column.ground();
        final int top = top(x, z);
        final boolean reshaped = ground > y + k || (ground < y - k && y - k - ground <= MAX_FILL);
        for (int yy = top; yy > ground; yy--)
        {
            final BlockState state = level.getBlockState(cursor.set(x, yy, z));
            if (state.isAir())
            {
                continue;
            }
            if (state.is(BlockTags.LOGS))
            {
                if (Terrain.treeLog(level, cursor))
                {
                    fell(cursor.immutable());
                }
            }
            else if ((reshaped || (k == 1 && state.getBlock() instanceof LeavesBlock)) && Terrain.clearable(level, cursor, state))
            {
                out.add(new RouteStep(cursor.immutable(), AIR));
            }
        }
        final BlockState skin = level.getBlockState(cursor.set(x, ground, z));
        final boolean snowed = level.getBlockState(cursor.set(x, ground + 1, z)).is(Blocks.SNOW);
        if (ground > y + k)
        {
            for (int yy = ground; yy > y + k; yy--)
            {
                final BlockState state = level.getBlockState(cursor.set(x, yy, z));
                if (!state.getFluidState().isEmpty() || !(Terrain.natural(state) || Terrain.clearable(level, cursor, state)))
                {
                    return;
                }
                out.add(new RouteStep(cursor.immutable(), AIR));
            }
            final BlockState exposed = level.getBlockState(cursor.set(x, y + k, z));
            if (soil(skin) && exposed.is(BlockTags.DIRT) && !exposed.is(skin.getBlock()))
            {
                out.add(new RouteStep(cursor.immutable(), skin));
            }
            if (snowed)
            {
                out.add(new RouteStep(new BlockPos(x, y + k + 1, z), SNOW));
            }
        }
        else if (reshaped)
        {
            for (int yy = ground + 1; yy < y - k; yy++)
            {
                out.add(new RouteStep(new BlockPos(x, yy, z), DIRT));
            }
            out.add(new RouteStep(new BlockPos(x, y - k, z), soil(skin) ? skin : DIRT));
            if (snowed)
            {
                out.add(new RouteStep(new BlockPos(x, y - k + 1, z), SNOW));
            }
        }
    }

    private void deckColumn(final List<RouteStep> out, final int i, final int x, final int z, final int d2)
    {
        if (d2 > RING_1)
        {
            return;
        }
        final RouteProbe.Column column = RouteProbe.of(level, x, z);
        if (column.blocked() || column.lava())
        {
            return;
        }
        final int y = plan.y[i];
        final int top = top(x, z);
        if (d2 > ROAD)
        {
            // Beside the deck: only what reaches up to it goes (a tree top, a branch)
            for (int yy = top; yy >= y; yy--)
            {
                final BlockState state = level.getBlockState(cursor.set(x, yy, z));
                if (state.is(BlockTags.LOGS) && Terrain.treeLog(level, cursor))
                {
                    fell(cursor.immutable());
                }
                else if (state.getBlock() instanceof LeavesBlock && Terrain.clearable(level, cursor, state))
                {
                    out.add(new RouteStep(cursor.immutable(), AIR));
                }
            }
            return;
        }
        if (!clearAbove(out, x, z, y, top))
        {
            return;
        }
        final Direction dir = travel[i] == null ? Direction.EAST : travel[i];
        final boolean edge = d2 >= 9;
        final int role = roles[i];
        final int base = column.base();
        boolean held = false;
        if ((role & END) != 0 && !column.wet() && y - base <= ABUTMENT_MAX)
        {
            for (int yy = base + 1; yy < y; yy++)
            {
                out.add(new RouteStep(new BlockPos(x, yy, z), masonry(x, yy, z)));
            }
            held = true;
        }
        else if ((role & PIER) != 0 && y - 1 > base)
        {
            if (edge)
            {
                if (y - base <= MAX_PIER)
                {
                    for (int yy = base + 1; yy < y; yy++)
                    {
                        out.add(new RouteStep(new BlockPos(x, yy, z), column.wet() && yy <= column.ground() ? masonry(x, yy, z) : TIMBER));
                    }
                }
            }
            else
            {
                final BlockState across = TIMBER.setValue(RotatedPillarBlock.AXIS, dir.getClockWise().getAxis());
                for (int yy = y - 1; yy > base + 1 && y - yy <= MAX_PIER; yy -= TIE_EVERY)
                {
                    out.add(new RouteStep(new BlockPos(x, yy, z), across));
                }
            }
            held = true;
        }
        if (edge && !held && (role & (BRACE_TO_NEXT | BRACE_TO_PREVIOUS)) != 0 && y - 1 > base)
        {
            out.add(new RouteStep(new BlockPos(x, y - 1, z), DECK_STEP.setValue(StairBlock.HALF, Half.TOP)
                                                                   .setValue(StairBlock.FACING, (role & BRACE_TO_NEXT) != 0 ? dir : dir.getOpposite())));
        }
        final BlockState deck;
        if (stairs[i] != null)
        {
            deck = DECK_STEP.setValue(StairBlock.FACING, stairs[i]);
        }
        else
        {
            deck = edge ? TIMBER.setValue(RotatedPillarBlock.AXIS, dir.getAxis()) : PLANKS;
        }
        out.add(new RouteStep(new BlockPos(x, y, z), deck));
        if (edge)
        {
            out.add(new RouteStep(new BlockPos(x, y + 1, z), RAIL));
            if ((role & (PIER | END)) != 0)
            {
                out.add(new RouteStep(new BlockPos(x, y + 2, z), LANTERN));
            }
        }
    }

    /**
     * @param side   how far across the bore this column is, 0 at the centre
     * @param square whether the column lies straight across from its cell (always, on a straight bore)
     */
    private void boreColumn(final List<RouteStep> out, final int i, final int x, final int z, final int d2, final int side, final boolean square)
    {
        final int y = plan.y[i];
        final int role = roles[i];
        final boolean lined = (role & (PORTAL | LINED)) != 0;
        if (d2 > ROAD)
        {
            if (square && side == 4)
            {
                for (int yy = y + ((role & PORTAL) != 0 ? 0 : 1); yy <= y + 4; yy++)
                {
                    line(out, x, yy, z, lined);
                }
            }
            return;
        }
        final int across = Math.min(3, side);
        final int height = across <= 1 ? 5 : across == 2 ? 4 : 3;
        // The roof first, so nothing loose comes down while the bore is cut under it
        line(out, x, y + height + 1, z, lined);
        if ((role & PORTAL) != 0)
        {
            if (across >= 2)
            {
                line(out, x, y + height + 2, z, true);
            }
            else if (across == 0)
            {
                out.add(new RouteStep(new BlockPos(x, y + height + 1, z), KEYSTONE));
            }
        }
        for (int yy = y + height; yy > y; yy--)
        {
            if (!level.getBlockState(cursor.set(x, yy, z)).isAir())
            {
                out.add(new RouteStep(cursor.immutable(), AIR));
            }
        }
        if (!sound(x, y - 1, z))
        {
            out.add(new RouteStep(new BlockPos(x, y - 1, z), masonry(x, y - 1, z)));
        }
        out.add(new RouteStep(new BlockPos(x, y, z), stairs[i] != null ? step(x, z).setValue(StairBlock.FACING, stairs[i]) : floor(x, z)));
        if ((role & SUPPORT) != 0)
        {
            final Direction dir = travel[i] == null ? Direction.EAST : travel[i];
            if (across == 3)
            {
                for (int yy = y + 1; yy <= y + 3; yy++)
                {
                    out.add(new RouteStep(new BlockPos(x, yy, z), TIMBER));
                }
            }
            else
            {
                out.add(new RouteStep(new BlockPos(x, y + 4, z), TIMBER.setValue(RotatedPillarBlock.AXIS, dir.getClockWise().getAxis())));
                if (across == 0)
                {
                    out.add(new RouteStep(new BlockPos(x, y + 3, z), HANGING_LANTERN));
                }
            }
        }
        else if ((role & LINED) != 0 && across == 0)
        {
            out.add(new RouteStep(new BlockPos(x, y + height, z), HANGING_LANTERN));
        }
    }

    /**
     * Stone where the bore needs a wall or a roof: always when lined, otherwise only where the rock is missing or
     * would not hold.
     */
    private void line(final List<RouteStep> out, final int x, final int y, final int z, final boolean always)
    {
        if (always || !sound(x, y, z))
        {
            out.add(new RouteStep(new BlockPos(x, y, z), masonry(x, y, z)));
        }
    }

    private boolean sound(final int x, final int y, final int z)
    {
        final BlockState state = level.getBlockState(cursor.set(x, y, z));
        return state.getFluidState().isEmpty() && !(state.getBlock() instanceof FallingBlock) && state.isCollisionShapeFullBlock(level, cursor);
    }

    private int top(final int x, final int z)
    {
        // Through the chunk: asked of the level, an unloaded chunk answers the bottom of the world
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    /**
     * Clears a column from the top down to just above fromY: plants, earth and rock go, a tree rooted or reaching
     * there is felled whole.
     *
     * @return false when something a player built stands in it: the column is then left alone
     */
    private boolean clearAbove(final List<RouteStep> out, final int x, final int z, final int fromY, final int top)
    {
        final int start = out.size();
        for (int yy = top; yy > fromY; yy--)
        {
            final BlockState state = level.getBlockState(cursor.set(x, yy, z));
            if (state.isAir())
            {
                continue;
            }
            if (state.is(BlockTags.LOGS) && Terrain.treeLog(level, cursor))
            {
                fell(cursor.immutable());
            }
            else if (!state.getFluidState().isEmpty() || Terrain.clearable(level, cursor, state) || Terrain.natural(state))
            {
                out.add(new RouteStep(cursor.immutable(), AIR));
            }
            else
            {
                while (out.size() > start)
                {
                    out.removeLast();
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Marks the tree this log belongs to for felling; the blocks go at the end of the slice, all trees together, so
     * the leaves two neighbours shared do not stay hanging between them.
     */
    private void fell(final BlockPos log)
    {
        if (!felled.contains(log.asLong()))
        {
            TreeFelling.trunk(level, log, falling);
        }
    }

    private void fellMarked(final List<RouteStep> out)
    {
        if (falling.isEmpty())
        {
            return;
        }
        final Set<Long> leaves = new HashSet<>();
        TreeFelling.orphans(level, falling, leaves);
        for (final long packed : leaves)
        {
            out.add(new RouteStep(BlockPos.of(packed), AIR));
        }
        for (final long packed : falling)
        {
            if (felled.add(packed))
            {
                out.add(new RouteStep(BlockPos.of(packed), AIR));
            }
        }
        falling.clear();
    }

    private static boolean soil(final BlockState state)
    {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK);
    }

    /**
     * The road's surface: trodden earth at the centre, more and more coarse dirt toward the edges, and
     * null now and then on the outermost column — the ground left as it is, so the edge is not a ruled line.
     */
    @Nullable
    private static BlockState surface(final int x, final int z, final int d2)
    {
        final float v = noise(x, z, 1);
        if (d2 <= 4)
        {
            return v < 0.94F ? PATH : COARSE;
        }
        if (d2 <= 8)
        {
            return v < 0.85F ? PATH : COARSE;
        }
        return v < 0.55F ? PATH : v < 0.80F ? COARSE : null;
    }

    private static BlockState floor(final int x, final int z)
    {
        return noise(x, z, 2) < 0.85F ? PATH : COARSE;
    }

    private static BlockState paving(final int x, final int z)
    {
        return noise(x, z, 3) < 0.25F ? MOSSY_COBBLE : COBBLE;
    }

    private static BlockState step(final int x, final int z)
    {
        return noise(x, z, 3) < 0.25F ? MOSSY_STEP : STEP;
    }

    private static BlockState masonry(final int x, final int y, final int z)
    {
        final float v = noise(x * 31 + y, z, 4);
        return v < 0.12F ? MOSSY_MASONRY : v < 0.20F ? CRACKED_MASONRY : MASONRY;
    }

    private static float noise(final int x, final int z, final int salt)
    {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
