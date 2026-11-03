package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.google.common.reflect.TypeToken;
import fr.kodistan.interregne.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workers never ask for tools: the right one is issued the moment they need it, at the best tier their building
 * allows (wood, stone, iron, diamond, netherite as it levels up), and replaced automatically when the hut is upgraded.
 */
public final class AutoTools
{
    /**
     * Best vanilla item per equipment type and maximum tier, found once.
     */
    private static final Map<String, ItemStack> BEST = new HashMap<>();

    private AutoTools()
    {
    }

    /**
     * Make sure the worker carries the best tool of this type its building allows.
     */
    public static void equip(final AbstractEntityCitizen worker, final IBuilding building, final EquipmentTypeEntry type, final int minimalLevel)
    {
        if (worker == null || building == null || worker.getCitizenData() == null || !enabled())
        {
            return;
        }
        final int maxLevel = Math.max(minimalLevel, building.getMaxEquipmentLevel());
        final ItemStack best = best(type, minimalLevel, maxLevel);
        if (best.isEmpty())
        {
            // No vanilla item fits: keep MineColonies' own behavior
            return;
        }
        final int bestLevel = type.getMiningLevel(best);

        final IItemHandler inventory = worker.getInventoryCitizen();
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && type.checkIsEquipment(stack))
            {
                final int level = type.getMiningLevel(stack);
                if (level >= bestLevel && level <= maxLevel)
                {
                    return;
                }
                if (level < bestLevel)
                {
                    // An outgrown tool: hand it back to the Empire
                    inventory.extractItem(slot, stack.getCount(), false);
                }
            }
        }

        final ItemStack rest = ItemHandlerHelper.insertItemStacked(inventory, best.copy(), false);
        if (!rest.isEmpty())
        {
            worker.spawnAtLocation(rest);
        }
        cancelToolRequests(worker, building, type);
    }

    private static boolean enabled()
    {
        try
        {
            return Config.AUTO_TOOLS.get();
        }
        catch (final IllegalStateException e)
        {
            return false;
        }
    }

    /**
     * Requests made before the tool was issued are no longer needed.
     */
    private static void cancelToolRequests(final AbstractEntityCitizen worker, final IBuilding building, final EquipmentTypeEntry type)
    {
        final List<IRequest<? extends Tool>> open = building.getOpenRequestsOfTypeFiltered(worker.getCitizenData(), TypeToken.of(Tool.class),
          request -> request.getRequest().getEquipmentType().equals(type));
        for (final IRequest<? extends Tool> request : open)
        {
            building.getColony().getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
        }
    }

    /**
     * The highest-tier vanilla item of this type within the levels, wood preferred over gold at the bottom tier.
     */
    private static ItemStack best(final EquipmentTypeEntry type, final int minLevel, final int maxLevel)
    {
        return BEST.computeIfAbsent(type.getRegistryName() + "|" + minLevel + "|" + maxLevel, key -> {
            ItemStack found = ItemStack.EMPTY;
            int foundLevel = Integer.MIN_VALUE;
            for (final Item item : BuiltInRegistries.ITEM)
            {
                final var id = BuiltInRegistries.ITEM.getKey(item);
                if (!id.getNamespace().equals("minecraft") || id.getPath().startsWith("golden_"))
                {
                    continue;
                }
                final ItemStack stack = new ItemStack(item);
                if (!type.checkIsEquipment(stack))
                {
                    continue;
                }
                final int level = type.getMiningLevel(stack);
                if (level >= minLevel && level <= maxLevel && level > foundLevel)
                {
                    found = stack;
                    foundLevel = level;
                }
            }
            return found;
        });
    }
}
