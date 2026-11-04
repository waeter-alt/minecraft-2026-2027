package fr.kodistan.interregne.mixin.client;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.EntityListModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.FoodItemListModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinimumStockModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RequestTaskModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.GraveyardManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerGuardAssignModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerLevelManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RestaurantMenuModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import fr.kodistan.interregne.client.hut.ModuleScreens;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes which module a window was made for, so that opening it shows our page for that module instead.
 */
@Mixin(value = {ItemListModuleView.class, FoodItemListModuleView.class, EntityListModuleView.class, MinimumStockModuleView.class,
  BuildingStatisticsModuleView.class, RequestTaskModuleView.class, CraftingModuleView.class, RestaurantMenuModuleView.class, ToolModuleView.class,
  BuildingFarmer.FarmerFieldsModuleView.class, BuildingPlantation.PlantationFieldsModuleView.class, MinerLevelManagementModuleView.class,
  MinerGuardAssignModuleView.class, GraveyardManagementModuleView.class}, remap = false)
public abstract class ModuleViewWindowMixin
{
    @Inject(method = "getWindow", at = @At("RETURN"))
    private void interregne$noteWindow(final CallbackInfoReturnable<BOWindow> cir)
    {
        ModuleScreens.made((IBuildingModuleView) (Object) this, cir.getReturnValue());
    }
}
