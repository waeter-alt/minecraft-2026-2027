package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import fr.kodistan.interregne.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Without builders, every building would be "too far from any builder": lift that check.
 */
@Mixin(value = WorkOrderBuilding.class, remap = false)
public abstract class WorkOrderBuildingMixin
{
    @Inject(method = "tooFarFromAnyBuilder", at = @At("HEAD"), cancellable = true)
    private void interregne$noBuilderNeeded(final IColony colony, final int level, final CallbackInfoReturnable<Boolean> cir)
    {
        if (Config.INSTANT_BUILD.get())
        {
            cir.setReturnValue(false);
        }
    }
}
