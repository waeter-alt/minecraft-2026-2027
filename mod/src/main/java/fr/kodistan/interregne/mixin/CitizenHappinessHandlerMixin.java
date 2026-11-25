package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Security no longer asks for a guard-to-citizen ratio (about 2 for 3): guards are bought, not drafted from the
 * population. A colony with no garrison at all leaves its citizens worried; one bought guard is enough to reassure
 * them. The "no guard near my home/work" chatter stays as MineColonies wrote it: it only checks for a nearby tower.
 */
@Mixin(value = CitizenHappinessHandler.class, remap = false)
public abstract class CitizenHappinessHandlerMixin
{
    private static final double NO_GARRISON = 0.5;

    @Inject(method = "getGuardFactor", at = @At("HEAD"), cancellable = true)
    private static void interregne$garrisonSecurity(final IColony colony, final CallbackInfoReturnable<Double> cir)
    {
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.getJob() instanceof AbstractJobGuard<?>)
            {
                cir.setReturnValue(1.0);
                return;
            }
        }
        cir.setReturnValue(NO_GARRISON);
    }
}
