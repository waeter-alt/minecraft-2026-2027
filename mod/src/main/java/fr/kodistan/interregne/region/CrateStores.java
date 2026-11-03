package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.core.tileentities.TileEntityWareHouse;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Counting, taking and storing crates in any inventory, and reaching a colony's warehouse racks.
 */
public final class CrateStores
{
    private CrateStores()
    {
    }

    /**
     * The colony's warehouse, when its chunk is loaded.
     */
    @Nullable
    public static IBuilding warehouse(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return null;
        }
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof IWareHouse && level.isLoaded(building.getPosition()) && building.getTileEntity() != null)
            {
                return building;
            }
        }
        return null;
    }

    /**
     * Every rack of the colony's warehouse as one inventory; null without a loaded warehouse.
     */
    @Nullable
    public static IItemHandler warehouseRacks(final IColony colony)
    {
        final IBuilding warehouse = warehouse(colony);
        return warehouse == null ? null : warehouse.getItemHandlerCap();
    }

    /**
     * Store crates in the warehouse racks, the way the magic network files goods.
     *
     * @return how many were stored.
     */
    public static int storeInWarehouse(final IColony colony, final Crate crate, final int count)
    {
        final IBuilding warehouse = warehouse(colony);
        if (warehouse == null || !(warehouse.getTileEntity() instanceof TileEntityWareHouse storage))
        {
            return 0;
        }
        int left = count;
        while (left > 0)
        {
            final ItemStack stack = crate.stack(Math.min(left, Crates.STACK_SIZE));
            final AbstractTileEntityRack rack = storage.getRackForStack(stack);
            if (rack == null)
            {
                break;
            }
            final int offered = stack.getCount();
            final ItemStack rest = ItemHandlerHelper.insertItemStacked(rack.getItemHandlerCap(), stack, false);
            final int stored = offered - rest.getCount();
            left -= stored;
            if (stored == 0)
            {
                break;
            }
        }
        if (left < count)
        {
            warehouse.markDirty();
        }
        return count - left;
    }

    public static int count(@Nullable final IItemHandler handler, final Crate crate)
    {
        if (handler == null)
        {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (Crate.of(stack) == crate)
            {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Every crate of any type in the inventory.
     */
    public static int countAll(final IItemHandler handler)
    {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (Crate.of(stack) != null)
            {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Take up to this many crates.
     *
     * @return how many were taken.
     */
    public static int extract(@Nullable final IItemHandler handler, final Crate crate, final int amount)
    {
        if (handler == null)
        {
            return 0;
        }
        int taken = 0;
        for (int slot = 0; slot < handler.getSlots() && taken < amount; slot++)
        {
            if (Crate.of(handler.getStackInSlot(slot)) == crate)
            {
                taken += handler.extractItem(slot, amount - taken, false).getCount();
            }
        }
        return taken;
    }
}
