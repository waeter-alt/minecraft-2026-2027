package fr.kodistan.interregne.politics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Empire's state, saved with the world: who the Intendants are, the edicts in force, the imperial treasury, the tithe schedule and what each colony has delivered for the current tithe.
 */
public final class Empire extends SavedData
{
    private static final String NAME = "interregne_empire";

    /**
     * Weight of the tithe, set by edict: Clémente, Ordinaire, Écrasante.
     */
    public enum TitheWeight
    {
        CLEMENTE("Clémente", 0.5),
        ORDINAIRE("Ordinaire", 1.0),
        ECRASANTE("Écrasante", 2.0);

        private final String label;
        private final double factor;

        TitheWeight(final String label, final double factor)
        {
            this.label = label;
            this.factor = factor;
        }

        public String label()
        {
            return label;
        }

        public double factor()
        {
            return factor;
        }
    }

    private final Set<UUID> intendants = new HashSet<>();
    private TitheWeight titheWeight = TitheWeight.ORDINAIRE;
    private long treasury;
    private long titheDueAt;
    private boolean titheActive;

    /**
     * Crates delivered for the current tithe, by colony key ("dimension:id") then crate id.
     */
    private final Map<String, Map<String, Integer>> delivered = new HashMap<>();

    /**
     * Presence Influence earned today and online minutes counted toward the next point, per player.
     */
    private final Map<UUID, Integer> presenceToday = new HashMap<>();
    private long presenceDay = -1;

    public static Empire get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Empire::new, Empire::load, null), NAME);
    }

    public Set<UUID> intendants()
    {
        return intendants;
    }

    public boolean isIntendant(final UUID player)
    {
        return intendants.contains(player);
    }

    public TitheWeight titheWeight()
    {
        return titheWeight;
    }

    public void setTitheWeight(final TitheWeight weight)
    {
        titheWeight = weight;
        setDirty();
    }

    public long treasury()
    {
        return treasury;
    }

    public void addTreasury(final long amount)
    {
        treasury = Math.max(0, treasury + amount);
        setDirty();
    }

    public long titheDueAt()
    {
        return titheDueAt;
    }

    public boolean titheActive()
    {
        return titheActive;
    }

    public void startTithe(final long dueAt)
    {
        titheActive = true;
        titheDueAt = dueAt;
        delivered.clear();
        setDirty();
    }

    public void endTithe()
    {
        titheActive = false;
        delivered.clear();
        setDirty();
    }

    public Map<String, Integer> deliveredBy(final String colonyKey)
    {
        return delivered.computeIfAbsent(colonyKey, key -> new HashMap<>());
    }

    public void markDirty()
    {
        setDirty();
    }

    /**
     * Presence points earned today, resetting at each new real day.
     */
    public int presenceToday(final UUID player, final long day)
    {
        if (day != presenceDay)
        {
            presenceDay = day;
            presenceToday.clear();
        }
        return presenceToday.getOrDefault(player, 0);
    }

    public void addPresence(final UUID player)
    {
        presenceToday.merge(player, 1, Integer::sum);
        setDirty();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final ListTag list = new ListTag();
        intendants.forEach(uuid -> list.add(StringTag.valueOf(uuid.toString())));
        tag.put("intendants", list);
        tag.putString("titheWeight", titheWeight.name());
        tag.putLong("treasury", treasury);
        tag.putLong("titheDueAt", titheDueAt);
        tag.putBoolean("titheActive", titheActive);
        final CompoundTag deliveries = new CompoundTag();
        delivered.forEach((colony, crates) -> {
            final CompoundTag entry = new CompoundTag();
            crates.forEach(entry::putInt);
            deliveries.put(colony, entry);
        });
        tag.put("delivered", deliveries);
        return tag;
    }

    private static Empire load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final Empire empire = new Empire();
        tag.getList("intendants", Tag.TAG_STRING).forEach(entry -> empire.intendants.add(UUID.fromString(entry.getAsString())));
        try
        {
            empire.titheWeight = TitheWeight.valueOf(tag.getString("titheWeight"));
        }
        catch (final IllegalArgumentException e)
        {
            empire.titheWeight = TitheWeight.ORDINAIRE;
        }
        empire.treasury = tag.getLong("treasury");
        empire.titheDueAt = tag.getLong("titheDueAt");
        empire.titheActive = tag.getBoolean("titheActive");
        final CompoundTag deliveries = tag.getCompound("delivered");
        for (final String colony : deliveries.getAllKeys())
        {
            final CompoundTag entry = deliveries.getCompound(colony);
            final Map<String, Integer> crates = new HashMap<>();
            entry.getAllKeys().forEach(crate -> crates.put(crate, entry.getInt(crate)));
            empire.delivered.put(colony, crates);
        }
        return empire;
    }
}
