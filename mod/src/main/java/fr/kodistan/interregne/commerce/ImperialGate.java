package fr.kodistan.interregne.commerce;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.region.MarketBlock;
import fr.kodistan.interregne.region.MarketSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a colony's caravans to the Empire arrive: for now a Marché impérial block placed by the Intendants. This is
 * the only place that knows it: when the imperial city gets its own colony, the destination is redefined here and
 * the roads and caravans follow.
 */
public final class ImperialGate
{
    /**
     * A destination and how far it lies from the asking colony, in blocks.
     */
    public record Dock(BlockPos pos, int distance)
    {
    }

    private ImperialGate()
    {
    }

    /**
     * The destinations that exist, nearest to {@code from} first; the markets that no longer stand are forgotten.
     */
    public static List<Dock> docks(final MinecraftServer server, final BlockPos from)
    {
        final ServerLevel level = server.overworld();
        final MarketSites sites = MarketSites.get(server);
        final List<Dock> docks = new ArrayList<>();
        for (final BlockPos pos : sites.all())
        {
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof MarketBlock))
            {
                sites.remove(pos);
                continue;
            }
            docks.add(new Dock(pos, (int) Math.round(Math.sqrt(pos.distSqr(from)))));
        }
        docks.sort(Comparator.comparingInt(Dock::distance));
        return docks;
    }

    /**
     * The destination the player chose ({@code chosen}, a packed position), the nearest one when it is 0 or unknown.
     *
     * @return null when there is none
     */
    @Nullable
    public static BlockPos find(final MinecraftServer server, final IColony colony, final long chosen)
    {
        final List<Dock> docks = docks(server, colony.getCenter());
        for (final Dock dock : docks)
        {
            if (chosen != 0 && dock.pos().asLong() == chosen)
            {
                return dock.pos();
            }
        }
        return docks.isEmpty() ? null : docks.getFirst().pos();
    }
}
