package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColonyManager;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a planned route into blocks, one cell of its centre line at a time ({@link #slice}), reading the world as it
 * is at that moment — so the site can lay the road as it goes, with only the chunks around its two fronts loaded.
 * <ul>
 * <li>On the ground: a road in a corridor seven wide, cobble steps where it changes level (see docs/GOTCHA.md), the
 * ground cut or banked up to it, and three columns of shoulder each side eased toward the natural slope so the road
 * never runs in a trench nor on a wall. Trees rooted on the road or its shoulders are felled whole.</li>
 * <li>Over a hollow or water: a deck, on piers every few blocks standing on the ground or the river bed.</li>
 * <li>Through a rise: an arched bore five high at the crown, a portal at each end, frames and lanterns along it, and
 * stone wherever the rock is missing or would not hold (sand, gravel, water, a cave).</li>
 * </ul>
 * The shape never depends on the level; what is laid along it does ({@link RoadStyle}, {@link RoadDecor}). Raising
 * a road one level runs the builder again over the same plan with the next style: the blocks of the old style are
 * then cleared or replaced. Nothing a player built is ever touched: such a column is left as it stands.
 */
public final class RouteBuilder implements RouteWork
{
    private static final int REACH = 7;
    static final int ROAD = 12;
    static final int RING_1 = 20;
    static final int RING_2 = 30;
    static final int RING_3 = 42;

    private static final int PIER_EVERY = 6;
    private static final int TIE_EVERY = 8;
    static final int MAX_PIER = 46;
    private static final int ABUTMENT_MAX = 6;
    private static final int SUPPORT_EVERY = 6;
    static final int MAX_FILL = 10;
    private static final int FLIGHT_GAP = 3;

    static final int PIER = 1;
    static final int BRACE_TO_NEXT = 2;
    static final int BRACE_TO_PREVIOUS = 4;
    static final int END = 8;
    static final int PORTAL = 16;
    private static final int LINED = 32;
    private static final int SUPPORT = 64;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW.defaultBlockState();

    final ServerLevel level;
    final RoutePlan plan;
    final RoadStyle style;
    /** The registry index of the road being rebuilt one level up, -1 for a new road. */
    final int rebuilding;
    final int n;
    final Direction[] travel;
    private final Direction[] stairs;
    private final boolean[] paved;
    final int[] roles;
    /** On a bridge: how many cells to the nearest pier or bank (the arch is deepest next to them). */
    private final int[] support;
    private final Set<Long> felled = new HashSet<>();
    private final Set<Long> falling = new HashSet<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final RoadDecor decor;

    public RouteBuilder(final ServerLevel level, final RoutePlan plan)
    {
        this(level, plan, RoadStyle.PATH, -1);
    }

    /**
     * @param war a war road: built straight in the war style, otherwise a road at level 1
     */
    public RouteBuilder(final ServerLevel level, final RoutePlan plan, final boolean war)
    {
        this(level, plan, war ? RoadStyle.WAR : RoadStyle.PATH, -1);
    }

    /**
     * @param rebuilding the registry index of the road this plan already stands as (raised to this style), or -1
     */
    public RouteBuilder(final ServerLevel level, final RoutePlan plan, final RoadStyle style, final int rebuilding)
    {
        this.style = style;
        this.rebuilding = rebuilding;
        this.level = level;
        this.plan = plan;
        this.n = plan.length();
        this.travel = new Direction[n];
        this.stairs = new Direction[n];
        this.paved = new boolean[n];
        this.roles = new int[n];
        this.support = new int[n];
        Arrays.fill(support, Integer.MAX_VALUE);
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
        this.decor = new RoadDecor(this);
    }

    private void bridge(final int first, final int last)
    {
        roles[first] |= END;
        roles[last] |= END;
        final int length = last - first + 1;
        final int piers = (length - 1) / PIER_EVERY;
        final List<Integer> held = new ArrayList<>();
        held.add(first);
        held.add(last);
        for (int k = 1; k <= piers; k++)
        {
            final int at = first + Math.round(k * length / (float) (piers + 1));
            roles[at] |= PIER;
            held.add(at);
            if (at - 1 > first)
            {
                roles[at - 1] |= BRACE_TO_NEXT;
            }
            if (at + 1 < last)
            {
                roles[at + 1] |= BRACE_TO_PREVIOUS;
            }
        }
        for (int c = first; c <= last; c++)
        {
            for (final int h : held)
            {
                support[c] = Math.min(support[c], Math.abs(c - h));
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

    @Override
    public int cells()
    {
        return n;
    }

    @Override
    public BlockPos cell(final int i)
    {
        return new BlockPos(plan.x[i], plan.y[i], plan.z[i]);
    }

    public RoutePlan plan()
    {
        return plan;
    }

    /**
     * Every block to set for one cell of the centre line: the columns of road, shoulder and structure nearest to it.
     */
    @Override
    public List<RouteStep> slice(final int i)
    {
        final List<RouteStep> out = new ArrayList<>(256);
        // Rebuilt without the third-party claims it was planned around: a cell on a colony's land keeps what it has
        if (rebuilding >= 0 && IColonyManager.getInstance().getColonyByPosFromWorld(level, new BlockPos(plan.x[i], plan.y[i], plan.z[i])) != null)
        {
            return out;
        }
        final RouteRegistry registry = RouteRegistry.get(level.getServer());
        final Direction dir = travel[i];
        // A cell riding a road that is already there (its steps, a deck) adds nothing beside it: no bank is raised
        // along an existing bridge, no shoulder cut twice
        final boolean riding = plan.mode[i] == RoutePlan.SURFACE && probe(plan.x[i], plan.z[i]).blocked();
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
                    case RoutePlan.BRIDGE -> deckColumn(out, i, x, z, d2, side, along == 0);
                    case RoutePlan.TUNNEL -> boreColumn(out, i, x, z, d2, side, along == 0);
                    default ->
                    {
                        // Slabs already laid by another road at its own level: laying ours over them doubles them a
                        // block or two off. Only on the surface: a deck or a bore over/under that road is still needed.
                        if (inOtherBand(registry, x, z))
                        {
                            continue;
                        }
                        if (plan.footprint.road(x, z))
                        {
                            roadColumn(out, i, x, z, d2, Math.min(3, side));
                        }
                        else if (!riding)
                        {
                            final int k = d2 <= RING_1 ? 1 : d2 <= RING_2 ? 2 : 3;
                            shoulderColumn(out, x, z, plan.y[i], k);
                            decor.shoulder(out, i, x, z, d2, side);
                        }
                    }
                }
            }
        }
        fellMarked(out);
        if (plan.mode[i] == RoutePlan.SURFACE && !riding)
        {
            decor.along(out, i);
        }
        if ((roles[i] & PORTAL) != 0)
        {
            decor.portal(out, i);
        }
        return out;
    }

    boolean inOtherBand(final RouteRegistry registry, final int x, final int z)
    {
        return rebuilding < 0 ? registry.inBand(x, z) : registry.inBand(x, z, rebuilding);
    }

    /**
     * The column as the road sees it; when rebuilding, the blocks the road laid itself are looked through, so its old
     * surface and decor read as the ground they stand on and not as something a player built.
     */
    RouteProbe.Column probe(final int x, final int z)
    {
        return rebuilding < 0 ? RouteProbe.of(level, x, z) : RouteProbe.ofIgnoring(level, x, z, RouteBuilder::laid);
    }

    /** A block of an earlier style of this road (not ground that happens to share its material). */
    static boolean laid(final BlockState state)
    {
        return RoadStyle.ours(state) && !Terrain.natural(state);
    }

    private void roadColumn(final List<RouteStep> out, final int i, final int x, final int z, final int d2, final int side)
    {
        final RouteProbe.Column column = probe(x, z);
        if (column.blocked() || column.lava())
        {
            return;
        }
        final int y = plan.y[i];
        if (!clearAbove(out, x, z, y, top(x, z)))
        {
            return;
        }
        BlockState cap = stairs[i] != null ? style.step(x, z).setValue(StairBlock.FACING, stairs[i])
                                           : paved[i] ? style.paving(x, z, side) : style.surface(x, z, d2, side, i % 5 == 0);
        if (cap == null)
        {
            if (column.ground() == y && !column.wet())
            {
                // Ragged edge: the ground is already at the road's level, it stays as it is
                return;
            }
            cap = style.verge();
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
        final RouteProbe.Column column = probe(x, z);
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
            if (rebuilding >= 0 && laid(state))
            {
                out.add(new RouteStep(cursor.immutable(), AIR));
            }
            else if (state.is(BlockTags.LOGS))
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

    /**
     * Height of the ground of a shoulder column once the road has eased it (see {@link #shoulderColumn}), or
     * Integer.MIN_VALUE when nothing may stand there (water, lava, someone's build).
     */
    int easedGround(final int x, final int z, final int y, final int k)
    {
        final RouteProbe.Column column = probe(x, z);
        if (column.blocked() || column.lava() || column.wet())
        {
            return Integer.MIN_VALUE;
        }
        final int ground = column.ground();
        if (ground > y + k)
        {
            return y + k;
        }
        if (ground < y - k && y - k - ground <= MAX_FILL)
        {
            return y - k;
        }
        return ground;
    }

    private void deckColumn(final List<RouteStep> out, final int i, final int x, final int z, final int d2, final int side, final boolean square)
    {
        if (d2 > RING_1)
        {
            return;
        }
        // Rebuilding, an older deck's own blocks are never the ground, not even the earth of a war road's ruts
        final RouteProbe.Column column = rebuilding < 0 ? RouteProbe.of(level, x, z) : RouteProbe.ofIgnoring(level, x, z, RoadStyle::ours);
        if (column.blocked() || column.lava())
        {
            return;
        }
        final int y = plan.y[i];
        final int top = top(x, z);
        if (d2 > ROAD)
        {
            // Beside the deck: only what reaches up to it goes (a tree top, a branch, an older style's decor)
            for (int yy = top; yy >= y; yy--)
            {
                final BlockState state = level.getBlockState(cursor.set(x, yy, z));
                if (rebuilding >= 0 && laid(state))
                {
                    out.add(new RouteStep(cursor.immutable(), AIR));
                }
                else if (state.is(BlockTags.LOGS) && Terrain.treeLog(level, cursor))
                {
                    fell(cursor.immutable());
                }
                else if (state.getBlock() instanceof LeavesBlock && Terrain.clearable(level, cursor, state))
                {
                    out.add(new RouteStep(cursor.immutable(), AIR));
                }
            }
            if (rebuilding >= 0)
            {
                clearLaidBelow(out, x, z, y, column);
            }
            if (square && side == 4)
            {
                decor.cutwater(out, i, x, z, column);
            }
            return;
        }
        if (!clearAbove(out, x, z, y, top))
        {
            return;
        }
        if (rebuilding >= 0)
        {
            clearLaidBelow(out, x, z, y, column);
        }
        if (style.timberBridge())
        {
            timberDeck(out, i, x, z, d2, column);
        }
        else
        {
            stoneDeck(out, i, x, z, d2, side, column);
        }
    }

    /** Down from under the deck, an older deck's masonry and piles go, as far as the first block of real ground. */
    private void clearLaidBelow(final List<RouteStep> out, final int x, final int z, final int y, final RouteProbe.Column column)
    {
        // Under the river's surface a pier gives its place back to the water, not to a pocket of air
        final int waterTop = column.wet() ? column.ground() : Integer.MIN_VALUE;
        for (int yy = y - 1; yy > Math.min(column.base(), y - 1) - MAX_PIER && yy > level.getMinBuildHeight(); yy--)
        {
            final BlockState state = level.getBlockState(cursor.set(x, yy, z));
            if (laid(state))
            {
                out.add(new RouteStep(cursor.immutable(), yy <= waterTop ? Blocks.WATER.defaultBlockState() : AIR));
            }
            else if (!state.isAir() && state.getFluidState().isEmpty())
            {
                return;
            }
        }
    }

    /** Level 1 and 2: a timber deck on trestles (level 1: three planks wide, no rail) or on stone piers (level 2). */
    private void timberDeck(final List<RouteStep> out, final int i, final int x, final int z, final int d2, final RouteProbe.Column column)
    {
        final boolean path = style == RoadStyle.PATH;
        final int y = plan.y[i];
        if (path && d2 > 2)
        {
            // The plank way is three wide: the rest of a wider deck it replaces goes
            if (rebuilding >= 0 && RoadStyle.ours(level.getBlockState(cursor.set(x, y, z))))
            {
                out.add(new RouteStep(cursor.immutable(), AIR));
            }
            return;
        }
        final Direction dir = travel[i] == null ? Direction.EAST : travel[i];
        final boolean edge = path ? d2 >= 1 : d2 >= 9;
        final int role = roles[i];
        final int base = column.base();
        final BlockState timber = style.timber();
        boolean held = false;
        if ((role & END) != 0 && !column.wet() && y - base <= ABUTMENT_MAX)
        {
            for (int yy = base + 1; yy < y; yy++)
            {
                out.add(new RouteStep(new BlockPos(x, yy, z), style.masonry(x, yy, z)));
            }
            held = true;
        }
        else if ((role & PIER) != 0 && y - 1 > base && y - base <= MAX_PIER)
        {
            if (path)
            {
                if (edge)
                {
                    for (int yy = base + 1; yy < y; yy++)
                    {
                        out.add(new RouteStep(new BlockPos(x, yy, z), timber));
                    }
                }
                else
                {
                    out.add(new RouteStep(new BlockPos(x, y - 1, z), timber.setValue(RotatedPillarBlock.AXIS, dir.getClockWise().getAxis())));
                }
            }
            else
            {
                // A stone pier under the whole deck, a beam across on top of it
                for (int yy = base + 1; yy < y - 1; yy++)
                {
                    out.add(new RouteStep(new BlockPos(x, yy, z), style.masonry(x, yy, z)));
                }
                out.add(new RouteStep(new BlockPos(x, y - 1, z), timber.setValue(RotatedPillarBlock.AXIS, dir.getClockWise().getAxis())));
            }
            held = true;
        }
        if (!path && edge && !held && (role & (BRACE_TO_NEXT | BRACE_TO_PREVIOUS)) != 0 && y - 1 > base)
        {
            out.add(new RouteStep(new BlockPos(x, y - 1, z), style.deckStep().setValue(StairBlock.HALF, Half.TOP)
                                                                    .setValue(StairBlock.FACING, (role & BRACE_TO_NEXT) != 0 ? dir : dir.getOpposite())));
        }
        final BlockState deck;
        if (stairs[i] != null)
        {
            deck = style.deckStep().setValue(StairBlock.FACING, stairs[i]);
        }
        else
        {
            deck = edge && !path ? timber.setValue(RotatedPillarBlock.AXIS, dir.getAxis()) : style.planks();
        }
        out.add(new RouteStep(new BlockPos(x, y, z), deck));
        if (edge && !path)
        {
            out.add(new RouteStep(new BlockPos(x, y + 1, z), style.rail()));
            if ((role & (PIER | END)) != 0)
            {
                out.add(new RouteStep(new BlockPos(x, y + 2, z), style.lantern()));
            }
        }
    }

    /** Level 3 and up, and war: masonry arches springing from the piers, a dressed face and a parapet. */
    private void stoneDeck(final List<RouteStep> out, final int i, final int x, final int z, final int d2, final int side,
      final RouteProbe.Column column)
    {
        final int y = plan.y[i];
        final boolean edge = d2 >= 9;
        final int role = roles[i];
        final int base = column.base();
        if ((role & (PIER | END)) != 0 && y - base <= MAX_PIER)
        {
            for (int yy = base + 1; yy < y; yy++)
            {
                out.add(new RouteStep(new BlockPos(x, yy, z), yy == y - 1 && edge ? style.dressed(x, yy, z) : style.masonry(x, yy, z)));
            }
        }
        else
        {
            final int a = support[i];
            final int rows = Math.min(y - 1 - base, a <= 1 ? 3 : a == 2 ? 2 : 1);
            for (int yy = y - rows; yy < y; yy++)
            {
                out.add(new RouteStep(new BlockPos(x, yy, z), yy == y - 1 && edge ? style.dressed(x, yy, z) : style.masonry(x, yy, z)));
            }
        }
        final BlockState deck;
        if (stairs[i] != null)
        {
            deck = style.step(x, z).setValue(StairBlock.FACING, stairs[i]);
        }
        else
        {
            deck = edge ? style.dressed(x, y, z) : style.paving(x, z, Math.min(2, side));
        }
        out.add(new RouteStep(new BlockPos(x, y, z), deck));
        if (edge)
        {
            decor.parapet(out, i, x, z);
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
                out.add(new RouteStep(new BlockPos(x, y + height + 1, z), style.keystone()));
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
            out.add(new RouteStep(new BlockPos(x, y - 1, z), style.masonry(x, y - 1, z)));
        }
        out.add(new RouteStep(new BlockPos(x, y, z), stairs[i] != null ? style.step(x, z).setValue(StairBlock.FACING, stairs[i]) : style.paving(x, z, across)));
        final BlockState hanging = style.lantern().setValue(LanternBlock.HANGING, true);
        if ((role & SUPPORT) != 0)
        {
            final Direction dir = travel[i] == null ? Direction.EAST : travel[i];
            final boolean timber = style.timberBridge();
            if (across == 3)
            {
                for (int yy = y + 1; yy <= y + 3; yy++)
                {
                    out.add(new RouteStep(new BlockPos(x, yy, z), timber ? style.timber() : style.dressed(x, yy, z)));
                }
            }
            else
            {
                out.add(new RouteStep(new BlockPos(x, y + 4, z), timber ? style.timber().setValue(RotatedPillarBlock.AXIS, dir.getClockWise().getAxis())
                                                                        : style.dressed(x, y + 4, z)));
                if (across == 0)
                {
                    out.add(new RouteStep(new BlockPos(x, y + 3, z), hanging));
                }
            }
        }
        else if ((role & LINED) != 0 && across == 0)
        {
            out.add(new RouteStep(new BlockPos(x, y + height, z), hanging));
        }
    }

    /**
     * Stone where the bore needs a wall or a roof: always when lined, otherwise only where the rock is missing or
     * would not hold.
     */
    private void line(final List<RouteStep> out, final int x, final int y, final int z, final boolean always)
    {
        if (always || !sound(x, y, z) || rebuilding >= 0 && laid(level.getBlockState(cursor.set(x, y, z))))
        {
            out.add(new RouteStep(new BlockPos(x, y, z), style.masonry(x, y, z)));
        }
    }

    private boolean sound(final int x, final int y, final int z)
    {
        final BlockState state = level.getBlockState(cursor.set(x, y, z));
        return state.getFluidState().isEmpty() && !(state.getBlock() instanceof FallingBlock) && state.isCollisionShapeFullBlock(level, cursor);
    }

    int top(final int x, final int z)
    {
        // Through the chunk: asked of the level, an unloaded chunk answers the bottom of the world
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    /**
     * Clears a column from the top down to just above fromY: plants, earth and rock go, a tree rooted or reaching
     * there is felled whole, and when rebuilding the old style's blocks too.
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
            else if (!state.getFluidState().isEmpty() || Terrain.clearable(level, cursor, state) || Terrain.natural(state)
                       || rebuilding >= 0 && laid(state))
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
}
