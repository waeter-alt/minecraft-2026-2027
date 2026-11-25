package fr.kodistan.interregne.mixin;

import com.minecolonies.core.entity.ai.workers.guard.EntityAIRanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Archères shoot without arrows in their pack: no fetching or requesting arrows at the tower.
 */
@Mixin(value = EntityAIRanger.class, remap = false)
public abstract class EntityAIRangerMixin
{
    @Inject(method = "atBuildingActions", at = @At("HEAD"), cancellable = true)
    private void interregne$noArrows(final CallbackInfo ci)
    {
        ci.cancel();
    }
}
