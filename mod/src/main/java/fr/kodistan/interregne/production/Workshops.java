package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.mixin.AbstractCraftingBuildingModuleAccessor;
import fr.kodistan.interregne.region.CrateStores;
import fr.kodistan.interregne.warehouse.MagicNetwork;
import net.minecraft.server.level.ServerLevel;
import com.minecolonies.api.items.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crafters work from the start with no orders: every production cycle, each one turns what the warehouse holds in
 * surplus into what it lacks, with the recipes it knows and the player left switched on. It stops once the warehouse
 * holds enough of an item and never digs into the last few of an ingredient. Furnace recipes burn the warehouse's fuel.
 * The sifter is handed its meshes. Real orders from citizens and the player still go through the crafter's own work as before.
 */
public final class Workshops
{
    private static final int SMELT_TICKS = 200;

    /**
     * Fuel already burnt and not used up, per crafter.
     */
    private static final Map<String, Integer> HEAT = new HashMap<>();

    private Workshops()
    {
    }

    public static void craftSurplus(final IColony colony, final IBuilding building)
    {
        final List<AbstractCraftingBuildingModule> modules = building.getModulesByType(AbstractCraftingBuildingModule.class);
        if (modules.isEmpty() || !(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        final IBuilding warehouse = Huts.warehouse(colony);
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        if (warehouse == null || racks == null)
        {
            return;
        }

        final List<IRecipeStorage> recipes = new ArrayList<>();
        for (final AbstractCraftingBuildingModule module : modules)
        {
            final List<IToken<?>> disabled = ((AbstractCraftingBuildingModuleAccessor) module).interregne$disabledRecipes();
            for (final IToken<?> token : new ArrayList<>(module.getRecipes()))
            {
                final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipe(token);
                // Loot recipes (the sifter's) have no fixed result but are worth running all the same
                if (storage != null && !disabled.contains(token) && (!storage.getPrimaryOutput().isEmpty() || storage.getLootTable() != null))
                {
                    recipes.add(storage);
                }
            }
        }

        if (Huts.SIFTER.equals(Huts.id(building)))
        {
            supplyMeshes(racks, building.getBuildingLevel());
        }

        final String key = colony.getID() + ":" + building.getPosition().asLong();
        int budget = building.getBuildingLevel() * Config.CRAFT_SURPLUS_PER_LEVEL.get();
        boolean crafted = false;
        for (final IRecipeStorage recipe : recipes)
        {
            if (budget <= 0)
            {
                break;
            }
            if (undoes(recipe, recipes))
            {
                continue;
            }
            final boolean furnace = recipe.getIntermediate() == Blocks.FURNACE;
            while (budget > 0 && (recipe.getPrimaryOutput().isEmpty() || count(racks, recipe.getPrimaryOutput()) < Config.CRAFT_TARGET.get()) && spare(racks, recipe))
            {
                if (furnace && !heat(racks, key))
                {
                    break;
                }
                if (!recipe.fullfillRecipe(level, List.of(racks)))
                {
                    break;
                }
                if (furnace)
                {
                    HEAT.merge(key, -SMELT_TICKS, Integer::sum);
                }
                budget -= Math.max(1, recipe.getPrimaryOutput().getCount());
                crafted = true;
            }
        }
        if (crafted)
        {
            MagicNetwork.comet(level, warehouse.getPosition(), building.getPosition(), building.getBuildingLevel());
            MagicNetwork.comet(level, building.getPosition(), warehouse.getPosition(), building.getBuildingLevel());
        }
    }

    /**
     * Every ingredient is there with some left over afterwards.
     */
    private static boolean spare(final IItemHandler racks, final IRecipeStorage recipe)
    {
        for (final ItemStorage input : recipe.getCleanedInput())
        {
            // A tool (the sifter's mesh) comes back after use: having it is enough
            final boolean tool = recipe.getCraftingTools().stream().anyMatch(stack -> same(input, stack));
            if (count(racks, input) - input.getAmount() < (tool ? 0 : Config.CRAFT_RESERVE.get()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether another recipe of the same crafter turns this one's result back into one of its ingredients (ingots and
     * blocks): crafting those from surplus would go back and forth for nothing.
     */
    private static boolean undoes(final IRecipeStorage recipe, final List<IRecipeStorage> recipes)
    {
        for (final IRecipeStorage other : recipes)
        {
            if (other == recipe)
            {
                continue;
            }
            final boolean takesOutput = other.getCleanedInput().stream().anyMatch(input -> same(input, recipe.getPrimaryOutput()));
            final boolean givesInput = recipe.getCleanedInput().stream().anyMatch(input -> same(input, other.getPrimaryOutput()));
            if (takesOutput && givesInput)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Enough fuel burning for one more smelt, lighting another piece from the warehouse if needed.
     */
    private static boolean heat(final IItemHandler racks, final String key)
    {
        while (HEAT.getOrDefault(key, 0) < SMELT_TICKS)
        {
            int found = -1;
            for (int slot = 0; slot < racks.getSlots(); slot++)
            {
                final ItemStack stack = racks.getStackInSlot(slot);
                if (!stack.isEmpty() && !stack.hasCraftingRemainingItem() && stack.getBurnTime(RecipeType.SMELTING) > 0)
                {
                    found = slot;
                    break;
                }
            }
            if (found < 0)
            {
                return false;
            }
            final ItemStack fuel = racks.extractItem(found, 1, false);
            if (fuel.isEmpty())
            {
                return false;
            }
            HEAT.merge(key, fuel.getBurnTime(RecipeType.SMELTING), Integer::sum);
        }
        return true;
    }

    /**
     * The sifter is handed its meshes: one of each kind its level allows waits in the warehouse, replaced when worn out.
     */
    private static void supplyMeshes(final IItemHandler racks, final int level)
    {
        final Item[] meshes = {ModItems.sifterMeshString, ModItems.sifterMeshFlint, ModItems.sifterMeshIron, ModItems.sifterMeshDiamond};
        for (int tier = 0; tier < Math.min(meshes.length, level); tier++)
        {
            if (InventoryUtils.getItemCountInItemHandler(racks, meshes[tier]) == 0)
            {
                ItemHandlerHelper.insertItemStacked(racks, new ItemStack(meshes[tier]), false);
            }
        }
    }

    private static int count(final IItemHandler racks, final ItemStack wanted)
    {
        return InventoryUtils.getItemCountInItemHandler(racks, stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, wanted, false, true));
    }

    private static int count(final IItemHandler racks, final ItemStorage wanted)
    {
        return InventoryUtils.getItemCountInItemHandler(racks, stack -> same(wanted, stack));
    }

    private static boolean same(final ItemStorage storage, final ItemStack stack)
    {
        return !stack.isEmpty() && ItemStackUtils.compareItemStacksIgnoreStackSize(stack, storage.getItemStack(), !storage.ignoreDamageValue(), !storage.ignoreNBT());
    }
}
