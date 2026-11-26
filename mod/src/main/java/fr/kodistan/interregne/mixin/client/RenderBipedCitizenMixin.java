package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.render.RenderBipedCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.kodistan.interregne.client.GuardRenderers;
import fr.kodistan.interregne.garrison.Garrison;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A bought guard is drawn as our légionnaire or archère (GeckoLib) rather than a MineColonies citizen.
 */
@Mixin(value = RenderBipedCitizen.class, remap = false)
public abstract class RenderBipedCitizenMixin
{
    @Unique
    private GuardRenderers.GuardRenderer interregne$legionnaire;
    @Unique
    private GuardRenderers.GuardRenderer interregne$archere;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void interregne$guardRenderers(final EntityRendererProvider.Context context, final CallbackInfo ci)
    {
        interregne$legionnaire = GuardRenderers.create(context, Garrison.LEGIONNAIRE);
        interregne$archere = GuardRenderers.create(context, Garrison.ARCHERE);
    }

    @Inject(method = "render(Lcom/minecolonies/api/entity/citizen/AbstractEntityCitizen;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at = @At("HEAD"), cancellable = true)
    private void interregne$guardLook(final AbstractEntityCitizen entity, final float yaw, final float partialTick, final PoseStack poseStack,
      final MultiBufferSource buffers, final int light, final CallbackInfo ci)
    {
        final GuardRenderers.Look look = GuardRenderers.look(entity);
        if (look == null)
        {
            return;
        }
        (look.kind() == Garrison.ARCHERE ? interregne$archere : interregne$legionnaire)
          .renderGuard(entity, look.level(), yaw, partialTick, poseStack, buffers, light);
        ci.cancel();
    }
}
