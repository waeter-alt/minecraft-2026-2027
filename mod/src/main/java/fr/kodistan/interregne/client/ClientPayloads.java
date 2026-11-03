package fr.kodistan.interregne.client;

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
