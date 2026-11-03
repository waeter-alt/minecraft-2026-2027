package fr.kodistan.interregne.pvp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * PvP-free cuboids (the imperial city, markets...), saved with the world.
 */
public final class SafeZones extends SavedData
{
    private static final String NAME = "interregne_safe_zones";

    /**
     * A named cuboid in one dimension, bounds included.
     */
    public record Zone(String name, ResourceKey<Level> dimension, BoundingBox box)
    {
        public boolean contains(final ResourceKey<Level> level, final BlockPos pos)
        {
            return dimension.equals(level) && box.isInside(pos);
        }
    }

    /**
     * Zones by lower-case name.
     */
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public static SafeZones get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(SafeZones::new, SafeZones::load, null), NAME);
    }

    /**
     * Add a zone, replacing any zone of the same name.
     *
     * @return the replaced zone, if any.
     */
    @Nullable
    public Zone put(final Zone zone)
    {
        final Zone previous = zones.put(key(zone.name()), zone);
        setDirty();
        return previous;
    }

    @Nullable
    public Zone remove(final String name)
    {
        final Zone removed = zones.remove(key(name));
        if (removed != null)
        {
            setDirty();
        }
        return removed;
    }

    public Collection<Zone> all()
    {
        return Collections.unmodifiableCollection(zones.values());
    }

    /**
     * The safe zone containing this position, or null.
     */
    @Nullable
    public Zone at(final ResourceKey<Level> level, final BlockPos pos)
    {
        for (final Zone zone : zones.values())
        {
            if (zone.contains(level, pos))
            {
                return zone;
            }
        }
        return null;
    }

    private static String key(final String name)
    {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final ListTag list = new ListTag();
        for (final Zone zone : zones.values())
        {
            final CompoundTag entry = new CompoundTag();
            entry.putString("name", zone.name());
            entry.putString("dimension", zone.dimension().location().toString());
            entry.putIntArray("box", new int[] {zone.box().minX(), zone.box().minY(), zone.box().minZ(), zone.box().maxX(), zone.box().maxY(), zone.box().maxZ()});
            list.add(entry);
        }
        tag.put("zones", list);
        return tag;
    }

    private static SafeZones load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final SafeZones data = new SafeZones();
        final ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            final ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            final int[] box = entry.getIntArray("box");
            if (dimension == null || box.length != 6)
            {
                continue;
            }
            final Zone zone = new Zone(entry.getString("name"), ResourceKey.create(Registries.DIMENSION, dimension),
              new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
            data.zones.put(key(zone.name()), zone);
        }
        return data;
    }
}
