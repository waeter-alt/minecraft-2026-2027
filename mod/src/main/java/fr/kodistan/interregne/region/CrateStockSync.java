package fr.kodistan.interregne.region;

import fr.kodistan.interregne.network.FarmCropsSyncPayload;
import fr.kodistan.interregne.production.Farms;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.CrateStockPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps every colony member's client aware of the crates in their warehouse, so the hut and Comptoir windows can show
 * what is really available without a round trip.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class CrateStockSync
{
    private static final int INTERVAL = 100;

    private static int ticks;

    private CrateStockSync()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (++ticks % INTERVAL != 0)
        {
            return;
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final List<Player> members = colony.getMessagePlayerEntities();
            if (members.isEmpty())
            {
                continue;
            }
            final CrateStockPayload payload = new CrateStockPayload(stock(colony));
            final FarmCropsSyncPayload crops = new FarmCropsSyncPayload(Farms.cropsGrown(colony).stream().map(Farms.Crop::id).toList());
            for (final Player member : members)
            {
                if (member instanceof ServerPlayer serverPlayer)
                {
                    PacketDistributor.sendToPlayer(serverPlayer, payload);
                    PacketDistributor.sendToPlayer(serverPlayer, crops);
                }
            }
        }
    }

    /**
     * Crates in the colony warehouse, one count per crate type in enum order (zeros without a loaded warehouse).
     */
    public static List<Integer> stock(final IColony colony)
    {
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        final List<Integer> counts = new ArrayList<>();
        for (final Crate crate : Crate.values())
        {
            counts.add(racks == null ? 0 : CrateStores.count(racks, crate));
        }
        return counts;
    }
}
