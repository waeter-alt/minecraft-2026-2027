// [claude-build] Rendu du Rodeur des Racines : son modele Blockbench, avec la texture du Gardien des Racines (memes matieres).
package fr.atelier.grappin;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RodeurRendu extends MobRenderer<Rodeur, RodeurModele> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/golem.png");

    public RodeurRendu(EntityRendererProvider.Context contexte) {
        super(contexte, new RodeurModele(contexte.bakeLayer(RodeurModele.COUCHE)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(Rodeur bete) {
        return TEXTURE;
    }
}
