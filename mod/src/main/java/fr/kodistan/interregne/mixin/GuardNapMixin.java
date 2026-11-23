package fr.kodistan.interregne.mixin;

import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Guards never doze off at their post.
 */
@Mixin(value = AbstractEntityAIGuard.class, remap = false)
public abstract class GuardNapMixin
{
    @Inject(method = "shouldSleep", at = @At("HEAD"), cancellable = true)
    private void interregne$noNap(final CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(false);
    }
}
