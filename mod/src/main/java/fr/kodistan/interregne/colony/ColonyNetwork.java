package fr.kodistan.interregne.colony;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ColonyNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        Net.toClient(r, ColonyColorsPayload.TYPE, ColonyColorsPayload.STREAM_CODEC, fr.kodistan.interregne.client.ClientColonyColors::receive);
        r.playToServer(FarmCropPayload.TYPE, FarmCropPayload.STREAM_CODEC, FarmChoice::onChoice);
        Net.toClient(r, OpenFarmCropPayload.TYPE, OpenFarmCropPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.blocks.CropChoiceScreen.openForFarm(p));
        Net.toClient(r, FarmCropsSyncPayload.TYPE, FarmCropsSyncPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.ClientFarmCrops.set(p.crops()));
        r.playToServer(PlacementModePayload.TYPE, PlacementModePayload.STREAM_CODEC, BuildNow::onPayload);
        r.playToServer(IntendanceActionPayload.TYPE, IntendanceActionPayload.STREAM_CODEC, IntendanceDesk::onAction);
        Net.toClient(r, IntendanceQuotePayload.TYPE, IntendanceQuotePayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.ClientIntendance.set(p));
    }

    private ColonyNetwork()
    {
    }
}
