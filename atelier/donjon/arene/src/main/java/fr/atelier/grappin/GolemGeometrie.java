// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/golem/golem.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class GolemGeometrie {
    private GolemGeometrie() {
    }

    public static final String[] LUEURS = {
        "fixe/rouge0",
        "fixe/rouge1",
        "fixe/rouge2",
        "fixe/rouge3",
        "fixe/rouge4",
        "corps/veine_18",
        "corps/veine_19",
        "corps/veine_20",
        "corps/veine_21",
        "corps/veine_22",
        "corps/veine_23",
        "corps/tete/lueur_oeil_g_40",
        "corps/tete/lueur_oeil_d_41",
    };
    public static final String[] ROUGES = {
        "fixe/rouge0",
        "fixe/rouge1",
        "fixe/rouge2",
        "fixe/rouge3",
        "fixe/rouge4",
    };
    public static final String[] COUVERCLES = {
        "fixe/couv0",
        "fixe/couv1",
        "fixe/couv2",
        "fixe/couv3",
        "fixe/couv4",
    };
    public static final String TETE = "corps/tete";

    public static LayerDefinition creerCouche() {
        MeshDefinition maillage = new MeshDefinition();
        PartDefinition p0 = maillage.getRoot().addOrReplaceChild("racine", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p1 = p0.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 224).addBox(-26.0F, -24.0F, -22.0F, 52.0F, 24.0F, 44.0F).texOffs(0, 0).addBox(-23.0F, -60.0F, -20.0F, 46.0F, 36.0F, 40.0F).texOffs(0, 0).addBox(-36.6F, -9.2F, -21.0F, 14.0F, 10.4F, 14.0F).texOffs(0, 0).addBox(-35.0F, -9.2F, 7.0F, 14.0F, 10.4F, 14.0F).texOffs(0, 0).addBox(22.6F, -9.2F, -21.0F, 14.0F, 10.4F, 14.0F).texOffs(0, 0).addBox(21.0F, -9.2F, 7.0F, 14.0F, 10.4F, 14.0F).texOffs(0, 0).addBox(18.8F, -31.2F, -4.8F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(7.0F, -31.2F, -22.813328F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(-16.6F, -31.2F, -22.813328F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(-28.4F, -31.2F, -4.8F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(-16.6F, -31.2F, 13.213328F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(7.0F, -31.2F, 13.213328F, 9.6F, 7.2F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p0.addOrReplaceChild("fixe", CubeListBuilder.create().texOffs(0, 0).addBox(-4.8F, -140.8F, -19.733333F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -140.8F, -26.986667F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -140.8F, -34.24F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -140.8F, -41.493333F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -108.8F, -19.733333F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -108.8F, -26.986667F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -108.8F, -34.24F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -108.8F, -41.493333F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(5.924622F, -164.8F, -13.799027F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(11.133724F, -164.8F, -18.169982F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(16.342827F, -164.8F, -22.540938F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(21.551929F, -164.8F, -26.911894F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-12.266667F, -124.8F, 8.132646F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-15.893333F, -124.8F, 14.414217F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-19.52F, -124.8F, 20.695788F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-23.146667F, -124.8F, 26.977359F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(2.666667F, -92.8F, 8.132646F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(6.293333F, -92.8F, 14.414217F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(9.92F, -92.8F, 20.695788F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(13.546667F, -92.8F, 26.977359F, 9.6F, 9.6F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("rouge0", CubeListBuilder.create().texOffs(0, 130).addBox(-11.52F, -8.64F, -11.52F, 23.04F, 17.28F, 23.04F),
                PartPose.offsetAndRotation(0.0F, -136.0F, -42.666667F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("couv0", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, -12.0F, -16.0F, 32.0F, 24.0F, 32.0F),
                PartPose.offsetAndRotation(0.0F, -136.0F, -42.666667F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("rouge1", CubeListBuilder.create().texOffs(0, 130).addBox(-11.52F, -8.64F, -11.52F, 23.04F, 17.28F, 23.04F),
                PartPose.offsetAndRotation(0.0F, -104.0F, -42.666667F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("couv1", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, -12.0F, -16.0F, 32.0F, 24.0F, 32.0F),
                PartPose.offsetAndRotation(0.0F, -104.0F, -42.666667F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("rouge2", CubeListBuilder.create().texOffs(0, 130).addBox(-13.44F, -10.08F, -13.44F, 26.88F, 20.16F, 26.88F),
                PartPose.offsetAndRotation(30.641778F, -160.0F, -25.711504F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("couv2", CubeListBuilder.create().texOffs(0, 0).addBox(-18.666667F, -14.0F, -18.666667F, 37.333333F, 28.0F, 37.333333F),
                PartPose.offsetAndRotation(30.641778F, -160.0F, -25.711504F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("rouge3", CubeListBuilder.create().texOffs(0, 130).addBox(-11.52F, -8.64F, -11.52F, 23.04F, 17.28F, 23.04F),
                PartPose.offsetAndRotation(-21.333333F, -120.0F, 36.950417F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("couv3", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, -12.0F, -16.0F, 32.0F, 24.0F, 32.0F),
                PartPose.offsetAndRotation(-21.333333F, -120.0F, 36.950417F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("rouge4", CubeListBuilder.create().texOffs(0, 130).addBox(-11.52F, -8.64F, -11.52F, 23.04F, 17.28F, 23.04F),
                PartPose.offsetAndRotation(21.333333F, -88.0F, 36.950417F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("couv4", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, -12.0F, -16.0F, 32.0F, 24.0F, 32.0F),
                PartPose.offsetAndRotation(21.333333F, -88.0F, 36.950417F, 0.0F, 0.0F, 0.0F));
        PartDefinition p3 = p0.addOrReplaceChild("corps", CubeListBuilder.create().texOffs(0, 0).addBox(-32.0F, -90.0F, -22.0F, 64.0F, 56.0F, 44.0F).texOffs(0, 0).addBox(-36.0F, -98.0F, -20.0F, 72.0F, 12.0F, 40.0F).texOffs(0, 0).addBox(-6.0F, -54.8F, 22.4F, 12.0F, 9.6F, 8.0F).texOffs(0, 0).addBox(-6.0F, -70.0F, 22.4F, 12.0F, 9.6F, 8.0F).texOffs(0, 0).addBox(-6.0F, -85.2F, 22.4F, 12.0F, 9.6F, 8.0F).texOffs(0, 224).addBox(-16.0F, -101.2F, -8.0F, 32.0F, 4.0F, 24.0F).texOffs(0, 0).addBox(-10.0F, -104.0F, -17.2F, 20.0F, 9.6F, 20.0F).texOffs(0, 0).addBox(-58.0F, -106.0F, -18.0F, 32.0F, 32.0F, 36.0F).texOffs(330, 140).addBox(-60.4F, -104.0F, -14.0F, 2.4F, 28.0F, 28.0F).texOffs(0, 224).addBox(-54.0F, -109.6F, -14.0F, 24.0F, 4.0F, 28.0F).texOffs(0, 0).addBox(26.0F, -106.0F, -18.0F, 32.0F, 32.0F, 36.0F).texOffs(330, 140).addBox(58.0F, -104.0F, -14.0F, 2.4F, 28.0F, 28.0F).texOffs(0, 224).addBox(30.0F, -109.6F, -14.0F, 24.0F, 4.0F, 28.0F).texOffs(260, 0).addBox(-25.6F, -60.0F, -24.2F, 51.2F, 3.2F, 2.0F).texOffs(260, 0).addBox(-25.6F, -52.4F, -24.2F, 51.2F, 3.2F, 2.0F).texOffs(260, 0).addBox(-25.6F, -44.8F, -24.2F, 51.2F, 3.2F, 2.0F).texOffs(260, 0).addBox(-2.4F, -59.2F, -24.6F, 4.8F, 17.6F, 2.0F).texOffs(260, 0).addBox(-30.8F, -81.6F, -24.0F, 8.8F, 22.4F, 3.2F).texOffs(260, 0).addBox(-34.4F, -52.8F, -23.2F, 3.2F, 13.6F, 22.4F).texOffs(0, 0).addBox(-49.8F, -123.2F, 4.2F, 3.6F, 14.4F, 3.6F).texOffs(0, 224).addBox(-50.4F, -129.8F, 0.8F, 4.8F, 3.6F, 10.4F).texOffs(260, 0).addBox(22.0F, -81.6F, -24.0F, 8.8F, 22.4F, 3.2F).texOffs(260, 0).addBox(31.2F, -52.8F, -23.2F, 3.2F, 13.6F, 22.4F).texOffs(0, 0).addBox(46.2F, -123.2F, 4.2F, 3.6F, 14.4F, 3.6F).texOffs(0, 224).addBox(45.6F, -129.8F, 0.8F, 4.8F, 3.6F, 10.4F),
                PartPose.offsetAndRotation(0.0F, -24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p3.addOrReplaceChild("arete_10", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(-34.4F, -111.6F, -2.4F, 0.0F, 0.0F, -0.314159F));
        p3.addOrReplaceChild("arete_11", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(-44.8F, -111.6F, -2.4F, 0.0F, 0.0F, -0.314159F));
        p3.addOrReplaceChild("feuille_12", CubeListBuilder.create().texOffs(0, 224).addBox(-5.2F, -1.6F, -3.2F, 10.4F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(-48.0F, -124.8F, 6.0F, 0.0F, 0.0F, -0.349066F));
        p3.addOrReplaceChild("feuille_13", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.4F, -2.8F, 8.0F, 2.8F, 5.6F),
                PartPose.offsetAndRotation(-44.0F, -120.8F, 6.0F, 0.0F, 0.0F, 0.523599F));
        p3.addOrReplaceChild("arete_14", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(34.4F, -111.6F, -2.4F, 0.0F, 0.0F, 0.314159F));
        p3.addOrReplaceChild("arete_15", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(44.8F, -111.6F, -2.4F, 0.0F, 0.0F, 0.314159F));
        p3.addOrReplaceChild("feuille_16", CubeListBuilder.create().texOffs(0, 224).addBox(-5.2F, -1.6F, -3.2F, 10.4F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(48.0F, -124.8F, 6.0F, 0.0F, 0.0F, 0.349066F));
        p3.addOrReplaceChild("feuille_17", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.4F, -2.8F, 8.0F, 2.8F, 5.6F),
                PartPose.offsetAndRotation(44.0F, -120.8F, 6.0F, 0.0F, 0.0F, -0.523599F));
        p3.addOrReplaceChild("veine_18", CubeListBuilder.create().texOffs(330, 200).addBox(-1.0F, -10.0F, -0.6F, 2.0F, 20.0F, 1.2F),
                PartPose.offsetAndRotation(-22.4F, -56.0F, -23.0F, 0.0F, 0.0F, -0.20944F));
        p3.addOrReplaceChild("veine_19", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(-25.6F, -43.2F, -23.0F, 0.0F, 0.0F, 0.314159F));
        p3.addOrReplaceChild("veine_20", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(-36.0F, -96.0F, -18.6F, 0.0F, 0.0F, 0.0F));
        p3.addOrReplaceChild("veine_21", CubeListBuilder.create().texOffs(330, 200).addBox(-1.0F, -10.0F, -0.6F, 2.0F, 20.0F, 1.2F),
                PartPose.offsetAndRotation(22.4F, -56.0F, -23.0F, 0.0F, 0.0F, 0.20944F));
        p3.addOrReplaceChild("veine_22", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(25.6F, -43.2F, -23.0F, 0.0F, 0.0F, -0.314159F));
        p3.addOrReplaceChild("veine_23", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(36.0F, -96.0F, -18.6F, 0.0F, 0.0F, 0.0F));
        PartDefinition p4 = p3.addOrReplaceChild("tete", CubeListBuilder.create().texOffs(260, 0).addBox(-22.0F, -36.8F, -22.0F, 44.0F, 40.0F, 40.0F).texOffs(0, 0).addBox(-23.2F, -33.4F, -26.4F, 46.4F, 6.0F, 7.2F).texOffs(260, 0).addBox(-26.4F, -24.0F, -18.0F, 5.6F, 20.0F, 24.0F).texOffs(260, 0).addBox(20.8F, -24.0F, -18.0F, 5.6F, 20.0F, 24.0F).texOffs(260, 0).addBox(-23.6F, -47.6F, -7.2F, 10.4F, 13.6F, 10.4F).texOffs(260, 0).addBox(13.2F, -47.6F, -7.2F, 10.4F, 13.6F, 10.4F).texOffs(260, 0).addBox(-29.2F, -25.2F, -5.6F, 6.4F, 8.8F, 6.4F).texOffs(260, 0).addBox(22.8F, -25.2F, -5.6F, 6.4F, 8.8F, 6.4F).texOffs(0, 224).addBox(-14.0F, -41.2F, -16.0F, 28.0F, 4.8F, 28.0F).texOffs(0, 224).addBox(-12.4F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F).texOffs(0, 224).addBox(-5.6F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F).texOffs(0, 224).addBox(1.2F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F).texOffs(0, 224).addBox(8.0F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -100.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        p4.addOrReplaceChild("corne_24", CubeListBuilder.create().texOffs(260, 0).addBox(-4.8F, -8.4F, -4.8F, 9.6F, 16.8F, 9.6F),
                PartPose.offsetAndRotation(-22.0F, -53.6F, -2.0F, 0.0F, 0.0F, -0.244346F));
        p4.addOrReplaceChild("corne_25", CubeListBuilder.create().texOffs(260, 0).addBox(-4.4F, -8.4F, -4.4F, 8.8F, 16.8F, 8.8F),
                PartPose.offsetAndRotation(-27.2F, -68.0F, -2.0F, 0.0F, 0.0F, -0.20944F));
        p4.addOrReplaceChild("corne_26", CubeListBuilder.create().texOffs(260, 0).addBox(-4.0F, -7.6F, -4.0F, 8.0F, 15.2F, 8.0F),
                PartPose.offsetAndRotation(-29.6F, -82.4F, -2.0F, 0.0F, 0.0F, -0.034907F));
        p4.addOrReplaceChild("corne_27", CubeListBuilder.create().texOffs(260, 0).addBox(-3.6F, -6.4F, -3.6F, 7.2F, 12.8F, 7.2F),
                PartPose.offsetAndRotation(-26.8F, -94.4F, -2.0F, 0.0F, 0.0F, 0.558505F));
        p4.addOrReplaceChild("corne_28", CubeListBuilder.create().texOffs(260, 0).addBox(-3.2F, -5.2F, -3.2F, 6.4F, 10.4F, 6.4F),
                PartPose.offsetAndRotation(-20.8F, -102.4F, -2.0F, 0.0F, 0.0F, 1.082104F));
        p4.addOrReplaceChild("brindille_29", CubeListBuilder.create().texOffs(260, 0).addBox(-1.8F, -4.8F, -1.8F, 3.6F, 9.6F, 3.6F),
                PartPose.offsetAndRotation(-34.4F, -76.0F, -2.0F, 0.0F, 0.0F, -0.663225F));
        p4.addOrReplaceChild("corne_30", CubeListBuilder.create().texOffs(260, 0).addBox(-4.8F, -8.4F, -4.8F, 9.6F, 16.8F, 9.6F),
                PartPose.offsetAndRotation(22.0F, -53.6F, -2.0F, 0.0F, 0.0F, 0.244346F));
        p4.addOrReplaceChild("corne_31", CubeListBuilder.create().texOffs(260, 0).addBox(-4.4F, -8.4F, -4.4F, 8.8F, 16.8F, 8.8F),
                PartPose.offsetAndRotation(27.2F, -68.0F, -2.0F, 0.0F, 0.0F, 0.20944F));
        p4.addOrReplaceChild("corne_32", CubeListBuilder.create().texOffs(260, 0).addBox(-4.0F, -7.6F, -4.0F, 8.0F, 15.2F, 8.0F),
                PartPose.offsetAndRotation(29.6F, -82.4F, -2.0F, 0.0F, 0.0F, 0.034907F));
        p4.addOrReplaceChild("corne_33", CubeListBuilder.create().texOffs(260, 0).addBox(-3.6F, -6.4F, -3.6F, 7.2F, 12.8F, 7.2F),
                PartPose.offsetAndRotation(26.8F, -94.4F, -2.0F, 0.0F, 0.0F, -0.558505F));
        p4.addOrReplaceChild("corne_34", CubeListBuilder.create().texOffs(260, 0).addBox(-3.2F, -5.2F, -3.2F, 6.4F, 10.4F, 6.4F),
                PartPose.offsetAndRotation(20.8F, -102.4F, -2.0F, 0.0F, 0.0F, -1.082104F));
        p4.addOrReplaceChild("brindille_35", CubeListBuilder.create().texOffs(260, 0).addBox(-1.8F, -4.8F, -1.8F, 3.6F, 9.6F, 3.6F),
                PartPose.offsetAndRotation(34.4F, -76.0F, -2.0F, 0.0F, 0.0F, 0.663225F));
        p4.addOrReplaceChild("feuille_36", CubeListBuilder.create().texOffs(0, 224).addBox(-4.8F, -1.6F, -3.6F, 9.6F, 3.2F, 7.2F),
                PartPose.offsetAndRotation(-20.0F, -108.8F, -2.0F, 0.0F, 0.0F, 0.349066F));
        p4.addOrReplaceChild("feuille_37", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.6F, -3.2F, 8.0F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(-29.6F, -89.6F, -2.0F, 0.0F, 0.0F, -0.698132F));
        p4.addOrReplaceChild("feuille_38", CubeListBuilder.create().texOffs(0, 224).addBox(-4.8F, -1.6F, -3.6F, 9.6F, 3.2F, 7.2F),
                PartPose.offsetAndRotation(20.0F, -108.8F, -2.0F, 0.0F, 0.0F, -0.349066F));
        p4.addOrReplaceChild("feuille_39", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.6F, -3.2F, 8.0F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(29.6F, -89.6F, -2.0F, 0.0F, 0.0F, 0.698132F));
        p4.addOrReplaceChild("lueur_oeil_g_40", CubeListBuilder.create().texOffs(330, 200).addBox(-5.6F, -2.0F, -0.8F, 11.2F, 4.0F, 1.6F),
                PartPose.offsetAndRotation(-10.0F, -20.0F, -31.2F, 0.0F, 0.0F, 0.349066F));
        p4.addOrReplaceChild("lueur_oeil_d_41", CubeListBuilder.create().texOffs(330, 200).addBox(-5.6F, -2.0F, -0.8F, 11.2F, 4.0F, 1.6F),
                PartPose.offsetAndRotation(10.0F, -20.0F, -31.2F, 0.0F, 0.0F, -0.349066F));
        PartDefinition p5 = p4.addOrReplaceChild("machoire", CubeListBuilder.create().texOffs(0, 0).addBox(-18.0F, 0.0F, -28.8F, 36.0F, 8.0F, 32.0F).texOffs(0, 0).addBox(-13.6F, 0.4F, -22.4F, 3.2F, 12.0F, 3.2F).texOffs(0, 0).addBox(-7.6F, 2.4F, -22.4F, 3.2F, 16.0F, 3.2F).texOffs(0, 0).addBox(-1.6F, 0.4F, -22.4F, 3.2F, 12.0F, 3.2F).texOffs(0, 0).addBox(4.4F, 2.4F, -22.4F, 3.2F, 16.0F, 3.2F).texOffs(0, 0).addBox(10.4F, 0.4F, -22.4F, 3.2F, 12.0F, 3.2F).texOffs(330, 100).addBox(-15.4F, 1.2F, -31.4F, 3.6F, 9.6F, 3.6F).texOffs(330, 100).addBox(11.8F, 1.2F, -31.4F, 3.6F, 9.6F, 3.6F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        p5.addOrReplaceChild("croc_42", CubeListBuilder.create().texOffs(330, 100).addBox(-1.2F, -2.4F, -1.2F, 2.4F, 4.8F, 2.4F),
                PartPose.offsetAndRotation(-12.8F, 0.0F, -28.0F, 0.0F, 0.0F, 0.279253F));
        p5.addOrReplaceChild("croc_43", CubeListBuilder.create().texOffs(330, 100).addBox(-1.2F, -2.4F, -1.2F, 2.4F, 4.8F, 2.4F),
                PartPose.offsetAndRotation(12.8F, 0.0F, -28.0F, 0.0F, 0.0F, -0.279253F));
        PartDefinition p6 = p3.addOrReplaceChild("bras_g", CubeListBuilder.create().texOffs(0, 0).addBox(-14.2F, 6.0F, -14.2F, 22.0F, 40.0F, 26.0F).texOffs(0, 0).addBox(-18.8F, 42.8F, -18.2F, 28.0F, 8.0F, 30.0F).texOffs(0, 0).addBox(-18.4F, 46.0F, -18.8F, 24.0F, 32.0F, 28.0F).texOffs(260, 0).addBox(-15.6F, 49.6F, -20.8F, 18.4F, 24.0F, 2.4F).texOffs(260, 0).addBox(-12.0F, 12.0F, -16.4F, 14.4F, 28.0F, 2.4F),
                PartPose.offsetAndRotation(-44.0F, -88.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p7 = p6.addOrReplaceChild("main_g", CubeListBuilder.create().texOffs(0, 0).addBox(-19.0F, 1.2F, -16.8F, 30.0F, 20.0F, 32.0F).texOffs(0, 0).addBox(-16.0F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(-8.4F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(-0.8F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(6.8F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(-15.8F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(-16.2F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(-8.2F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(-8.6F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(-0.6F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(-1.0F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(7.0F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(6.6F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(4.4F, 8.8F, -12.8F, 8.0F, 11.2F, 8.0F).texOffs(0, 0).addBox(-15.0F, 13.0F, 2.6F, 2.8F, 22.0F, 2.8F).texOffs(0, 0).addBox(-8.6F, 15.0F, -3.0F, 2.8F, 18.0F, 2.8F).texOffs(0, 0).addBox(-2.2F, 17.0F, -8.6F, 2.8F, 14.0F, 2.8F),
                PartPose.offsetAndRotation(-4.0F, 72.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p8 = p3.addOrReplaceChild("bras_d", CubeListBuilder.create().texOffs(0, 0).addBox(-12.6F, -27.2F, -19.8F, 22.0F, 24.0F, 22.0F).texOffs(260, 0).addBox(2.4F, -24.0F, -17.2F, 2.4F, 17.6F, 16.8F).texOffs(0, 0).addBox(5.8F, -11.2F, -7.0F, 2.8F, 16.0F, 2.8F).texOffs(0, 0).addBox(3.8F, -11.2F, -12.6F, 2.8F, 16.0F, 2.8F).texOffs(0, 0).addBox(1.8F, -11.2F, -18.2F, 2.8F, 16.0F, 2.8F),
                PartPose.offsetAndRotation(44.0F, -88.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p9 = p8.addOrReplaceChild("poing_d", CubeListBuilder.create().texOffs(0, 0).addBox(-14.2F, -13.6F, -19.0F, 22.0F, 24.0F, 22.0F).texOffs(330, 100).addBox(-9.2F, -44.8F, -22.4F, 7.2F, 12.8F, 7.2F).texOffs(260, 0).addBox(-12.4F, -10.0F, -21.2F, 18.4F, 16.8F, 2.4F).texOffs(0, 0).addBox(-21.2F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(-12.4F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(-3.6F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(5.2F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(6.4F, -14.4F, -29.2F, 8.0F, 8.0F, 8.8F),
                PartPose.offsetAndRotation(-1.6F, -32.0F, -12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p10 = p3.addOrReplaceChild("branches_dos", CubeListBuilder.create().texOffs(0, 0).addBox(-22.2F, -38.0F, 0.2F, 4.4F, 18.4F, 4.4F).texOffs(0, 0).addBox(-28.2F, -46.8F, 0.6F, 3.6F, 12.0F, 3.6F).texOffs(0, 0).addBox(17.8F, -38.0F, 0.2F, 4.4F, 18.4F, 4.4F).texOffs(0, 0).addBox(24.6F, -46.8F, 0.6F, 3.6F, 12.0F, 3.6F),
                PartPose.offsetAndRotation(0.0F, -80.0F, 20.0F, 0.0F, 0.0F, 0.0F));
        p10.addOrReplaceChild("feuille_44", CubeListBuilder.create().texOffs(0, 224).addBox(-6.0F, -1.6F, -4.0F, 12.0F, 3.2F, 8.0F),
                PartPose.offsetAndRotation(-24.0F, -48.0F, 2.4F, 0.0F, 0.0F, -0.436332F));
        p10.addOrReplaceChild("feuille_45", CubeListBuilder.create().texOffs(0, 224).addBox(-4.4F, -1.6F, -3.2F, 8.8F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(-18.4F, -39.2F, 2.4F, 0.0F, 0.0F, 0.523599F));
        p10.addOrReplaceChild("feuille_46", CubeListBuilder.create().texOffs(0, 224).addBox(-6.0F, -1.6F, -4.0F, 12.0F, 3.2F, 8.0F),
                PartPose.offsetAndRotation(24.0F, -48.0F, 2.4F, 0.0F, 0.0F, 0.436332F));
        p10.addOrReplaceChild("feuille_47", CubeListBuilder.create().texOffs(0, 224).addBox(-4.4F, -1.6F, -3.2F, 8.8F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(18.4F, -39.2F, 2.4F, 0.0F, 0.0F, -0.523599F));
        PartDefinition p11 = p3.addOrReplaceChild("lianes", CubeListBuilder.create().texOffs(0, 0).addBox(-25.4F, 32.0F, -3.0F, 2.8F, 14.4F, 2.8F).texOffs(0, 0).addBox(-13.4F, 34.8F, -3.0F, 2.8F, 18.4F, 2.8F).texOffs(0, 0).addBox(-1.4F, 32.0F, -3.0F, 2.8F, 14.4F, 2.8F).texOffs(0, 0).addBox(10.6F, 34.8F, -3.0F, 2.8F, 18.4F, 2.8F).texOffs(0, 0).addBox(22.6F, 32.0F, -3.0F, 2.8F, 14.4F, 2.8F),
                PartPose.offsetAndRotation(0.0F, -72.0F, -20.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 512, 512);
    }
}
