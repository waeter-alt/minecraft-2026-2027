package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.routes.plan.RoutePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.List;

/**
 * Everything a road's level adds beside and over its shape: lampposts, walls, hedges, colonnades and palisades along
 * the ground, the parapet and cutwaters of a stone bridge, the facade over each portal. One style at a time
 * ({@link RoadStyle}); the concept renders of 2026-11-26 are the reference for each.
 */
final class RoadDecor
{
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final RouteBuilder b;
    private final RoutePlan plan;
    private final RoadStyle style;
    /** Surface cells with no bridge or bore within three cells: only there does decor stand along the road. */
    private final boolean[] open;

    RoadDecor(final RouteBuilder builder)
    {
        this.b = builder;
        this.plan = builder.plan;
        this.style = builder.style;
        this.open = new boolean[builder.n];
        for (int i = 0; i < builder.n; i++)
        {
            boolean ok = plan.mode[i] == RoutePlan.SURFACE;
            for (int k = Math.max(0, i - 3); ok && k <= Math.min(builder.n - 1, i + 3); k++)
            {
                ok = plan.mode[k] == RoutePlan.SURFACE;
            }
            open[i] = ok && i >= 3 && i < builder.n - 3;
        }
    }

    // ------------------------------------------------------------------ along the ground

    /** Decor on one shoulder column (walls, hedges, palisades, the imperial pavement). */
    void shoulder(final List<RouteStep> out, final int i, final int x, final int z, final int d2, final int side)
    {
        if (!open[i])
        {
            return;
        }
        final int y = plan.y[i];
        switch (style)
        {
            case IMPERIAL ->
            {
                if (d2 <= RouteBuilder.RING_1)
                {
                    final RouteProbe.Column column = b.probe(x, z);
                    if (!column.blocked() && !column.wet() && !column.lava() && column.ground() >= y - 1 && column.ground() <= y)
                    {
                        put(out, x, y, z, state(Blocks.POLISHED_ANDESITE));
                    }
                }
                else if (d2 >= 24 && d2 <= 27 && i % 20 >= 2)
                {
                    final int g = b.easedGround(x, z, y, 2);
                    if (g != Integer.MIN_VALUE)
                    {
                        put(out, x, g + 1, z, state(Blocks.OAK_LEAVES).setValue(LeavesBlock.PERSISTENT, true));
                    }
                }
            }
            case ROYAL ->
            {
                if (d2 >= 24 && d2 <= 27 && i % 16 >= 2)
                {
                    final int g = b.easedGround(x, z, y, 2);
                    if (g != Integer.MIN_VALUE)
                    {
                        put(out, x, g + 1, z, RoadStyle.noise(x, z, 6) < 0.35F ? state(Blocks.MOSSY_COBBLESTONE_WALL) : state(Blocks.COBBLESTONE_WALL));
                    }
                }
            }
            case WAR ->
            {
                if (d2 >= 34 && d2 <= 38 && i % 24 < 6)
                {
                    final int g = b.easedGround(x, z, y, 3);
                    if (g != Integer.MIN_VALUE)
                    {
                        put(out, x, g + 1, z, state(Blocks.DARK_OAK_FENCE));
                        if (((x + z) & 1) == 0)
                        {
                            put(out, x, g + 2, z, state(Blocks.DARK_OAK_FENCE));
                        }
                    }
                }
            }
            default ->
            {
            }
        }
    }

