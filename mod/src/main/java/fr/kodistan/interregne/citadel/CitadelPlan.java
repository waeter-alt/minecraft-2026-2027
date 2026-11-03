package fr.kodistan.interregne.citadel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The imperial citadel, laid out block by block relative to its center (x east, z south, y 0 = ground level).
 * Composed of small builders (walls, towers, buildings, streets) over noisy palettes so it looks handmade.
 * The gate faces south; the palace stands at the north end, the Hôtel des Ventes and the market on each side of
 * the main street, with houses filling the districts and reserved grounds for the arena and the dungeon gate.
 */
final class CitadelPlan
{
    private final int     half;
    private final Palette rampart;
    private final Palette deepslate;
    private final Palette street;
    private final Palette houseStone;
    private final Palette slateRoof;
    private final Palette copperRoof;
    private final Palette blackstone;

    /**
     * Placements in order; a later placement at the same spot wins.
     */
    final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

    /**
     * Signs to write once their block exists: position and the lines of text.
     */
    final Map<BlockPos, String[]> signs = new LinkedHashMap<>();

    /**
     * Named spots the later systems need (throne, counters, arena, dungeon gate).
     */
    final Map<String, BlockPos> landmarks = new LinkedHashMap<>();

    CitadelPlan(final int half, final long seed)
    {
        this.half = half;
        this.rampart = Palette.rampart(seed);
        this.deepslate = Palette.deepslate(seed + 1);
        this.street = Palette.street(seed + 2);
        this.houseStone = Palette.houseStone(seed + 3);
        this.slateRoof = Palette.slateRoof(seed + 4);
        this.copperRoof = Palette.copperRoof(seed + 5);
        this.blackstone = Palette.blackstone(seed + 6);
    }

    int half()
    {
        return half;
    }

    /**
     * Lay out the whole citadel.
     */
    CitadelPlan build()
    {
        streets();
        palace();
        hotelDesVentes();
        market();
        arenaGround();
        dungeonGate();
        houses();
        ramparts();
        return this;
    }

    // ------------------------------------------------------------------ primitives

    private void set(final int x, final int y, final int z, final BlockState state)
    {
        blocks.put(new BlockPos(x, y, z), state);
    }

    private void set(final int x, final int y, final int z, final Block block)
    {
        set(x, y, z, block.defaultBlockState());
    }

