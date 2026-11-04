package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractWindowModuleBuilding;
import fr.kodistan.interregne.client.hut.TerminalScreen;
import fr.kodistan.interregne.warehouse.MagicNetwork;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a warehouse, the hut window's "all inventory" button opens the warehouse register instead.
 */
@Mixin(value = AbstractWindowModuleBuilding.class, remap = false)
public abstract class AbstractWindowModuleBuildingMixin
{
    @Shadow
    @Final
    protected IBuildingView building;

    @Inject(method = "allInventoryClicked", at = @At("HEAD"), cancellable = true)
    private void interregne$openTerminal(final CallbackInfo ci)
    {
        if (MagicNetwork.enabled() && building.getBuildingType().getRegistryName().getPath().equals("warehouse"))
        {
            ci.cancel();
            TerminalScreen.open(building, null);
        }
    }
}
