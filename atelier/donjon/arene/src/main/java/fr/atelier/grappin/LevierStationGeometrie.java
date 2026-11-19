// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/levier_station.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class LevierStationGeometrie {
    private LevierStationGeometrie() {
    }

    public static final String[] LUEURS = {
        "socle/lueur_voyant_0",
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
        PartDefinition p1 = p0.addOrReplaceChild("socle", CubeListBuilder.create().texOffs(8, 0).addBox(-7.0F, -2.0F, -7.0F, 14.0F, 2.0F, 14.0F).texOffs(78, 34).addBox(5.0F, -2.5F, -6.0F, 1.0F, 0.5F, 1.0F).texOffs(82, 34).addBox(-6.0F, -2.5F, -6.0F, 1.0F, 0.5F, 1.0F).texOffs(86, 34).addBox(5.0F, -2.5F, 5.0F, 1.0F, 0.5F, 1.0F).texOffs(90, 34).addBox(-6.0F, -2.5F, 5.0F, 1.0F, 0.5F, 1.0F).texOffs(64, 0).addBox(-3.5F, -8.0F, -5.0F, 7.0F, 6.0F, 10.0F).texOffs(74, 20).addBox(-4.0F, -6.5F, -3.0F, 0.5F, 3.5F, 6.0F).texOffs(98, 0).addBox(2.5F, -10.0F, -6.0F, 1.0F, 2.0F, 12.0F).texOffs(26, 20).addBox(2.5F, -11.5F, -5.0F, 1.0F, 1.5F, 10.0F).texOffs(88, 20).addBox(2.5F, -12.5F, -3.5F, 1.0F, 1.0F, 7.0F).texOffs(0, 20).addBox(-3.5F, -10.0F, -6.0F, 1.0F, 2.0F, 12.0F).texOffs(48, 20).addBox(-3.5F, -11.5F, -5.0F, 1.0F, 1.5F, 10.0F).texOffs(104, 20).addBox(-3.5F, -12.5F, -3.5F, 1.0F, 1.0F, 7.0F).texOffs(36, 34).addBox(-4.5F, -10.0F, -1.0F, 9.0F, 2.0F, 2.0F).texOffs(58, 34).addBox(-1.5F, -7.0F, -5.2F, 3.0F, 3.0F, 0.2F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p1.addOrReplaceChild("lueur_voyant_0", CubeListBuilder.create().texOffs(72, 34).addBox(-1.0F, -6.5F, -5.5F, 2.0F, 2.0F, 0.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("manche", CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, -18.0F, -0.75F, 1.5F, 18.0F, 1.5F).texOffs(12, 34).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 3.0F, 3.0F).texOffs(0, 34).addBox(-1.25F, -18.0F, -1.25F, 2.5F, 5.0F, 2.5F).texOffs(24, 34).addBox(-1.5F, -20.5F, -1.5F, 3.0F, 2.5F, 3.0F).texOffs(70, 20).addBox(-0.25F, -12.0F, 1.0F, 0.5F, 10.0F, 0.5F).texOffs(66, 34).addBox(-0.5F, -13.0F, 1.0F, 1.0F, 1.5F, 1.5F),
                PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 128, 128);
    }
}
