package fr.kodistan.interregne.client;

import fr.kodistan.interregne.region.Crate;

import java.util.List;

/**
 * The crates in the local player's colony warehouse, as last synced by the server.
 */
public final class ClientCrateStock
{
    private static List<Integer> counts = List.of();

    private ClientCrateStock()
    {
    }

    public static void set(final List<Integer> latest)
    {
        counts = List.copyOf(latest);
    }

    public static int get(final Crate crate)
    {
        return crate.ordinal() < counts.size() ? counts.get(crate.ordinal()) : 0;
    }
}
