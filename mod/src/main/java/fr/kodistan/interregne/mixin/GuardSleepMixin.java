package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSleepHandler;
import fr.kodistan.interregne.garrison.BoughtGuards;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CitizenSleepHandler.class, remap = false)
public abstract class GuardSleepMixin
{
    @Shadow
    @Final
    private AbstractEntityCitizen citizen;

    @Inject(method = "shouldGoSleep", at = @At("HEAD"), cancellable = true)
    private void interregne$noSleep(final CallbackInfoReturnable<Boolean> cir)
    {
        if (!citizen.level().isClientSide && BoughtGuards.is(citizen.getCitizenData()))
        {
            cir.setReturnValue(false);
        }
    }
}
