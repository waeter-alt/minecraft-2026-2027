package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.InventoryUtils;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Small helpers shared by the production rules: which hut is which, whether it is staffed, where goods go.
 */
public final class Huts
{
    public static final String CHICKEN    = "chickenherder";
    public static final String COWBOY     = "cowboy";
    public static final String SHEPHERD   = "shepherd";
    public static final String SWINEHERD  = "swineherder";
    public static final String RABBIT     = "rabbithutch";
    public static final String FISHERMAN  = "fisherman";
    public static final String FARMER     = "farmer";
    public static final String LUMBERJACK = "lumberjack";
    public static final String COOK       = "cook";

    private Huts()
    {
    }

    /**
     * MineColonies' id of the building type ("chickenherder", "cook", ...).
     */
    public static String id(final IBuilding building)
    {
        return building.getBuildingType().getRegistryName().getPath();
    }

    /**
     * Built and with at least one worker assigned.
     */
    public static boolean staffed(final IBuilding building)
    {
        return building.getBuildingLevel() > 0 && !building.getAllAssignedCitizen().isEmpty();
    }

    @Nullable
    public static IBuilding warehouse(final IColony colony)
    {
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof IWareHouse)
            {
                return building;
            }
        }
        return null;
    }

    /**
     * Put goods in the hut's racks; whatever does not fit is dropped at its door rather than lost.
     */
    public static void give(final IBuilding building, final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }
        final IItemHandler handler = building.getItemHandlerCap();
        if (handler == null || !InventoryUtils.addItemStackToItemHandler(handler, stack))
        {
            Containers.dropItemStack(building.getColony().getWorld(), building.getPosition().getX() + 0.5, building.getPosition().getY() + 1, building.getPosition().getZ() + 0.5, stack);
        }
    }

    public static Iterable<IColony> colonies()
    {
        return IColonyManager.getInstance().getAllColonies();
    }
}
