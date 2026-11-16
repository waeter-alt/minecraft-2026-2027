package fr.kodistan.interregne.client.hut;

import fr.kodistan.interregne.network.DiplomacyActionPayload;
import fr.kodistan.interregne.network.DiplomacySyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Each colony's view of the others (alliances and proposals) as the server last told this client. */
public final class ClientDiplomacy
{
    private static final Map<Integer, DiplomacySyncPayload> BY_COLONY = new HashMap<>();

    private ClientDiplomacy()
    {
    }

    public static void put(final DiplomacySyncPayload payload)
    {
        BY_COLONY.put(payload.colony(), payload);
    }

    @Nullable
    public static DiplomacySyncPayload of(final int colony)
    {
        return BY_COLONY.get(colony);
    }

    public static void refresh(final int colony)
    {
        PacketDistributor.sendToServer(new DiplomacyActionPayload(colony, DiplomacyActionPayload.REFRESH, 0));
    }

    public static void act(final int colony, final int action, final int target)
    {
        PacketDistributor.sendToServer(new DiplomacyActionPayload(colony, action, target));
    }
}
