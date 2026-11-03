package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import fr.kodistan.interregne.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Hunger slows a colony down instead of stopping it: with no restaurant to go to, hungry citizens keep working
 * at a reduced pace rather than standing about. Neglect is felt, but never blocks.
 */
public final class Hunger
{
    /**
     * Saturation under which a citizen looks for food (MineColonies' restaurant limit).
     */
    private static final double HUNGRY = 2.5;

    private Hunger()
    {
    }

    /**
     * How much slower a citizen works because of hunger: 1 when fed.
     */
    public static double slowdown(@Nullable final ICitizenData citizen)
    {
        if (citizen == null)
        {
            return 1.0;
        }
        try
        {
            if (citizen.getSaturation() <= 0)
            {
                return Config.STARVING_SLOWDOWN.get();
            }
            if (citizen.getSaturation() <= HUNGRY)
            {
                return Config.HUNGRY_SLOWDOWN.get();
            }
        }
        catch (final IllegalStateException e)
        {
            // Server config not loaded yet
        }
        return 1.0;
    }

    /**
     * True when a hungry citizen has nowhere to eat: no restaurant in the colony and no food on them.
     * MineColonies would make them wait for food forever; we let them keep working instead.
     */
    public static boolean nowhereToEat(final AbstractEntityCitizen citizen)
    {
        final ICitizenData data = citizen.getCitizenData();
        if (data == null || data.getColony() == null)
        {
            return false;
        }
        if (data.getColony().getBuildingManager().getBestBuilding(citizen, BuildingCook.class) != null)
        {
            return false;
        }
        final var inventory = citizen.getInventoryCitizen();
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD))
            {
                return false;
            }
        }
        return true;
    }
}
