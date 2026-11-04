package fr.kodistan.interregne.mixin.client;

import com.minecolonies.core.client.gui.WindowHireWorker;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The building of MineColonies' hiring window, so our hiring screen can open in its place.
 */
@Mixin(value = WindowHireWorker.class, remap = false)
public interface WindowHireWorkerAccessor
{
    @Accessor("building")
    AbstractBuildingView interregne$building();
}
