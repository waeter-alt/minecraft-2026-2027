package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenExperienceHandler;
import fr.kodistan.interregne.garrison.Garrison;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Troops gain no experience: their strength is their troop level, nothing else.
 */
@Mixin(value = CitizenExperienceHandler.class, remap = false)
public abstract class GuardExperienceMixin
{
    @Shadow
    @Final
    private AbstractEntityCitizen citizen;

    @Inject(method = "addExperience", at = @At("HEAD"), cancellable = true)
    private void interregne$noXp(final double xp, final CallbackInfo ci)
    {
        if (Garrison.kindOf(citizen.getCitizenData()) >= 0)
        {
            ci.cancel();
        }
    }
}
