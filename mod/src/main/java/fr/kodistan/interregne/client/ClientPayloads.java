package fr.kodistan.interregne.client;

import fr.kodistan.interregne.client.hud.InterregneHud;
import fr.kodistan.interregne.network.CrateStockPayload;
import fr.kodistan.interregne.network.InfluenceSyncPayload;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.TreasurySyncPayload;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.OpenMarketPayload;

/**
 * Client-side handling of server-to-client payloads. Only ever called on a client, so it may touch client classes.
 */
public final class ClientPayloads
{
    public static void syncCrateStock(final CrateStockPayload payload)
    {
        ClientCrateStock.set(payload.counts());
    }

    public static void openThrone(final OpenThronePayload payload)
    {
        WindowThrone.open(payload);
    }

    public static void syncInfluence(final InfluenceSyncPayload payload)
    {
        InterregneHud.setInfluence(payload.influence(), payload.delta());
    }

    private ClientPayloads()
    {
    }

    public static void openBank(final OpenBankPayload payload)
    {
        WindowBank.open(payload);
    }

    public static void openRegionalBank(final OpenRegionalBankPayload payload)
    {
        WindowRegionalBank.open(payload);
    }

    public static void syncTreasury(final TreasurySyncPayload payload)
    {
        ClientTreasury.set(payload.colonyKey(), payload.balance());
    }

    public static void openComptoir(final OpenComptoirPayload payload)
    {
        WindowComptoir.open(payload);
    }

    public static void openMarket(final OpenMarketPayload payload)
    {
        WindowMarket.open(payload);
    }
}
