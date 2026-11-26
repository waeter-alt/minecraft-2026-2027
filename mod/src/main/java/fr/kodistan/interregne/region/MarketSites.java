package fr.kodistan.interregne.region;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the Marchés impériaux stand, so a caravan can be sent to one. A market registers itself when placed or used;
 * one that no longer stands is dropped by whoever looks ({@link fr.kodistan.interregne.commerce.ImperialGate}).
 */
public final class MarketSites extends SavedData
{
    private static final String NAME = "interregne_market_sites";

    private final Set<Long> sites = new LinkedHashSet<>();

    public static MarketSites get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(MarketSites::new, MarketSites::load, null), NAME);
    }

    public void add(final BlockPos pos)
    {
        if (sites.add(pos.asLong()))
        {
            setDirty();
        }
    }

    public void remove(final BlockPos pos)
    {
        if (sites.remove(pos.asLong()))
        {
            setDirty();
        }
    }

    public List<BlockPos> all()
    {
        final List<BlockPos> list = new ArrayList<>();
        for (final long packed : sites)
        {
            list.add(BlockPos.of(packed));
        }
        return list;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        tag.putLongArray("sites", sites.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static MarketSites load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final MarketSites data = new MarketSites();
        for (final long packed : tag.getLongArray("sites"))
        {
            data.sites.add(packed);
        }
        return data;
    }
}
