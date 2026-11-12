package fr.kodistan.interregne.routes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Every Poste commercial standing in the world, saved with it. A test fixture for the trade-route algorithm, not
 * tied to colonies: any two posts can be linked, regardless of who owns the land they stand on.
 */
public final class RouteRegistry extends SavedData
{
    private static final String NAME = "interregne_routes";

    private final Set<Long> posts = new HashSet<>();

    public static RouteRegistry get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RouteRegistry::new, RouteRegistry::load, null), NAME);
    }

    public void add(final BlockPos pos)
    {
        if (posts.add(pos.asLong()))
        {
            setDirty();
        }
    }

    public void remove(final BlockPos pos)
    {
        if (posts.remove(pos.asLong()))
        {
            setDirty();
        }
    }

    /**
     * The closest other post to this one, or null when there is none (or only itself).
     */
    public BlockPos nearestOther(final BlockPos from)
    {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (final long packed : posts)
        {
            final BlockPos candidate = BlockPos.of(packed);
            if (candidate.equals(from))
            {
                continue;
            }
            final double distSq = from.distSqr(candidate);
            if (distSq < best)
            {
                best = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static RouteRegistry load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final RouteRegistry registry = new RouteRegistry();
        for (final long packed : tag.getLongArray("posts"))
        {
            registry.posts.add(packed);
        }
        return registry;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final long[] array = new long[posts.size()];
        int i = 0;
        for (final long packed : posts)
        {
            array[i++] = packed;
        }
        tag.putLongArray("posts", array);
        return tag;
    }
}