    /** Decor standing at a given cell: lampposts, milestones, a wayside cross, banner poles at a bridge head. */
    void along(final List<RouteStep> out, final int i)
    {
        final Direction dir = b.travel[i];
        if (dir == null)
        {
            return;
        }
        final int y = plan.y[i];
        if (style == RoadStyle.ROYAL && (i + 1 < b.n && plan.mode[i + 1] == RoutePlan.BRIDGE || i > 0 && plan.mode[i - 1] == RoutePlan.BRIDGE))
        {
            final int g = stand(i, -1, 4);
            if (g != Integer.MIN_VALUE)
            {
                final BlockPos pole = at(i, -1, 4);
                for (int k = 1; k <= 5; k++)
                {
                    put(out, pole.getX(), g + k, pole.getZ(), state(Blocks.SPRUCE_LOG));
                }
                banner(out, i, -1, 5, g + 4, Blocks.RED_WALL_BANNER);
            }
        }
        if (!open[i])
        {
            return;
        }
        final int alternate = (i / 10) % 2 == 0 ? 1 : -1;
        switch (style)
        {
            case GRAVEL ->
            {
                if (i % 10 == 5)
                {
                    post(out, i, alternate, 4, state(Blocks.DARK_OAK_FENCE), 2, style.lantern());
                }
            }
            case PAVED ->
            {
                if (i % 8 == 4)
                {
                    post(out, i, 1, 4, state(Blocks.STONE_BRICK_WALL), 2, style.lantern());
                    post(out, i, -1, 4, state(Blocks.STONE_BRICK_WALL), 2, style.lantern());
                }
                if (i % 48 == 20)
                {
                    post(out, i, 1, 5, state(Blocks.CHISELED_STONE_BRICKS), 1, state(Blocks.STONE_BRICK_SLAB));
                }
            }
            case ROYAL ->
            {
                if (i % 10 == 5)
                {
                    gallows(out, i, alternate, state(Blocks.SPRUCE_LOG), state(Blocks.SPRUCE_SLAB), null);
                }
                if (i % 96 == 48)
                {
                    cross(out, i, -1, 6);
                }
            }
            case IMPERIAL ->
            {
                if (i % 5 == 0)
                {
                    for (final int sign : new int[] {1, -1})
                    {
                        final int g = stand(i, sign, 4);
                        if (g == Integer.MIN_VALUE)
                        {
                            continue;
                        }
                        final BlockPos p = at(i, sign, 4);
                        for (int k = 1; k <= 4; k++)
                        {
                            put(out, p.getX(), g + k, p.getZ(), state(Blocks.QUARTZ_PILLAR));
                        }
                        put(out, p.getX(), g + 5, p.getZ(), state(Blocks.CHISELED_QUARTZ_BLOCK));
                        put(out, p.getX(), g + 6, p.getZ(), style.lantern());
                        if (i % 10 == 0)
                        {
                            banner(out, i, sign, 5, g + 3, Blocks.WHITE_WALL_BANNER);
                        }
                    }
                }
            }
            case WAR ->
            {
                if (i % 12 == 6)
                {
                    gallows(out, i, alternate, state(Blocks.POLISHED_BLACKSTONE_WALL), state(Blocks.POLISHED_BLACKSTONE_SLAB), Blocks.RED_WALL_BANNER);
                }
                if (i % 36 == 18)
                {
                    post(out, i, -alternate, 5, state(Blocks.POLISHED_BLACKSTONE), 1, state(Blocks.SOUL_CAMPFIRE));
                }
            }
            default ->
            {
            }
        }
    }

    /** A post of `height` blocks of `body` with `crown` on top, on the ground `side` columns out. */
    private void post(final List<RouteStep> out, final int i, final int sign, final int side, final BlockState body, final int height,
      final BlockState crown)
    {
        final int g = stand(i, sign, side);
        if (g == Integer.MIN_VALUE)
        {
            return;
        }
        final BlockPos p = at(i, sign, side);
        for (int k = 1; k <= height; k++)
        {
            put(out, p.getX(), g + k, p.getZ(), body);
        }
        put(out, p.getX(), g + height + 1, p.getZ(), crown);
    }

    /**
     * A post four high on the shoulder with an arm reaching over the edge of the road, a lantern hanging from it;
     * the arm meets the top of the post and the lantern's chain meets the arm.
     */
    private void gallows(final List<RouteStep> out, final int i, final int sign, final BlockState body, final BlockState arm,
      final Block banner)
    {
        final int g = stand(i, sign, 4);
        if (g == Integer.MIN_VALUE || g < plan.y[i])
        {
            return;
        }
        final BlockPos p = at(i, sign, 4);
        final BlockPos over = at(i, sign, 3);
        for (int k = 1; k <= 4; k++)
        {
            put(out, p.getX(), g + k, p.getZ(), body);
        }
        put(out, over.getX(), g + 4, over.getZ(), arm);
        put(out, over.getX(), g + 3, over.getZ(), style.lantern().setValue(LanternBlock.HANGING, true));
        if (banner != null)
        {
            banner(out, i, sign, 5, g + 3, banner);
        }
    }

