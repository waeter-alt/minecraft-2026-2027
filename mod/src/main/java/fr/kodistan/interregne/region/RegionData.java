package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Which crate each colony produces, and the state of each colony's Comptoir régional, saved with the world.
 * The Comptoir's level and stock belong to the colony, so moving or rebuilding the block loses nothing.
 */
public final class RegionData extends SavedData
{
    private static final String NAME = "interregne_regions";

    private final Map<String, Crate>    regions   = new HashMap<>();
    private final Map<String, Comptoir> comptoirs = new HashMap<>();

    /**
     * A colony's Comptoir: where it stands (null when not placed), its level, crates kept inside,
     * production progress in ticks and the game time it was last advanced.
     */
    public static final class Comptoir
    {
        @Nullable
        public BlockPos pos;
        public int      level = 1;
        public int      stock;
        public long     progress;
        public long     lastTime = -1;
    }

    public static RegionData get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RegionData::new, RegionData::load, null), NAME);
    }

    /**
     * Stable key of a colony across dimensions.
     */
    public static String key(final IColony colony)
    {
        return colony.getDimension().location() + "#" + colony.getID();
    }

    @Nullable
    public Crate regionOf(final IColony colony)
    {
        return regions.get(key(colony));
    }

    public boolean hasRegion(final IColony colony)
    {
        return regions.containsKey(key(colony));
    }

    public void setRegion(final IColony colony, final Crate crate)
    {
        regions.put(key(colony), crate);
        setDirty();
    }

    /**
     * How many colonies already produce each crate.
     */
    public Map<Crate, Integer> claims()
    {
        final Map<Crate, Integer> claims = new EnumMap<>(Crate.class);
        for (final Crate crate : Crate.values())
        {
            claims.put(crate, 0);
        }
        regions.values().forEach(crate -> claims.merge(crate, 1, Integer::sum));
        return claims;
    }

    public Comptoir comptoir(final IColony colony)
    {
        return comptoirs.computeIfAbsent(key(colony), key -> new Comptoir());
    }

    @Nullable
    public Comptoir existingComptoir(final IColony colony)
    {
        return comptoirs.get(key(colony));
    }

    public void touch()
    {
        setDirty();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final CompoundTag regionTag = new CompoundTag();
        regions.forEach((colony, crate) -> regionTag.putString(colony, crate.id()));
        tag.put("regions", regionTag);

        final CompoundTag comptoirTag = new CompoundTag();
        comptoirs.forEach((colony, comptoir) -> {
            final CompoundTag entry = new CompoundTag();
            if (comptoir.pos != null)
            {
                entry.putLong("pos", comptoir.pos.asLong());
            }
            entry.putInt("level", comptoir.level);
            entry.putInt("stock", comptoir.stock);
            entry.putLong("progress", comptoir.progress);
            entry.putLong("lastTime", comptoir.lastTime);
            comptoirTag.put(colony, entry);
        });
        tag.put("comptoirs", comptoirTag);
        return tag;
    }

    private static RegionData load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final RegionData data = new RegionData();
        final CompoundTag regionTag = tag.getCompound("regions");
        for (final String colony : regionTag.getAllKeys())
        {
            final Crate crate = Crate.byId(regionTag.getString(colony));
            if (crate != null)
            {
                data.regions.put(colony, crate);
            }
        }
        final CompoundTag comptoirTag = tag.getCompound("comptoirs");
        for (final String colony : comptoirTag.getAllKeys())
        {
            final CompoundTag entry = comptoirTag.getCompound(colony);
            final Comptoir comptoir = new Comptoir();
            comptoir.pos = entry.contains("pos") ? BlockPos.of(entry.getLong("pos")) : null;
            comptoir.level = Math.max(1, entry.getInt("level"));
            comptoir.stock = entry.getInt("stock");
            comptoir.progress = entry.getLong("progress");
            comptoir.lastTime = entry.getLong("lastTime");
            data.comptoirs.put(colony, comptoir);
        }
        return data;
    }
}
