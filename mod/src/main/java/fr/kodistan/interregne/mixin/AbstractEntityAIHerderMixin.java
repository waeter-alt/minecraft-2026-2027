package fr.kodistan.interregne.mixin;

import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Breeders produce by level on their own (HutProduction): the herd is scenery, so the herder never slaughters it
 * and never asks the warehouse for breeding feed.
 */
@Mixin(value = AbstractEntityAIHerder.class, remap = false)
public abstract class AbstractEntityAIHerderMixin
{
    @Inject(method = "chanceToButcher", at = @At("HEAD"), cancellable = true)
    private void interregne$neverButcher(final List<? extends Animal> allAnimals, final CallbackInfoReturnable<Double> cir)
    {
        cir.setReturnValue(0.0);
    }

    @Redirect(method = "prepareForHerding", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder;checkIfRequestForItemExistOrCreateAsync(Lnet/minecraft/world/item/ItemStack;II)Z",
      ordinal = 0))
    private boolean interregne$noFeedRequest(final AbstractEntityAIHerder<?, ?> self, final ItemStack stack, final int count, final int minCount)
    {
        return true;
    }
}
