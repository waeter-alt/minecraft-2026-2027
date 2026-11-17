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
    private static final Map<Integer, Long> RECEIVED_AT = new HashMap<>();

    private ClientDiplomacy()
    {
    }

    public static void put(final DiplomacySyncPayload payload)
    {
        BY_COLONY.put(payload.colony(), payload);
        RECEIVED_AT.put(payload.colony(), System.currentTimeMillis());
    }

    @Nullable
    public static DiplomacySyncPayload of(final int colony)
    {
        return BY_COLONY.get(colony);
    }

    /** Seconds since the colony's last sync arrived, to run a war notice's countdown between two syncs. */
    public static int secondsSince(final int colony)
    {
        return (int) ((System.currentTimeMillis() - RECEIVED_AT.getOrDefault(colony, System.currentTimeMillis())) / 1000);
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
