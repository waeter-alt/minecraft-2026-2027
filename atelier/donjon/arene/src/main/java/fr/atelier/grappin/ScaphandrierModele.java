// [claude-build] Le modele du Scaphandrier rouille (client), genere depuis atelier/modeles/station/scaphandrier.bbmodel (ScaphandrierGeometrie,
// ScaphandrierAnimations : atelier/scripts/bbmodel_vers_java.py --nom Scaphandrier). Ici : les animations selon l'etat, le regard du casque,
// le hublot de face toujours lumineux (deuxieme passe de rendu) et ses fissures qui apparaissent avec les degats.
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

public class ScaphandrierModele extends HierarchicalModel<Scaphandrier> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "scaphandrier"), "main");
    private static final float DEG = (float) Math.PI / 180F;
    private static final float REGARD_MAX = 25;

    private final ModelPart racine;
    private final ModelPart casque;
    private final ModelPart fissure1;
    private final ModelPart fissure2;
    private final ModelPart brise;
    private final List<ModelPart> lueurs = new ArrayList<>();
    private final List<ModelPart> toutes;
    private boolean hublotAllume = true;

    public ScaphandrierModele(ModelPart maillage) {
        this.racine = maillage.getChild("racine");
        this.casque = os("corps/casque");
        this.fissure1 = os("corps/casque/fissure1");
        this.fissure2 = os("corps/casque/fissure2");
        this.brise = os("corps/casque/hublot_brise");
        for (String chemin : ScaphandrierGeometrie.LUEURS) {
            lueurs.add(os(chemin));
        }
        this.toutes = racine.getAllParts().toList();
    }

    public static LayerDefinition creerCouche() {
        return ScaphandrierGeometrie.creerCouche();
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
    public void setupAnim(Scaphandrier s, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        racine.getAllParts().forEach(ModelPart::resetPose);
        this.animateWalk(ScaphandrierAnimations.MARCHE, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        this.animate(s.repos, ScaphandrierAnimations.REPOS, ageInTicks);
        this.animate(s.visee, ScaphandrierAnimations.VISER, ageInTicks);
        this.animate(s.tir, ScaphandrierAnimations.TIRER, ageInTicks);
        this.animate(s.coince, ScaphandrierAnimations.COINCE, ageInTicks);
        this.animate(s.coup, ScaphandrierAnimations.COUP, ageInTicks);
        this.animate(s.mort, ScaphandrierAnimations.MORT, ageInTicks);
        if (s.deathTime == 0) {
            casque.yRot += Math.max(-REGARD_MAX, Math.min(REGARD_MAX, netHeadYaw)) * DEG;
            casque.xRot += Math.max(-REGARD_MAX, Math.min(REGARD_MAX, headPitch)) * DEG;
        }
        int hublot = s.deathTime > 0 ? 3 : ScaphandrierLogique.hublot(s.getHealth() / s.getMaxHealth());
        fissure1.visible = hublot == 1 || hublot == 2;
        fissure2.visible = hublot == 2;
        brise.visible = hublot == 3;
        hublotAllume = hublot < 3;                                 // hublot eclate : plus de lumiere, un trou noir
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer tampon, int lumiere, int superposition, int couleur) {
        lueurs.forEach(p -> p.visible = false);
        racine.render(pose, tampon, lumiere, superposition, couleur);
        lueurs.forEach(p -> p.visible = true);
        if (hublotAllume) {
            for (ModelPart p : toutes) {
                p.skipDraw = !lueurs.contains(p);                 // seconde passe : seulement le hublot, toujours lumineux
            }
            racine.render(pose, tampon, LightTexture.FULL_BRIGHT, superposition, couleur);
            toutes.forEach(p -> p.skipDraw = false);
        }
    }
}
