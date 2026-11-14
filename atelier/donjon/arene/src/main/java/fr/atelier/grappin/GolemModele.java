// [claude-build] Le modele du Gardien des Racines (client), d'apres la figurine de golem d'arbre, redessine pour qu'on lise son corps du premier coup d'œil :
//   - tete en bois CLAIR, large, sur un cou, avec masque rouge, yeux ambre lumineux et deux grandes cornes de branches qui se recourbent vers l'interieur ;
//   - torse large en ecorce SOMBRE avec un plastron rouge, de gros epaulements a bouts de rondins, une taille de mousse bien verte ;
//   - bras gauche pendant (grosse main de bois) et bras droit leve qui porte un enorme poing rouge a corne creme.
// Dessine a 4 unites par bloc (GolemRendu l'agrandit x4) ; seule la moitie haute est visible, le reste est sous la lave. Chaque point faible (voir Golem.NOMS_PIECES) est un cube rouge
// recouvert d'une plaque d'ecorce qui s'efface quand la piece est ouverte ; les cubes rouges et les yeux sont rendus lumineux. Les zones de texture : atelier/scripts/texture_golem.py.
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;

public class GolemModele extends EntityModel<Golem> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "golem"), "main");
    private static final float U = 4.0F;                         // unites de modele par bloc

    private static final int[] SOMBRE = {0, 0};                  // ecorce sombre : le corps
    private static final int[] ROUGE = {0, 130};
    private static final int[] MOUSSE = {0, 224};
    private static final int[] CLAIR = {260, 0};                 // bois clair : la tete et les cornes
    private static final int[] MASQUE = {260, 100};
    private static final int[] CREME = {330, 100};
    private static final int[] CERNES = {330, 140};
    private static final int[] AMBRE = {330, 200};
    private static final int[] VISAGE = {260, 230};              // plaque sculptee du visage (cube 10 x 8,5 x 2 blocs)

    private final ModelPart racine;
    private final ModelPart lueurs;
    private final ModelPart veines;
    private final ModelPart[] couvercles = new ModelPart[Golem.NOMS_PIECES.length];
    private final ModelPart[] rouges = new ModelPart[Golem.NOMS_PIECES.length];
    private boolean deuxiemePhase;

    public GolemModele(ModelPart racine) {
        this.racine = racine;
        this.lueurs = racine.getChild("lueurs");
        this.veines = racine.getChild("veines");
        for (int i = 0; i < couvercles.length; i++) {
            couvercles[i] = racine.getChild("couv" + i);
            rouges[i] = lueurs.getChild("rouge" + i);
        }
    }

    private static int numero = 0;

    /**
     * Un cube de (w x h x d) blocs centre en (cx, cy, cz) en blocs, dans le repere de l'entite : cx > 0 = a la droite du lacet 0 (est), cy = hauteur au-dessus de la lave, cz > 0 = devant.
     * Dans le modele, x va dans le meme sens, y vers le bas et z vers l'arriere (le rendu retourne y et z). `rz` : rotation autour de l'axe avant-arriere, en degres.
     */
    private static void cube(PartDefinition p, String nom, int[] zone, double cx, double cy, double cz, double w, double h, double d, double rz) {
        p.addOrReplaceChild(nom + (numero++), CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox((float) (-w * U / 2), (float) (-h * U / 2), (float) (-d * U / 2), (float) (w * U), (float) (h * U), (float) (d * U)),
                PartPose.offsetAndRotation((float) (cx * U), (float) (24 - cy * U), (float) (-cz * U), 0, 0, (float) Math.toRadians(rz)));
    }

    /** Cube rouge d'un point faible, nomme « rouge<i> » pour pouvoir le faire pulser quand il est ouvert. */
    private static void rouge(PartDefinition p, int i, int[] zone, double cx, double cy, double cz, double w, double h, double d) {
        p.addOrReplaceChild("rouge" + i, CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox((float) (-w * U / 2), (float) (-h * U / 2), (float) (-d * U / 2), (float) (w * U), (float) (h * U), (float) (d * U)),
                PartPose.offset((float) (cx * U), (float) (24 - cy * U), (float) (-cz * U)));
    }

    private static void corps(PartDefinition p, double cx, double cy, double cz, double w, double h, double d) {
        cube(p, "c", SOMBRE, cx, cy, cz, w, h, d, 0);
    }

    private static void bois(PartDefinition p, double cx, double cy, double cz, double w, double h, double d, double rz) {
        cube(p, "b", CLAIR, cx, cy, cz, w, h, d, rz);
    }

    public static LayerDefinition creerCouche() {
        numero = 0;
        MeshDefinition maillage = new MeshDefinition();
        PartDefinition r = maillage.getRoot();

        // ---------------------------------------------------------------- taille de mousse, hanches, racines
        cube(r, "mousse", MOUSSE, 0, 3, 0, 13, 6, 11, 0);
        corps(r, 0, 10.5, 0, 11.5, 9, 10);
        for (int s = -1; s <= 1; s += 2) {
            corps(r, s * 7.4, 1.0, 3.5, 3.5, 2.6, 3.5);                  // racines qui plongent dans la lave
            corps(r, s * 7.0, 1.0, -3.5, 3.5, 2.6, 3.5);
        }

        // ---------------------------------------------------------------- torse large, epaulement, dos
        corps(r, 0, 21.5, 0, 16, 14, 11);
        corps(r, 0, 29, 0, 18, 3, 10);                                    // epaulement qui relie les deux bras
        for (int k = 0; k < 3; k++) {                                     // crete du dos
            corps(r, 0, 18.5 + k * 3.8, -6.6, 3, 2.4, 2);
        }
        cube(r, "mousse", MOUSSE, 0, 30.8, -1, 8, 1, 6, 0);                // touffe sur le dos

        // ---------------------------------------------------------------- cou et tete en bois clair
        corps(r, 0, 30.8, 1.8, 5, 2.4, 5);
        bois(r, 0, 35.2, 2.5, 11, 10, 10, 0);                             // la tete
        corps(r, 0, 38.6, 7.7, 11.6, 1.5, 1.8);                           // arcade sombre
        bois(r, -5.9, 34.5, 3.5, 1.4, 5, 6, 0);                           // joues
        bois(r, 5.9, 34.5, 3.5, 1.4, 5, 6, 0);
        corps(r, 0, 30.0, 4.2, 9, 2, 8);                                  // machoire
        for (int k = 0; k < 5; k++) {                                     // barbe de racines
            corps(r, -3 + k * 1.5, 29.4 - (k % 2), 6.2, 0.8, 3 + (k % 2), 0.8);
        }

        // ---------------------------------------------------------------- les deux cornes de branches, bien visibles : elles montent, s'ecartent, puis se recourbent vers l'interieur
        for (int s = -1; s <= 1; s += 2) {
            bois(r, s * 4.6, 41.2, 2.5, 2.6, 3.4, 2.6, 0);
            bois(r, s * 5.5, 44.4, 2.5, 2.4, 4.2, 2.4, s * 14);
            bois(r, s * 6.8, 48.0, 2.5, 2.2, 4.2, 2.2, s * 12);
            bois(r, s * 7.4, 51.6, 2.5, 2.0, 3.8, 2.0, s * 2);
            bois(r, s * 6.7, 54.6, 2.5, 1.8, 3.2, 1.8, -s * 32);
            bois(r, s * 5.2, 56.6, 2.5, 1.6, 2.6, 1.6, -s * 62);
            bois(r, s * 8.6, 50.0, 2.5, 0.9, 2.4, 0.9, s * 38);           // brindille
        }

        // ---------------------------------------------------------------- epaules, bras gauche pendant, bras droit leve
        for (int s = -1; s <= 1; s += 2) {
            corps(r, s * 10.5, 28.5, 0, 8, 8, 9);
            cube(r, "rondin", CERNES, s * 14.8, 28.5, 0, 0.6, 7, 7, 0);   // bout de rondin a cernes
            cube(r, "mousse", MOUSSE, s * 10.5, 32.9, 0, 6, 1, 7, 0);
        }
        corps(r, -11.8, 21.5, 0.3, 5.5, 10, 6.5);                         // bras gauche
        corps(r, -12.2, 16.3, 0.8, 7, 2, 7.5);
        corps(r, -12.6, 12.5, 1.2, 6, 8, 7);
        corps(r, -13.0, 7.2, 2.2, 7.5, 5, 8);                             // grosse main de bois
        for (int k = 0; k < 4; k++) {
            corps(r, -15.2 + k * 1.9, 5.4, 6.5, 1.6, 3.2, 1.8);
        }
        corps(r, 10.6, 31.8, 2.2, 5.5, 6, 5.5);                           // bras droit leve
        corps(r, 9.8, 36.4, 5.0, 5.5, 6, 5.5);
        cube(r, "corne", CREME, 9.2, 45.6, 7.7, 1.8, 3.2, 1.8, 0);           // corne creme du poing

        // ---------------------------------------------------------------- details : on les place en petits volumes, clairs sur le sombre ou l'inverse, pour preciser la forme sans la brouiller
        for (int k = 0; k < 3; k++) {                                     // cage thoracique : cotes claires sur le devant du torse, sous le plastron
            bois(r, 0, 20.6 - k * 1.9, 5.8, 12.8, 0.8, 0.5, 0);
        }
        bois(r, 0, 18.6, 5.9, 1.2, 4.4, 0.5, 0);                          // sternum
        for (int s = -1; s <= 1; s += 2) {
            bois(r, s * 6.6, 23.6, 5.6, 2.2, 5.6, 0.8, 0);                // plaques sur les cotes du torse
            bois(r, s * 8.2, 17.5, 3.0, 0.8, 3.4, 5.6, 0);
            for (int k = 0; k < 2; k++) {                                // arrete de bois sur la crete des epaules
                bois(r, s * (8.6 + k * 2.6), 33.9, 0.6, 1.4, 1.6, 1.4, s * 18);
            }
            bois(r, s * 6.5, 36.2, 2.6, 1.6, 2.2, 1.6, 0);                // nœuds en guise d'oreilles
            cube(r, "croc", CREME, s * 3.4, 29.5, 8.4, 0.9, 2.4, 0.9, 0);    // defenses qui montent de la machoire
            cube(r, "croc", CREME, s * 3.2, 31.0, 8.0, 0.6, 1.2, 0.6, -s * 16);
            // jeune pousse sur chaque epaule : une tige et trois feuilles
            corps(r, s * 12.0, 35.0, -1.5, 0.9, 3.6, 0.9);
            cube(r, "feuille", MOUSSE, s * 12.0, 37.2, -1.5, 2.6, 0.8, 1.6, s * 20);
            cube(r, "feuille", MOUSSE, s * 11.0, 36.2, -1.5, 2.0, 0.7, 1.4, -s * 30);
            cube(r, "feuille", MOUSSE, s * 12.0, 38.0, -1.5, 1.2, 0.9, 2.6, 0);
            // branches qui poussent dans le dos, avec feuilles
            corps(r, s * 5.0, 33.2, -5.6, 1.1, 4.6, 1.1);
            corps(r, s * 6.6, 36.2, -5.6, 0.9, 3.0, 0.9);
            cube(r, "feuille", MOUSSE, s * 6.0, 38.0, -5.6, 3.0, 0.8, 2.0, s * 25);
            cube(r, "feuille", MOUSSE, s * 4.6, 35.8, -5.6, 2.2, 0.8, 1.6, -s * 30);
            // feuilles aux pointes des cornes
            cube(r, "feuille", MOUSSE, s * 5.0, 58.2, 2.5, 2.4, 0.8, 1.8, -s * 20);
            cube(r, "feuille", MOUSSE, s * 7.4, 53.4, 2.5, 2.0, 0.8, 1.6, s * 40);
        }
        cube(r, "mousse", MOUSSE, 0, 40.7, 2.5, 7, 1.2, 7, 0);            // chevelure de mousse sur le crane
        for (int k = 0; k < 4; k++) {
            cube(r, "mousse", MOUSSE, -2.6 + k * 1.7, 41.8, 2.5, 1.0, 1.4, 1.0, 0);
        }
        // ceinture de racines noueuses autour de la taille
        for (int k = 0; k < 6; k++) {
            double a_ = k * Math.PI / 3;
            corps(r, Math.cos(a_) * 5.9, 6.9, Math.sin(a_) * 5.2, 2.4, 1.8, 2.4);
        }
        // bras gauche : plaques d'armure claires, vrais doigts a deux articles, pouce, phalanges, lianes qui pendent dans la lave
        bois(r, -12.6, 12.6, 4.9, 4.6, 6, 0.6, 0);
        bois(r, -12.2, 21.5, 3.8, 3.6, 7, 0.6, 0);
        for (int k = 0; k < 4; k++) {
            corps(r, -15.2 + k * 1.9, 3.6, 6.6, 1.5, 2.4, 1.6);           // bout des doigts
            corps(r, -15.2 + k * 1.9, 9.8, 4.4, 1.7, 1.2, 1.9);           // phalanges sur le dessus de la main
        }
        corps(r, -9.9, 6.4, 4.2, 2, 2.8, 2);                              // pouce
        for (int k = 0; k < 3; k++) {
            corps(r, -15.4 + k * 1.6, 4.0, 1.0 + k * 1.4, 0.7, 5.5 - k, 0.7);   // lianes
        }
        // bras droit leve : plaques claires, doigts qui serrent le poing, lianes
        bois(r, 9.8, 36.4, 8.0, 4.6, 4.2, 0.6, 0);
        bois(r, 11.9, 31.8, 2.2, 0.6, 4.4, 4.2, 0);
        for (int k = 0; k < 4; k++) {
            corps(r, 6.2 + k * 2.2, 36.6, 11.9, 1.8, 2.4, 1.8);
        }
        corps(r, 13.2, 38.6, 9.2, 2.0, 2.0, 2.2);                         // pouce
        for (int k = 0; k < 3; k++) {
            corps(r, 12.8 - k * 0.5, 28.8, 1.4 + k * 1.4, 0.7, 4.0, 0.7);
        }
        // lianes pendues au torse et au bras
        for (int k = 0; k < 5; k++) {
            corps(r, -6.0 + k * 3.0, 14.2 - (k % 2) * 1.2, 5.4, 0.7, 3.6 + (k % 2), 0.7);
        }
        // veines de seve ambre sur la poitrine (lumineuses)
        PartDefinition veines = r.addOrReplaceChild("veines", CubeListBuilder.create(), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            cube(veines, "veine", AMBRE, s * 5.6, 20.0, 5.75, 0.5, 5.0, 0.3, s * 12);
            cube(veines, "veine", AMBRE, s * 6.4, 16.8, 5.75, 0.4, 3.0, 0.3, -s * 18);
            cube(veines, "veine", AMBRE, s * 9.0, 30.0, 4.65, 0.4, 3.0, 0.3, 0);
        }

        // ---------------------------------------------------------------- points faibles : cube rouge (lumineux) + plaque d'ecorce qui s'efface quand la piece est ouverte
        PartDefinition lum = r.addOrReplaceChild("lueurs", CubeListBuilder.create(), PartPose.ZERO);
        rouge(lum, 0, MASQUE, 0, 35.2, 7.9, 8, 7, 1);                               // 0 : masque (visible quand la plaque du visage s'efface)
        rouge(lum, 1, ROUGE, 0, 25, 6.2, 8, 6, 2);                                  // 1 : plastron
        rouge(lum, 2, ROUGE, 9.2, 40, 7.7, 7, 7, 7);                                // 2 : poing
        rouge(lum, 3, ROUGE, -3.6, 30, -6.4, 4.4, 4.4, 3);                          // 3 : noeud du dos, a gauche (gros, il doit se voir de loin)
        rouge(lum, 4, ROUGE, 3.6, 22, -6.4, 4.4, 4.4, 3);                           // 4 : noeud du dos, a droite
        cube(lum, "oeilg", AMBRE, -2.5, 36.0, 9.8, 2.8, 1.0, 0.4, 20);               // yeux ambre inclines (regard mauvais), toujours lumineux
        cube(lum, "oeild", AMBRE, 2.5, 36.0, 9.8, 2.8, 1.0, 0.4, -20);
        double[][] couv = {{0, 35.2, 8.4, 10, 8.5, 2}, {0, 25, 6.9, 9, 7, 2}, {9.2, 40, 7.7, 7.8, 7.8, 7.8}, {-3.6, 30, -7.5, 5.6, 5.6, 3}, {3.6, 22, -7.5, 5.6, 5.6, 3}};
        for (int i = 0; i < couv.length; i++) {
            int[] zone = i == 0 ? VISAGE : SOMBRE;
            CubeListBuilder cb = CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox((float) (-couv[i][3] * U / 2), (float) (-couv[i][4] * U / 2), (float) (-couv[i][5] * U / 2),
                    (float) (couv[i][3] * U), (float) (couv[i][4] * U), (float) (couv[i][5] * U));
            if (i == 0) {
                cb.texOffs(CLAIR[0], CLAIR[1]).addBox(-4, -6, -10, 8, 14, 6);          // nez : une arete saillante au milieu du visage
                cb.texOffs(CLAIR[0], CLAIR[1]).addBox(-17, -9, -7, 10, 3, 3);          // arcade gauche
                cb.texOffs(CLAIR[0], CLAIR[1]).addBox(7, -9, -7, 10, 3, 3);            // arcade droite
                cb.texOffs(CLAIR[0], CLAIR[1]).addBox(-18, 3, -6, 5, 9, 2);            // pommette gauche
                cb.texOffs(CLAIR[0], CLAIR[1]).addBox(13, 3, -6, 5, 9, 2);             // pommette droite
            }
            r.addOrReplaceChild("couv" + i, cb, PartPose.offset((float) (couv[i][0] * U), (float) (24 - couv[i][1] * U), (float) (-couv[i][2] * U)));
        }
        return LayerDefinition.create(maillage, 512, 512);
    }

    @Override
    public void setupAnim(Golem boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
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
        lueurs.visible = false;
        veines.visible = false;
        racine.render(pose, tampon, lumiere, superposition, teinte);                       // le bois, eclaire normalement
        lueurs.visible = true;
        veines.visible = true;
        lueurs.render(pose, tampon, LightTexture.FULL_BRIGHT, superposition, couleur);     // parties rouges et yeux, toujours lumineux
        veines.render(pose, tampon, LightTexture.FULL_BRIGHT, superposition, couleur);     // veines de seve
    }
}