    private void box(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final Palette palette, final boolean hollow)
    {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
        {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                {
                    final boolean edge = x == x1 || x == x2 || z == z1 || z == z2 || y == y1 || y == y2;
                    if (!hollow || edge)
                    {
                        set(x, y, z, palette.full(x, y, z));
                    }
                    else
                    {
                        set(x, y, z, Blocks.AIR);
                    }
                }
            }
        }
    }

    private void fill(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final BlockState state)
    {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
        {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                {
                    set(x, y, z, state);
                }
            }
        }
    }

    private void lantern(final int x, final int y, final int z, final boolean hanging)
    {
        set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging));
    }

    /**
     * A street lamp: a dark post topped with a lantern.
     */
    private void lamp(final int x, final int z)
    {
        set(x, 0, z, Blocks.POLISHED_BLACKSTONE_WALL);
        set(x, 1, z, Blocks.POLISHED_BLACKSTONE_WALL);
        set(x, 2, z, Blocks.CHAIN);
        lantern(x, 3, z, false);
    }

    private void banner(final int x, final int y, final int z, final Direction facing, final boolean gold)
    {
        final Block block = gold ? Blocks.YELLOW_WALL_BANNER : Blocks.PURPLE_WALL_BANNER;
        set(x, y, z, block.defaultBlockState().setValue(WallBannerBlock.FACING, facing));
    }

    private void sign(final int x, final int y, final int z, final Direction facing, final String... lines)
    {
        set(x, y, z, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING, facing));
        signs.put(new BlockPos(x, y, z), lines);
    }

    // ------------------------------------------------------------------ ramparts

    private void ramparts()
    {
        final int height = 9;
        for (int i = -half; i <= half; i++)
        {
            wallColumn(i, -half, height);
            wallColumn(i, half, height);
            wallColumn(-half, i, height);
            wallColumn(half, i, height);
        }
        // Walkway behind the parapet, lit regularly
        for (int i = -half + 2; i <= half - 2; i++)
        {
            for (final int[] spot : new int[][]{{i, -half + 1}, {i, half - 1}, {-half + 1, i}, {half - 1, i}})
            {
                set(spot[0], height - 1, spot[1], rampart.slab(spot[0], height - 1, spot[1], SlabType.TOP));
            }
            if (Math.floorMod(i, 7) == 0)
            {
                lantern(i, height, -half + 1, false);
                lantern(i, height, half - 1, false);
                lantern(-half + 1, height, i, false);
                lantern(half - 1, height, i, false);
            }
        }
        for (final int[] corner : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}, {-half, 0}, {half, 0}, {0, -half}})
        {
            tower(corner[0], corner[1], 5, 15);
        }
        gatehouse();
    }

    private void wallColumn(final int x, final int z, final int height)
    {
        final int depth = 3;
        for (int d = 0; d < depth; d++)
        {
            final int wx = x == -half ? x + d : x == half ? x - d : x;
            final int wz = z == -half ? z + d : z == half ? z - d : z;
            for (int y = 0; y < height; y++)
            {
                set(wx, y, wz, rampart.full(wx, y, wz));
            }
        }
        // Crenellations on the outer edge
        if (Math.floorMod(x + z, 2) == 0)
        {
            set(x, height, z, rampart.full(x, height, z));
        }
    }

    private void tower(final int cx, final int cz, final int radius, final int height)
    {
        for (int x = cx - radius; x <= cx + radius; x++)
        {
            for (int z = cz - radius; z <= cz + radius; z++)
            {
                final double distance = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (distance > radius + 0.4)
                {
                    continue;
                }
                final boolean shell = distance > radius - 1.2;
                for (int y = 0; y < height; y++)
                {
                    set(x, y, z, shell ? deepslate.full(x, y, z) : Blocks.AIR.defaultBlockState());
                }
                set(x, 0, z, deepslate.full(x, 0, z));
                set(x, height, z, shell && Math.floorMod(x + z, 2) == 0 ? deepslate.full(x, height, z) : deepslate.slab(x, height, z, SlabType.BOTTOM));
                if (shell && (Math.floorMod(x * 3 + z, 9) == 0) && height > 6)
                {
                    set(x, height - 4, z, Blocks.AIR);
                }
            }
        }
        lantern(cx, height - 1, cz, true);
        lantern(cx, height + 1, cz, false);
        banner(cx, height - 2, cz - radius - 1, Direction.NORTH, false);
        banner(cx, height - 2, cz + radius + 1, Direction.SOUTH, true);
    }

    private void gatehouse()
    {
        final int z = half;
        tower(-6, z, 4, 14);
        tower(6, z, 4, 14);
        // The passage through the wall, with an iron-bar portcullis hanging over it
        fill(-2, 0, z - 3, 2, 6, z, Blocks.AIR.defaultBlockState());
        for (int x = -2; x <= 2; x++)
        {
            set(x, 6, z, Blocks.IRON_BARS);
            set(x, 5, z, Blocks.IRON_BARS);
            set(x, 7, z, rampart.full(x, 7, z));
        }
        banner(-3, 6, z + 1, Direction.SOUTH, false);
        banner(3, 6, z + 1, Direction.SOUTH, false);
        sign(0, 8, z + 1, Direction.SOUTH, "", "L'INTERRÈGNE", "Cité impériale", "");
        lantern(-3, 4, z + 1, false);
        lantern(3, 4, z + 1, false);
    }

    // ------------------------------------------------------------------ streets

    private void streets()
    {
        // Main street from the gate to the palace, and a cross street
        for (int z = -half + 4; z <= half; z++)
        {
            for (int x = -3; x <= 3; x++)
            {
                set(x, -1, z, street.full(x, -1, z));
            }
            if (Math.floorMod(z, 8) == 0 && z < half - 6)
            {
                lamp(-4, z);
                lamp(4, z);
            }
        }
        for (int x = -half + 3; x <= half - 3; x++)
        {
            for (int z = 18; z <= 21; z++)
            {
                set(x, -1, z, street.full(x, -1, z));
            }
        }
    }

    // ------------------------------------------------------------------ palace

    private void palace()
    {
        final int front = -half + 34;
        final int back = -half + 6;
        final int terrace = 4;
        // Raised terrace and its grand staircase down to the main street
        for (int x = -26; x <= 26; x++)
        {
            for (int z = back - 2; z <= front + 4; z++)
            {
                for (int y = -1; y < terrace; y++)
                {
                    set(x, y, z, blackstone.full(x, y, z));
                }
            }
        }
        for (int step = 0; step < terrace; step++)
        {
            for (int x = -5; x <= 5; x++)
            {
                set(x, step, front + 5 + (terrace - 1 - step), deepslate.stair(x, step, front + 5 + step, Direction.NORTH, false));
            }
        }
        // Main hall: deepslate walls, tall windows, a long purple carpet to the throne
        box(-18, terrace, back, 18, terrace + 13, front, deepslate, true);
        for (int z = back + 3; z <= front - 3; z += 4)
        {
            for (int y = terrace + 3; y <= terrace + 8; y++)
            {
                set(-18, y, z, Blocks.PURPLE_STAINED_GLASS_PANE);
                set(18, y, z, Blocks.PURPLE_STAINED_GLASS_PANE);
            }
            for (int y = terrace + 1; y <= terrace + 12; y++)
            {
                set(-11, y, z, Blocks.POLISHED_BLACKSTONE);
                set(11, y, z, Blocks.POLISHED_BLACKSTONE);
            }
            lantern(-11, terrace + 12, z + 2, true);
            lantern(11, terrace + 12, z + 2, true);
        }
        for (int z = back + 6; z <= front; z++)
        {
            for (int x = -2; x <= 2; x++)
            {
                set(x, terrace + 1, z, Blocks.PURPLE_CARPET.defaultBlockState());
            }
            set(-3, terrace + 1, z, Blocks.YELLOW_CARPET.defaultBlockState());
            set(3, terrace + 1, z, Blocks.YELLOW_CARPET.defaultBlockState());
        }
        // Door
        fill(-2, terrace + 1, front, 2, terrace + 6, front, Blocks.AIR.defaultBlockState());
        banner(-4, terrace + 6, front + 1, Direction.SOUTH, false);
        banner(4, terrace + 6, front + 1, Direction.SOUTH, false);
        // Throne dais at the back
        for (int step = 0; step < 3; step++)
        {
            for (int x = -5 + step; x <= 5 - step; x++)
            {
                for (int z = back + 1; z <= back + 5 - step; z++)
                {
                    set(x, terrace + 1 + step, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        final int throneY = terrace + 4;
        set(0, throneY, back + 2, Blocks.GOLD_BLOCK);
        set(0, throneY + 1, back + 2, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        set(-1, throneY + 1, back + 2, Blocks.GOLD_BLOCK);
        set(1, throneY + 1, back + 2, Blocks.GOLD_BLOCK);
        set(0, throneY + 2, back + 1, Blocks.GOLD_BLOCK);
        banner(0, throneY + 4, back + 1, Direction.SOUTH, false);
        banner(-2, throneY + 3, back + 1, Direction.SOUTH, true);
        banner(2, throneY + 3, back + 1, Direction.SOUTH, true);
        landmarks.put("trône (Empereur)", new BlockPos(0, throneY + 1, back + 2));
        landmarks.put("Trône impérial (bloc du mod)", new BlockPos(0, terrace + 1, back + 7));
        set(0, terrace + 1, back + 7, Blocks.GOLD_BLOCK);
        // Roof and corner towers
        for (int x = -19; x <= 19; x++)
        {
            for (int z = back - 1; z <= front + 1; z++)
            {
                set(x, terrace + 14, z, slateRoof.slab(x, terrace + 14, z, SlabType.BOTTOM));
            }
        }
        tower(-19, back, 4, terrace + 22);
        tower(19, back, 4, terrace + 22);
        tower(-19, front, 3, terrace + 18);
        tower(19, front, 3, terrace + 18);
    }

    // ------------------------------------------------------------------ Hôtel des Ventes

    private void hotelDesVentes()
    {
        final int x1 = -half + 8;
        final int x2 = -7;
        final int z1 = -6;
        final int z2 = 15;
        box(x1, 0, z1, x2, 9, z2, houseStone, true);
        fill(x1 + 1, -1, z1 + 1, x2 - 1, -1, z2 - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
        // Arches on the street side
        for (int z = z1 + 2; z <= z2 - 2; z += 4)
        {
            fill(x2, 0, z, x2, 3, z + 1, Blocks.AIR.defaultBlockState());
            set(x2, 4, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
            set(x2, 4, z + 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
            lantern(x2 - 1, 8, z, true);
        }
        // Counters along the north wall: one for the bank, one for the market
        for (int x = x1 + 3; x <= x1 + 14; x++)
        {
            set(x, 0, z1 + 3, Blocks.STRIPPED_DARK_OAK_LOG);
            set(x, 1, z1 + 3, Blocks.DARK_OAK_SLAB);
        }
        set(x1 + 5, 0, z1 + 2, Blocks.GOLD_BLOCK);
        sign(x1 + 5, 1, z1 + 4, Direction.SOUTH, "Ici :", "Guichet de la", "Banque", "impériale");
        set(x1 + 12, 0, z1 + 2, Blocks.GOLD_BLOCK);
        sign(x1 + 12, 1, z1 + 4, Direction.SOUTH, "Ici :", "Marché", "impérial", "");
        landmarks.put("Guichet de la Banque impériale", new BlockPos(x1 + 5, 0, z1 + 2));
        landmarks.put("Marché impérial", new BlockPos(x1 + 12, 0, z1 + 2));
        // Central lounge
        for (int x = x1 + 6; x <= x1 + 12; x++)
        {
            for (int z = z1 + 8; z <= z1 + 13; z++)
            {
                set(x, 0, z, Blocks.RED_CARPET.defaultBlockState());
            }
        }
        set(x1 + 9, 0, z1 + 10, Blocks.DARK_OAK_FENCE);
        set(x1 + 9, 1, z1 + 10, Blocks.DARK_OAK_PRESSURE_PLATE);
        lantern(x1 + 9, 8, z1 + 10, true);
        // Private alcoves along the west wall
        for (int i = 0; i < 4; i++)
        {
            final int z = z1 + 2 + i * 5;
            fill(x1 + 1, 0, z, x1 + 3, 3, z, houseStone.full(x1, 0, z));
            set(x1 + 1, 0, z + 2, Blocks.PURPLE_CARPET.defaultBlockState());
            set(x1 + 2, 0, z + 2, Blocks.PURPLE_CARPET.defaultBlockState());
            lantern(x1 + 1, 3, z + 2, true);
        }
        sign(x2 + 1, 6, (z1 + z2) / 2, Direction.EAST, "", "Hôtel des", "Ventes", "");
    }

    // ------------------------------------------------------------------ market

    private void market()
    {
        final int cx = 26;
        final int cz = 5;
        // Paved square
        for (int x = cx - 14; x <= cx + 14; x++)
        {
            for (int z = cz - 12; z <= cz + 12; z++)
            {
                set(x, -1, z, street.full(x, -1, z));
            }
        }
        // Fountain
        for (int x = cx - 3; x <= cx + 3; x++)
        {
            for (int z = cz - 3; z <= cz + 3; z++)
            {
                final boolean rim = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                set(x, 0, z, rim ? Blocks.STONE_BRICK_WALL.defaultBlockState() : Blocks.WATER.defaultBlockState());
                set(x, -1, z, Blocks.POLISHED_DEEPSLATE);
            }
        }
        set(cx, 0, cz, Blocks.CHISELED_STONE_BRICKS);
        set(cx, 1, cz, Blocks.CHISELED_STONE_BRICKS);
        set(cx, 2, cz, Blocks.GOLD_BLOCK);
        set(cx, 3, cz, Blocks.WATER);
        // Stalls around the square, each with a colored awning
        final DyeColor[] colors = {DyeColor.PURPLE, DyeColor.YELLOW, DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.ORANGE};
        int index = 0;
        for (final int[] stall : new int[][]{{cx - 11, cz - 9}, {cx - 3, cz - 10}, {cx + 6, cz - 9}, {cx - 11, cz + 7}, {cx - 3, cz + 8}, {cx + 6, cz + 7}})
        {
            stall(stall[0], stall[1], colors[index++ % colors.length]);
        }
        lamp(cx - 7, cz);
        lamp(cx + 7, cz);
    }

    private void stall(final int x, final int z, final DyeColor color)
    {
        final Block wool = switch (color)
        {
            case PURPLE -> Blocks.PURPLE_WOOL;
            case YELLOW -> Blocks.YELLOW_WOOL;
            case RED -> Blocks.RED_WOOL;
            case BLUE -> Blocks.BLUE_WOOL;
            case GREEN -> Blocks.GREEN_WOOL;
            default -> Blocks.ORANGE_WOOL;
        };
        for (final int[] post : new int[][]{{0, 0}, {4, 0}, {0, 2}, {4, 2}})
        {
            set(x + post[0], 0, z + post[1], Blocks.SPRUCE_FENCE);
            set(x + post[0], 1, z + post[1], Blocks.SPRUCE_FENCE);
        }
        for (int dx = -1; dx <= 5; dx++)
        {
            for (int dz = -1; dz <= 3; dz++)
            {
                set(x + dx, 2, z + dz, wool);
            }
        }
        for (int dx = 1; dx <= 3; dx++)
        {
            set(x + dx, 0, z, Blocks.BARREL);
        }
        lantern(x + 2, 1, z + 1, true);
    }

    // ------------------------------------------------------------------ reserved grounds

    private void arenaGround()
    {
        final int cx = 30;
        final int cz = half - 22;
        final int radius = 13;
        for (int x = cx - radius; x <= cx + radius; x++)
        {
            for (int z = cz - radius; z <= cz + radius; z++)
            {
                final double distance = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (distance <= radius)
                {
                    set(x, -1, z, distance > radius - 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    if (distance > radius - 1)
                    {
                        set(x, 0, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
                    }
                }
            }
        }
        sign(cx, 1, cz - radius - 1, Direction.NORTH, "", "Arène", "(à venir)", "");
        landmarks.put("arène (centre)", new BlockPos(cx, 0, cz));
    }

    private void dungeonGate()
    {
        final int cx = -30;
        final int cz = half - 18;
        // A dark archway set in a block of blackstone: the future entrance to the instanced dungeons
        for (int x = cx - 5; x <= cx + 5; x++)
        {
            for (int z = cz - 2; z <= cz + 2; z++)
            {
                for (int y = 0; y <= 9; y++)
                {
                    set(x, y, z, blackstone.full(x, y, z));
                }
            }
        }
        for (int x = cx - 2; x <= cx + 2; x++)
        {
            for (int y = 0; y <= 5; y++)
            {
                set(x, y, cz + 2, Blocks.BLACK_CONCRETE);
                set(x, y, cz + 1, Blocks.BLACK_CONCRETE);
            }
        }
        set(cx - 3, 3, cz + 3, Blocks.SOUL_LANTERN);
        set(cx + 3, 3, cz + 3, Blocks.SOUL_LANTERN);
        set(cx, 7, cz + 3, Blocks.CRYING_OBSIDIAN);
        sign(cx, 6, cz + 3, Direction.SOUTH, "", "Porte des", "Profondeurs", "");
        landmarks.put("Porte des Profondeurs", new BlockPos(cx, 0, cz + 3));
    }

    // ------------------------------------------------------------------ houses

    private void houses()
    {
        final java.util.Random random = new java.util.Random(half * 31L + 11);
        for (int x = -half + 8; x <= half - 16; x += 13)
        {
            for (int z = -half + 40; z <= half - 12; z += 13)
            {
                final int w = 7 + random.nextInt(3);
                final int d = 7 + random.nextInt(3);
                if (reserved(x, z, x + w, z + d))
                {
                    continue;
                }
                house(x, z, w, d, random.nextBoolean(), random);
            }
        }
    }

    private boolean reserved(final int x1, final int z1, final int x2, final int z2)
    {
        final int[][] zones = {
          {-6, -half, 6, half},                       // main street
          {-half, 16, half, 23},                      // cross street
          {-half + 6, -8, -5, 17},                    // Hôtel des Ventes
          {10, -9, 42, 19},                           // market
          {15, half - 37, 45, half - 7},              // arena
          {-38, half - 24, -22, half - 13},           // dungeon gate
          {-28, -half, 28, -half + 40}                // palace terrace
        };
        for (final int[] zone : zones)
        {
            if (x1 <= zone[2] && x2 >= zone[0] && z1 <= zone[3] && z2 >= zone[1])
            {
                return true;
            }
        }
        return false;
    }

    private void house(final int x, final int z, final int w, final int d, final boolean copper, final java.util.Random random)
    {
        final int height = 5 + random.nextInt(2);
        box(x, 0, z, x + w, height, z + d, houseStone, true);
        // Timber frame corners and lintels
        for (int y = 0; y <= height; y++)
        {
            set(x, y, z, Blocks.DARK_OAK_LOG);
            set(x + w, y, z, Blocks.DARK_OAK_LOG);
            set(x, y, z + d, Blocks.DARK_OAK_LOG);
            set(x + w, y, z + d, Blocks.DARK_OAK_LOG);
        }
        fill(x + 1, -1, z + 1, x + w - 1, -1, z + d - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        // Door facing the nearest street and two windows
        final int door = x + w / 2;
        set(door, 0, z + d, Blocks.AIR);
        set(door, 1, z + d, Blocks.AIR);
        set(x + 2, 2, z + d, Blocks.GLASS_PANE);
        set(x + w - 2, 2, z + d, Blocks.GLASS_PANE);
        set(x, 2, z + d / 2, Blocks.GLASS_PANE);
        lantern(x + w / 2, height - 1, z + d / 2, true);
        lantern(door + 1, 2, z + d + 1, false);
        // Pitched roof along x, copper or slate
        final Palette roof = copper ? copperRoof : slateRoof;
        for (int layer = 0; layer <= d / 2 + 1; layer++)
        {
            final int y = height + 1 + layer;
            final int za = z - 1 + layer;
            final int zb = z + d + 1 - layer;
            for (int xx = x - 1; xx <= x + w + 1; xx++)
            {
                if (za < zb)
                {
                    set(xx, y, za, roof.stair(xx, y, za, Direction.SOUTH, false));
                    set(xx, y, zb, roof.stair(xx, y, zb, Direction.NORTH, false));
                }
                else
                {
                    set(xx, y, za, roof.slab(xx, y, za, SlabType.BOTTOM));
                }
            }
        }
        // Chimney with a gentle smoke
        set(x + 1, height + 1, z + 1, Blocks.BRICKS);
        set(x + 1, height + 2, z + 1, Blocks.BRICKS);
        set(x + 1, height + 3, z + 1, Blocks.BRICKS);
        set(x + 1, height + 4, z + 1, Blocks.CAMPFIRE);
        // Steampunk touch: a chain and a copper gear-like trapdoor on the facade
        set(x + w - 1, height, z + d + 1, Blocks.CHAIN);
        set(x + w - 1, height - 1, z + d + 1, Blocks.CUT_COPPER_SLAB);
    }

    static Component describe(final Map<String, BlockPos> landmarks, final BlockPos origin)
    {
        final StringBuilder text = new StringBuilder("⚜ Citadelle générée. Repères :");
        landmarks.forEach((name, pos) -> {
            final BlockPos world = origin.offset(pos);
            text.append("\n- ").append(name).append(" : ").append(world.getX()).append(' ').append(world.getY()).append(' ').append(world.getZ());
        });
        return Component.literal(text.toString());
    }
}
