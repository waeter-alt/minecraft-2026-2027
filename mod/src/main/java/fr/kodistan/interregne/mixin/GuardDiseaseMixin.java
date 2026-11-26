package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenDiseaseHandler;
import fr.kodistan.interregne.garrison.BoughtGuards;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CitizenDiseaseHandler.class, remap = false)
public abstract class GuardDiseaseMixin
{
    @Shadow
    @Final
    private ICitizenData citizenData;

    @Inject(method = "setDisease", at = @At("HEAD"), cancellable = true)
    private void interregne$noDisease(final CallbackInfoReturnable<Boolean> cir)
    {
        if (BoughtGuards.is(citizenData))
        {
            cir.setReturnValue(false);
        }
    }
}
