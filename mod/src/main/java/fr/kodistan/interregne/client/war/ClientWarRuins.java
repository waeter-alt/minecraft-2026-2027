package fr.kodistan.interregne.client.war;

import com.minecolonies.api.colony.IColonyView;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.network.WarRuinsSyncPayload;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The colonies' war-ruin counts as the server last told this client. */
public final class ClientWarRuins
{
    private static final Map<String, WarRuinsSyncPayload> RUINS = new HashMap<>();

    private ClientWarRuins()
    {
    }

    public static void put(final WarRuinsSyncPayload payload)
    {
        if (payload.count() <= 0)
        {
            RUINS.remove(payload.colony());
            return;
        }
        RUINS.put(payload.colony(), payload);
    }

    @Nullable
    public static WarRuinsSyncPayload of(final IColonyView colony)
    {
        return RUINS.get(RegionalTreasury.keyOf(colony.getDimension(), colony.getID()));
    }
}
