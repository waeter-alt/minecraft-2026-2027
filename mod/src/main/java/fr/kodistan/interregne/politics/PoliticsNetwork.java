package fr.kodistan.interregne.politics;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PoliticsNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        r.playToServer(ThroneActionPayload.TYPE, ThroneActionPayload.STREAM_CODEC, ThroneBlock::onAction);
        Net.toClient(r, OpenThronePayload.TYPE, OpenThronePayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.blocks.ThroneScreen.open(p));
        Net.toClient(r, InfluenceSyncPayload.TYPE, InfluenceSyncPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.hud.InterregneHud.setInfluence(p.influence(), p.delta()));
    }

    private PoliticsNetwork()
    {
    }
}
