// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/porte_station.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class PorteStationGeometrie {
    private PorteStationGeometrie() {
    }

    public static final String[] LUEURS = {
        "lueur_feu_g_0",
        "lueur_feu_d_1",
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
        PartDefinition p0 = maillage.getRoot().addOrReplaceChild("racine", CubeListBuilder.create().texOffs(0, 208).addBox(-64.0F, -4.0F, -7.0F, 128.0F, 4.0F, 14.0F).texOffs(0, 0).addBox(56.0F, -128.0F, -7.0F, 8.0F, 124.0F, 14.0F).texOffs(44, 0).addBox(-64.0F, -128.0F, -7.0F, 8.0F, 124.0F, 14.0F).texOffs(0, 186).addBox(-56.0F, -128.0F, -7.0F, 112.0F, 8.0F, 14.0F).texOffs(364, 0).addBox(55.0F, -84.0F, -7.5F, 10.0F, 40.0F, 15.0F).texOffs(414, 0).addBox(-65.0F, -84.0F, -7.5F, 10.0F, 40.0F, 15.0F),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p0.addOrReplaceChild("lueur_feu_g_0", CubeListBuilder.create().texOffs(396, 138).addBox(26.0F, -116.0F, -7.4F, 10.0F, 8.0F, 14.8F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p0.addOrReplaceChild("lueur_feu_d_1", CubeListBuilder.create().texOffs(446, 138).addBox(-36.0F, -116.0F, -7.4F, 10.0F, 8.0F, 14.8F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p1 = p0.addOrReplaceChild("dalle", CubeListBuilder.create().texOffs(116, 0).addBox(2.0F, -62.0F, -4.0F, 54.0F, 58.0F, 8.0F).texOffs(240, 0).addBox(-56.0F, -62.0F, -4.0F, 54.0F, 58.0F, 8.0F).texOffs(0, 138).addBox(2.0F, -106.0F, -4.0F, 54.0F, 40.0F, 8.0F).texOffs(124, 138).addBox(-56.0F, -106.0F, -4.0F, 54.0F, 40.0F, 8.0F).texOffs(88, 0).addBox(-2.0F, -106.0F, -5.0F, 4.0F, 102.0F, 10.0F).texOffs(0, 226).addBox(-56.0F, -66.0F, -5.0F, 112.0F, 4.0F, 10.0F).texOffs(244, 226).addBox(-56.0F, -110.0F, -5.0F, 112.0F, 4.0F, 10.0F).texOffs(248, 138).addBox(-14.0F, -78.0F, -6.0F, 28.0F, 28.0F, 12.0F).texOffs(328, 138).addBox(-10.0F, -74.0F, -7.0F, 20.0F, 20.0F, 14.0F).texOffs(0, 240).addBox(42.0F, -16.0F, -5.0F, 4.0F, 4.0F, 10.0F).texOffs(28, 240).addBox(18.0F, -16.0F, -5.0F, 4.0F, 4.0F, 10.0F).texOffs(56, 240).addBox(-22.0F, -16.0F, -5.0F, 4.0F, 4.0F, 10.0F).texOffs(84, 240).addBox(-46.0F, -16.0F, -5.0F, 4.0F, 4.0F, 10.0F).texOffs(112, 240).addBox(42.0F, -100.0F, -5.0F, 4.0F, 4.0F, 10.0F).texOffs(140, 240).addBox(18.0F, -100.0F, -5.0F, 4.0F, 4.0F, 10.0F).texOffs(168, 240).addBox(-22.0F, -100.0F, -5.0F, 4.0F, 4.0F, 10.0F).texOffs(196, 240).addBox(-46.0F, -100.0F, -5.0F, 4.0F, 4.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 512, 512);
    }
}
