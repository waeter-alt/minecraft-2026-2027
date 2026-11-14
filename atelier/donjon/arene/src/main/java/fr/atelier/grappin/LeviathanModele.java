// [claude-build] Les modeles du Leviathan (client) : la tete (crane, museau, machoire a dents, arcades, crete dorsale, barbillons, yeux ambre et perle du front), un anneau du corps (ecailles, ventre clair,
// nageoire dorsale et nageoires laterales) et la queue (nageoire en croissant). Chaque modele est dessine autour de son centre, a 4 unites par bloc ; LeviathanRendu les place le long du chemin de la tete et les agrandit x4.
// La perle et les yeux sont rendus lumineux. Les zones de texture : atelier/scripts/texture_leviathan.py.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public final class LeviathanModele {
    private LeviathanModele() {
    }

    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "leviathan"), "main");

    private static final int[] ECAILLES = {0, 0};
    private static final int[] VENTRE = {0, 130};
    private static final int[] NAGEOIRE = {130, 130};
    private static final int[] DENT = {0, 196};
    private static final int[] AMBRE = {40, 196};
    private static final int[] PERLE = {80, 196};

    private static float rad(float degres) {
        return (float) Math.toRadians(degres);
    }

    private static CubeListBuilder cube(int[] zone, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(zone[0], zone[1]).addBox(x, y, z, w, h, d);
    }

    public static LayerDefinition creerCouche() {
        MeshDefinition maillage = new MeshDefinition();
        PartDefinition r = maillage.getRoot();

        // ---------------------------------------------------------------- la tete : le museau pointe vers -z (l'avant du modele)
        PartDefinition tete = r.addOrReplaceChild("tete", CubeListBuilder.create(), PartPose.ZERO);
        tete.addOrReplaceChild("crane", cube(ECAILLES, -8, -6, -4, 16, 12, 14), PartPose.ZERO);
        tete.addOrReplaceChild("museau", cube(ECAILLES, -6, -3, -18, 12, 5, 14), PartPose.ZERO);
        tete.addOrReplaceChild("narines", cube(VENTRE, -5, -4, -17, 10, 1.5F, 3), PartPose.ZERO);
        PartDefinition machoire = tete.addOrReplaceChild("machoire", cube(VENTRE, -5, 0, -14, 10, 3, 12), PartPose.offsetAndRotation(0, 2, -4, rad(16), 0, 0));
        for (int i = 0; i < 6; i++) {                                                   // dents des deux machoires
            for (int s = -1; s <= 1; s += 2) {
                tete.addOrReplaceChild("dent_h" + i + "_" + s, cube(DENT, s * 5.5F - 0.5F, 2, -16 + i * 2, 1, 3, 1), PartPose.ZERO);
                machoire.addOrReplaceChild("dent_b" + i + "_" + s, cube(DENT, s * 4.5F - 0.5F, -2.5F, -13 + i * 2, 1, 2.5F, 1), PartPose.ZERO);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            tete.addOrReplaceChild("arcade" + s, cube(ECAILLES, s * 8.5F - 1.5F, -8, -12, 3, 3, 12), PartPose.ZERO);
            tete.addOrReplaceChild("corne" + s, cube(ECAILLES, -1, -9, 0, 2, 8, 2), PartPose.offsetAndRotation(s * 7, -4, 8, rad(-30), 0, rad(s * 20)));
            tete.addOrReplaceChild("barbillon" + s, cube(VENTRE, -0.5F, 0, -0.5F, 1, 9, 1), PartPose.offsetAndRotation(s * 3, 5, -14, rad(10), 0, rad(s * 14)));
            tete.addOrReplaceChild("nageoire_t" + s, cube(NAGEOIRE, 0, -0.5F, -3, 10, 1, 8), PartPose.offsetAndRotation(s * 8, 2, 4, 0, rad(s * -20), rad(s * 25)));
        }
        for (int i = 0; i < 5; i++) {                                                   // crete dorsale
            tete.addOrReplaceChild("crete" + i, cube(NAGEOIRE, -0.5F, -16 + i * 1.2F, 0, 1, 11 - i, 2), PartPose.offsetAndRotation(0, -6, -1 + i * 2.6F, rad(-12), 0, 0));
        }
        // les yeux et la perle : rendus lumineux
        PartDefinition lum = tete.addOrReplaceChild("lueurs", CubeListBuilder.create(), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            lum.addOrReplaceChild("oeil" + s, cube(AMBRE, s * 8.2F - 0.5F, -4, -10, 1.5F, 3.5F, 5), PartPose.ZERO);
        }
        tete.addOrReplaceChild("perle", cube(PERLE, -3, -3, -3, 6, 6, 6), PartPose.offset(0, -12, -6));           // a la meme place que sa boite de collision (Leviathan.positionPerle)

        // ---------------------------------------------------------------- un anneau du corps
        PartDefinition anneau = r.addOrReplaceChild("anneau", CubeListBuilder.create(), PartPose.ZERO);
        anneau.addOrReplaceChild("corps", cube(ECAILLES, -7, -7, -10, 14, 14, 20), PartPose.ZERO);
        anneau.addOrReplaceChild("ventre", cube(VENTRE, -6, 5, -10, 12, 3, 20), PartPose.ZERO);
        anneau.addOrReplaceChild("dos", cube(NAGEOIRE, -0.5F, -16, -8, 1, 10, 16), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            anneau.addOrReplaceChild("nageoire" + s, cube(NAGEOIRE, 0, -0.5F, -4, 9, 1, 8), PartPose.offsetAndRotation(s * 6, 3, 0, 0, 0, rad(s * 30)));
            anneau.addOrReplaceChild("epine" + s, cube(ECAILLES, -0.5F, -5, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(s * 6.5F, -6, -3, 0, 0, rad(s * 40)));
        }

        // ---------------------------------------------------------------- la queue : un fuseau et une nageoire en croissant
        PartDefinition queue = r.addOrReplaceChild("queue", CubeListBuilder.create(), PartPose.ZERO);
        queue.addOrReplaceChild("fuseau", cube(ECAILLES, -5, -5, -10, 10, 10, 20), PartPose.ZERO);
        queue.addOrReplaceChild("fuseau_f", cube(ECAILLES, -3, -3, 10, 6, 6, 8), PartPose.ZERO);
        queue.addOrReplaceChild("dos", cube(NAGEOIRE, -0.5F, -12, -8, 1, 7, 14), PartPose.ZERO);
        for (int s = -1; s <= 1; s += 2) {
            queue.addOrReplaceChild("croissant" + s, cube(NAGEOIRE, 0, -0.5F, 0, 18, 1, 9), PartPose.offsetAndRotation(0, 0, 16, 0, rad(s * 35), 0));
            queue.addOrReplaceChild("croissant_v" + s, cube(NAGEOIRE, -0.5F, 0, 0, 1, 12, 9), PartPose.offsetAndRotation(0, s < 0 ? -12 : 0, 17, rad(s * -10), 0, 0));
        }
        return LayerDefinition.create(maillage, 256, 256);
    }
}
