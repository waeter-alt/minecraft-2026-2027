// [claude-build] Le modele du Gardien des Racines (client) : un modele en os, genere depuis atelier/modeles/golem/golem.bbmodel
// (GolemGeometrie, GolemAnimations : atelier/scripts/bbmodel_vers_java.py). Pour retoucher sa forme ou ses animations : Blockbench, puis
// relancer bbmodel_vers_java.py. Ici, seulement ce que Blockbench ne sait pas faire : les parties toujours lumineuses (yeux, veines,
// bourgeons), le couvercle d'ecorce qui s'efface et le bourgeon qui bat sur le point faible ouvert, la teinte rouge de la phase 2.
// Les points faibles sont dans l'os « fixe », qu'aucune animation ne touche : ils restent sur leurs boites de collision.
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

public class GolemModele extends HierarchicalModel<Golem> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "golem"), "main");

    private final ModelPart racine;
    private final List<ModelPart> lueurs = new ArrayList<>();
    private final List<ModelPart> toutes;
    private final ModelPart[] couvercles = new ModelPart[GolemGeometrie.COUVERCLES.length];
    private final ModelPart[] rouges = new ModelPart[GolemGeometrie.ROUGES.length];
    private boolean deuxiemePhase;

    public GolemModele(ModelPart maillage) {
        this.racine = maillage.getChild("racine");
        for (String chemin : GolemGeometrie.LUEURS) {
            lueurs.add(os(chemin));
        }
        for (int i = 0; i < couvercles.length; i++) {
            couvercles[i] = os(GolemGeometrie.COUVERCLES[i]);
            rouges[i] = os(GolemGeometrie.ROUGES[i]);
        }
        this.toutes = racine.getAllParts().toList();
    }

    public static LayerDefinition creerCouche() {
        return GolemGeometrie.creerCouche();
    }

    /** Une partie par son chemin depuis la racine, par exemple « corps/tete ». */
    final ModelPart os(String chemin) {
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
    public void setupAnim(Golem boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        racine.getAllParts().forEach(ModelPart::resetPose);
        int ouverte = boss.pieceOuverte();
        for (int i = 0; i < couvercles.length; i++) {
            couvercles[i].visible = i != ouverte;
            float pulse = i == ouverte ? 1.15F + 0.12F * (float) Math.sin(ageInTicks * 0.4F) : 1.0F;     // la piece ouverte gonfle et bat comme un cœur
            rouges[i].xScale = pulse;
            rouges[i].yScale = pulse;
            rouges[i].zScale = pulse;
        }
        deuxiemePhase = boss.deuxiemePhase();
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer tampon, int lumiere, int superposition, int couleur) {
        int teinte = deuxiemePhase ? net.minecraft.util.FastColor.ARGB32.multiply(couleur, 0xFFFFB8A0) : couleur;      // phase 2 : le bois rougeoie
        lueurs.forEach(p -> p.visible = false);
        racine.render(pose, tampon, lumiere, superposition, teinte);                       // le bois, eclaire normalement
        lueurs.forEach(p -> p.visible = true);
        for (ModelPart p : toutes) {                                                       // puis seulement les lueurs, toujours lumineuses : les os
            p.skipDraw = !lueurs.contains(p);                                              // restent dessines (sans leurs cubes) pour placer leurs enfants
        }
        racine.render(pose, tampon, LightTexture.FULL_BRIGHT, superposition, couleur);
        toutes.forEach(p -> p.skipDraw = false);
    }
}
