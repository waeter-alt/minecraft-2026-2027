package fr.kodistan.interregne.warehouse;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.inventory.api.CombinedItemHandler;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.core.util.SortingUtils;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The warehouse keeps itself tidy: its racks are sorted in the background, at any level,
 * so the chest room always reads like a well-kept store.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class AutoSort
{
    private static int ticks;

    private AutoSort()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final int seconds = Config.AUTO_SORT_SECONDS.get();
        if (seconds <= 0 || ++ticks % (seconds * 20) != 0)
        {
            return;
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
            {
                if (building instanceof BuildingWareHouse warehouse)
                {
                    sort(colony, warehouse);
                }
            }
        }
    }

    public static void sort(final IColony colony, final BuildingWareHouse warehouse)
    {
        // Only with every rack loaded: sorting moves items between racks
        if (!WorldUtil.isBlockLoaded(colony.getWorld(), warehouse.getPosition()))
        {
            return;
        }
        for (final BlockPos rack : warehouse.getContainers())
        {
            if (!WorldUtil.isBlockLoaded(colony.getWorld(), rack))
            {
                return;
            }
        }
        if (warehouse.getTileEntity() != null && warehouse.getItemHandlerCap() instanceof CombinedItemHandler inventory)
        {
            try
            {
                SortingUtils.sort(colony.getWorld().registryAccess(), inventory);
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.warn("Warehouse auto-sort failed for colony {}", colony.getID(), e);
            }
        }
    }
}
