// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/valve_station.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class ValveStationGeometrie {
    private ValveStationGeometrie() {
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
        PartDefinition p1 = p0.addOrReplaceChild("socle", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -2.0F, -7.0F, 14.0F, 2.0F, 14.0F).texOffs(122, 25).addBox(5.0F, -2.5F, -6.0F, 1.0F, 0.5F, 1.0F).texOffs(0, 29).addBox(-6.0F, -2.5F, -6.0F, 1.0F, 0.5F, 1.0F).texOffs(4, 29).addBox(5.0F, -2.5F, 5.0F, 1.0F, 0.5F, 1.0F).texOffs(8, 29).addBox(-6.0F, -2.5F, 5.0F, 1.0F, 0.5F, 1.0F).texOffs(56, 0).addBox(-2.5F, -11.0F, -2.5F, 5.0F, 9.0F, 5.0F).texOffs(100, 0).addBox(-3.5F, -5.5F, -3.5F, 7.0F, 1.5F, 7.0F).texOffs(0, 16).addBox(-3.5F, -9.5F, -3.5F, 7.0F, 1.5F, 7.0F).texOffs(28, 16).addBox(-2.0F, -8.0F, -7.0F, 4.0F, 2.5F, 4.5F).texOffs(98, 16).addBox(-2.75F, -8.5F, -7.0F, 5.5F, 3.5F, 1.0F).texOffs(120, 16).addBox(-0.75F, -12.5F, -0.75F, 1.5F, 1.5F, 1.5F).texOffs(90, 16).addBox(-4.5F, -5.5F, -1.5F, 1.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p1.addOrReplaceChild("lueur_voyant_0", CubeListBuilder.create().texOffs(0, 25).addBox(-5.0F, -5.0F, -1.0F, 0.5F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p1.addOrReplaceChild("roue", CubeListBuilder.create().texOffs(46, 16).addBox(-1.75F, -1.5F, -1.75F, 3.5F, 2.5F, 3.5F).texOffs(90, 25).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 1.0F, 2.0F).texOffs(6, 25).addBox(-2.5F, -1.3F, -6.0F, 5.0F, 1.6F, 1.3F).texOffs(20, 25).addBox(-2.5F, -1.3F, 4.7F, 5.0F, 1.6F, 1.3F).texOffs(62, 16).addBox(-6.0F, -1.3F, -2.5F, 1.3F, 1.6F, 5.0F).texOffs(76, 16).addBox(4.7F, -1.3F, -2.5F, 1.3F, 1.6F, 5.0F).texOffs(98, 25).addBox(-5.0F, -0.9F, -0.6F, 10.0F, 0.8F, 1.2F).texOffs(76, 0).addBox(-0.6F, -0.9F, -5.0F, 1.2F, 0.8F, 10.0F).texOffs(112, 16).addBox(-5.4F, -3.7F, -0.7F, 1.2F, 2.4F, 1.4F),
                PartPose.offsetAndRotation(0.0F, -12.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("coin_ne_1", CubeListBuilder.create().texOffs(34, 25).addBox(-2.1F, -0.8F, -0.65F, 4.2F, 1.6F, 1.3F),
                PartPose.offsetAndRotation(-3.9F, -0.5F, -3.9F, 0.0F, -0.785398F, 0.0F));
        p2.addOrReplaceChild("coin_se_2", CubeListBuilder.create().texOffs(48, 25).addBox(-2.1F, -0.8F, -0.65F, 4.2F, 1.6F, 1.3F),
                PartPose.offsetAndRotation(-3.9F, -0.5F, 3.9F, 0.0F, 0.785398F, 0.0F));
        p2.addOrReplaceChild("coin_no_3", CubeListBuilder.create().texOffs(62, 25).addBox(-2.1F, -0.8F, -0.65F, 4.2F, 1.6F, 1.3F),
                PartPose.offsetAndRotation(3.9F, -0.5F, -3.9F, 0.0F, 0.785398F, 0.0F));
        p2.addOrReplaceChild("coin_so_4", CubeListBuilder.create().texOffs(76, 25).addBox(-2.1F, -0.8F, -0.65F, 4.2F, 1.6F, 1.3F),
                PartPose.offsetAndRotation(3.9F, -0.5F, 3.9F, 0.0F, -0.785398F, 0.0F));
        return LayerDefinition.create(maillage, 128, 128);
    }
}
