package fr.kodistan.interregne.commerce;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server side of the kingdom map: any player may ask where the colonies and the caravans are. A caravan walking on
 * paper (no player near it) is placed from its progress along the road, so the map is always complete.
 */
public final class KingdomMap
{
    /** A client asking more often than this (ticks) is ignored: the map refreshes once a second. */
    private static final int MIN_GAP = 8;

    private static final Map<UUID, Long> LAST = new HashMap<>();

    private KingdomMap()
    {
    }

    public static void onRequest(final MapRequestPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final long now = player.server.getTickCount();
        final Long last = LAST.put(player.getUUID(), now);
        if (last != null && now - last < MIN_GAP)
        {
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(player.server));
    }

    public static MapStatePayload snapshot(final MinecraftServer server)
    {
        final List<MapStatePayload.Colony> colonies = new ArrayList<>();
        final Map<Integer, String> names = new HashMap<>();
        for (final IColony colony : IColonyManager.getInstance().getColonies(server.overworld()))
        {
            colonies.add(new MapStatePayload.Colony(colony.getID(), colony.getName(), colony.getCenter().getX(), colony.getCenter().getZ()));
            names.put(colony.getID(), colony.getName());
        }
        final List<MapStatePayload.Caravan> caravans = new ArrayList<>();
        for (final Contract contract : Contracts.get(server).all())
        {
            if (contract.state == Contract.OFFER || contract.path.length < 2)
            {
                continue;
            }
            final double[] where = Contracts.where(contract);
            final Crate crate = Crate.byId(contract.crate);
            final String label = names.getOrDefault(contract.seller, "?") + " → " + names.getOrDefault(contract.buyer, "?") + " : " + contract.quantity + " × "
                                   + (crate == null ? "?" : crate.displayName().toLowerCase());
            final int percent = (int) Math.round(100.0 * contract.progress / (contract.path.length - 1));
            caravans.add(new MapStatePayload.Caravan(contract.id, contract.seller, contract.buyer, label, (int) Math.round(where[0]), (int) Math.round(where[1]),
              (int) Math.round(where[2]), percent, contract.escort, contract.engaged));
        }
        return new MapStatePayload(colonies, caravans);
    }
}
