package fr.kodistan.interregne.mixin.client;

import fr.kodistan.interregne.client.hud.Grievances;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Citizens with a grievance glow through walls, so the player finds them from afar.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftMixin
{
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void interregne$grievanceGlow(final Entity entity, final CallbackInfoReturnable<Boolean> cir)
    {
        if (Grievances.glows(entity.getId()))
        {
            cir.setReturnValue(true);
        }
    }
}
