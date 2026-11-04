package fr.kodistan.interregne.mixin.client;

import fr.kodistan.interregne.client.hut.CitizenBits;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No name tag (nor MineColonies' interaction icon above it) on a citizen drawn inside our screens.
 */
@Mixin(value = LivingEntityRenderer.class, remap = false)
public abstract class LivingEntityRendererMixin
{
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void interregne$noNameInScreens(final LivingEntity entity, final CallbackInfoReturnable<Boolean> cir)
    {
        if (CitizenBits.drawingInScreen())
        {
            cir.setReturnValue(false);
        }
    }
}
