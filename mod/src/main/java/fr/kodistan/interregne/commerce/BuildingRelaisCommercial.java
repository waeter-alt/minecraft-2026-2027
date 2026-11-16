package fr.kodistan.interregne.commerce;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.WindowHutMinPlaceholder;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * Where a colony's trade contracts are made. Its level will cap the number of contracts and caravans at once.
 */
public class BuildingRelaisCommercial extends AbstractBuilding
{
    public static final String SCHEMATIC = "relais";
    public static final int MAX_LEVEL = 5;

    public BuildingRelaisCommercial(@NotNull final IColony colony, final BlockPos pos)
    {
        super(colony, pos);
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return SCHEMATIC;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_LEVEL;
    }

    public static class View extends AbstractBuildingView
    {
        public View(final IColonyView colony, final BlockPos pos)
        {
            super(colony, pos);
        }

        @NotNull
        @Override
        public BOWindow getWindow()
        {
            return new WindowHutMinPlaceholder<>(this);
        }
    }
}
