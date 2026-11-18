package fr.atelier.grappin.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.atelier.grappin.CapeOndulation;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;

// Aucun evenement NeoForge ne tombe entre la pose du modele (setupAnim) et son dessin : il faut s'y accrocher directement.
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void grappin$ondulation(LivingEntity entite, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        CapeOndulation.appliquer((PlayerModel<?>) (Object) this, entite, ageInTicks);
    }
}
