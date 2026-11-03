package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.workers.CitizenAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import fr.kodistan.interregne.colony.Hunger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hungry citizen with nowhere to eat keeps working (slowed down) instead of stopping to wait for food.
 */
@Mixin(value = CitizenAI.class, remap = false)
public abstract class CitizenAIMixin
{
    @Shadow
    @Final
    private EntityCitizen citizen;

    @Shadow
    private IState lastState;

    @Inject(method = "shouldEat", at = @At("RETURN"), cancellable = true)
    private void interregne$keepWorkingWhenNoFood(final CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValue() && lastState != CitizenAIState.EATING && Hunger.nowhereToEat(citizen))
        {
            cir.setReturnValue(false);
        }
    }
}
