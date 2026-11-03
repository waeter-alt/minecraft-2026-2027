package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.core.colony.workorders.WorkManager;
import fr.kodistan.interregne.colony.InstantBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every new build order passes through here: hand it to the instant builder instead of the builders' queue.
 */
@Mixin(value = WorkManager.class, remap = false)
public abstract class WorkManagerMixin
{
    @Inject(method = "addWorkOrder", at = @At("HEAD"), cancellable = true)
    private void interregne$instantBuild(final IServerWorkOrder order, final boolean readingFromNbt, final CallbackInfo ci)
    {
        if (!readingFromNbt && InstantBuilder.tryTakeOver(((WorkManager) (Object) this).getColony(), order))
        {
            ci.cancel();
        }
    }
}
