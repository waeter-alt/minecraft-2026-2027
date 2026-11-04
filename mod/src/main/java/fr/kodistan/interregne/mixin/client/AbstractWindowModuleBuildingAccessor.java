package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractWindowModuleBuilding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The building a hut window shows, so our hut screen can open in its place.
 */
@Mixin(value = AbstractWindowModuleBuilding.class, remap = false)
public interface AbstractWindowModuleBuildingAccessor
{
    @Accessor("building")
    IBuildingView interregne$building();
}
