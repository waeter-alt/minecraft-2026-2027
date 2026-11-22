package fr.kodistan.interregne.garrison;

import fr.kodistan.interregne.network.Net;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class GarrisonNetwork
{
    private GarrisonNetwork()
    {
    }

    public static void register(final PayloadRegistrar r)
    {
        r.playToServer(GarrisonActionPayload.TYPE, GarrisonActionPayload.STREAM_CODEC, GarrisonDesk::onAction);
        Net.toClient(r, GarrisonStatePayload.TYPE, GarrisonStatePayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.hut.HutBodies.receive(p));
    }
}
