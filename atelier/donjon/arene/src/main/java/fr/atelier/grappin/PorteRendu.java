// [claude-build] Dessin de la porte du donjon (modele PorteGeometrie, animation PorteAnimations, generes depuis porte_donjon.bbmodel) :
// tournee comme l'entite, mise a la largeur et a la hauteur de son arche, texture de sa couleur, sceau toujours lumineux.
package fr.atelier.grappin;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class PorteRendu extends EntityRenderer<PorteDonjon> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "porte_donjon"), "main");

    static class Modele extends HierarchicalModel<PorteDonjon> {
        private final ModelPart racine;
        final List<ModelPart> lueurs = new ArrayList<>();
        final List<ModelPart> toutes;

        Modele(ModelPart maillage) {
            this.racine = maillage.getChild("racine");
            for (String chemin : PorteGeometrie.LUEURS) {
                ModelPart p = racine;
                for (String nom : chemin.split("/")) {
                    p = p.getChild(nom);
                }
                lueurs.add(p);
            }
            this.toutes = racine.getAllParts().toList();
        }

        @Override
        public ModelPart root() {
            return racine;
        }

        @Override
        public void setupAnim(PorteDonjon porte, float a, float b, float age, float d, float e) {
            racine.getAllParts().forEach(ModelPart::resetPose);
            this.animate(porte.ouverture, PorteAnimations.OUVERTURE, age);
        }
    }

    private final Modele modele;

    public PorteRendu(EntityRendererProvider.Context contexte) {
        super(contexte);
        this.modele = new Modele(contexte.bakeLayer(COUCHE));
    }

    @Override
    public ResourceLocation getTextureLocation(PorteDonjon porte) {
        return ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/" + DonjonLogique.texture(porte.couleur()) + ".png");
    }

    @Override
    public void render(PorteDonjon porte, float lacet, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere) {
        modele.setupAnim(porte, 0, 0, porte.tickCount + partiel, 0, 0);
        float[] echelle = DonjonLogique.echelle(porte.largeur(), porte.hauteur());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - porte.getYRot()));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(echelle[0], echelle[1], 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        var tampon = tampons.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(porte)));
        modele.lueurs.forEach(p -> p.visible = false);
        modele.root().render(pose, tampon, lumiere, OverlayTexture.NO_OVERLAY);
        modele.lueurs.forEach(p -> p.visible = true);
        for (ModelPart p : modele.toutes) {
            p.skipDraw = !modele.lueurs.contains(p);
        }
        modele.root().render(pose, tampon, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);      // le sceau brille toujours
        modele.toutes.forEach(p -> p.skipDraw = false);
        pose.popPose();
        super.render(porte, lacet, partiel, pose, tampons, lumiere);
    }
}
