package fr.kodistan.interregne.economy;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class EconomyNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        Net.toClient(r, WalletSyncPayload.TYPE, WalletSyncPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.hud.InterregneHud.setWallet(p.bank(), p.regional()));
        r.playToServer(BankActionPayload.TYPE, BankActionPayload.STREAM_CODEC, BankCounterBlock::onAction);
        Net.toClient(r, OpenBankPayload.TYPE, OpenBankPayload.STREAM_CODEC, p -> fr.kodistan.interregne.client.blocks.BankScreen.open(p));
        r.playToServer(RegionalBankActionPayload.TYPE, RegionalBankActionPayload.STREAM_CODEC, RegionalBank::onAction);
        Net.toClient(r, OpenRegionalBankPayload.TYPE, OpenRegionalBankPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.blocks.RegionalBankScreen.open(p));
        Net.toClient(r, TreasurySyncPayload.TYPE, TreasurySyncPayload.STREAM_CODEC,
          p -> fr.kodistan.interregne.client.ClientTreasury.set(p.colonyKey(), p.balance()));
    }

    private EconomyNetwork()
    {
    }
}
