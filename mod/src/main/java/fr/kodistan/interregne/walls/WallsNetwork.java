package fr.kodistan.interregne.walls;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WallsNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        r.playToServer(WallBuildPayload.TYPE, WallBuildPayload.STREAM_CODEC, Walls::onBuild);
        r.playToServer(WallDemolishPayload.TYPE, WallDemolishPayload.STREAM_CODEC, Walls::onDemolish);
        Net.toClient(r, WallSyncPayload.TYPE, WallSyncPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.walls.ClientWalls.put(p));
    }

    private WallsNetwork()
    {
    }
}
