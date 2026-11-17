package fr.kodistan.interregne.commerce;

import fr.kodistan.interregne.network.Net;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CommerceNetwork
{
    private CommerceNetwork()
    {
    }

    public static void register(final PayloadRegistrar r)
    {
        r.playToServer(RelaisActionPayload.TYPE, RelaisActionPayload.STREAM_CODEC, RelaisDesk::onAction);
        r.playToServer(MapRequestPayload.TYPE, MapRequestPayload.STREAM_CODEC, KingdomMap::onRequest);
        Net.toClient(r, MapStatePayload.TYPE, MapStatePayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.commerce.KingdomMapScreen.receive(p));
        Net.toClient(r, RelaisStatePayload.TYPE, RelaisStatePayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.hut.HutBodies.receive(p));
    }
}
