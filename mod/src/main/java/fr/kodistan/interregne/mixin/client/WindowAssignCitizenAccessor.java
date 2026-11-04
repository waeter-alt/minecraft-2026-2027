package fr.kodistan.interregne.mixin.client;

import com.minecolonies.core.client.gui.WindowAssignCitizen;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The house of MineColonies' housing window, so our housing screen can open in its place.
 */
@Mixin(value = WindowAssignCitizen.class, remap = false)
public interface WindowAssignCitizenAccessor
{
    @Accessor("building")
    LivingBuildingView interregne$building();
}
