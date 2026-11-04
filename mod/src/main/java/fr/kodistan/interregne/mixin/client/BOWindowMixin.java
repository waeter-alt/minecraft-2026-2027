package fr.kodistan.interregne.mixin.client;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.WindowHutMinPlaceholder;
import com.minecolonies.core.client.gui.huts.WindowHutWorkerModulePlaceholder;
import com.minecolonies.core.client.gui.townhall.WindowMainPage;
import fr.kodistan.interregne.client.hut.ColonyHubScreen;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import fr.kodistan.interregne.client.hut.CitizenScreen;
import fr.kodistan.interregne.client.hut.HutScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hut window with workers opens our hut screen instead, wherever it is opened from: the hut block, a module's
 * « main » tab, the way back from the stock window; so do huts without workers. The town hall opens the colony hub,
 * and the citizen window our citizen screen.
 */
@Mixin(value = BOWindow.class, remap = false)
public abstract class BOWindowMixin
{
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private void interregne$hutScreen(final CallbackInfo ci)
    {
        final Class<?> type = ((Object) this).getClass();
        if ((type == WindowHutWorkerModulePlaceholder.class || type == WindowHutMinPlaceholder.class) && HutScreen.enabled())
        {
            ci.cancel();
            HutScreen.open(((AbstractWindowModuleBuildingAccessor) this).interregne$building());
        }
        else if (type == WindowMainPage.class && HutScreen.enabled() && !ColonyHubScreen.consumeOpenOriginal())
        {
            ci.cancel();
            ColonyHubScreen.open(((AbstractWindowModuleBuildingAccessor) this).interregne$building());
        }
        else if (type == MainWindowCitizen.class && HutScreen.enabled())
        {
            ci.cancel();
            CitizenScreen.open(((MainWindowCitizen) (Object) this).getCitizen(), null);
        }
    }
}
