package fr.kodistan.interregne.region;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.WindowHutMinPlaceholder;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * The colony's crate factory. Its level, built by a builder like any building, sets how fast the crates come out.
 */
public class BuildingDepotRegional extends AbstractBuilding
{
    public static final String SCHEMATIC = "depot";
    public static final int MAX_LEVEL = 5;

    public BuildingDepotRegional(@NotNull final IColony colony, final BlockPos pos)
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
