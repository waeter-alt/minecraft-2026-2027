// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/casier.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class CasierGeometrie {
    private CasierGeometrie() {
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
        PartDefinition p1 = p0.addOrReplaceChild("caisse", CubeListBuilder.create().texOffs(0, 0).addBox(7.0F, -32.0F, -8.0F, 1.0F, 32.0F, 16.0F).texOffs(34, 0).addBox(-8.0F, -32.0F, -8.0F, 1.0F, 32.0F, 16.0F).texOffs(68, 0).addBox(-7.0F, -32.0F, -8.0F, 14.0F, 1.0F, 16.0F).texOffs(128, 0).addBox(-7.0F, -31.0F, -8.0F, 14.0F, 29.0F, 1.0F).texOffs(158, 0).addBox(-7.0F, -3.0F, -7.0F, 14.0F, 1.0F, 14.0F).texOffs(0, 48).addBox(-7.0F, -2.0F, -8.0F, 14.0F, 2.0F, 15.0F).texOffs(58, 48).addBox(-7.0F, -26.0F, -7.0F, 14.0F, 1.0F, 13.0F).texOffs(112, 48).addBox(-0.5F, -22.0F, -7.0F, 1.0F, 1.0F, 2.0F).texOffs(118, 48).addBox(-0.5F, -23.0F, -5.5F, 1.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("porte", CubeListBuilder.create().texOffs(122, 48).addBox(-14.0F, -15.0F, -1.0F, 14.0F, 29.0F, 1.0F).texOffs(152, 48).addBox(-11.0F, -13.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(170, 48).addBox(-11.0F, -11.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(188, 48).addBox(-11.0F, -9.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(206, 48).addBox(-11.0F, -7.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(224, 48).addBox(-11.0F, 5.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(0, 78).addBox(-11.0F, 7.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(18, 78).addBox(-11.0F, 9.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(36, 78).addBox(-11.0F, 11.0F, 0.0F, 8.0F, 0.5F, 0.5F).texOffs(54, 78).addBox(-9.5F, -5.0F, 0.0F, 5.0F, 2.5F, 0.5F).texOffs(66, 78).addBox(-12.5F, -5.0F, 0.0F, 2.0F, 2.0F, 0.5F).texOffs(72, 78).addBox(-12.5F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F).texOffs(76, 78).addBox(-12.5F, 3.0F, 0.0F, 1.0F, 1.0F, 1.0F).texOffs(80, 78).addBox(-12.5F, -1.5F, 1.0F, 1.0F, 6.0F, 1.0F).texOffs(84, 78).addBox(-12.25F, -3.0F, 0.5F, 1.5F, 1.5F, 0.5F),
                PartPose.offsetAndRotation(7.0F, -16.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 256, 256);
    }
}
