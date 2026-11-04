package fr.kodistan.interregne.mixin.client;

import fr.kodistan.interregne.client.dev.DevOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev captures only: while {@link DevOverrides#hover} is set, every screen (ours and MineColonies') sees the mouse
 * there, so a capture never shows a stray hover from wherever the real cursor sits.
 */
@Mixin(value = MouseHandler.class, remap = false)
public abstract class MouseHandlerMixin
{
    @Inject(method = "xpos", at = @At("HEAD"), cancellable = true)
    private void interregne$devX(final CallbackInfoReturnable<Double> cir)
    {
        final int[] hover = DevOverrides.hover;
        if (hover != null)
        {
            cir.setReturnValue(hover[0] * Minecraft.getInstance().getWindow().getGuiScale());
        }
    }

    @Inject(method = "ypos", at = @At("HEAD"), cancellable = true)
    private void interregne$devY(final CallbackInfoReturnable<Double> cir)
    {
        final int[] hover = DevOverrides.hover;
        if (hover != null)
        {
            cir.setReturnValue(hover[1] * Minecraft.getInstance().getWindow().getGuiScale());
        }
    }
}
