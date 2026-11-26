package fr.kodistan.interregne.region;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RegionNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        Net.toClient(r, CrateStockPayload.TYPE, CrateStockPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.ClientCrateStock.set(p.counts()));
        Net.toClient(r, ColonyEntryPayload.TYPE, ColonyEntryPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.hud.ColonyBanner.show(p.name(), p.crate()));
        r.playToServer(RegionActionPayload.TYPE, RegionActionPayload.STREAM_CODEC, Regions::onAction);
        Net.toClient(r, OpenComptoirPayload.TYPE, OpenComptoirPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.hut.HutBodies.receive(p));
        Net.toClient(r, OpenMarketPayload.TYPE, OpenMarketPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.blocks.MarketScreen.open(p));
    }

    private RegionNetwork()
    {
    }
}
