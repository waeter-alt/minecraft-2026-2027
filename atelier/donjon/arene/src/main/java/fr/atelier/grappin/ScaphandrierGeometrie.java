// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/scaphandrier.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class ScaphandrierGeometrie {
    private ScaphandrierGeometrie() {
    }

    public static final String[] LUEURS = {
        "corps/casque/lueur_hublot_0",
    };
    public static final String[] ROUGES = {
    };
    public static final String[] MASQUES = {
    };
    public static final String[][] PETALES = {
    };
    public static final String TETE = "";

    public static LayerDefinition creerCouche() {
        MeshDefinition maillage = new MeshDefinition();
        PartDefinition p0 = maillage.getRoot().addOrReplaceChild("racine", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p1 = p0.addOrReplaceChild("corps", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -14.0F, -4.5F, 14.0F, 14.0F, 9.0F).texOffs(0, 53).addBox(-7.5F, -3.0F, -5.0F, 15.0F, 3.0F, 10.0F).texOffs(94, 78).addBox(-5.0F, -12.0F, -5.5F, 10.0F, 8.0F, 1.0F).texOffs(70, 97).addBox(2.5F, -11.5F, -6.5F, 2.0F, 2.0F, 1.0F).texOffs(76, 97).addBox(-4.5F, -11.5F, -6.5F, 2.0F, 2.0F, 1.0F).texOffs(82, 97).addBox(2.5F, -6.5F, -6.5F, 2.0F, 2.0F, 1.0F).texOffs(88, 97).addBox(-4.5F, -6.5F, -6.5F, 2.0F, 2.0F, 1.0F).texOffs(18, 23).addBox(-8.0F, -15.5F, -6.0F, 16.0F, 2.5F, 12.0F).texOffs(94, 97).addBox(5.75F, -15.0F, -6.75F, 1.5F, 1.5F, 0.75F).texOffs(100, 97).addBox(1.25F, -15.0F, -6.75F, 1.5F, 1.5F, 0.75F).texOffs(106, 97).addBox(-2.75F, -15.0F, -6.75F, 1.5F, 1.5F, 0.75F).texOffs(112, 97).addBox(-7.25F, -15.0F, -6.75F, 1.5F, 1.5F, 0.75F).texOffs(86, 23).addBox(-4.0F, -13.0F, 4.5F, 8.0F, 10.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("casque", CubeListBuilder.create().texOffs(46, 0).addBox(-5.0F, -11.5F, -5.0F, 10.0F, 10.5F, 10.0F).texOffs(0, 88).addBox(-3.5F, -13.0F, -3.5F, 7.0F, 1.5F, 7.0F).texOffs(102, 0).addBox(-6.5F, -10.5F, -3.5F, 1.5F, 8.5F, 7.0F).texOffs(0, 23).addBox(5.0F, -10.5F, -3.5F, 1.5F, 8.5F, 7.0F).texOffs(52, 66).addBox(-3.5F, -10.5F, 5.0F, 7.0F, 8.5F, 1.5F).texOffs(30, 78).addBox(-4.5F, -10.5F, -6.0F, 9.0F, 9.0F, 1.0F).texOffs(62, 97).addBox(-1.0F, -14.0F, -1.0F, 2.0F, 1.5F, 2.0F).texOffs(0, 104).addBox(-4.5F, -10.5F, -7.0F, 9.0F, 1.0F, 1.0F).texOffs(20, 104).addBox(-4.5F, -2.5F, -7.0F, 9.0F, 1.0F, 1.0F).texOffs(28, 88).addBox(-4.5F, -9.5F, -7.0F, 1.0F, 7.0F, 1.0F).texOffs(32, 88).addBox(3.5F, -9.5F, -7.0F, 1.0F, 7.0F, 1.0F).texOffs(36, 88).addBox(-0.4F, -9.5F, -6.9F, 0.8F, 7.0F, 0.3F).texOffs(104, 88).addBox(-7.0F, -8.0F, -2.0F, 0.5F, 4.0F, 4.0F).texOffs(114, 88).addBox(6.5F, -8.0F, -2.0F, 0.5F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("lueur_hublot_0", CubeListBuilder.create().texOffs(40, 88).addBox(-3.5F, -9.5F, -6.5F, 7.0F, 7.0F, 0.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p3 = p2.addOrReplaceChild("fissure1", CubeListBuilder.create().texOffs(56, 88).addBox(-3.4F, -9.4F, -6.6F, 6.8F, 6.8F, 0.05F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p4 = p2.addOrReplaceChild("fissure2", CubeListBuilder.create().texOffs(72, 88).addBox(-3.4F, -9.4F, -6.65F, 6.8F, 6.8F, 0.05F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p5 = p2.addOrReplaceChild("hublot_brise", CubeListBuilder.create().texOffs(88, 88).addBox(-3.5F, -9.5F, -6.55F, 7.0F, 7.0F, 0.45F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p6 = p1.addOrReplaceChild("bras_g", CubeListBuilder.create().texOffs(0, 39).addBox(-3.5F, -2.0F, -3.5F, 4.5F, 7.0F, 7.0F).texOffs(50, 53).addBox(-3.0F, 5.0F, -2.5F, 3.5F, 8.0F, 5.0F).texOffs(50, 78).addBox(-3.5F, 13.0F, -3.0F, 4.5F, 4.0F, 6.0F),
                PartPose.offsetAndRotation(-8.0F, -13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p7 = p6.addOrReplaceChild("cle", CubeListBuilder.create().texOffs(86, 53).addBox(-0.75F, -0.5F, -12.0F, 1.5F, 1.5F, 11.0F).texOffs(44, 97).addBox(-2.0F, 0.5F, -15.0F, 4.0F, 1.5F, 3.0F).texOffs(12, 97).addBox(-2.0F, -2.0F, -15.0F, 1.5F, 2.5F, 3.0F).texOffs(22, 97).addBox(0.5F, -2.0F, -15.0F, 1.5F, 2.5F, 3.0F),
                PartPose.offsetAndRotation(-1.0F, 15.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p8 = p1.addOrReplaceChild("bras_d", CubeListBuilder.create().texOffs(24, 39).addBox(-1.0F, -2.0F, -3.5F, 4.5F, 7.0F, 7.0F).texOffs(68, 53).addBox(-0.5F, 5.0F, -2.5F, 3.5F, 8.0F, 5.0F).texOffs(72, 78).addBox(-1.0F, 13.0F, -3.0F, 4.5F, 4.0F, 6.0F),
                PartPose.offsetAndRotation(8.0F, -13.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p9 = p8.addOrReplaceChild("harpon", CubeListBuilder.create().texOffs(74, 23).addBox(-1.25F, -12.0F, -1.5F, 2.5F, 12.0F, 3.0F).texOffs(0, 97).addBox(-2.25F, -10.0F, 1.5F, 3.0F, 4.0F, 2.5F).texOffs(58, 97).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.5F, 1.0F).texOffs(118, 97).addBox(-1.5F, 0.0F, -0.25F, 1.0F, 1.5F, 0.5F).texOffs(122, 97).addBox(0.5F, 0.0F, -0.25F, 1.0F, 1.5F, 0.5F),
                PartPose.offsetAndRotation(3.75F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p10 = p1.addOrReplaceChild("tuyaux", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -13.0F, 5.0F, 0.0F, 0.0F, 0.0F));
        p10.addOrReplaceChild("tuyau_g_1", CubeListBuilder.create().texOffs(94, 0).addBox(-0.75F, -7.0F, -0.75F, 1.5F, 14.0F, 1.5F),
                PartPose.offsetAndRotation(-2.75F, 7.0F, 2.75F, 0.0F, 0.0F, -0.392699F));
        p10.addOrReplaceChild("tuyau_d_2", CubeListBuilder.create().texOffs(86, 0).addBox(-0.75F, -7.5F, -0.75F, 1.5F, 15.0F, 1.5F),
                PartPose.offsetAndRotation(2.75F, 8.5F, 2.75F, 0.0F, 0.0F, 0.392699F));
        p10.addOrReplaceChild("tuyau_bout_3", CubeListBuilder.create().texOffs(32, 97).addBox(-1.25F, -1.0F, -1.25F, 2.5F, 2.0F, 2.5F),
                PartPose.offsetAndRotation(2.75F, 16.0F, 2.75F, 0.0F, 0.0F, 0.392699F));
        PartDefinition p11 = p0.addOrReplaceChild("jambe_g", CubeListBuilder.create().texOffs(48, 39).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F).texOffs(0, 66).addBox(-3.0F, 8.0F, -3.5F, 6.0F, 4.5F, 6.5F).texOffs(70, 66).addBox(-3.5F, 12.5F, -4.5F, 7.0F, 1.5F, 8.0F),
                PartPose.offsetAndRotation(-3.5F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p12 = p0.addOrReplaceChild("jambe_d", CubeListBuilder.create().texOffs(68, 39).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F).texOffs(26, 66).addBox(-3.0F, 8.0F, -3.5F, 6.0F, 4.5F, 6.5F).texOffs(0, 78).addBox(-3.5F, 12.5F, -4.5F, 7.0F, 1.5F, 8.0F),
                PartPose.offsetAndRotation(3.5F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 128, 128);
    }
}
