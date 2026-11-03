package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.WalletSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every two seconds, tell each player what they hold at the Imperial Bank and in their colony's treasury,
 * so the HUD can show their whole fortune.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class WalletSync
{
    private static final int INTERVAL = 40;

    private static int ticks;

    private WalletSync()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (++ticks % INTERVAL != 0)
        {
            return;
        }
        final ImperialBank bank = ImperialBank.get(event.getServer());
        final RegionalTreasury treasury = RegionalTreasury.get(event.getServer());
        for (final ServerPlayer player : event.getServer().getPlayerList().getPlayers())
        {
            final IColony colony = ImperialBank.colonyOf(player);
            PacketDistributor.sendToPlayer(player, new WalletSyncPayload(bank.balance(player.getUUID()), colony == null ? 0 : treasury.balance(colony)));
        }
    }
}
