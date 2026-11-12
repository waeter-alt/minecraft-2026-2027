// [claude-build] Le rendu du Seraphin (client) : le modele agrandi a 1,5 fois, sans ombre, et lumineux quand l'oeil est ouvert.
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class SeraphinRendu extends MobRenderer<Seraphin, SeraphinModele> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/seraphin.png");

    public SeraphinRendu(EntityRendererProvider.Context contexte) {
        super(contexte, new SeraphinModele(contexte.bakeLayer(SeraphinModele.COUCHE)), 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(Seraphin boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Seraphin boss, PoseStack pose, float partialTick) {
        pose.scale(0.9F, 0.9F, 0.9F);       // le modele est dessine a l'echelle x2
    }

    @Override
    protected int getBlockLightLevel(Seraphin boss, BlockPos position) {
        return boss.oeilOuvert() ? 15 : super.getBlockLightLevel(boss, position);
    }
}
