package fr.kodistan.interregne.garrison;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The guards a colony bought, by citizen: their kind and troop level. They stay out of the population count, and their
 * model and level come from here. An entry whose citizen died (or whose id went to someone else) is dropped when read.
 */
public final class GarrisonRoster extends SavedData
{
    private static final String NAME = "interregne_garrison";

    public record Guard(UUID uuid, int kind, int level)
    {
    }

    private final Map<Integer, Map<Integer, Guard>> colonies = new HashMap<>();

    public static GarrisonRoster get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(GarrisonRoster::new, GarrisonRoster::load, null), NAME);
    }

    @Nullable
    public static GarrisonRoster current()
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : get(server);
    }

    public void add(final IColony colony, final ICitizenData citizen, final int kind, final int level)
    {
        colonies.computeIfAbsent(colony.getID(), k -> new HashMap<>()).put(citizen.getId(), new Guard(citizen.getUUID(), kind, level));
        setDirty();
    }

    @Nullable
    public Guard of(final IColony colony, final ICitizenData citizen)
    {
        final Map<Integer, Guard> guards = colonies.get(colony.getID());
        final Guard guard = guards == null ? null : guards.get(citizen.getId());
        return guard != null && guard.uuid().equals(citizen.getUUID()) ? guard : null;
    }

    /**
     * Bought guards still alive in this colony.
     */
    public int count(final IColony colony)
    {
        final Map<Integer, Guard> guards = colonies.get(colony.getID());
        if (guards == null)
        {
            return 0;
        }
        final Iterator<Map.Entry<Integer, Guard>> it = guards.entrySet().iterator();
        while (it.hasNext())
        {
            final Map.Entry<Integer, Guard> entry = it.next();
            final ICitizenData citizen = colony.getCitizenManager().getCivilian(entry.getKey());
            if (citizen == null || !citizen.getUUID().equals(entry.getValue().uuid()))
            {
                it.remove();
                setDirty();
            }
        }
        return guards.size();
    }

    /**
     * Bought guards of this colony, for the population checks; 0 on the client or without a server.
     */
    public static int countIn(final IColony colony)
    {
        if (colony.getWorld() == null || colony.getWorld().isClientSide)
        {
            return 0;
        }
        final GarrisonRoster roster = current();
        return roster == null ? 0 : roster.count(colony);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ListTag list = new ListTag();
        colonies.forEach((colony, guards) -> guards.forEach((id, guard) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("colony", colony);
            entry.putInt("citizen", id);
            entry.putUUID("uuid", guard.uuid());
            entry.putInt("kind", guard.kind());
            entry.putInt("level", guard.level());
            list.add(entry);
        }));
        tag.put("guards", list);
        return tag;
    }

    private static GarrisonRoster load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final GarrisonRoster data = new GarrisonRoster();
        for (final Tag t : tag.getList("guards", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) t;
            data.colonies.computeIfAbsent(entry.getInt("colony"), k -> new HashMap<>())
              .put(entry.getInt("citizen"), new Guard(entry.getUUID("uuid"), entry.getInt("kind"), entry.getInt("level")));
        }
        return data;
    }
}
