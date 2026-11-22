package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracksTower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Garrison places: a guard tower holds as many guards as its level (1 to 5), a barracks tower twice its level. The
 * barracks is unique and each of its levels adds a tower, so every barracks upgrade weighs far more than a guard tower.
 */
@Mixin(value = WorkerBuildingModule.class, remap = false)
public abstract class WorkerBuildingModuleMixin
{
    @Inject(method = "getModuleMax", at = @At("RETURN"), cancellable = true)
    private void interregne$garrisonPlaces(final CallbackInfoReturnable<Integer> cir)
    {
        if (!((Object) this instanceof GuardBuildingModule))
        {
            return;
        }
        final IBuilding building = ((IBuildingModule) this).getBuilding();
        if (building == null)
        {
            return;
        }
        final int level = building.getBuildingLevel();
        cir.setReturnValue(building instanceof BuildingBarracksTower ? 2 * level : level);
    }
}
