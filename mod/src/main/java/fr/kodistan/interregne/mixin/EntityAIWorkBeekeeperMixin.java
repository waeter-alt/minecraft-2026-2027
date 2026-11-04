package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkBeekeeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

/**
 * An apiary yields honey on its own (Apiary) and its hives are decoration: the beekeeper never breeds bees, shears
 * hives or asks for bottles and shears; he stays by his hut.
 */
@Mixin(value = EntityAIWorkBeekeeper.class, remap = false)
public abstract class EntityAIWorkBeekeeperMixin
{
    @Inject(method = "prepareForHerding", at = @At("HEAD"), cancellable = true)
    private void interregne$noHiveWork(final CallbackInfoReturnable<IAIState> cir)
    {
        cir.setReturnValue(START_WORKING);
    }
}