    private void cross(final List<RouteStep> out, final int i, final int sign, final int side)
    {
        final int g = stand(i, sign, side);
        if (g == Integer.MIN_VALUE)
        {
            return;
        }
        final BlockPos p = at(i, sign, side);
        final Direction dir = b.travel[i];
        put(out, p.getX(), g, p.getZ(), state(Blocks.MOSSY_COBBLESTONE));
        for (int k = 1; k <= 3; k++)
        {
            put(out, p.getX(), g + k, p.getZ(), state(Blocks.SPRUCE_FENCE));
        }
        put(out, p.getX() + dir.getStepX(), g + 3, p.getZ() + dir.getStepZ(), state(Blocks.SPRUCE_FENCE));
        put(out, p.getX() - dir.getStepX(), g + 3, p.getZ() - dir.getStepZ(), state(Blocks.SPRUCE_FENCE));
    }

    /** A wall banner hung on the outer face of whatever stands `side - 1` columns out, facing away from the road. */
    private void banner(final List<RouteStep> out, final int i, final int sign, final int side, final int y, final Block banner)
    {
        final BlockPos p = at(i, sign, side);
        put(out, p.getX(), y, p.getZ(), banner.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, across(i, sign)));
    }

    /**
     * Ground height a piece of decor stands on, `side` columns out on that side, or Integer.MIN_VALUE when it may not
     * stand there (out of the road's corridor, on another road, on water or someone's build, or too far off level).
     */
    private int stand(final int i, final int sign, final int side)
    {
        final BlockPos p = at(i, sign, side);
        if (plan.footprint.owner(p.getX(), p.getZ()) < 0 || plan.footprint.road(p.getX(), p.getZ())
              || b.inOtherBand(RouteRegistry.get(b.level.getServer()), p.getX(), p.getZ()))
        {
            return Integer.MIN_VALUE;
        }
        final int d2 = plan.footprint.distanceSq(p.getX(), p.getZ());
        final int k = d2 <= RouteBuilder.RING_1 ? 1 : d2 <= RouteBuilder.RING_2 ? 2 : 3;
        final int g = b.easedGround(p.getX(), p.getZ(), plan.y[i], k);
        return g == Integer.MIN_VALUE || Math.abs(g - plan.y[i]) > 1 ? Integer.MIN_VALUE : g;
    }

    private Direction across(final int i, final int sign)
    {
        final Direction dir = b.travel[i] == null ? Direction.EAST : b.travel[i];
        return sign > 0 ? dir.getClockWise() : dir.getCounterClockWise();
    }

    private BlockPos at(final int i, final int sign, final int side)
    {
        final Direction a = across(i, sign);
        return new BlockPos(plan.x[i] + a.getStepX() * side, plan.y[i], plan.z[i] + a.getStepZ() * side);
    }

    // ------------------------------------------------------------------ stone bridges

    /** The parapet over one edge column of a stone deck. */
    void parapet(final List<RouteStep> out, final int i, final int x, final int z)
    {
        final int y = plan.y[i];
        final int role = b.roles[i];
        final boolean refuge = (role & RouteBuilder.PIER) != 0;
        final boolean end = (role & RouteBuilder.END) != 0;
        switch (style)
        {
            case PAVED ->
            {
                if (end)
                {
                    put(out, x, y + 1, z, style.keystone());
                    put(out, x, y + 2, z, style.lantern());
                }
                else
                {
                    put(out, x, y + 1, z, state(Blocks.STONE_BRICK_WALL));
                }
            }
            case ROYAL ->
            {
                if (end)
                {
                    put(out, x, y + 1, z, style.keystone());
                    put(out, x, y + 2, z, style.lantern());
                }
                else if (!refuge)
                {
                    put(out, x, y + 1, z, style.masonry(x, y + 1, z));
                    put(out, x, y + 2, z, state(Blocks.STONE_BRICK_SLAB));
                }
            }
            case IMPERIAL ->
            {
                if (end)
                {
                    put(out, x, y + 1, z, state(Blocks.CHISELED_QUARTZ_BLOCK));
                    put(out, x, y + 2, z, state(Blocks.QUARTZ_PILLAR));
                    put(out, x, y + 3, z, state(Blocks.QUARTZ_PILLAR));
                    put(out, x, y + 4, z, state(Blocks.CHISELED_QUARTZ_BLOCK));
                    put(out, x, y + 5, z, style.lantern());
                }
                else if (!refuge)
                {
                    if (i % 6 == 3)
                    {
                        put(out, x, y + 1, z, state(Blocks.QUARTZ_PILLAR));
                        put(out, x, y + 2, z, state(Blocks.QUARTZ_PILLAR));
                        put(out, x, y + 3, z, style.lantern());
                    }
                    else
                    {
                        put(out, x, y + 1, z, state(Blocks.IRON_BARS));
                        put(out, x, y + 2, z, state(Blocks.SMOOTH_QUARTZ_SLAB));
                    }
                }
            }
            case WAR ->
            {
                put(out, x, y + 1, z, state(Blocks.POLISHED_BLACKSTONE_BRICKS));
                if (i % 2 == 0)
                {
                    put(out, x, y + 2, z, state(Blocks.POLISHED_BLACKSTONE_BRICKS));
                    if (i % 6 == 0)
                    {
                        put(out, x, y + 3, z, style.lantern());
                    }
                }
            }
            default ->
            {
            }
        }
    }

    /**
     * The column just beyond the parapet, square to its cell: a cutwater under each pier carrying a refuge in the
     * parapet (level 4 and 5), spikes and banners on the war bridge.
     */
    void cutwater(final List<RouteStep> out, final int i, final int x, final int z, final RouteProbe.Column column)
    {
        final int y = plan.y[i];
        final int role = b.roles[i];
        final int base = column.base();
        final boolean pier = (role & RouteBuilder.PIER) != 0;
        if (style == RoadStyle.ROYAL || style == RoadStyle.IMPERIAL)
        {
            if (!pier || y - base > RouteBuilder.MAX_PIER)
            {
                return;
            }
            for (int yy = base + 1; yy < y; yy++)
            {
                put(out, x, yy, z, style.masonry(x, yy, z));
            }
            put(out, x, y, z, style.dressed(x, y, z));
            put(out, x, y + 1, z, style == RoadStyle.IMPERIAL ? state(Blocks.QUARTZ_PILLAR) : style.masonry(x, y + 1, z));
            put(out, x, y + 2, z, style.lantern());
        }
        else if (style == RoadStyle.WAR)
        {
            if (pier && y - base <= RouteBuilder.MAX_PIER)
            {
                for (int yy = base + 1; yy < y; yy++)
                {
                    put(out, x, yy, z, state(Blocks.BLACKSTONE));
                }
                put(out, x, y, z, state(Blocks.IRON_BARS));
            }
            else if (i % 4 == 2)
            {
                final Direction outward = Direction.getNearest(x - plan.x[i], 0, z - plan.z[i]);
                put(out, x, y, z, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, outward));
            }
        }
    }

    // ------------------------------------------------------------------ portals

    /** The facade over a portal: from the timber frame of a path to the pediment of the imperial road. */
    void portal(final List<RouteStep> out, final int i)
    {
        final boolean entry = i == 0 || plan.mode[i - 1] != RoutePlan.TUNNEL;
        final Direction outward = entry ? (b.travel[i] == null ? null : b.travel[i].getOpposite()) : i + 1 < b.n ? b.travel[i + 1] : b.travel[i];
        if (outward == null)
        {
            return;
        }
        final Face f = new Face(out, i, outward);
        if (b.rebuilding >= 0)
        {
            f.clearOld();
        }
        switch (style)
        {
            case PATH ->
            {
                for (final int u : new int[] {-4, 4})
                {
                    for (int v = 1; v <= 5; v++)
                    {
                        f.set(u, v, 0, style.timber());
                    }
                }
                for (int u = -4; u <= 4; u++)
                {
                    f.set(u, 6, 0, style.timber().setValue(RotatedPillarBlock.AXIS, f.across.getAxis()));
                }
                for (final int u : new int[] {-3, 3})
                {
                    f.set(u, 5, 0, style.planks());
                }
            }
            case GRAVEL ->
            {
                for (final int u : new int[] {-4, 4})
                {
                    for (int v = 1; v <= 6; v++)
                    {
                        f.set(u, v, 0, style.masonry(0, v, u));
                    }
                }
                for (int u = -4; u <= 4; u++)
                {
                    f.set(u, 7, 0, style.masonry(u, 7, 0));
                }
                for (int u = -3; u <= 3; u++)
                {
                    f.set(u, 6, 0, style.timber().setValue(RotatedPillarBlock.AXIS, f.across.getAxis()));
                }
                for (final int u : new int[] {-3, 3})
                {
                    f.set(u, 5, 0, style.masonry(u, 5, 0));
                }
                f.force(0, 5, 0, style.lantern().setValue(LanternBlock.HANGING, true));
            }
            case PAVED ->
            {
                f.wall(5, 7, 0, true);
                f.arch(Blocks.STONE_BRICK_STAIRS);
                for (final int u : new int[] {-5, 5})
                {
                    f.column(u, 1, 1, 6, style.masonry(u, 0, 1));
                    f.set(u, 7, 1, style.keystone());
                    f.set(u, 8, 1, style.lantern());
                }
                for (int u = -6; u <= 6; u++)
                {
                    f.set(u, 8, 0, state(Blocks.STONE_BRICK_SLAB));
                }
            }
            case ROYAL -> guardhouse(f);
            case IMPERIAL -> pediment(f);
            case WAR -> gate(f);
            default ->
            {
            }
        }
    }

    /** Level 4: a vaulted stone portal under a half-timbered guardhouse jutting over the road. */
    private void guardhouse(final Face f)
    {
        f.wall(6, 8, 0, true);
        f.arch(Blocks.STONE_BRICK_STAIRS);
        for (final int u : new int[] {-6, 6})
        {
            f.column(u, 1, 1, 7, style.masonry(u, 0, 1));
            f.set(u, 8, 1, style.lantern().setValue(LanternBlock.HANGING, true));
        }
        for (int u = -7; u <= 7; u++)
        {
            for (int w = -1; w <= 1; w++)
            {
                f.set(u, 9, w, style.masonry(u, 9, w));
            }
            for (int v = 10; v <= 12; v++)
            {
                final int a = Math.abs(u);
                final BlockState front;
                if (a == 7 || a == 3 && v < 12)
                {
                    front = state(Blocks.SPRUCE_LOG);
                }
                else if (v == 12)
                {
                    front = state(Blocks.SPRUCE_LOG).setValue(RotatedPillarBlock.AXIS, f.across.getAxis());
                }
                else if (v == 11 && (u == 0 || a == 5))
                {
                    front = state(Blocks.GLASS_PANE);
                }
                else
                {
                    front = state(Blocks.WHITE_TERRACOTTA);
                }
                f.set(u, v, 1, front);
                f.set(u, v, 0, state(Blocks.SPRUCE_PLANKS));
                f.set(u, v, -1, state(Blocks.SPRUCE_PLANKS));
            }
        }
        // Gable roof along the road's crossing, the ridge over the middle row; each stair faces the ridge
        final BlockState in = state(Blocks.DEEPSLATE_TILE_STAIRS).setValue(StairBlock.FACING, f.outward.getOpposite());
        final BlockState back = state(Blocks.DEEPSLATE_TILE_STAIRS).setValue(StairBlock.FACING, f.outward);
        for (int u = -8; u <= 8; u++)
        {
            f.set(u, 13, 2, in);
            f.set(u, 13, 1, state(Blocks.DEEPSLATE_TILES));
            f.set(u, 14, 1, in);
            f.set(u, 13, 0, state(Blocks.DEEPSLATE_TILES));
            f.set(u, 14, 0, state(Blocks.DEEPSLATE_TILES));
            f.set(u, 15, 0, state(Blocks.DEEPSLATE_TILE_SLAB));
            f.set(u, 13, -1, state(Blocks.DEEPSLATE_TILES));
            f.set(u, 14, -1, back);
            f.set(u, 13, -2, back);
        }
        for (final int u : new int[] {-4, 4})
        {
            f.set(u, 11, 2, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f.outward));
        }
    }

    /** Level 5: a quartz facade, two pairs of columns, an entablature and a stepped pediment. */
    private void pediment(final Face f)
    {
        f.wall(8, 9, 0, true);
        f.arch(Blocks.QUARTZ_STAIRS);
        for (final int u : new int[] {-7, -5, 5, 7})
        {
            f.column(u, 1, 1, 7, state(Blocks.QUARTZ_PILLAR));
            f.set(u, 8, 1, state(Blocks.CHISELED_QUARTZ_BLOCK));
        }
        for (int u = -8; u <= 8; u++)
        {
            f.set(u, 9, 1, state(Blocks.SMOOTH_QUARTZ));
        }
        final int[] widths = {8, 5, 2};
        for (int row = 0; row < widths.length; row++)
        {
            final int w = widths[row];
            for (int u = -w; u <= w; u++)
            {
                final BlockState block = u == -w ? state(Blocks.SMOOTH_QUARTZ_STAIRS).setValue(StairBlock.FACING, f.across)
                                                 : u == w ? state(Blocks.SMOOTH_QUARTZ_STAIRS).setValue(StairBlock.FACING, f.across.getOpposite())
                                                          : state(Blocks.SMOOTH_QUARTZ);
                f.set(u, 10 + row, 1, block);
            }
        }
        f.set(0, 13, 1, state(Blocks.CHISELED_QUARTZ_BLOCK));
        for (final int u : new int[] {-6, 6})
        {
            f.set(u, 7, 1, Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f.outward));
        }
        for (final int u : new int[] {-3, 3})
        {
            f.force(u, 8, 1, style.lantern().setValue(LanternBlock.HANGING, true));
        }
    }

    /** War: a crenellated blackstone gate between two towers, the portcullis half raised. */
    private void gate(final Face f)
    {
        f.wall(5, 7, 0, false);
        for (int u = -5; u <= 5; u += 2)
        {
            f.set(u, 8, 0, style.dressed(u, 8, 0));
        }
        f.set(0, 6, 0, style.keystone());
        for (int u = -3; u <= 3; u++)
        {
            final int a = Math.abs(u);
            f.force(u, a <= 1 ? 5 : a == 2 ? 4 : 3, 0, state(Blocks.IRON_BARS));
        }
        f.force(0, 4, 0, state(Blocks.IRON_BARS));
        for (final int u : new int[] {-7, -6, 6, 7})
        {
            for (int w = 1; w <= 2; w++)
            {
                f.column(u, w, 1, 9, state(Blocks.BLACKSTONE));
                if ((Math.abs(u) == 7) == (w == 1))
                {
                    f.set(u, 10, w, state(Blocks.POLISHED_BLACKSTONE_BRICKS));
                }
            }
        }
        for (final int u : new int[] {-6, 6})
        {
            f.set(u, 7, 3, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f.outward));
        }
        for (final int u : new int[] {-3, 3})
        {
            f.force(u, 6, 1, state(Blocks.POLISHED_BLACKSTONE_SLAB));
            f.force(u, 5, 1, style.lantern().setValue(LanternBlock.HANGING, true));
        }
    }

    /**
     * The plane of a portal: u across (positive on the outward direction's clockwise side), v up from the road, w
     * outward (0 the portal's own plane, positive over the approach, negative into the rise).
     */
    private final class Face
    {
        final List<RouteStep> out;
        final int i;
        final Direction outward;
        final Direction across;

        Face(final List<RouteStep> out, final int i, final Direction outward)
        {
            this.out = out;
            this.i = i;
            this.outward = outward;
            this.across = outward.getClockWise();
        }

        BlockPos pos(final int u, final int v, final int w)
        {
            return new BlockPos(plan.x[i] + across.getStepX() * u + outward.getStepX() * w, plan.y[i] + v,
              plan.z[i] + across.getStepZ() * u + outward.getStepZ() * w);
        }

        /** The opening of the bore and the road in front of it, never walled. */
        boolean opening(final int u, final int v, final int w)
        {
            final int a = Math.abs(u);
            return w >= 0 && a <= 3 && v >= 1 && v <= (a <= 1 ? 5 : a == 2 ? 4 : 3);
        }

        void set(final int u, final int v, final int w, final BlockState state)
        {
            if (!opening(u, v, w))
            {
                force(u, v, w, state);
            }
        }

        void force(final int u, final int v, final int w, final BlockState state)
        {
            final BlockPos p = pos(u, v, w);
            if (b.probe(p.getX(), p.getZ()).blocked() && !b.level.getBlockState(p).isAir() && !RouteBuilder.laid(b.level.getBlockState(p)))
            {
                return;
            }
            put(out, p.getX(), p.getY(), p.getZ(), state);
        }

        /** The facade wall in the portal's plane, `half` columns each side, up to v = top. */
        void wall(final int half, final int top, final int w, final boolean keystone)
        {
            for (int u = -half; u <= half; u++)
            {
                for (int v = 1; v <= top; v++)
                {
                    final BlockPos p = pos(u, v, w);
                    set(u, v, w, style.dressed(p.getX(), p.getY(), p.getZ()));
                }
            }
            if (keystone)
            {
                set(0, 6, w, style.keystone());
            }
        }

        /** Upside-down stairs rounding the two upper corners of the opening. */
        void arch(final Block stairs)
        {
            for (final int u : new int[] {-3, 3, -2, 2})
            {
                final int v = Math.abs(u) == 3 ? 4 : 5;
                final Direction toward = u < 0 ? across.getOpposite() : across;
                force(u, v, 0, stairs.defaultBlockState().setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, toward));
            }
        }

        /** A column from the ground up to v = top, filled down to the ground when the approach dips. */
        void column(final int u, final int w, final int from, final int top, final BlockState state)
        {
            final BlockPos p = pos(u, 0, w);
            final RouteProbe.Column column = b.probe(p.getX(), p.getZ());
            if (!column.blocked() && !column.wet() && !column.lava())
            {
                for (int yy = Math.max(column.ground() + 1, plan.y[i] + 1 - RouteBuilder.MAX_FILL); yy < plan.y[i] + from; yy++)
                {
                    put(out, p.getX(), yy, p.getZ(), state);
                }
            }
            for (int v = from; v <= top; v++)
            {
                set(u, v, w, state);
            }
        }

        /** An older level's facade goes before this one is set (the bore's own lining stays). */
        void clearOld()
        {
            for (int u = -9; u <= 9; u++)
            {
                for (int w = -2; w <= 3; w++)
                {
                    for (int v = 1; v <= 15; v++)
                    {
                        if (w == 0 && Math.abs(u) <= 4 || w < 0 && v < 8 || opening(u, v, w))
                        {
                            continue;
                        }
                        final BlockPos p = pos(u, v, w);
                        if (RouteBuilder.laid(b.level.getBlockState(p)))
                        {
                            put(out, p.getX(), p.getY(), p.getZ(), AIR);
                        }
                    }
                }
            }
        }
    }

    private static void put(final List<RouteStep> out, final int x, final int y, final int z, final BlockState state)
    {
        out.add(new RouteStep(new BlockPos(x, y, z), state));
    }

    private static BlockState state(final Block block)
    {
        return block.defaultBlockState();
    }
}
