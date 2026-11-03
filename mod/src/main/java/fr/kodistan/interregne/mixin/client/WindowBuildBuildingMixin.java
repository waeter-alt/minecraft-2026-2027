package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import fr.kodistan.interregne.client.BuildWindowPricing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replace the hut window's builder material list with the construction price.
 */
@Mixin(value = WindowBuildBuilding.class, remap = false)
public abstract class WindowBuildBuildingMixin
{
    @Shadow
    @Final
    private IBuildingView building;

    @Inject(method = "updateResources", at = @At("HEAD"), cancellable = true)
    private void interregne$showPrice(final CallbackInfo ci)
    {
        if (BuildWindowPricing.enabled())
        {
            ci.cancel();
            BuildWindowPricing.apply((WindowBuildBuilding) (Object) this, building);
        }
    }
}
