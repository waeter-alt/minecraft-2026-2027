package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingEnchanter;
import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * The enchanter gathers from the whole colony: every worker hut (other enchanters excepted) is a drain target.
 */
public final class AutoEnchanter
{
    private AutoEnchanter()
    {
    }

    /**
     * Add the colony's worker huts missing from the enchanter's drain list, as not yet drained.
     */
    public static void addAllWorkerHuts(final IBuilding enchanter, final Map<BlockPos, Boolean> targets)
    {
        if (enchanter.getColony() == null)
        {
            return;
        }
        for (final IBuilding building : enchanter.getColony().getBuildingManager().getBuildings().values())
        {
            if (!(building instanceof BuildingEnchanter) && building.hasModule(WorkerBuildingModule.class) && building.getBuildingLevel() > 0)
            {
                targets.putIfAbsent(building.getPosition(), false);
            }
        }
    }
}
