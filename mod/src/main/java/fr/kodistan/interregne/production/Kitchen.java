package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.region.CrateStores;
import fr.kodistan.interregne.warehouse.MagicNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The restaurant is an early-game building, so it needs no fuel and no recipe setup:
 * raw food in its racks (or in its cook's pockets) is cooked on the spot, an empty menu fills itself with the cooked
 * form of whatever raw food the colony has, and once its own stock for the villagers is full it cooks the warehouse's
 * raw food for the players.
 */
public final class Kitchen
{
    private Kitchen()
    {
    }

    /**
     * Cook what is raw and fill an empty menu. Cheap, run often.
     */
    public static void tick(final IBuilding restaurant)
    {
        cookAll(restaurant.getItemHandlerCap());
        for (final ICitizenData cook : restaurant.getAllAssignedCitizen())
        {
            cookAll(cook.getInventory());
        }
        autoMenu(restaurant);
    }

    /**
     * Once the villagers' stock is full, cook a batch of the warehouse's raw food back into the warehouse.
     */
    public static void cookSurplus(final IColony colony, final IBuilding restaurant)
    {
        final RestaurantMenuModule menu = restaurant.getFirstModuleOccurance(RestaurantMenuModule.class);
        final IBuilding warehouse = Huts.warehouse(colony);
        if (menu == null || warehouse == null || menu.getMenu().isEmpty() || !stocked(restaurant, menu))
        {
            return;
        }
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        if (racks == null)
        {
            return;
        }
        int budget = restaurant.getBuildingLevel() * Config.KITCHEN_SURPLUS_PER_LEVEL.get();
        boolean cooked = false;
        for (int slot = 0; slot < racks.getSlots() && budget > 0; slot++)
        {
            final ItemStack stack = racks.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStackUtils.ISCOOKABLE.test(stack))
            {
                continue;
            }
            final ItemStack raw = racks.extractItem(slot, Math.min(budget, stack.getCount()), false);
            budget -= raw.getCount();
            final ItemStack result = cooked(raw);
            final ItemStack rest = ItemHandlerHelper.insertItemStacked(racks, result, false);
            if (!rest.isEmpty())
            {
                Huts.give(warehouse, rest);
            }
            cooked = true;
        }
        if (cooked && colony.getWorld() instanceof ServerLevel level)
        {
            MagicNetwork.comet(level, warehouse.getPosition(), restaurant.getPosition(), restaurant.getBuildingLevel());
            MagicNetwork.comet(level, restaurant.getPosition(), warehouse.getPosition(), restaurant.getBuildingLevel());
        }
    }

    /**
     * Every menu dish at the stock the restaurant keeps for its level.
     */
    private static boolean stocked(final IBuilding restaurant, final RestaurantMenuModule menu)
    {
        for (final ItemStorage dish : menu.getMenu())
        {
            final int target = dish.getItemStack().getMaxStackSize() * menu.getExpectedStock();
            if (InventoryUtils.getCountFromBuilding(restaurant, stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, dish.getItemStack(), false, true)) < target)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Until the player picks dishes, the menu offers the cooked form of every raw food in the warehouse or the restaurant.
     */
    private static void autoMenu(final IBuilding restaurant)
    {
        final RestaurantMenuModule menu = restaurant.getFirstModuleOccurance(RestaurantMenuModule.class);
        if (menu == null || !menu.getMenu().isEmpty())
        {
            return;
        }
        addDishes(menu, restaurant.getItemHandlerCap());
        addDishes(menu, CrateStores.warehouseRacks(restaurant.getColony()));
    }

    private static void addDishes(final RestaurantMenuModule menu, @Nullable final IItemHandler handler)
    {
        if (handler == null)
        {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStackUtils.ISCOOKABLE.test(stack))
            {
                final ItemStack dish = smelt(stack);
                if (!menu.getMenu().contains(new ItemStorage(dish)))
                {
                    menu.addMenuItem(dish);
                }
            }
        }
    }

    private static void cookAll(@Nullable final IItemHandler handler)
    {
        if (handler == null)
        {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStackUtils.ISCOOKABLE.test(stack))
            {
                continue;
            }
            final ItemStack raw = handler.extractItem(slot, stack.getCount(), false);
            final ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, cooked(raw), false);
            if (!rest.isEmpty())
            {
                // No room for the cooked food: leave that part raw
                handler.insertItem(slot, raw.copyWithCount(rest.getCount()), false);
            }
        }
    }

    private static ItemStack cooked(final ItemStack raw)
    {
        return smelt(raw).copyWithCount(raw.getCount());
    }

    private static ItemStack smelt(final ItemStack raw)
    {
        return IColonyManager.getInstance().getCompatibilityManager().getFurnaceRecipes().getSmeltingResult(raw).copy();
    }
}
