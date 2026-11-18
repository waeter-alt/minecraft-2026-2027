// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/donjon/levier_racine.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class LevierGeometrie {
    private LevierGeometrie() {
    }

    public static final String[] LUEURS = {
        "socle/manche/cristal/lueur_cristal_1",
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
        PartDefinition p1 = p0.addOrReplaceChild("socle", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -4.0F, -7.0F, 14.0F, 4.0F, 14.0F).texOffs(68, 0).addBox(-5.0F, -12.0F, -4.0F, 10.0F, 8.0F, 8.0F).texOffs(0, 18).addBox(-6.0F, -14.0F, -5.0F, 12.0F, 2.0F, 10.0F).texOffs(16, 30).addBox(3.0F, -5.0F, 3.0F, 4.0F, 1.0F, 4.0F).texOffs(72, 18).addBox(7.0F, -2.0F, -2.0F, 3.0F, 2.0F, 4.0F).texOffs(86, 18).addBox(-10.0F, -2.0F, -3.0F, 3.0F, 2.0F, 4.0F).texOffs(32, 30).addBox(-2.0F, -2.0F, -10.0F, 4.0F, 2.0F, 3.0F).texOffs(46, 30).addBox(-3.0F, -2.0F, 7.0F, 4.0F, 2.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p1.addOrReplaceChild("racine_pilier_0", CubeListBuilder.create().texOffs(44, 18).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 10.0F, 2.0F),
                PartPose.offsetAndRotation(-5.0F, -7.0F, -4.0F, 0.0F, 0.0F, -0.392699F));
        PartDefinition p2 = p1.addOrReplaceChild("cliquet", CubeListBuilder.create().texOffs(104, 0).addBox(-2.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F).texOffs(96, 30).addBox(-2.0F, -0.5F, -5.5F, 2.0F, 1.0F, 1.0F).texOffs(102, 30).addBox(-2.0F, -0.5F, 3.5F, 2.0F, 1.0F, 1.0F).texOffs(108, 30).addBox(-2.0F, 4.5F, -0.5F, 2.0F, 1.0F, 1.0F).texOffs(114, 30).addBox(-2.0F, -4.5F, -0.5F, 2.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-6.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p3 = p1.addOrReplaceChild("manche", CubeListBuilder.create().texOffs(60, 30).addBox(-6.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F).texOffs(56, 0).addBox(-1.5F, -15.0F, -1.5F, 3.0F, 15.0F, 3.0F).texOffs(100, 18).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 2.0F, 4.0F).texOffs(0, 30).addBox(-2.0F, -11.5F, -2.0F, 4.0F, 1.5F, 4.0F).texOffs(120, 30).addBox(-3.5F, -8.0F, -0.5F, 2.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p4 = p3.addOrReplaceChild("cristal", CubeListBuilder.create().texOffs(88, 30).addBox(2.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F).texOffs(92, 30).addBox(-3.0F, -1.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p4.addOrReplaceChild("lueur_cristal_1", CubeListBuilder.create().texOffs(52, 18).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 6.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 128, 128);
    }
}
