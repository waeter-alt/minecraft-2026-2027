// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/donjon/porte_donjon.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class PorteGeometrie {
    private PorteGeometrie() {
    }

    public static final String[] LUEURS = {
        "dalle/sceau/lueur_gemme_8",
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
        PartDefinition p1 = p0.addOrReplaceChild("dalle", CubeListBuilder.create().texOffs(0, 0).addBox(56.0F, -97.0F, -6.0F, 8.0F, 93.0F, 12.0F).texOffs(40, 0).addBox(48.0F, -102.0F, -4.0F, 8.0F, 98.0F, 8.0F).texOffs(72, 0).addBox(48.0F, -108.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(112, 0).addBox(40.0F, -109.0F, -4.0F, 8.0F, 105.0F, 8.0F).texOffs(144, 0).addBox(40.0F, -115.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(184, 0).addBox(32.0F, -114.0F, -4.0F, 8.0F, 110.0F, 8.0F).texOffs(216, 0).addBox(32.0F, -120.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(256, 0).addBox(24.0F, -117.0F, -4.0F, 8.0F, 113.0F, 8.0F).texOffs(288, 0).addBox(24.0F, -123.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(328, 0).addBox(16.0F, -120.0F, -4.0F, 8.0F, 116.0F, 8.0F).texOffs(360, 0).addBox(16.0F, -126.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(400, 0).addBox(8.0F, -121.0F, -4.0F, 8.0F, 117.0F, 8.0F).texOffs(432, 0).addBox(8.0F, -127.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(472, 0).addBox(0.0F, -122.0F, -4.0F, 8.0F, 118.0F, 8.0F).texOffs(0, 126).addBox(0.0F, -128.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(40, 126).addBox(-8.0F, -122.0F, -4.0F, 8.0F, 118.0F, 8.0F).texOffs(72, 126).addBox(-8.0F, -128.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(112, 126).addBox(-16.0F, -121.0F, -4.0F, 8.0F, 117.0F, 8.0F).texOffs(144, 126).addBox(-16.0F, -127.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(184, 126).addBox(-24.0F, -120.0F, -4.0F, 8.0F, 116.0F, 8.0F).texOffs(216, 126).addBox(-24.0F, -126.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(256, 126).addBox(-32.0F, -117.0F, -4.0F, 8.0F, 113.0F, 8.0F).texOffs(288, 126).addBox(-32.0F, -123.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(328, 126).addBox(-40.0F, -114.0F, -4.0F, 8.0F, 110.0F, 8.0F).texOffs(360, 126).addBox(-40.0F, -120.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(400, 126).addBox(-48.0F, -109.0F, -4.0F, 8.0F, 105.0F, 8.0F).texOffs(432, 126).addBox(-48.0F, -115.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(472, 126).addBox(-56.0F, -102.0F, -4.0F, 8.0F, 98.0F, 8.0F).texOffs(0, 252).addBox(-56.0F, -108.0F, -6.0F, 8.0F, 6.0F, 12.0F).texOffs(40, 252).addBox(-64.0F, -97.0F, -6.0F, 8.0F, 93.0F, 12.0F).texOffs(80, 252).addBox(-1.0F, -122.0F, -5.0F, 2.0F, 118.0F, 10.0F).texOffs(104, 252).addBox(-64.0F, -4.0F, -6.0F, 128.0F, 4.0F, 12.0F).texOffs(0, 380).addBox(-56.0F, -28.0F, -5.0F, 112.0F, 6.0F, 10.0F).texOffs(244, 380).addBox(46.5F, -26.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(252, 380).addBox(46.5F, -26.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(260, 380).addBox(30.5F, -26.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(268, 380).addBox(30.5F, -26.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(276, 380).addBox(14.5F, -26.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(284, 380).addBox(14.5F, -26.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(292, 380).addBox(-17.5F, -26.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(300, 380).addBox(-17.5F, -26.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(308, 380).addBox(-33.5F, -26.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(316, 380).addBox(-33.5F, -26.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(324, 380).addBox(-49.5F, -26.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(332, 380).addBox(-49.5F, -26.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(0, 396).addBox(-56.0F, -66.0F, -5.0F, 112.0F, 6.0F, 10.0F).texOffs(244, 396).addBox(46.5F, -64.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(252, 396).addBox(46.5F, -64.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(260, 396).addBox(30.5F, -64.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(268, 396).addBox(30.5F, -64.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(276, 396).addBox(14.5F, -64.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(284, 396).addBox(14.5F, -64.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(292, 396).addBox(-17.5F, -64.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(300, 396).addBox(-17.5F, -64.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(308, 396).addBox(-33.5F, -64.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(316, 396).addBox(-33.5F, -64.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(324, 396).addBox(-49.5F, -64.5F, -6.0F, 3.0F, 3.0F, 1.0F).texOffs(332, 396).addBox(-49.5F, -64.5F, 5.0F, 3.0F, 3.0F, 1.0F).texOffs(436, 396).addBox(30.0F, -7.0F, -5.0F, 26.0F, 3.0F, 10.0F).texOffs(0, 452).addBox(-50.0F, -6.0F, -5.0F, 30.0F, 2.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p1.addOrReplaceChild("racine0_0", CubeListBuilder.create().texOffs(340, 396).addBox(-2.0F, -22.0F, -1.0F, 4.0F, 44.0F, 2.0F),
                PartPose.offsetAndRotation(48.0F, -30.0F, -5.0F, 0.0F, 0.0F, 0.392699F));
        p1.addOrReplaceChild("racine0_1", CubeListBuilder.create().texOffs(352, 396).addBox(-2.0F, -22.0F, -1.0F, 4.0F, 44.0F, 2.0F),
                PartPose.offsetAndRotation(48.0F, -30.0F, 5.0F, 0.0F, 0.0F, 0.392699F));
        p1.addOrReplaceChild("racine1_2", CubeListBuilder.create().texOffs(364, 396).addBox(-2.0F, -27.0F, -1.0F, 4.0F, 54.0F, 2.0F),
                PartPose.offsetAndRotation(-42.0F, -35.0F, -5.0F, 0.0F, 0.0F, -0.392699F));
        p1.addOrReplaceChild("racine1_3", CubeListBuilder.create().texOffs(376, 396).addBox(-2.0F, -27.0F, -1.0F, 4.0F, 54.0F, 2.0F),
                PartPose.offsetAndRotation(-42.0F, -35.0F, 5.0F, 0.0F, 0.0F, -0.392699F));
        p1.addOrReplaceChild("racine2_4", CubeListBuilder.create().texOffs(388, 396).addBox(-2.0F, -22.0F, -1.0F, 4.0F, 44.0F, 2.0F),
                PartPose.offsetAndRotation(28.0F, -30.0F, -5.0F, 0.0F, 0.0F, -0.392699F));
        p1.addOrReplaceChild("racine2_5", CubeListBuilder.create().texOffs(400, 396).addBox(-2.0F, -22.0F, -1.0F, 4.0F, 44.0F, 2.0F),
                PartPose.offsetAndRotation(28.0F, -30.0F, 5.0F, 0.0F, 0.0F, -0.392699F));
        p1.addOrReplaceChild("racine3_6", CubeListBuilder.create().texOffs(412, 396).addBox(-2.0F, -27.0F, -1.0F, 4.0F, 54.0F, 2.0F),
                PartPose.offsetAndRotation(-26.0F, -35.0F, -5.0F, 0.0F, 0.0F, 0.392699F));
        p1.addOrReplaceChild("racine3_7", CubeListBuilder.create().texOffs(424, 396).addBox(-2.0F, -27.0F, -1.0F, 4.0F, 54.0F, 2.0F),
                PartPose.offsetAndRotation(-26.0F, -35.0F, 5.0F, 0.0F, 0.0F, 0.392699F));
        PartDefinition p2 = p1.addOrReplaceChild("sceau", CubeListBuilder.create().texOffs(80, 452).addBox(-16.0F, -18.0F, -7.0F, 32.0F, 4.0F, 14.0F).texOffs(172, 452).addBox(-16.0F, 14.0F, -7.0F, 32.0F, 4.0F, 14.0F).texOffs(264, 452).addBox(12.0F, -14.0F, -7.0F, 4.0F, 28.0F, 14.0F).texOffs(300, 452).addBox(-16.0F, -14.0F, -7.0F, 4.0F, 28.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, -96.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("lueur_gemme_8", CubeListBuilder.create().texOffs(336, 452).addBox(-9.0F, -9.0F, -8.0F, 18.0F, 18.0F, 16.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.785398F));
        return LayerDefinition.create(maillage, 512, 512);
    }
}
