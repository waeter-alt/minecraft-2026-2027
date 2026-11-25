package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracksTower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
        final IBuilding building = ((IBuildingModule) this).getBuilding();
        if (building == null)
        {
            return;
        }
        if (!((Object) this instanceof GuardBuildingModule))
        {
            // The academy and the archery sell troops: they train no one
            final String path = building.getBuildingType().getRegistryName().getPath();
            if ("combatacademy".equals(path) || "archery".equals(path))
            {
                cir.setReturnValue(0);
            }
            return;
        }
        final int level = building.getBuildingLevel();
        cir.setReturnValue(building instanceof BuildingBarracksTower ? 2 * level : level);
    }

    /**
     * An ally's contingent (AllyAid) joins the towers beyond their capacity: the check is skipped for those guests,
     * also when MineColonies assigns them again as the colony loads.
     */
    @Redirect(method = "assignCitizen", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/core/colony/buildings/modules/WorkerBuildingModule;isFull()Z"))
    private boolean interregne$guestsBeyondCapacity(final WorkerBuildingModule module, final ICitizenData citizen)
    {
        return !fr.kodistan.interregne.politics.AllyAid.isGuest(citizen) && module.isFull();
    }
}
