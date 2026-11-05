package fr.kodistan.interregne.walls;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.economy.RegionalTreasury;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Each colony's wall, saved with the world: its trace, gates and level, the ground it was built on (so it can be laid
 * out again over itself) and every block it placed (so an upgrade or a retrace removes what is no longer part of it).
 */
public final class WallRegistry extends SavedData
{
    private static final String NAME = "interregne_walls";

    /**
     * A colony's wall.
     *
     * @param ground the terrain under its footprint before it was built, by cell (see {@link #cell})
     * @param placed every block position it set, as BlockPos longs
     * @param spent  Couronnes paid for it in all (building, retouches, upgrades); -1 for a wall saved before this was kept
     */
    public record Wall(List<WallTrace.Point> points, List<WallTrace.Point> gates, boolean closed, int level, Map<Long, Terrain.Probe> ground,
                       long[] placed, long spent)
    {
    }

    private final Map<String, Wall> walls = new HashMap<>();

    public static WallRegistry get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WallRegistry::new, WallRegistry::load, null), NAME);
    }

    public static long cell(final int x, final int z)
    {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
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

    public void remove(final IColony colony)
    {
        walls.remove(RegionalTreasury.keyOf(colony));
        setDirty();
    }

    private static List<WallTrace.Point> points(final int[] xs, final int[] zs)
    {
        final List<WallTrace.Point> points = new ArrayList<>();
        for (int i = 0; i < Math.min(xs.length, zs.length); i++)
        {
            points.add(new WallTrace.Point(xs[i], zs[i]));
        }
        return points;
    }

    private static WallRegistry load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final WallRegistry registry = new WallRegistry();
        final CompoundTag all = tag.getCompound("walls");
        for (final String key : all.getAllKeys())
        {
            final CompoundTag entry = all.getCompound(key);
            final Map<Long, Terrain.Probe> ground = new HashMap<>();
            final long[] cells = entry.getLongArray("cells");
            final int[] grounds = entry.getIntArray("grounds");
            for (int i = 0; i < Math.min(cells.length, grounds.length); i++)
            {
                final boolean water = (grounds[i] & (1 << 20)) != 0;
                ground.put(cells[i], new Terrain.Probe((grounds[i] & 0xFFFFF) - 4096, false, water, true));
            }
            registry.walls.put(key, new Wall(points(entry.getIntArray("x"), entry.getIntArray("z")), points(entry.getIntArray("gx"), entry.getIntArray("gz")),
              entry.getBoolean("closed"), Math.max(1, entry.getInt("level")), ground, entry.getLongArray("placed"),
              entry.contains("spent") ? entry.getLong("spent") : -1));
        }
        return registry;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final CompoundTag all = new CompoundTag();
        for (final Map.Entry<String, Wall> entry : walls.entrySet())
        {
            final Wall value = entry.getValue();
            final CompoundTag wall = new CompoundTag();
            wall.putIntArray("x", value.points().stream().mapToInt(WallTrace.Point::x).toArray());
            wall.putIntArray("z", value.points().stream().mapToInt(WallTrace.Point::z).toArray());
            wall.putIntArray("gx", value.gates().stream().mapToInt(WallTrace.Point::x).toArray());
            wall.putIntArray("gz", value.gates().stream().mapToInt(WallTrace.Point::z).toArray());
            wall.putBoolean("closed", value.closed());
            wall.putInt("level", value.level());
            final long[] cells = new long[value.ground().size()];
            final int[] grounds = new int[cells.length];
            int i = 0;
            for (final Map.Entry<Long, Terrain.Probe> probe : value.ground().entrySet())
            {
                cells[i] = probe.getKey();
                grounds[i] = (probe.getValue().ground() + 4096) | (probe.getValue().water() ? 1 << 20 : 0);
                i++;
            }
            wall.putLongArray("cells", cells);
            wall.putIntArray("grounds", grounds);
            wall.putLongArray("placed", value.placed());
            wall.putLong("spent", value.spent());
            all.put(entry.getKey(), wall);
        }
        tag.put("walls", all);
        return tag;
    }
}
