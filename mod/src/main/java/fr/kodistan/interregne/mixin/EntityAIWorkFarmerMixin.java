package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.PREPARING;

/**
 * A farm yields its crop on its own (Farms) and scarecrow fields are decoration: the farmer never hoes, sows or
 * harvests them, and never asks for seeds.
 */
@Mixin(value = EntityAIWorkFarmer.class, remap = false)
public abstract class EntityAIWorkFarmerMixin
{
    @Inject(method = "prepareForFarming", at = @At("HEAD"), cancellable = true)
    private void interregne$noFieldWork(final CallbackInfoReturnable<IAIState> cir)
    {
        cir.setReturnValue(PREPARING);
    }
}
