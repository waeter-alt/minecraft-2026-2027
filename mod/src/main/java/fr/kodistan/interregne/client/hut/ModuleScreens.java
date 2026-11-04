package fr.kodistan.interregne.client.hut;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.modules.IEntityListModuleView;
import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.api.colony.buildings.modules.IMinimumStockModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CourierAssignmentModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.EnchanterStationsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ExpeditionLogModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerAssignmentModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseOptionsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.FieldsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.GraveyardManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerGuardAssignModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerLevelManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RestaurantMenuModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RequestTaskModuleView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Our pages in place of MineColonies' module windows. A module view's window is noted when it is made
 * ({@code ModuleViewWindowMixin}), so when that very window is opened, our page for its module opens instead.
 */
public final class ModuleScreens
{
    @Nullable
    private static IBuildingModuleView pendingView;
    @Nullable
    private static BOWindow pendingWindow;

    private ModuleScreens()
    {
    }

    public static void made(final IBuildingModuleView view, final BOWindow window)
    {
        pendingView = view;
        pendingWindow = window;
    }

    /**
     * Opens our page for this window's module; false when we have none and the original must open.
     */
    public static boolean replace(final BOWindow window)
    {
        if (window != pendingWindow || pendingView == null)
        {
            return false;
        }
        final IBuildingModuleView view = pendingView;
        pendingView = null;
        pendingWindow = null;
        final IBuildingView building = view.getBuildingView();
        // From the hut window or one of its pages, the page takes the hut window's place and Escape goes back to it
        final Screen parent = HutNav.parentFor(Minecraft.getInstance().screen);
        if (view instanceof IItemListModuleView list)
        {
            ItemListScreen.open(building, list, parent);
        }
        else if (view instanceof IEntityListModuleView list)
        {
            EntityListScreen.open(building, list, parent);
        }
        else if (view instanceof IMinimumStockModuleView stock)
        {
            MinimumStockScreen.open(building, stock, parent);
        }
        else if (view instanceof BuildingStatisticsModuleView stats)
        {
            StatsScreen.open(building, stats, parent);
        }
        else if (view instanceof RequestTaskModuleView)
        {
            TasksScreen.open(building, parent);
        }
        else if (view instanceof RestaurantMenuModuleView menu)
        {
            MenuScreen.open(building, menu, parent);
        }
        else if (view instanceof ToolModuleView tool)
        {
            ToolScreen.open(building, tool, parent);
        }
        else if (view instanceof FieldsModuleView fields)
        {
            FieldsScreen.open(building, fields, parent);
        }
        else if (view instanceof MinerLevelManagementModuleView levels)
        {
            MineLevelsScreen.open(building, levels, parent);
        }
        else if (view instanceof MinerGuardAssignModuleView)
        {
            MineGuardsScreen.open(building, parent);
        }
        else if (view instanceof GraveyardManagementModuleView graveyard)
        {
            GraveyardScreen.open(building, graveyard, parent);
        }
        else if (view instanceof EnchanterStationsModuleView stations)
        {
            EnchanterScreen.open(building, stations, parent);
        }
        else if (view instanceof WarehouseOptionsModuleView options)
        {
            WarehouseOptionsScreen.open(building, options, parent);
        }
        else if (view instanceof ExpeditionLogModuleView log)
        {
            ExpeditionScreen.open(building, log, parent);
        }
        else if (view instanceof CourierAssignmentModuleView || view instanceof MinerAssignmentModuleView)
        {
            HireScreen.open(building, parent);
        }
        else if (view.getClass() == CraftingModuleView.class)
        {
            RecipesScreen.open(building, (CraftingModuleView) view, parent);
        }
        else
        {
            return false;
        }
        mark(view);
        return true;
    }

    /**
     * Lights the page's entry in the left column of the screen just opened.
     */
    public static void mark(final IBuildingModuleView view)
    {
        if (Minecraft.getInstance().screen instanceof ModuleScreen page)
        {
            page.navCurrent = HutNav.key(view);
        }
    }
}
