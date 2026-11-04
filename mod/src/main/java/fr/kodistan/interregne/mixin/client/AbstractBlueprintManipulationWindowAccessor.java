package fr.kodistan.interregne.mixin.client;

import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The build tool's ground style, to open a fresh build tool window after the catalogue; and the placement controls,
 * which our placement panel for the starting camp drives.
 */
@Mixin(value = AbstractBlueprintManipulationWindow.class, remap = false)
public interface AbstractBlueprintManipulationWindowAccessor
{
    @Accessor("groundstyle")
    int interregne$groundstyle();

    @Invoker("moveForwardClicked")
    void interregne$forward();

    @Invoker("moveBackClicked")
    void interregne$back();

    @Invoker("moveLeftClicked")
    void interregne$left();

    @Invoker("moveRightClicked")
    void interregne$right();

    @Invoker("moveUpClicked")
    void interregne$up();

    @Invoker("moveDownClicked")
    void interregne$down();

    @Invoker("rotateRightClicked")
    void interregne$rotateRight();

    @Invoker("rotateLeftClicked")
    void interregne$rotateLeft();

    @Invoker("mirrorClicked")
    void interregne$mirror();

    @Invoker("confirmClicked")
    void interregne$confirm();

    @Invoker("cancelClicked")
    void interregne$cancel();
}
