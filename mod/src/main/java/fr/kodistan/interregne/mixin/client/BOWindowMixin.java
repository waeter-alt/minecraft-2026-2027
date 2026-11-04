package fr.kodistan.interregne.mixin.client;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.WindowClipBoard;
import com.minecolonies.core.client.gui.WindowInteraction;
import com.minecolonies.core.client.gui.WindowPostBox;
import com.minecolonies.core.client.gui.WindowSupplies;
import fr.kodistan.interregne.client.hut.SuppliesScreen;
import com.minecolonies.core.network.messages.server.colony.building.postbox.PostBoxRequestMessage;
import fr.kodistan.interregne.client.hut.ItemPickerScreen;
import com.minecolonies.api.colony.IVisitorViewData;
import com.minecolonies.core.client.gui.modules.SettingsModuleWindow;
import fr.kodistan.interregne.client.hut.SettingsScreen;
import fr.kodistan.interregne.client.hut.HutNav;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import net.minecraft.client.gui.screens.Screen;
import com.minecolonies.core.client.gui.WindowAssignCitizen;
import com.minecolonies.core.client.gui.WindowHireWorker;
import fr.kodistan.interregne.client.hut.HireScreen;
import fr.kodistan.interregne.client.hut.ModuleScreens;
import fr.kodistan.interregne.client.hut.HousingScreen;
import com.minecolonies.core.client.gui.WindowHutMinPlaceholder;
import com.minecolonies.core.client.gui.WindowRequestDetail;
import fr.kodistan.interregne.client.hut.ClipboardScreen;
import fr.kodistan.interregne.client.hut.RequestScreen;
import net.minecraft.client.Minecraft;
import com.minecolonies.core.client.gui.huts.WindowHutLiving;
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
 * the citizen window our citizen screen, the request detail, the clipboard, hiring, housing and settings our own.
 */
@Mixin(value = BOWindow.class, remap = false)
public abstract class BOWindowMixin
{
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private void interregne$hutScreen(final CallbackInfo ci)
    {
        final Class<?> type = ((Object) this).getClass();
        if (HutScreen.enabled() && ModuleScreens.replace((BOWindow) (Object) this))
        {
            ci.cancel();
        }
        else if ((type == WindowHutWorkerModulePlaceholder.class || type == WindowHutMinPlaceholder.class || type == WindowHutLiving.class) && HutScreen.enabled())
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
        else if (type == WindowRequestDetail.class && HutScreen.enabled())
        {
            final WindowRequestDetailAccessor detail = (WindowRequestDetailAccessor) this;
            final IColonyView colony = IColonyManager.getInstance().getColonyView(detail.interregne$colonyId(), Minecraft.getInstance().level.dimension());
            if (colony != null)
            {
                ci.cancel();
                RequestScreen.openFrom(detail.interregne$request(), colony, detail.interregne$previous());
            }
        }
        else if (type == WindowHireWorker.class && HutScreen.enabled())
        {
            ci.cancel();
            HireScreen.open(((WindowHireWorkerAccessor) this).interregne$building(), null);
        }
        else if (type == WindowAssignCitizen.class && HutScreen.enabled())
        {
            ci.cancel();
            HousingScreen.open(((WindowAssignCitizenAccessor) this).interregne$building(), null);
        }
        else if (type == SettingsModuleWindow.class && HutScreen.enabled()
                   && SettingsScreen.supports(((SettingsModuleWindowAccessor) this).interregne$module()))
        {
            ci.cancel();
            final SettingsModuleWindowAccessor settings = (SettingsModuleWindowAccessor) this;
            SettingsScreen.open(settings.interregne$building(), settings.interregne$module(), HutNav.parentFor(Minecraft.getInstance().screen));
            ModuleScreens.mark(settings.interregne$module());
        }
        else if (type == WindowInteraction.class && HutScreen.enabled()
                   && !(((WindowInteractionAccessor) this).interregne$citizen() instanceof IVisitorViewData))
        {
            // The dialogue (four answers, half of them doing nothing) is gone: talking to a citizen opens their sheet.
            // Tavern visitors keep it, since recruiting them happens there.
            ci.cancel();
            CitizenScreen.open(((WindowInteractionAccessor) this).interregne$citizen(), null);
        }
        else if (type == WindowClipBoard.class && HutScreen.enabled())
        {
            ci.cancel();
            ClipboardScreen.open(((WindowClipBoardAccessor) this).interregne$colony());
        }
        else if (type == WindowSupplies.class && HutScreen.enabled())
        {
            // Placing the starting camp or ship: our side panel drives the unseen original
            ci.cancel();
            SuppliesScreen.open((WindowSupplies) (Object) this);
        }
        else if (type == WindowPostBox.class && HutScreen.enabled())
        {
            // The postbox is an order form: pick an item and a quantity, the couriers bring it here
            ci.cancel();
            final var postbox = ((WindowPostBoxAccessor) this).interregne$building();
            ItemPickerScreen.open("Commander", postbox, null, "objets", "Commander",
              (stack, quantity) -> new PostBoxRequestMessage(postbox, stack, quantity, false).sendToServer());
        }
    }
}
