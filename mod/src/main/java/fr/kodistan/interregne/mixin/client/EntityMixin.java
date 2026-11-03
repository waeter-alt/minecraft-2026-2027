package fr.kodistan.interregne.mixin.client;

import fr.kodistan.interregne.client.hud.Grievances;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The grievance glow is amber, whatever the citizen's team.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class EntityMixin
{
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void interregne$grievanceColor(final CallbackInfoReturnable<Integer> cir)
    {
        final Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() && Grievances.glows(self.getId()))
        {
            cir.setReturnValue(Grievances.GLOW_COLOR);
        }
    }
}
