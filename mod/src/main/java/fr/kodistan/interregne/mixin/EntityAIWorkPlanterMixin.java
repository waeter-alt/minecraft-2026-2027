package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkPlanter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;

/**
 * A plantation yields its plants on its own (Plantations) and its fields are decoration: the planter never plants or
 * harvests them, and never asks for plants. Crafting (paper, books) still runs.
 */
@Mixin(value = EntityAIWorkPlanter.class, remap = false)
public abstract class EntityAIWorkPlanterMixin
{
    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private void interregne$noFieldWork(final CallbackInfoReturnable<IAIState> cir)
    {
        cir.setReturnValue(IDLE);
    }
}
