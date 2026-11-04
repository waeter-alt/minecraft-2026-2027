package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFlorist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

/**
 * A florist yields flowers on its own (Florists) and its flower beds are decoration: he never composts or picks
 * them, and never asks for compost.
 */
@Mixin(value = EntityAIWorkFlorist.class, remap = false)
public abstract class EntityAIWorkFloristMixin
{
    @Inject(method = "decide", at = @At("HEAD"), cancellable = true)
    private void interregne$noBedWork(final CallbackInfoReturnable<IAIState> cir)
    {
        cir.setReturnValue(START_WORKING);
    }
}
