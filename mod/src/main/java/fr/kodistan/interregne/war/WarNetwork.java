package fr.kodistan.interregne.war;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WarNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        Net.toClient(r, WarRuinsSyncPayload.TYPE, WarRuinsSyncPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.war.ClientWarRuins.put(p));
        Net.toClient(r, WarRecapPayload.TYPE, WarRecapPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.war.WarRecapScreen.open(p));
        Net.toClient(r, AssaultDeathPayload.TYPE, AssaultDeathPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.war.AssaultDeathScreen.receive(p));
        Net.toClient(r, SiegeBannerSyncPayload.TYPE, SiegeBannerSyncPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.war.ClientSiegeBanners.put(p));
        r.playToServer(WarRebuildPayload.TYPE, WarRebuildPayload.STREAM_CODEC, WarAssaults::onRebuildAll);
    }

    private WarNetwork()
    {
    }
}
