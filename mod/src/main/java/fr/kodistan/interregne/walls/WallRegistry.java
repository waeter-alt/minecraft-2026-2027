package fr.kodistan.interregne.walls;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.economy.RegionalTreasury;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each colony's wall, saved with the world: its trace and level, so it can be shown, upgraded and edited later.
 */
public final class WallRegistry extends SavedData
{
    private static final String NAME = "interregne_walls";

    /**
     * A colony's wall.
     */
    public record Wall(List<WallTrace.Point> points, List<WallTrace.Point> gates, boolean closed, int level)
    {
    }

    private final Map<String, Wall> walls = new HashMap<>();

    public static WallRegistry get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WallRegistry::new, WallRegistry::load, null), NAME);
    }

    @Nullable
    public Wall of(final IColony colony)
    {
        return walls.get(RegionalTreasury.keyOf(colony));
    }

    public Map<String, Wall> all()
    {
        return walls;
    }

    public void put(final IColony colony, final Wall wall)
    {
        walls.put(RegionalTreasury.keyOf(colony), wall);
        setDirty();
    }

    private static WallRegistry load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final WallRegistry registry = new WallRegistry();
        final CompoundTag all = tag.getCompound("walls");
        for (final String key : all.getAllKeys())
        {
            final CompoundTag entry = all.getCompound(key);
            final List<WallTrace.Point> points = new ArrayList<>();
            final int[] xs = entry.getIntArray("x");
            final int[] zs = entry.getIntArray("z");
            for (int i = 0; i < Math.min(xs.length, zs.length); i++)
            {
                points.add(new WallTrace.Point(xs[i], zs[i]));
            }
            final List<WallTrace.Point> gates = new ArrayList<>();
            final int[] gxs = entry.getIntArray("gx");
            final int[] gzs = entry.getIntArray("gz");
            for (int i = 0; i < Math.min(gxs.length, gzs.length); i++)
            {
                gates.add(new WallTrace.Point(gxs[i], gzs[i]));
            }
            registry.walls.put(key, new Wall(points, gates, entry.getBoolean("closed"), Math.max(1, entry.getInt("level"))));
        }
        return registry;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final CompoundTag all = new CompoundTag();
        for (final Map.Entry<String, Wall> entry : walls.entrySet())
        {
            final CompoundTag wall = new CompoundTag();
            final List<WallTrace.Point> points = entry.getValue().points();
            wall.putIntArray("x", points.stream().mapToInt(WallTrace.Point::x).toArray());
            wall.putIntArray("z", points.stream().mapToInt(WallTrace.Point::z).toArray());
            final List<WallTrace.Point> gates = entry.getValue().gates();
            wall.putIntArray("gx", gates.stream().mapToInt(WallTrace.Point::x).toArray());
            wall.putIntArray("gz", gates.stream().mapToInt(WallTrace.Point::z).toArray());
            wall.putBoolean("closed", entry.getValue().closed());
            wall.putInt("level", entry.getValue().level());
            all.put(entry.getKey(), wall);
        }
        tag.put("walls", all);
        return tag;
    }
}
