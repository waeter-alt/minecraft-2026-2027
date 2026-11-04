package fr.kodistan.interregne.client;

import com.minecolonies.api.colony.IColonyView;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's copy of the regional treasuries of the player's colonies, pushed by the server when they change,
 * so windows can count the treasury without a round trip.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class ClientTreasury
{
    private static final Map<String, Long> BALANCES = new HashMap<>();

    private ClientTreasury()
    {
    }

    public static void set(final String colonyKey, final long balance)
    {
        BALANCES.put(colonyKey, balance);
    }

    /**
     * Another server may reuse the same colony ids.
     */
    @SubscribeEvent
    public static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        BALANCES.clear();
    }

    /**
     * The colony's treasury as last heard from the server; zero when unknown (not a member, or never synced).
     */
    public static long of(final IColonyView colony)
    {
        if (fr.kodistan.interregne.client.dev.DevOverrides.treasury != null)
        {
            return fr.kodistan.interregne.client.dev.DevOverrides.treasury;
        }
        return colony == null ? 0 : BALANCES.getOrDefault(RegionalTreasury.keyOf(colony.getDimension(), colony.getID()), 0L);
    }
}
