package fr.kodistan.interregne.mixin.client;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.huts.WindowHutWorkerModulePlaceholder;
import fr.kodistan.interregne.client.hut.HutScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hut window with workers opens our hut screen instead, wherever it is opened from: the hut block, a module's
 * « main » tab, the way back from the stock window.
 */
@Mixin(value = BOWindow.class, remap = false)
public abstract class BOWindowMixin
{
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private void interregne$hutScreen(final CallbackInfo ci)
    {
        if (((Object) this).getClass() == WindowHutWorkerModulePlaceholder.class && HutScreen.enabled())
        {
            ci.cancel();
            HutScreen.open(((AbstractWindowModuleBuildingAccessor) this).interregne$building());
        }
    }
}
