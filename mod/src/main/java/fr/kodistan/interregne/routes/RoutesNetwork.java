package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RoutesNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        Net.toClient(r, RoadLinesPayload.TYPE, RoadLinesPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.walls.ClientRoads.put(p));
    }

    private RoutesNetwork()
    {
    }
}
