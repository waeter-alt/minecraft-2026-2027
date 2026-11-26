package fr.kodistan.interregne.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.kodistan.interregne.client.war.AssaultCamera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dead player's view tips over sideways (vanilla bobHurt): not while the assault wait screen's aerial camera is on,
 * it would tilt the whole battlefield.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin
{
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void interregne$noDeathTilt(final PoseStack poseStack, final float partialTick, final CallbackInfo ci)
    {
        if (AssaultCamera.active())
        {
            ci.cancel();
        }
    }
}
