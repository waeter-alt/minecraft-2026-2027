package fr.kodistan.interregne.mixin.client;

import com.minecolonies.core.client.gui.WindowPostBox;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The building of MineColonies' postbox window, so our item picker can open in its place.
 */
@Mixin(value = WindowPostBox.class, remap = false)
public interface WindowPostBoxAccessor
{
    @Accessor("buildingView")
    AbstractBuildingView interregne$building();
}
