package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import fr.kodistan.interregne.colony.ColonyMaps;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * The town hall's view looks for filled maps in its chest to build the colony map: hand it our own tiles instead.
 */
@Mixin(value = BuildingTownHall.class, remap = false)
public abstract class BuildingTownHallMixin
{
    @Redirect(method = "serializeToView", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/util/InventoryUtils;getBuildingInventory(Lcom/minecolonies/api/colony/buildings/IBuilding;)Ljava/util/List;"))
    private List<ItemStack> interregne$colonyMaps(final IBuilding building)
    {
        return ColonyMaps.stacks(building);
    }
}
