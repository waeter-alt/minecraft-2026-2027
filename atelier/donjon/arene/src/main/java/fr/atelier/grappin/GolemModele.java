// [claude-build] Le modele du Gardien des Racines (client) : un modele en os, genere depuis atelier/modeles/golem/golem.bbmodel
// (GolemGeometrie, GolemAnimations : atelier/scripts/bbmodel_vers_java.py). Pour retoucher sa forme ou ses animations : Blockbench, puis
// relancer bbmodel_vers_java.py. Ici, seulement ce que Blockbench ne sait pas faire : les parties toujours lumineuses (yeux, veines,
// bourgeons), le masque qui se leve et les petales qui s'ouvrent sur le point faible ouvert, le cœur qui bat, la teinte rouge de la phase 2.
// Les points faibles suivent l'os qui les porte ; le serveur place leurs boites de collision au meme endroit (GolemSquelette).
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
    private final ModelPart[] rouges = new ModelPart[GolemGeometrie.ROUGES.length];
    private final ModelPart[] masques = new ModelPart[GolemGeometrie.ROUGES.length];              // null si le point faible n'a pas de masque
    private final ModelPart[][] petales = new ModelPart[GolemGeometrie.ROUGES.length][];


    private final ModelPart tete;
    private boolean deuxiemePhase;

    private static final float DEG = (float) Math.PI / 180F;
    private static final float TICKS_OUVERTURE = 10;            // 0,5 s pour s'ouvrir ou se fermer
    private static final float ANGLE_MASQUE = 50, LEVEE_MASQUE = 8, ANGLE_PETALE = 70;

    public GolemModele(ModelPart maillage) {
        this.racine = maillage.getChild("racine");
        for (String chemin : GolemGeometrie.LUEURS) {
            lueurs.add(os(chemin));
        }
        for (int i = 0; i < rouges.length; i++) {
            rouges[i] = os(GolemGeometrie.ROUGES[i]);
            masques[i] = GolemGeometrie.MASQUES[i].isEmpty() ? null : os(GolemGeometrie.MASQUES[i]);
            petales[i] = new ModelPart[GolemGeometrie.PETALES[i].length];
            for (int k = 0; k < petales[i].length; k++) {
                petales[i][k] = os(GolemGeometrie.PETALES[i][k]);
            }
        }
        this.toutes = racine.getAllParts().toList();
        this.tete = os(GolemGeometrie.TETE);
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
        this.animate(boss.repos, GolemAnimations.REPOS, ageInTicks);
        this.animate(boss.animEmergence, GolemAnimations.EMERGENCE, ageInTicks);
        this.animate(boss.lancer, GolemAnimations.LANCER, ageInTicks);
        this.animate(boss.douleur, GolemAnimations.DOULEUR, ageInTicks);
        this.animate(boss.rage, GolemAnimations.RAGE, ageInTicks);
        this.animate(boss.mort, GolemAnimations.MORT, ageInTicks);              // la plus importante en dernier
        if (!boss.mort.isStarted()) {
            tete.yRot += GolemAnimationsEtat.lacetTete(netHeadYaw) * DEG;      // il suit du regard le joueur vise
            tete.xRot += GolemAnimationsEtat.tangageTete(headPitch) * DEG;
        }
        float[] ouverture = boss.ouverture;                                    // garde sur le boss : le modele est partage par tous les Gardiens
        float pas = boss.ageOuverture < 0 ? 1 : Math.max(0, ageInTicks - boss.ageOuverture) / TICKS_OUVERTURE;
        boss.ageOuverture = ageInTicks;
        int ouverte = boss.pieceOuverte();
        for (int i = 0; i < rouges.length; i++) {
            float o = ouverture[i] = net.minecraft.util.Mth.approach(ouverture[i], i == ouverte ? 1 : 0, pas);
            float e = o * o * (3 - 2 * o);                                     // depart et arrivee en douceur
            if (masques[i] != null) {                                          // le masque se leve et bascule vers l'avant
                masques[i].xRot -= ANGLE_MASQUE * DEG * e;
                masques[i].y -= LEVEE_MASQUE * e;
            }
            for (int k = 0; k < petales[i].length; k++) {                      // les petales s'ecartent vers l'exterieur
                float a = ANGLE_PETALE * DEG * e;
                switch (k) {
                    case 0 -> petales[i][k].zRot += a;
                    case 1 -> petales[i][k].zRot -= a;
                    case 2 -> petales[i][k].xRot += a;
                    default -> petales[i][k].xRot -= a;
                }
            }
            float pulse = o > 0.5F ? 1.15F + 0.12F * (float) Math.sin(ageInTicks * 0.4F) : 1.0F;     // le cœur ouvert gonfle et bat
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
