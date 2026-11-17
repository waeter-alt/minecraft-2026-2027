package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RoutesNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        r.playToServer(RoadActionPayload.TYPE, RoadActionPayload.STREAM_CODEC, RoadDesk::onAction);
        Net.toClient(r, RoadStatePayload.TYPE, RoadStatePayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.routes.RoadScreen.open(p));
        Net.toClient(r, RoadLinesPayload.TYPE, RoadLinesPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.walls.ClientRoads.put(p));
    }

    private RoutesNetwork()
    {
    }
}
