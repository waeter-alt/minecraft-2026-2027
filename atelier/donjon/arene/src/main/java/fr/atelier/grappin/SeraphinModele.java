// [claude-build] Le modele du Seraphin (client), d'apres la gravure de reference : un grand oeil entoure d'une couronne de petales, de deux rangees de plumes dressees,
// d'une rangee sur les cotes, de grandes plumes qui retombent en cocon, et d'ailes de chauve-souris derriere. Dessine a l'echelle x2 (1 unite = 1/32 de bloc) puis reduit
// par SeraphinRendu, pour avoir deux fois plus de details. Les zones de texture sont decrites dans atelier/scripts/texture_seraphin.py.
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class SeraphinModele extends EntityModel<Seraphin> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "seraphin"), "main");
    private static final String[] GROUPES = {"haut", "haut2", "haut3", "milieu", "bas", "bas2", "chauve"};
    private static final float[] AMPLITUDES = {0.10F, 0.13F, 0.07F, 0.08F, 0.05F, 0.06F, 0.16F};     // battement de chaque groupe d'ailes (rad)
    private static final float OEIL_Y = 22;                                          // hauteur du centre de l'oeil (y vers le bas, pieds a 48)

    private final ModelPart racine;
    private final ModelPart oeil;
    private final ModelPart paupiere;
    private final ModelPart couronne;
    private final ModelPart aureole;
    private boolean deuxiemePhase;
    private final ModelPart[][] ailes = new ModelPart[GROUPES.length][2];             // [groupe][0 = gauche (x negatif), 1 = droite]

    public SeraphinModele(ModelPart racine) {
        this.racine = racine;
        this.oeil = racine.getChild("oeil");
        this.paupiere = oeil.getChild("paupiere");
        this.couronne = oeil.getChild("couronne");
        this.aureole = racine.getChild("aureole");
        for (int g = 0; g < GROUPES.length; g++) {
            ailes[g][0] = racine.getChild(GROUPES[g] + "_g");
            ailes[g][1] = racine.getChild(GROUPES[g] + "_d");
        }
    }

    private static float rad(float degres) {
        return (float) Math.toRadians(degres);
    }

    /** Une plume en deux segments (base large puis pointe fine, legerement recourbee), qui pointe vers le haut puis pivote de `degres` autour de son pied. */
    private static void plume(PartDefinition groupe, String nom, float degres, float longueur, float z, int tuile, float cote) {
        float l1 = Math.round(longueur * 0.62F);
        float l2 = longueur - l1;
        PartDefinition p = groupe.addOrReplaceChild(nom,
                CubeListBuilder.create().texOffs(tuile * 16, 0).addBox(-3, -l1, -1, 6, l1, 2),
                PartPose.offsetAndRotation(0, 0, z, 0, 0, rad(degres) * cote));
        p.addOrReplaceChild("t", CubeListBuilder.create().texOffs(192, 0).addBox(-2, -l2, -1, 4, l2, 2),
                PartPose.offsetAndRotation(0, -l1, 0, 0, 0, rad(7) * cote));
    }

    /** Un groupe de `n` plumes en eventail, de `deg0` a `deg1` ; les longueurs gonflent au milieu de l'eventail (de `min` a `max`). */
    private static void eventail(PartDefinition racine, String nom, float x, float y, float z, int n, float min, float max, float deg0, float deg1, int tuile) {
        float cote = x < 0 ? -1 : 1;
        PartDefinition g = racine.addOrReplaceChild(nom, CubeListBuilder.create(), PartPose.offset(x, y, z));
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0.5F : (float) i / (n - 1);
            float longueur = min + (max - min) * (float) Math.sin(Math.PI * t);
            plume(g, "p" + i, deg0 + (deg1 - deg0) * t, Math.max(min, longueur), (i % 2) * 1.6F, tuile, cote);
        }
    }

    /** Aile de chauve-souris : trois os et trois pans de membrane, derriere les plumes. */
    private static void chauve(PartDefinition racine, String nom, float x, float y, float z) {
        float cote = x < 0 ? -1 : 1;
        PartDefinition g = racine.addOrReplaceChild(nom, CubeListBuilder.create(), PartPose.offset(x, y, z));
        float[] angles = {35, 68, 100};
        float[] longueurs = {56, 50, 38};
        for (int i = 0; i < 3; i++) {
            g.addOrReplaceChild("os" + i, CubeListBuilder.create().texOffs(72, 90).addBox(-1, -longueurs[i], -1, 2, longueurs[i], 2),
                    PartPose.rotation(0, 0, rad(angles[i]) * cote));
        }
        float[] anglesPans = {51.5F, 84, 116};
        float[] longueursPans = {50, 44, 30};
        for (int i = 0; i < 3; i++) {
            g.addOrReplaceChild("pan" + i, CubeListBuilder.create().texOffs(0, 96).addBox(-6, -longueursPans[i], 0, 12, longueursPans[i], 1),
                    PartPose.rotation(0, 0, rad(anglesPans[i]) * cote));
        }
    }

    public static LayerDefinition creerCouche() {
        MeshDefinition maillage = new MeshDefinition();
        PartDefinition r = maillage.getRoot();
        // l'oeil : globe 16 x 16 x 16, pupille en saillie, cadre en relief (paupieres), paupiere qui cache le tout quand l'oeil est ferme
        PartDefinition oeil = r.addOrReplaceChild("oeil", CubeListBuilder.create().texOffs(0, 160).addBox(-8, -8, -8, 16, 16, 16)
                .texOffs(80, 160).addBox(-4, -4, -10, 8, 8, 2)
                .texOffs(160, 160).addBox(-9, -9, -9, 18, 2, 2).addBox(-9, 7, -9, 18, 2, 2).addBox(-9, -7, -9, 2, 14, 2).addBox(7, -7, -9, 2, 14, 2),
                PartPose.offset(0, OEIL_Y, 0));
        oeil.addOrReplaceChild("paupiere", CubeListBuilder.create().texOffs(110, 160).addBox(-9, -9, -11.5F, 18, 18, 2), PartPose.ZERO);
        // la couronne : douze petales rayonnants autour de l'oeil, devant les ailes
        PartDefinition couronne = oeil.addOrReplaceChild("couronne", CubeListBuilder.create(), PartPose.offset(0, 0, -6));
        for (int i = 0; i < 12; i++) {
            float a = i * 30;
            float longueur = (i % 2 == 0) ? 20 : 14;
            couronne.addOrReplaceChild("pet" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-2, -longueur, -1, 4, longueur, 2),
                    PartPose.offsetAndRotation((float) Math.sin(rad(a)) * 13, (float) -Math.cos(rad(a)) * 13, 0, 0, 0, rad(a)));
        }
        // l'aureole doree derriere l'ensemble : seize segments disposes en cercle
        PartDefinition aureole = r.addOrReplaceChild("aureole", CubeListBuilder.create(), PartPose.offset(0, OEIL_Y, 16));
        for (int i = 0; i < 16; i++) {
            float a = i * 22.5F;
            aureole.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs(160, 160).addBox(-5, -1.5F, -1, 10, 3, 2),
                    PartPose.offsetAndRotation((float) Math.sin(rad(a)) * 40, (float) -Math.cos(rad(a)) * 40, 0, 0, 0, rad(a)));
        }
        for (float cote : new float[] {-1, 1}) {
            String s = cote < 0 ? "_g" : "_d";
            eventail(r, "haut" + s, cote * 6, OEIL_Y - 8, 3.0F, 7, 40, 58, 8, 64, 0);          // couronne dressee vers le haut
            eventail(r, "haut2" + s, cote * 8, OEIL_Y - 4, 6.0F, 6, 34, 46, 22, 78, 1);        // seconde rangee, derriere
            eventail(r, "haut3" + s, cote * 4, OEIL_Y - 6, 1.0F, 5, 22, 32, 15, 55, 2);          // petites plumes serrees autour de l'oeil
            eventail(r, "milieu" + s, cote * 9, OEIL_Y + 1, 4.5F, 7, 40, 52, 70, 118, 2);      // sur les cotes
            eventail(r, "bas" + s, cote * 5, OEIL_Y + 6, 5.0F, 8, 54, 74, 130, 186, 3);        // grandes plumes qui retombent en cocon
            eventail(r, "bas2" + s, cote * 3, OEIL_Y + 8, -0.5F, 6, 34, 46, 140, 184, 1);       // seconde rangee de plumes, devant
            chauve(r, "chauve" + s, cote * 12, OEIL_Y + 4, 9.0F);
        }
        return LayerDefinition.create(maillage, 256, 256);
    }

    @Override
    public void setupAnim(Seraphin boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean ouvert = boss.oeilOuvert();
        deuxiemePhase = boss.deuxiemePhase();
        aureole.zRot = ageInTicks * 0.006F;
        paupiere.visible = !ouvert;
        oeil.xRot = rad(headPitch);                                    // la pupille suit la cible
        oeil.yRot = rad(netHeadYaw);
        couronne.zRot = ageInTicks * (ouvert ? 0.05F : 0.012F);        // la couronne tourne, plus vite quand l'oeil est ouvert
        float vitesse = ouvert ? 0.35F : 0.12F;                        // les ailes battent plus vite quand il attaque
        for (int g = 0; g < GROUPES.length; g++) {
            float battement = (float) Math.sin(ageInTicks * vitesse + g * 0.8F) * AMPLITUDES[g];
            ailes[g][0].zRot = -battement;
            ailes[g][1].zRot = battement;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer tampon, int lumiere, int superposition, int couleur) {
        racine.render(pose, tampon, lumiere, superposition, deuxiemePhase ? net.minecraft.util.FastColor.ARGB32.multiply(couleur, 0xFFFFC0A0) : couleur);      // phase 2 : le blanc vire au rose dore
    }
}
