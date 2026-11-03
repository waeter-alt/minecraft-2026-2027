package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.workorders.AbstractWorkOrder;
import fr.kodistan.interregne.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Without builders, MineColonies refuses orders no builder could take: lift that check.
 */
@Mixin(value = AbstractWorkOrder.class, remap = false)
public abstract class AbstractWorkOrderMixin
{
    @Inject(method = "canBeResolved", at = @At("HEAD"), cancellable = true)
    private void interregne$noBuilderNeeded(final IColony colony, final int level, final CallbackInfoReturnable<Boolean> cir)
    {
        if (Config.INSTANT_BUILD.get())
        {
            cir.setReturnValue(true);
        }
    }
}
