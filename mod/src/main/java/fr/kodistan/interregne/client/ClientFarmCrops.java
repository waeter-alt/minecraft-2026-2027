package fr.kodistan.interregne.client;

import java.util.List;

/**
 * The crops grown by the player's colony farms, as last sent by the server.
 */
public final class ClientFarmCrops
{
    private static List<String> crops = List.of();

    private ClientFarmCrops()
    {
    }

    public static void set(final List<String> latest)
    {
        crops = List.copyOf(latest);
    }

    public static List<String> get()
    {
        return crops;
    }
}
