package fr.kodistan.interregne.client.walls;

import com.minecolonies.api.colony.IColonyView;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.network.WallSyncPayload;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The colonies' walls as the server last told this client.
 */
public final class ClientWalls
{
    private static final Map<String, WallSyncPayload> WALLS = new HashMap<>();

    private ClientWalls()
    {
    }

    public static void put(final WallSyncPayload payload)
    {
        if (payload.points().isEmpty())
        {
            WALLS.remove(payload.colony());
            return;
        }
        WALLS.put(payload.colony(), payload);
    }

    @Nullable
    public static WallSyncPayload of(final IColonyView colony)
    {
        return WALLS.get(RegionalTreasury.keyOf(colony.getDimension(), colony.getID()));
    }
}
