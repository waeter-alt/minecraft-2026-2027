// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/rodeur/rodeur.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class RodeurGeometrie {
    private RodeurGeometrie() {
    }

    public static final String[] LUEURS = {
        "corps/cou/tete/lueur_oeil_g_4",
        "corps/cou/tete/lueur_oeil_d_5",
        "corps/lianes/lueur_baie0_10",
        "corps/lianes/lueur_baie1_11",
        "corps/lianes/lueur_baie2_12",
    };
    public static final String[] ROUGES = {
    };
    public static final String[] MASQUES = {
    };
    public static final String[][] PETALES = {
    };
    public static final String TETE = "corps/cou/tete";

    public static LayerDefinition creerCouche() {
        MeshDefinition maillage = new MeshDefinition();
        PartDefinition p0 = maillage.getRoot().addOrReplaceChild("racine", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p1 = p0.addOrReplaceChild("corps", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, -8.0F, -15.0F, 8.0F, 8.0F, 28.0F).texOffs(330, 140).addBox(1.0F, -8.0F, -13.0F, 8.0F, 8.0F, 28.0F).texOffs(330, 140).addBox(-8.0F, -1.0F, -13.0F, 8.0F, 8.0F, 25.0F).texOffs(0, 0).addBox(0.0F, -1.0F, -12.0F, 8.0F, 8.0F, 25.0F).texOffs(0, 0).addBox(-6.0F, -6.0F, -12.0F, 12.0F, 11.0F, 24.0F).texOffs(0, 0).addBox(-8.0F, -6.0F, -19.0F, 16.0F, 14.0F, 7.0F).texOffs(330, 140).addBox(-7.0F, -6.0F, 11.0F, 14.0F, 12.0F, 6.0F).texOffs(0, 224).addBox(-8.0F, -11.0F, -13.0F, 16.0F, 3.0F, 24.0F).texOffs(0, 224).addBox(-7.0F, -9.0F, -18.0F, 14.0F, 3.0F, 6.0F).texOffs(330, 140).addBox(-9.5F, -1.0F, -9.0F, 19.0F, 3.0F, 2.0F).texOffs(330, 140).addBox(-9.5F, -2.0F, 0.0F, 19.0F, 3.0F, 2.0F).texOffs(330, 140).addBox(-9.5F, -1.0F, 9.0F, 19.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -20.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("cou", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -4.0F, -9.0F, 10.0F, 10.0F, 10.0F).texOffs(0, 224).addBox(-4.0F, -6.0F, -8.0F, 8.0F, 2.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, -4.0F, -16.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p3 = p2.addOrReplaceChild("tete", CubeListBuilder.create().texOffs(260, 0).addBox(-6.0F, -5.0F, -12.0F, 12.0F, 11.0F, 12.0F).texOffs(260, 0).addBox(-4.0F, -1.0F, -19.0F, 8.0F, 6.0F, 7.0F).texOffs(0, 0).addBox(-6.5F, -4.0F, -13.0F, 13.0F, 2.0F, 4.0F).texOffs(0, 0).addBox(-2.0F, -1.0F, -19.5F, 4.0F, 2.0F, 1.0F).texOffs(330, 100).addBox(-3.5F, 4.0F, -18.5F, 1.0F, 3.0F, 1.0F).texOffs(330, 100).addBox(2.5F, 4.0F, -18.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.0F, 0.0F, 0.0F));
        p3.addOrReplaceChild("corne_g_0", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(-4.0F, -7.0F, -6.0F, 0.349066F, 0.0F, -0.261799F));
        p3.addOrReplaceChild("corne_d_1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(4.0F, -7.0F, -6.0F, 0.349066F, 0.0F, 0.261799F));
        p3.addOrReplaceChild("corne_bout_g_2", CubeListBuilder.create().texOffs(260, 0).addBox(-0.75F, -1.5F, -0.75F, 1.5F, 3.0F, 1.5F),
                PartPose.offsetAndRotation(-4.75F, -9.5F, -8.25F, 0.610865F, 0.0F, -0.436332F));
        p3.addOrReplaceChild("corne_bout_d_3", CubeListBuilder.create().texOffs(260, 0).addBox(-0.75F, -1.5F, -0.75F, 1.5F, 3.0F, 1.5F),
                PartPose.offsetAndRotation(4.75F, -9.5F, -8.25F, 0.610865F, 0.0F, 0.436332F));
        p3.addOrReplaceChild("lueur_oeil_g_4", CubeListBuilder.create().texOffs(330, 200).addBox(-5.2F, -2.5F, -12.4F, 3.0F, 1.5F, 0.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p3.addOrReplaceChild("lueur_oeil_d_5", CubeListBuilder.create().texOffs(330, 200).addBox(2.2F, -2.5F, -12.4F, 3.0F, 1.5F, 0.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p4 = p3.addOrReplaceChild("machoire", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 1.0F, -14.0F, 8.0F, 4.0F, 14.0F).texOffs(330, 100).addBox(-2.5F, -2.0F, -13.5F, 1.0F, 3.0F, 1.0F).texOffs(330, 100).addBox(1.5F, -2.0F, -13.5F, 1.0F, 3.0F, 1.0F).texOffs(0, 224).addBox(-3.0F, 5.0F, -10.0F, 6.0F, 1.5F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 4.0F, -5.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p5 = p1.addOrReplaceChild("patte_av_g", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -1.0F, -3.0F, 6.5F, 11.0F, 6.0F).texOffs(330, 140).addBox(-3.0F, 10.0F, -4.0F, 5.5F, 8.0F, 6.5F).texOffs(330, 100).addBox(-2.5F, 16.0F, -6.0F, 4.5F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-6.0F, 2.0F, -12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p6 = p1.addOrReplaceChild("patte_av_d", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -1.0F, -3.0F, 6.5F, 11.0F, 6.0F).texOffs(330, 140).addBox(-2.5F, 10.0F, -4.0F, 5.5F, 8.0F, 6.5F).texOffs(330, 100).addBox(-2.0F, 16.0F, -6.0F, 4.5F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(6.0F, 2.0F, -12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p7 = p1.addOrReplaceChild("patte_ar_g", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -3.0F, 7.0F, 12.0F, 7.0F).texOffs(330, 140).addBox(-3.0F, 10.0F, -2.0F, 5.5F, 8.0F, 6.0F).texOffs(330, 100).addBox(-2.5F, 16.0F, -4.0F, 4.5F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-6.0F, 2.0F, 10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p8 = p1.addOrReplaceChild("patte_ar_d", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -3.0F, 7.0F, 12.0F, 7.0F).texOffs(330, 140).addBox(-2.5F, 10.0F, -2.0F, 5.5F, 8.0F, 6.0F).texOffs(330, 100).addBox(-2.0F, 16.0F, -4.0F, 4.5F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(6.0F, 2.0F, 10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p9 = p1.addOrReplaceChild("queue", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 4.0F, 10.0F).texOffs(330, 140).addBox(-1.5F, 2.0F, 10.0F, 3.0F, 3.0F, 4.0F).texOffs(0, 224).addBox(-2.5F, 0.5F, 12.0F, 5.0F, 4.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, -2.0F, 16.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p10 = p1.addOrReplaceChild("crete", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p10.addOrReplaceChild("pointe0_6", CubeListBuilder.create().texOffs(260, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, -11.0F, 0.436332F, 0.0F, 0.0F));
        p10.addOrReplaceChild("pointe1_7", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -3.5F, -1.0F, 2.0F, 7.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.5F, -5.0F, 0.436332F, 0.0F, 0.0F));
        p10.addOrReplaceChild("pointe2_8", CubeListBuilder.create().texOffs(260, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 1.0F, 0.436332F, 0.0F, 0.0F));
        p10.addOrReplaceChild("pointe3_9", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -2.0F, 7.0F, 0.436332F, 0.0F, 0.0F));
        PartDefinition p11 = p1.addOrReplaceChild("lianes", CubeListBuilder.create().texOffs(0, 224).addBox(-5.5F, 0.0F, -8.5F, 1.0F, 6.0F, 1.0F).texOffs(0, 224).addBox(4.5F, 0.0F, -2.5F, 1.0F, 5.0F, 1.0F).texOffs(0, 224).addBox(-4.5F, 0.0F, 4.5F, 1.0F, 7.0F, 1.0F).texOffs(0, 224).addBox(3.5F, 0.0F, 8.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p11.addOrReplaceChild("lueur_baie0_10", CubeListBuilder.create().texOffs(330, 200).addBox(-6.0F, 5.0F, -9.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p11.addOrReplaceChild("lueur_baie1_11", CubeListBuilder.create().texOffs(330, 200).addBox(4.0F, 4.0F, -3.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p11.addOrReplaceChild("lueur_baie2_12", CubeListBuilder.create().texOffs(330, 200).addBox(-5.0F, 6.0F, 4.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 512, 512);
    }
}
