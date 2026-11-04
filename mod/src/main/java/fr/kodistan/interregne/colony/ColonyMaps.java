package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * The colony map of the town hall draws itself: MineColonies wanted filled maps put in the town hall's chest, we keep
 * our own vanilla map tiles over the colony instead and paint them from the terrain, a few columns each tick, wherever
 * chunks are loaded. The map opens from town hall level {@link #UNLOCK_LEVEL}.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ColonyMaps
{
    public static final int UNLOCK_LEVEL = 2;

    /**
     * Tiles on each side of the colony centre's tile at most: 7 × 7 tiles of 128 blocks.
     */
    private static final int MAX_TILE_RADIUS = 3;

    /**
     * Map columns of 128 pixels painted per tick.
     */
    private static final int COLUMNS_PER_TICK = 8;

    /**
     * Ticks between two resyncs of a colony's town hall once its map changed.
     */
    private static final long RESYNC_TICKS = 600;

    private record Job(int colonyId, Level level, MapItemSavedData data)
    {
    }

    private static final Queue<Job> JOBS = new ArrayDeque<>();
    @Nullable
    private static Job current;
    private static int column;
    private static boolean currentChanged;
    private static final Map<Integer, Boolean> CHANGED = new HashMap<>();
    private static final Map<Integer, Long> LAST_SYNC = new HashMap<>();

    private ColonyMaps()
    {
    }

    /**
     * The map tiles the town hall's view carries: none below the unlock level.
     */
    public static List<ItemStack> stacks(final IBuilding townHall)
    {
        final List<ItemStack> stacks = new ArrayList<>();
        if (townHall.getBuildingLevel() < UNLOCK_LEVEL)
        {
            return stacks;
        }
        final IColony colony = townHall.getColony();
        final Level level = colony.getWorld();
        if (level == null || level.getServer() == null)
        {
            return stacks;
        }
        for (final MapId id : tiles(colony, level).keySet())
        {
            final ItemStack stack = new ItemStack(Items.FILLED_MAP);
            stack.set(DataComponents.MAP_ID, id);
            stacks.add(stack);
        }
        return stacks;
    }

    /**
     * The colony's tiles, created the first time: every tile around its centre that its buildings reach, plus a margin.
     */
    private static Map<MapId, MapItemSavedData> tiles(final IColony colony, final Level level)
    {
        final BlockPos center = colony.getCenter();
        int reach = 64;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            final BlockPos pos = building.getPosition();
            reach = Math.max(reach, Math.max(Math.abs(pos.getX() - center.getX()), Math.abs(pos.getZ() - center.getZ())) + 48);
        }
        final int cx = tileOf(center.getX());
        final int cz = tileOf(center.getZ());
        final int minX = Math.max(cx - MAX_TILE_RADIUS, tileOf(center.getX() - reach));
        final int maxX = Math.min(cx + MAX_TILE_RADIUS, tileOf(center.getX() + reach));
        final int minZ = Math.max(cz - MAX_TILE_RADIUS, tileOf(center.getZ() - reach));
        final int maxZ = Math.min(cz + MAX_TILE_RADIUS, tileOf(center.getZ() + reach));

        final Data saved = Data.get(level.getServer());
        final Map<MapId, MapItemSavedData> tiles = new HashMap<>();
        for (int tx = minX; tx <= maxX; tx++)
        {
            for (int tz = minZ; tz <= maxZ; tz++)
            {
                final String key = colony.getID() + ":" + tx + ":" + tz;
                final Integer known = saved.ids.get(key);
                MapId id = known == null ? null : new MapId(known);
                MapItemSavedData data = id == null ? null : level.getMapData(id);
                if (data == null)
                {
                    id = level.getFreeMapId();
                    data = MapItemSavedData.createFresh(tx * 128, tz * 128, (byte) 0, false, false, level.dimension());
                    level.setMapData(id, data);
                    saved.ids.put(key, id.id());
                    saved.setDirty();
                }
                tiles.put(id, data);
            }
        }
        return tiles;
    }

    /**
     * The tile whose 128 blocks hold this coordinate, as vanilla aligns maps.
     */
    private static int tileOf(final int coordinate)
    {
        return Math.floorDiv(coordinate + 64, 128);
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (current == null)
        {
            current = JOBS.poll();
            column = 0;
            currentChanged = false;
            if (current == null)
            {
                refill(event.getServer());
                return;
            }
        }
        try
        {
            for (int i = 0; i < COLUMNS_PER_TICK && column < 128; i++, column++)
            {
                currentChanged |= paintColumn(current.level(), current.data(), column);
            }
        }
        catch (final RuntimeException e)
        {
            InterregneMod.LOGGER.error("Colony map tile failed", e);
            column = 128;
        }
        if (column >= 128)
        {
            if (currentChanged)
            {
                CHANGED.put(current.colonyId(), true);
            }
            current = null;
        }
    }

    /**
     * Queues every tile of every colony whose map is unlocked, and resyncs the town halls whose map changed.
     */
    private static void refill(final MinecraftServer server)
    {
        final long now = server.getTickCount();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final IBuilding townHall = colony.getBuildingManager().getTownHall();
            if (townHall == null || townHall.getBuildingLevel() < UNLOCK_LEVEL || colony.getWorld() == null)
            {
                continue;
            }
            if (CHANGED.getOrDefault(colony.getID(), false) && now - LAST_SYNC.getOrDefault(colony.getID(), Long.MIN_VALUE / 2) >= RESYNC_TICKS)
            {
                CHANGED.put(colony.getID(), false);
                LAST_SYNC.put(colony.getID(), now);
                townHall.markDirty();
            }
            for (final MapItemSavedData data : tiles(colony, colony.getWorld()).values())
            {
                JOBS.add(new Job(colony.getID(), colony.getWorld(), data));
            }
        }
    }

    /**
     * Paints one column of a tile from the loaded chunks, the way vanilla maps shade the ground; true when it changed.
     */
    private static boolean paintColumn(final Level level, final MapItemSavedData data, final int px)
    {
        final int worldX = data.centerX - 64 + px;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        double previous = Double.NaN;
        for (int pz = -1; pz < 128; pz++)
        {
            final int worldZ = data.centerZ - 64 + pz;
            final LevelChunk chunk = level.getChunkSource().getChunkNow(worldX >> 4, worldZ >> 4);
            if (chunk == null || chunk.isEmpty())
            {
                previous = Double.NaN;
                continue;
            }
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) + 1;
            BlockState state;
            int depth = 0;
            if (y <= level.getMinBuildHeight() + 1)
            {
                state = Blocks.BEDROCK.defaultBlockState();
            }
            else
            {
                do
                {
                    pos.set(worldX, --y, worldZ);
                    state = chunk.getBlockState(pos);
                }
                while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinBuildHeight());

                if (y > level.getMinBuildHeight() && !state.getFluidState().isEmpty())
                {
                    final BlockPos.MutableBlockPos below = pos.mutable();
                    BlockState under;
                    do
                    {
                        below.move(Direction.DOWN);
                        under = chunk.getBlockState(below);
                        depth++;
                    }
                    while (below.getY() > level.getMinBuildHeight() && !under.getFluidState().isEmpty());
                    if (!state.isFaceSturdy(level, pos, Direction.UP))
                    {
                        state = state.getFluidState().createLegacyBlock();
                    }
                }
            }
            final MapColor color = state.getMapColor(level, pos);
            final int checker = (px + pz) & 1;
            final MapColor.Brightness brightness;
            if (color == MapColor.WATER)
            {
                final double shade = depth * 0.1 + checker * 0.2;
                brightness = shade < 0.5 ? MapColor.Brightness.HIGH : shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            }
            else
            {
                final double slope = (Double.isNaN(previous) ? 0 : (y - previous) * 4.0 / 5.0) + (checker - 0.5) * 0.4;
                brightness = slope > 0.6 ? MapColor.Brightness.HIGH : slope < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            }
            previous = y;
            if (pz >= 0)
            {
                changed |= data.updateColor(px, pz, color.getPackedId(brightness));
            }
        }
        return changed;
    }

    /**
     * Which map id draws which tile of which colony, saved with the world.
     */
    private static final class Data extends SavedData
    {
        private static final String NAME = "interregne_colony_maps";

        private final Map<String, Integer> ids = new HashMap<>();

        private static Data get(final MinecraftServer server)
        {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Data::new, Data::load, null), NAME);
        }

        @Override
        public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
        {
            final CompoundTag ids = new CompoundTag();
            this.ids.forEach(ids::putInt);
            tag.put("ids", ids);
            return tag;
        }

        private static Data load(final CompoundTag tag, final HolderLookup.Provider provider)
        {
            final Data data = new Data();
            final CompoundTag ids = tag.getCompound("ids");
            for (final String key : ids.getAllKeys())
            {
                data.ids.put(key, ids.getInt(key));
            }
            return data;
        }
    }
}
