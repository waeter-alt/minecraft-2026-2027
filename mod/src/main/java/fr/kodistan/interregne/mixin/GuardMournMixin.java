package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.managers.CitizenManager;
import fr.kodistan.interregne.garrison.BoughtGuards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CitizenManager.class, remap = false)
public abstract class GuardMournMixin
{
    @Inject(method = "updateCitizenMourn", at = @At("HEAD"), cancellable = true)
    private void interregne$noMourn(final ICitizenData data, final boolean mourn, final CallbackInfo ci)
    {
        if (BoughtGuards.is(data))
        {
            ci.cancel();
        }
    }
}
