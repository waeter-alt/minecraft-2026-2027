// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/paravoile/paravoile_voile.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class ParavoileGeometrie {
    private ParavoileGeometrie() {
    }

    public static final String[] LUEURS = {
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
        PartDefinition p1 = p0.addOrReplaceChild("voile", CubeListBuilder.create().texOffs(44, 22).addBox(-2.9F, -28.5F, -8.0F, 5.8F, 3.0F, 16.0F).texOffs(44, 41).addBox(3.75F, -4.0F, -0.75F, 1.5F, 4.0F, 1.5F).texOffs(52, 41).addBox(-5.25F, -4.0F, -0.75F, 1.5F, 4.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, -25.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("aile_g", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.9F, -27.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("caisson0_0", CubeListBuilder.create().texOffs(104, 0).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(16.421765F, 8.602604F, 0.0F, 0.0F, 0.0F, 0.837758F));
        p2.addOrReplaceChild("caisson1_1", CubeListBuilder.create().texOffs(148, 0).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(12.382417F, 4.965558F, 0.0F, 0.0F, 0.0F, 0.628319F));
        p2.addOrReplaceChild("caisson2_2", CubeListBuilder.create().texOffs(192, 0).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(7.675153F, 2.247818F, 0.0F, 0.0F, 0.0F, 0.418879F));
        p2.addOrReplaceChild("caisson3_3", CubeListBuilder.create().texOffs(0, 22).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(2.505704F, 0.568162F, 0.0F, 0.0F, 0.0F, 0.20944F));
        p2.addOrReplaceChild("suspente0_av_4", CubeListBuilder.create().texOffs(32, 0).addBox(-0.25F, -9.582202F, -0.5F, 0.5F, 19.164404F, 1.0F),
                PartPose.offsetAndRotation(8.453524F, 16.30315F, -4.5F, 0.0F, 0.0F, 0.79696F));
        p2.addOrReplaceChild("suspente0_ar_5", CubeListBuilder.create().texOffs(36, 0).addBox(-0.25F, -9.582202F, -0.5F, 0.5F, 19.164404F, 1.0F),
                PartPose.offsetAndRotation(8.453524F, 16.30315F, 4.5F, 0.0F, 0.0F, 0.79696F));
        p2.addOrReplaceChild("suspente2_av_6", CubeListBuilder.create().texOffs(0, 0).addBox(-0.25F, -10.068806F, -0.5F, 0.5F, 20.137611F, 1.0F),
                PartPose.offsetAndRotation(4.332524F, 13.309068F, -4.5F, 0.0F, 0.0F, 0.274832F));
        p2.addOrReplaceChild("suspente2_ar_7", CubeListBuilder.create().texOffs(4, 0).addBox(-0.25F, -10.068806F, -0.5F, 0.5F, 20.137611F, 1.0F),
                PartPose.offsetAndRotation(4.332524F, 13.309068F, 4.5F, 0.0F, 0.0F, 0.274832F));
        p2.addOrReplaceChild("suspente3_av_8", CubeListBuilder.create().texOffs(8, 0).addBox(-0.25F, -10.486512F, -0.5F, 0.5F, 20.973025F, 1.0F),
                PartPose.offsetAndRotation(1.896918F, 12.517692F, -4.5F, 0.0F, 0.0F, 0.028318F));
        p2.addOrReplaceChild("suspente3_ar_9", CubeListBuilder.create().texOffs(12, 0).addBox(-0.25F, -10.486512F, -0.5F, 0.5F, 20.973025F, 1.0F),
                PartPose.offsetAndRotation(1.896918F, 12.517692F, 4.5F, 0.0F, 0.0F, 0.028318F));
        PartDefinition p3 = p2.addOrReplaceChild("stabilo_g", CubeListBuilder.create(),
                PartPose.offsetAndRotation(17.194274F, 10.300632F, 0.0F, 0.0F, 0.0F, 0.0F));
        p3.addOrReplaceChild("stabilo_g_10", CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 8.0F, 12.0F),
                PartPose.offsetAndRotation(0.87249F, 2.083392F, 0.0F, 0.0F, 0.0F, 0.994838F));
        PartDefinition p4 = p1.addOrReplaceChild("aile_d", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.9F, -27.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p4.addOrReplaceChild("caisson5_11", CubeListBuilder.create().texOffs(88, 22).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(-2.505704F, 0.568162F, 0.0F, 0.0F, 0.0F, -0.20944F));
        p4.addOrReplaceChild("caisson6_12", CubeListBuilder.create().texOffs(132, 22).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(-7.675153F, 2.247818F, 0.0F, 0.0F, 0.0F, -0.418879F));
        p4.addOrReplaceChild("caisson7_13", CubeListBuilder.create().texOffs(176, 22).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(-12.382417F, 4.965558F, 0.0F, 0.0F, 0.0F, -0.628319F));
        p4.addOrReplaceChild("caisson8_14", CubeListBuilder.create().texOffs(0, 41).addBox(-2.9F, -1.5F, -8.0F, 5.8F, 3.0F, 16.0F),
                PartPose.offsetAndRotation(-16.421765F, 8.602604F, 0.0F, 0.0F, 0.0F, -0.837758F));
        p4.addOrReplaceChild("suspente5_av_15", CubeListBuilder.create().texOffs(16, 0).addBox(-0.25F, -10.486512F, -0.5F, 0.5F, 20.973025F, 1.0F),
                PartPose.offsetAndRotation(-1.896918F, 12.517692F, -4.5F, 0.0F, 0.0F, -0.028318F));
        p4.addOrReplaceChild("suspente5_ar_16", CubeListBuilder.create().texOffs(20, 0).addBox(-0.25F, -10.486512F, -0.5F, 0.5F, 20.973025F, 1.0F),
                PartPose.offsetAndRotation(-1.896918F, 12.517692F, 4.5F, 0.0F, 0.0F, -0.028318F));
        p4.addOrReplaceChild("suspente6_av_17", CubeListBuilder.create().texOffs(24, 0).addBox(-0.25F, -10.068806F, -0.5F, 0.5F, 20.137611F, 1.0F),
                PartPose.offsetAndRotation(-4.332524F, 13.309068F, -4.5F, 0.0F, 0.0F, -0.274832F));
        p4.addOrReplaceChild("suspente6_ar_18", CubeListBuilder.create().texOffs(28, 0).addBox(-0.25F, -10.068806F, -0.5F, 0.5F, 20.137611F, 1.0F),
                PartPose.offsetAndRotation(-4.332524F, 13.309068F, 4.5F, 0.0F, 0.0F, -0.274832F));
        p4.addOrReplaceChild("suspente8_av_19", CubeListBuilder.create().texOffs(40, 0).addBox(-0.25F, -9.582202F, -0.5F, 0.5F, 19.164404F, 1.0F),
                PartPose.offsetAndRotation(-8.453524F, 16.30315F, -4.5F, 0.0F, 0.0F, -0.79696F));
        p4.addOrReplaceChild("suspente8_ar_20", CubeListBuilder.create().texOffs(44, 0).addBox(-0.25F, -9.582202F, -0.5F, 0.5F, 19.164404F, 1.0F),
                PartPose.offsetAndRotation(-8.453524F, 16.30315F, 4.5F, 0.0F, 0.0F, -0.79696F));
        PartDefinition p5 = p4.addOrReplaceChild("stabilo_d", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-17.194274F, 10.300632F, 0.0F, 0.0F, 0.0F, 0.0F));
        p5.addOrReplaceChild("stabilo_d_21", CubeListBuilder.create().texOffs(76, 0).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 8.0F, 12.0F),
                PartPose.offsetAndRotation(-0.87249F, 2.083392F, 0.0F, 0.0F, 0.0F, -0.994838F));
        return LayerDefinition.create(maillage, 256, 256);
    }
}
