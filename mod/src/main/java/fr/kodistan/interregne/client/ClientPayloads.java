package fr.kodistan.interregne.client;

import fr.kodistan.interregne.network.OpenBankPayload;

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
}
