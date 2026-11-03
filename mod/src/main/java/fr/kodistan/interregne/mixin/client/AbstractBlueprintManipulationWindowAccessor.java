package fr.kodistan.interregne.mixin.client;

import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The build tool's ground style, to open a fresh build tool window after the catalogue.
 */
@Mixin(value = AbstractBlueprintManipulationWindow.class, remap = false)
public interface AbstractBlueprintManipulationWindowAccessor
{
    @Accessor("groundstyle")
    int interregne$groundstyle();
}
