package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.modules.SettingsModuleWindow;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The building and settings of MineColonies' settings page, so our settings screen can open in its place.
 */
@Mixin(value = SettingsModuleWindow.class, remap = false)
public interface SettingsModuleWindowAccessor
{
    @Accessor("building")
    IBuildingView interregne$building();

    @Accessor("moduleView")
    SettingsModuleView interregne$module();
}
