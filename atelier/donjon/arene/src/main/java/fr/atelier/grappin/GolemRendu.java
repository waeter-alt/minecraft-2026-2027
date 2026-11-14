// [claude-build] Le rendu du Gardien des Racines (client) : le modele dessine a 4 unites par bloc est agrandi x4, sans ombre ; il s'eclaire quand un point faible est ouvert.
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class GolemRendu extends MobRenderer<Golem, GolemModele> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/golem.png");

    public GolemRendu(EntityRendererProvider.Context contexte) {
        super(contexte, new GolemModele(contexte.bakeLayer(GolemModele.COUCHE)), 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(Golem boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Golem boss, PoseStack pose, float partialTick) {
        pose.scale(4.0F, 4.0F, 4.0F);                       // le modele est dessine a 4 unites par bloc (1 unite = 1/16 de bloc par defaut)
    }

    @Override
    protected float getFlipDegrees(Golem boss) {
        return 0.0F;                                         // pas de renversement a la mort : un colosse de 40 blocs sortirait de la salle
    }

    @Override
    protected int getBlockLightLevel(Golem boss, BlockPos position) {
        return boss.pieceOuverte() >= 0 ? 15 : Math.max(8, super.getBlockLightLevel(boss, position));      // jamais noir : pendant l'emergence ses yeux sont dans la roche
    }
}
