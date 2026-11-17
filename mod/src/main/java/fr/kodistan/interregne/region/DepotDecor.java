package fr.kodistan.interregne.region;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecolonies.api.colony.IColony;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Create parts of the Dépôt Régional that only look alive: the hidden creative motors that turn every shaft, the
 * belt chains and the windmill. They are not in the blueprints (a builder lays one block at a time, and a half-built
 * belt chain or windmill breaks itself): once a level stands, the mod puts them down in one go from
 * {@code data/interregne/depot_decor.json}, and puts them back if they go missing. Nothing in them produces anything.
 * Without Create installed this does nothing.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class DepotDecor
{
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "depot_decor.json");
    private static final String BEARING = "create:windmill_bearing";

    /**
     * One part, relative to the hut and still in the blueprint's north-facing frame.
     */
    private record Part(BlockPos offset, Block block, Map<String, String> props, String id)
    {
    }

    private static Map<Integer, List<Part>> parts;

    private DepotDecor()
    {
    }

    /**
     * Makes sure this Dépôt's decor stands, whatever its level and orientation.
     */
    public static void refresh(final ServerLevel level, final BuildingDepotRegional depot)
    {
        final List<Part> list = parts(level.getServer()).get(depot.getBuildingLevel());
        if (list == null || list.isEmpty())
        {
            return;
        }
        final BlockPos hut = depot.getPosition();
        final RotationMirror rm = orientation(depot);

        // The windmill is put down once, as a whole: its sails become a moving structure and no longer exist as blocks
        final List<Part> windmill = new ArrayList<>();
        final List<Part> belts = new ArrayList<>();
        for (final Part part : list)
        {
            final BlockPos pos = place(hut, part.offset, rm);
            if (!level.isLoaded(pos))
            {
                return;
            }
            if (part.id.equals(BEARING) || part.id.equals("create:white_sail") || part.id.equals("create:sail_frame"))
            {
                windmill.add(part);
            }
            else if (part.id.equals("create:belt"))
            {
                belts.add(part);
            }
            else if (!level.getBlockState(pos).is(part.block))
            {
                level.setBlock(pos, state(part, rm), Block.UPDATE_ALL);
            }
        }
        if (!belts.isEmpty() && belts.stream().anyMatch(p -> !level.getBlockState(place(hut, p.offset, rm)).is(p.block)))
        {
            // The whole chain at once: a belt checks its neighbours when it first ticks
            for (final Part part : belts)
            {
                level.setBlock(place(hut, part.offset, rm), state(part, rm), Block.UPDATE_ALL);
            }
        }
        if (!windmill.isEmpty())
        {
            placeWindmill(level, hut, windmill, rm);
        }
    }

    private static void placeWindmill(final ServerLevel level, final BlockPos hut, final List<Part> windmill, final RotationMirror rm)
    {
        Part bearing = null;
        for (final Part part : windmill)
        {
            if (part.id.equals(BEARING))
            {
                bearing = part;
            }
        }
        if (bearing == null)
        {
            return;
        }
        final BlockPos bearingPos = place(hut, bearing.offset, rm);
        if (level.getBlockState(bearingPos).is(bearing.block))
        {
            return;
        }
        for (final Part part : windmill)
        {
            if (part != bearing)
            {
                level.setBlock(place(hut, part.offset, rm), state(part, rm), Block.UPDATE_ALL);
            }
        }
        level.setBlock(bearingPos, state(bearing, rm), Block.UPDATE_ALL);
        final BlockEntity entity = level.getBlockEntity(bearingPos);
        if (entity != null)
        {
            final CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
            tag.putBoolean("QueueAssembly", true);
            entity.loadWithComponents(tag, level.registryAccess());
            entity.setChanged();
        }
    }

    private static RotationMirror orientation(final BuildingDepotRegional depot)
    {
        final var entity = depot.getTileEntity();
        return entity == null ? RotationMirror.NONE : entity.getRotationMirror();
    }

    private static BlockPos place(final BlockPos hut, final BlockPos offset, final RotationMirror rm)
    {
        return hut.offset(rm.applyToPos(offset));
    }

    private static BlockState state(final Part part, final RotationMirror rm)
    {
        BlockState state = part.block.defaultBlockState();
        for (final Map.Entry<String, String> prop : part.props.entrySet())
        {
            final Property<?> property = part.block.getStateDefinition().getProperty(prop.getKey());
            if (property != null)
            {
                state = with(state, property, prop.getValue());
            }
        }
        return rm.applyToBlockState(state);
    }

    private static <T extends Comparable<T>> BlockState with(final BlockState state, final Property<T> property, final String value)
    {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    /**
     * Where this Dépôt's decor stands, so it cannot be broken by hand (a creative motor in a player's hands is free power).
     */
    private static boolean isDecor(final ServerLevel level, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            return false;
        }
        final BuildingDepotRegional depot = DepotRegional.of(colony);
        if (depot == null)
        {
            return false;
        }
        final List<Part> list = parts(level.getServer()).get(depot.getBuildingLevel());
        if (list == null)
        {
            return false;
        }
        final RotationMirror rm = orientation(depot);
        for (final Part part : list)
        {
            if (place(depot.getPosition(), part.offset, rm).equals(pos))
            {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level && !event.getPlayer().isCreative()
              && BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getNamespace().equals("create")
              && isDecor(level, event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    private static Map<Integer, List<Part>> parts(final MinecraftServer server)
    {
        if (parts == null)
        {
            final Map<Integer, List<Part>> loaded = new HashMap<>();
            try (Reader reader = new InputStreamReader(server.getResourceManager().getResourceOrThrow(FILE).open()))
            {
                final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (final Map.Entry<String, JsonElement> level : root.entrySet())
                {
                    final List<Part> list = new ArrayList<>();
                    for (final JsonElement element : level.getValue().getAsJsonArray())
                    {
                        final JsonObject o = element.getAsJsonObject();
                        final String id = o.get("block").getAsString();
                        final Block block = block(id);
                        if (block == null)
                        {
                            continue;
                        }
                        final JsonArray pos = o.getAsJsonArray("pos");
                        final Map<String, String> props = new HashMap<>();
                        o.getAsJsonObject("props").entrySet().forEach(e -> props.put(e.getKey(), e.getValue().getAsString()));
                        list.add(new Part(new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()), block, props, id));
                    }
                    loaded.put(Integer.parseInt(level.getKey()), list);
                }
            }
            catch (final Exception e)
            {
                InterregneMod.LOGGER.warn("Dépôt Régional: decor file unreadable, no animated parts: {}", e.toString());
            }
            parts = loaded;
        }
        return parts;
    }

    @Nullable
    private static Block block(final String id)
    {
        final ResourceLocation key = ResourceLocation.parse(id);
        return BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.get(key) : null;
    }
}
