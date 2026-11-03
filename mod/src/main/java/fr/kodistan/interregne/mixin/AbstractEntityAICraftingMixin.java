package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import fr.kodistan.interregne.colony.Hunger;
import fr.kodistan.interregne.colony.Yields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crafters progress through a recipe step by step rather than with delays: need fewer steps at higher levels,
 * more when hungry.
 */
@Mixin(value = AbstractEntityAICrafting.class, remap = false)
public abstract class AbstractEntityAICraftingMixin
{
    @Inject(method = "getRequiredProgressForMakingRawMaterial", at = @At("RETURN"), cancellable = true)
    private void interregne$craftingPace(final CallbackInfoReturnable<Integer> cir)
    {
        final AbstractEntityAIBasic<?, ?> ai = (AbstractEntityAIBasic<?, ?>) (Object) this;
        final AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) ai).interregne$getWorker();
        final double pace = Yields.speedFor(ai.building) / Hunger.slowdown(worker == null ? null : worker.getCitizenData());
        if (pace != 1.0)
        {
            cir.setReturnValue(Math.max(1, (int) Math.round(cir.getReturnValue() / pace)));
        }
    }
}
