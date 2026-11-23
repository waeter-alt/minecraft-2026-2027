// [claude-build] Le modele du Rodeur des Racines (client), genere depuis atelier/modeles/rodeur/rodeur.bbmodel (RodeurGeometrie,
// RodeurAnimations : atelier/scripts/bbmodel_vers_java.py --nom Rodeur). Ici : les animations selon l'etat de la bete, le regard,
// et les yeux et les baies toujours lumineux (deuxieme passe de rendu, comme le Gardien).
package fr.atelier.grappin;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;

public class RodeurModele extends HierarchicalModel<Rodeur> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "rodeur"), "main");
    private static final float DEG = (float) Math.PI / 180F;
    private static final float REGARD_MAX = 30;

    private final ModelPart racine;
    private final ModelPart tete;
    private final List<ModelPart> lueurs = new ArrayList<>();
    private final List<ModelPart> toutes;

    public RodeurModele(ModelPart maillage) {
        this.racine = maillage.getChild("racine");
        this.tete = os(RodeurGeometrie.TETE);
        for (String chemin : RodeurGeometrie.LUEURS) {
            lueurs.add(os(chemin));
        }
        this.toutes = racine.getAllParts().toList();
    }

    public static LayerDefinition creerCouche() {
        return RodeurGeometrie.creerCouche();
    }

    private ModelPart os(String chemin) {
        ModelPart p = racine;
        for (String nom : chemin.split("/")) {
            p = p.getChild(nom);
        }
        return p;
    }

    @Override
    public ModelPart root() {
        return racine;
    }

    @Override
    public void setupAnim(Rodeur bete, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        racine.getAllParts().forEach(ModelPart::resetPose);
        this.animateWalk(RodeurAnimations.MARCHE, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        this.animate(bete.repos, RodeurAnimations.REPOS, ageInTicks);
        this.animate(bete.grattage, RodeurAnimations.GRATTAGE, ageInTicks);
        this.animate(bete.charge, RodeurAnimations.CHARGE, ageInTicks);
        this.animate(bete.sonne, RodeurAnimations.SONNE, ageInTicks);
        this.animate(bete.morsure, RodeurAnimations.MORSURE, ageInTicks);
        this.animate(bete.reveil, RodeurAnimations.REVEIL, ageInTicks);
        this.animate(bete.mort, RodeurAnimations.MORT, ageInTicks);
        if (bete.deathTime == 0) {
            tete.yRot += Math.max(-REGARD_MAX, Math.min(REGARD_MAX, netHeadYaw)) * DEG;
            tete.xRot += Math.max(-REGARD_MAX, Math.min(REGARD_MAX, headPitch)) * DEG;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer tampon, int lumiere, int superposition, int couleur) {
        lueurs.forEach(p -> p.visible = false);
        racine.render(pose, tampon, lumiere, superposition, couleur);
        lueurs.forEach(p -> p.visible = true);
        for (ModelPart p : toutes) {
            p.skipDraw = !lueurs.contains(p);                 // seconde passe : seulement les yeux et les baies, toujours lumineux
        }
        racine.render(pose, tampon, LightTexture.FULL_BRIGHT, superposition, couleur);
        toutes.forEach(p -> p.skipDraw = false);
    }
}
