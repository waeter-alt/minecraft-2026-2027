package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards are bought at the combat academy and the archery: towers no longer take jobless citizens or trainees.
 */
@Mixin(value = GuardBuildingModule.class, remap = false)
public abstract class GuardBuildingModuleMixin
{
    @Inject(method = "onColonyTick", at = @At("HEAD"), cancellable = true)
    private void interregne$noAutoHire(final IColony colony, final CallbackInfo ci)
    {
        ci.cancel();
    }
}
