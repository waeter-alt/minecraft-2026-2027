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
        "corps/veine_8",
        "corps/veine_9",
        "corps/veine_10",
        "corps/veine_11",
        "corps/veine_12",
        "corps/veine_13",
        "corps/tete/lueur_oeil_g_30",
        "corps/tete/lueur_oeil_d_31",
        "corps/tete/pf0/rouge0",
        "corps/bras_d/pf2/rouge2",
        "corps/pf1/rouge1",
        "corps/pf3/rouge3",
        "corps/pf4/rouge4",
    };
    public static final String[] ROUGES = {
        "corps/tete/pf0/rouge0",
        "corps/pf1/rouge1",
        "corps/bras_d/pf2/rouge2",
        "corps/pf3/rouge3",
        "corps/pf4/rouge4",
    };
    public static final String[] MASQUES = {
        "corps/tete/pf0/pf0_masque",
        "",
        "",
        "",
        "",
    };
    public static final String[][] PETALES = {
        {},
        {"corps/pf1/pf1_petale0", "corps/pf1/pf1_petale1", "corps/pf1/pf1_petale2", "corps/pf1/pf1_petale3"},
        {"corps/bras_d/pf2/pf2_petale0", "corps/bras_d/pf2/pf2_petale1", "corps/bras_d/pf2/pf2_petale2", "corps/bras_d/pf2/pf2_petale3"},
        {"corps/pf3/pf3_petale0", "corps/pf3/pf3_petale1", "corps/pf3/pf3_petale2", "corps/pf3/pf3_petale3"},
        {"corps/pf4/pf4_petale0", "corps/pf4/pf4_petale1", "corps/pf4/pf4_petale2", "corps/pf4/pf4_petale3"},
    };
    public static final String TETE = "corps/tete";

    public static LayerDefinition creerCouche() {
        MeshDefinition maillage = new MeshDefinition();
        PartDefinition p0 = maillage.getRoot().addOrReplaceChild("racine", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p1 = p0.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-36.6F, -9.2F, -21.0F, 14.0F, 10.4F, 14.0F).texOffs(0, 0).addBox(-35.0F, -9.2F, 7.0F, 14.0F, 10.4F, 14.0F).texOffs(0, 0).addBox(22.6F, -9.2F, -21.0F, 14.0F, 10.4F, 14.0F).texOffs(0, 0).addBox(21.0F, -9.2F, 7.0F, 14.0F, 10.4F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p2 = p0.addOrReplaceChild("corps", CubeListBuilder.create().texOffs(0, 224).addBox(-26.0F, 0.0F, -22.0F, 52.0F, 24.0F, 44.0F).texOffs(0, 0).addBox(-23.0F, -36.0F, -20.0F, 46.0F, 36.0F, 40.0F).texOffs(0, 0).addBox(-32.0F, -90.0F, -22.0F, 64.0F, 56.0F, 44.0F).texOffs(0, 0).addBox(-36.0F, -98.0F, -20.0F, 72.0F, 12.0F, 40.0F).texOffs(0, 0).addBox(-6.0F, -54.8F, 22.4F, 12.0F, 9.6F, 8.0F).texOffs(0, 0).addBox(-6.0F, -70.0F, 22.4F, 12.0F, 9.6F, 8.0F).texOffs(0, 0).addBox(-6.0F, -85.2F, 22.4F, 12.0F, 9.6F, 8.0F).texOffs(0, 224).addBox(-16.0F, -101.2F, -8.0F, 32.0F, 4.0F, 24.0F).texOffs(0, 0).addBox(-10.0F, -104.0F, -17.2F, 20.0F, 9.6F, 20.0F).texOffs(0, 0).addBox(-58.0F, -106.0F, -18.0F, 32.0F, 32.0F, 36.0F).texOffs(330, 140).addBox(-60.4F, -104.0F, -14.0F, 2.4F, 28.0F, 28.0F).texOffs(0, 224).addBox(-54.0F, -109.6F, -14.0F, 24.0F, 4.0F, 28.0F).texOffs(0, 0).addBox(26.0F, -106.0F, -18.0F, 32.0F, 32.0F, 36.0F).texOffs(330, 140).addBox(58.0F, -104.0F, -14.0F, 2.4F, 28.0F, 28.0F).texOffs(0, 224).addBox(30.0F, -109.6F, -14.0F, 24.0F, 4.0F, 28.0F).texOffs(260, 0).addBox(-25.6F, -60.0F, -24.2F, 51.2F, 3.2F, 2.0F).texOffs(260, 0).addBox(-25.6F, -52.4F, -24.2F, 51.2F, 3.2F, 2.0F).texOffs(260, 0).addBox(-25.6F, -44.8F, -24.2F, 51.2F, 3.2F, 2.0F).texOffs(260, 0).addBox(-2.4F, -59.2F, -24.6F, 4.8F, 17.6F, 2.0F).texOffs(260, 0).addBox(-30.8F, -81.6F, -24.0F, 8.8F, 22.4F, 3.2F).texOffs(260, 0).addBox(-34.4F, -52.8F, -23.2F, 3.2F, 13.6F, 22.4F).texOffs(0, 0).addBox(-49.8F, -123.2F, 4.2F, 3.6F, 14.4F, 3.6F).texOffs(0, 224).addBox(-50.4F, -129.8F, 0.8F, 4.8F, 3.6F, 10.4F).texOffs(260, 0).addBox(22.0F, -81.6F, -24.0F, 8.8F, 22.4F, 3.2F).texOffs(260, 0).addBox(31.2F, -52.8F, -23.2F, 3.2F, 13.6F, 22.4F).texOffs(0, 0).addBox(46.2F, -123.2F, 4.2F, 3.6F, 14.4F, 3.6F).texOffs(0, 224).addBox(45.6F, -129.8F, 0.8F, 4.8F, 3.6F, 10.4F).texOffs(0, 0).addBox(18.8F, -7.2F, -4.8F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(7.0F, -7.2F, -22.813328F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(-16.6F, -7.2F, -22.813328F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(-28.4F, -7.2F, -4.8F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(-16.6F, -7.2F, 13.213328F, 9.6F, 7.2F, 9.6F).texOffs(0, 0).addBox(7.0F, -7.2F, 13.213328F, 9.6F, 7.2F, 9.6F),
                PartPose.offsetAndRotation(0.0F, -24.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("arete_0", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(-34.4F, -111.6F, -2.4F, 0.0F, 0.0F, -0.314159F));
        p2.addOrReplaceChild("arete_1", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(-44.8F, -111.6F, -2.4F, 0.0F, 0.0F, -0.314159F));
        p2.addOrReplaceChild("feuille_2", CubeListBuilder.create().texOffs(0, 224).addBox(-5.2F, -1.6F, -3.2F, 10.4F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(-48.0F, -124.8F, 6.0F, 0.0F, 0.0F, -0.349066F));
        p2.addOrReplaceChild("feuille_3", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.4F, -2.8F, 8.0F, 2.8F, 5.6F),
                PartPose.offsetAndRotation(-44.0F, -120.8F, 6.0F, 0.0F, 0.0F, 0.523599F));
        p2.addOrReplaceChild("arete_4", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(34.4F, -111.6F, -2.4F, 0.0F, 0.0F, 0.314159F));
        p2.addOrReplaceChild("arete_5", CubeListBuilder.create().texOffs(260, 0).addBox(-2.8F, -3.2F, -2.8F, 5.6F, 6.4F, 5.6F),
                PartPose.offsetAndRotation(44.8F, -111.6F, -2.4F, 0.0F, 0.0F, 0.314159F));
        p2.addOrReplaceChild("feuille_6", CubeListBuilder.create().texOffs(0, 224).addBox(-5.2F, -1.6F, -3.2F, 10.4F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(48.0F, -124.8F, 6.0F, 0.0F, 0.0F, 0.349066F));
        p2.addOrReplaceChild("feuille_7", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.4F, -2.8F, 8.0F, 2.8F, 5.6F),
                PartPose.offsetAndRotation(44.0F, -120.8F, 6.0F, 0.0F, 0.0F, -0.523599F));
        p2.addOrReplaceChild("veine_8", CubeListBuilder.create().texOffs(330, 200).addBox(-1.0F, -10.0F, -0.6F, 2.0F, 20.0F, 1.2F),
                PartPose.offsetAndRotation(-22.4F, -56.0F, -23.0F, 0.0F, 0.0F, -0.20944F));
        p2.addOrReplaceChild("veine_9", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(-25.6F, -43.2F, -23.0F, 0.0F, 0.0F, 0.314159F));
        p2.addOrReplaceChild("veine_10", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(-36.0F, -96.0F, -18.6F, 0.0F, 0.0F, 0.0F));
        p2.addOrReplaceChild("veine_11", CubeListBuilder.create().texOffs(330, 200).addBox(-1.0F, -10.0F, -0.6F, 2.0F, 20.0F, 1.2F),
                PartPose.offsetAndRotation(22.4F, -56.0F, -23.0F, 0.0F, 0.0F, 0.20944F));
        p2.addOrReplaceChild("veine_12", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(25.6F, -43.2F, -23.0F, 0.0F, 0.0F, -0.314159F));
        p2.addOrReplaceChild("veine_13", CubeListBuilder.create().texOffs(330, 200).addBox(-0.8F, -6.0F, -0.6F, 1.6F, 12.0F, 1.2F),
                PartPose.offsetAndRotation(36.0F, -96.0F, -18.6F, 0.0F, 0.0F, 0.0F));
        PartDefinition p3 = p2.addOrReplaceChild("tete", CubeListBuilder.create().texOffs(260, 0).addBox(-22.0F, -36.8F, -22.0F, 44.0F, 40.0F, 40.0F).texOffs(0, 0).addBox(-23.2F, -33.4F, -26.4F, 46.4F, 6.0F, 7.2F).texOffs(260, 0).addBox(-26.4F, -24.0F, -18.0F, 5.6F, 20.0F, 24.0F).texOffs(260, 0).addBox(20.8F, -24.0F, -18.0F, 5.6F, 20.0F, 24.0F).texOffs(260, 0).addBox(-23.6F, -47.6F, -7.2F, 10.4F, 13.6F, 10.4F).texOffs(260, 0).addBox(13.2F, -47.6F, -7.2F, 10.4F, 13.6F, 10.4F).texOffs(260, 0).addBox(-29.2F, -25.2F, -5.6F, 6.4F, 8.8F, 6.4F).texOffs(260, 0).addBox(22.8F, -25.2F, -5.6F, 6.4F, 8.8F, 6.4F).texOffs(0, 224).addBox(-14.0F, -41.2F, -16.0F, 28.0F, 4.8F, 28.0F).texOffs(0, 224).addBox(-12.4F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F).texOffs(0, 224).addBox(-5.6F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F).texOffs(0, 224).addBox(1.2F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F).texOffs(0, 224).addBox(8.0F, -46.0F, -4.0F, 4.0F, 5.6F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -100.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        p3.addOrReplaceChild("corne_14", CubeListBuilder.create().texOffs(260, 0).addBox(-4.8F, -8.4F, -4.8F, 9.6F, 16.8F, 9.6F),
                PartPose.offsetAndRotation(-22.0F, -53.6F, -2.0F, 0.0F, 0.0F, -0.244346F));
        p3.addOrReplaceChild("corne_15", CubeListBuilder.create().texOffs(260, 0).addBox(-4.4F, -8.4F, -4.4F, 8.8F, 16.8F, 8.8F),
                PartPose.offsetAndRotation(-27.2F, -68.0F, -2.0F, 0.0F, 0.0F, -0.20944F));
        p3.addOrReplaceChild("corne_16", CubeListBuilder.create().texOffs(260, 0).addBox(-4.0F, -7.6F, -4.0F, 8.0F, 15.2F, 8.0F),
                PartPose.offsetAndRotation(-29.6F, -82.4F, -2.0F, 0.0F, 0.0F, -0.034907F));
        p3.addOrReplaceChild("corne_17", CubeListBuilder.create().texOffs(260, 0).addBox(-3.6F, -6.4F, -3.6F, 7.2F, 12.8F, 7.2F),
                PartPose.offsetAndRotation(-26.8F, -94.4F, -2.0F, 0.0F, 0.0F, 0.558505F));
        p3.addOrReplaceChild("corne_18", CubeListBuilder.create().texOffs(260, 0).addBox(-3.2F, -5.2F, -3.2F, 6.4F, 10.4F, 6.4F),
                PartPose.offsetAndRotation(-20.8F, -102.4F, -2.0F, 0.0F, 0.0F, 1.082104F));
        p3.addOrReplaceChild("brindille_19", CubeListBuilder.create().texOffs(260, 0).addBox(-1.8F, -4.8F, -1.8F, 3.6F, 9.6F, 3.6F),
                PartPose.offsetAndRotation(-34.4F, -76.0F, -2.0F, 0.0F, 0.0F, -0.663225F));
        p3.addOrReplaceChild("corne_20", CubeListBuilder.create().texOffs(260, 0).addBox(-4.8F, -8.4F, -4.8F, 9.6F, 16.8F, 9.6F),
                PartPose.offsetAndRotation(22.0F, -53.6F, -2.0F, 0.0F, 0.0F, 0.244346F));
        p3.addOrReplaceChild("corne_21", CubeListBuilder.create().texOffs(260, 0).addBox(-4.4F, -8.4F, -4.4F, 8.8F, 16.8F, 8.8F),
                PartPose.offsetAndRotation(27.2F, -68.0F, -2.0F, 0.0F, 0.0F, 0.20944F));
        p3.addOrReplaceChild("corne_22", CubeListBuilder.create().texOffs(260, 0).addBox(-4.0F, -7.6F, -4.0F, 8.0F, 15.2F, 8.0F),
                PartPose.offsetAndRotation(29.6F, -82.4F, -2.0F, 0.0F, 0.0F, 0.034907F));
        p3.addOrReplaceChild("corne_23", CubeListBuilder.create().texOffs(260, 0).addBox(-3.6F, -6.4F, -3.6F, 7.2F, 12.8F, 7.2F),
                PartPose.offsetAndRotation(26.8F, -94.4F, -2.0F, 0.0F, 0.0F, -0.558505F));
        p3.addOrReplaceChild("corne_24", CubeListBuilder.create().texOffs(260, 0).addBox(-3.2F, -5.2F, -3.2F, 6.4F, 10.4F, 6.4F),
                PartPose.offsetAndRotation(20.8F, -102.4F, -2.0F, 0.0F, 0.0F, -1.082104F));
        p3.addOrReplaceChild("brindille_25", CubeListBuilder.create().texOffs(260, 0).addBox(-1.8F, -4.8F, -1.8F, 3.6F, 9.6F, 3.6F),
                PartPose.offsetAndRotation(34.4F, -76.0F, -2.0F, 0.0F, 0.0F, 0.663225F));
        p3.addOrReplaceChild("feuille_26", CubeListBuilder.create().texOffs(0, 224).addBox(-4.8F, -1.6F, -3.6F, 9.6F, 3.2F, 7.2F),
                PartPose.offsetAndRotation(-20.0F, -108.8F, -2.0F, 0.0F, 0.0F, 0.349066F));
        p3.addOrReplaceChild("feuille_27", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.6F, -3.2F, 8.0F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(-29.6F, -89.6F, -2.0F, 0.0F, 0.0F, -0.698132F));
        p3.addOrReplaceChild("feuille_28", CubeListBuilder.create().texOffs(0, 224).addBox(-4.8F, -1.6F, -3.6F, 9.6F, 3.2F, 7.2F),
                PartPose.offsetAndRotation(20.0F, -108.8F, -2.0F, 0.0F, 0.0F, -0.349066F));
        p3.addOrReplaceChild("feuille_29", CubeListBuilder.create().texOffs(0, 224).addBox(-4.0F, -1.6F, -3.2F, 8.0F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(29.6F, -89.6F, -2.0F, 0.0F, 0.0F, 0.698132F));
        p3.addOrReplaceChild("lueur_oeil_g_30", CubeListBuilder.create().texOffs(330, 200).addBox(-5.6F, -2.0F, -0.8F, 11.2F, 4.0F, 1.6F),
                PartPose.offsetAndRotation(-10.0F, -20.0F, -31.2F, 0.0F, 0.0F, 0.349066F));
        p3.addOrReplaceChild("lueur_oeil_d_31", CubeListBuilder.create().texOffs(330, 200).addBox(-5.6F, -2.0F, -0.8F, 11.2F, 4.0F, 1.6F),
                PartPose.offsetAndRotation(10.0F, -20.0F, -31.2F, 0.0F, 0.0F, -0.349066F));
        PartDefinition p4 = p3.addOrReplaceChild("machoire", CubeListBuilder.create().texOffs(0, 0).addBox(-18.0F, 0.0F, -28.8F, 36.0F, 8.0F, 32.0F).texOffs(0, 0).addBox(-13.6F, 0.4F, -22.4F, 3.2F, 12.0F, 3.2F).texOffs(0, 0).addBox(-7.6F, 2.4F, -22.4F, 3.2F, 16.0F, 3.2F).texOffs(0, 0).addBox(-1.6F, 0.4F, -22.4F, 3.2F, 12.0F, 3.2F).texOffs(0, 0).addBox(4.4F, 2.4F, -22.4F, 3.2F, 16.0F, 3.2F).texOffs(0, 0).addBox(10.4F, 0.4F, -22.4F, 3.2F, 12.0F, 3.2F).texOffs(330, 100).addBox(-15.4F, 1.2F, -31.4F, 3.6F, 9.6F, 3.6F).texOffs(330, 100).addBox(11.8F, 1.2F, -31.4F, 3.6F, 9.6F, 3.6F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
        p4.addOrReplaceChild("croc_32", CubeListBuilder.create().texOffs(330, 100).addBox(-1.2F, -2.4F, -1.2F, 2.4F, 4.8F, 2.4F),
                PartPose.offsetAndRotation(-12.8F, 0.0F, -28.0F, 0.0F, 0.0F, 0.279253F));
        p4.addOrReplaceChild("croc_33", CubeListBuilder.create().texOffs(330, 100).addBox(-1.2F, -2.4F, -1.2F, 2.4F, 4.8F, 2.4F),
                PartPose.offsetAndRotation(12.8F, 0.0F, -28.0F, 0.0F, 0.0F, -0.279253F));
        PartDefinition p5 = p3.addOrReplaceChild("pf0", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -12.0F, -34.666667F, 0.0F, 0.0F, 0.0F));
        p5.addOrReplaceChild("rouge0", CubeListBuilder.create().texOffs(0, 130).addBox(-12.8F, -7.2F, -2.0F, 25.6F, 14.4F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p6 = p5.addOrReplaceChild("pf0_masque", CubeListBuilder.create().texOffs(260, 0).addBox(-14.4F, 0.0F, -1.6F, 28.8F, 8.0F, 3.2F).texOffs(0, 0).addBox(-15.2F, 7.2F, -4.0F, 30.4F, 2.4F, 2.4F).texOffs(260, 0).addBox(-14.4F, 8.0F, -1.6F, 4.0F, 4.0F, 3.2F).texOffs(260, 0).addBox(10.4F, 8.0F, -1.6F, 4.0F, 4.0F, 3.2F).texOffs(260, 0).addBox(-14.4F, 12.0F, -1.6F, 8.8F, 8.8F, 3.2F).texOffs(260, 0).addBox(5.6F, 12.0F, -1.6F, 8.8F, 8.8F, 3.2F).texOffs(260, 0).addBox(-2.4F, 8.0F, -4.8F, 4.8F, 8.8F, 6.4F).texOffs(260, 0).addBox(-12.0F, 20.8F, -1.6F, 24.0F, 3.2F, 3.2F).texOffs(0, 0).addBox(-16.0F, 2.0F, -1.6F, 1.6F, 18.0F, 9.2F).texOffs(0, 0).addBox(14.4F, 2.0F, -1.6F, 1.6F, 18.0F, 9.2F).texOffs(0, 0).addBox(-12.0F, -4.8F, -2.4F, 3.2F, 4.8F, 3.2F).texOffs(0, 0).addBox(8.8F, -4.8F, -2.4F, 3.2F, 4.8F, 3.2F),
                PartPose.offsetAndRotation(0.0F, -12.0F, -1.6F, 0.0F, 0.0F, 0.0F));
        PartDefinition p7 = p2.addOrReplaceChild("bras_g", CubeListBuilder.create().texOffs(0, 0).addBox(-14.2F, 6.0F, -14.2F, 22.0F, 40.0F, 26.0F).texOffs(0, 0).addBox(-18.8F, 42.8F, -18.2F, 28.0F, 8.0F, 30.0F).texOffs(0, 0).addBox(-18.4F, 46.0F, -18.8F, 24.0F, 32.0F, 28.0F).texOffs(260, 0).addBox(-15.6F, 49.6F, -20.8F, 18.4F, 24.0F, 2.4F).texOffs(260, 0).addBox(-12.0F, 12.0F, -16.4F, 14.4F, 28.0F, 2.4F),
                PartPose.offsetAndRotation(-44.0F, -88.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p8 = p7.addOrReplaceChild("main_g", CubeListBuilder.create().texOffs(0, 0).addBox(-19.0F, 1.2F, -16.8F, 30.0F, 20.0F, 32.0F).texOffs(0, 0).addBox(-16.0F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(-8.4F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(-0.8F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(6.8F, 12.0F, -21.6F, 6.4F, 12.8F, 7.2F).texOffs(0, 0).addBox(-15.8F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(-16.2F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(-8.2F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(-8.6F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(-0.6F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(-1.0F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(7.0F, 20.8F, -21.6F, 6.0F, 9.6F, 6.4F).texOffs(0, 0).addBox(6.6F, -1.6F, -13.4F, 6.8F, 4.8F, 7.6F).texOffs(0, 0).addBox(4.4F, 8.8F, -12.8F, 8.0F, 11.2F, 8.0F).texOffs(0, 0).addBox(-15.0F, 13.0F, 2.6F, 2.8F, 22.0F, 2.8F).texOffs(0, 0).addBox(-8.6F, 15.0F, -3.0F, 2.8F, 18.0F, 2.8F).texOffs(0, 0).addBox(-2.2F, 17.0F, -8.6F, 2.8F, 14.0F, 2.8F),
                PartPose.offsetAndRotation(-4.0F, 72.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p9 = p2.addOrReplaceChild("bras_d", CubeListBuilder.create().texOffs(0, 0).addBox(-12.6F, -27.2F, -19.8F, 22.0F, 24.0F, 22.0F).texOffs(260, 0).addBox(2.4F, -24.0F, -17.2F, 2.4F, 17.6F, 16.8F).texOffs(0, 0).addBox(5.8F, -11.2F, -7.0F, 2.8F, 16.0F, 2.8F).texOffs(0, 0).addBox(3.8F, -11.2F, -12.6F, 2.8F, 16.0F, 2.8F).texOffs(0, 0).addBox(1.8F, -11.2F, -18.2F, 2.8F, 16.0F, 2.8F),
                PartPose.offsetAndRotation(44.0F, -88.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p10 = p9.addOrReplaceChild("poing_d", CubeListBuilder.create().texOffs(0, 0).addBox(-14.2F, -13.6F, -19.0F, 22.0F, 24.0F, 22.0F).texOffs(330, 100).addBox(-9.2F, -44.8F, -22.4F, 7.2F, 12.8F, 7.2F).texOffs(260, 0).addBox(-12.4F, -10.0F, -21.2F, 18.4F, 16.8F, 2.4F).texOffs(0, 0).addBox(-21.2F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(-12.4F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(-3.6F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(5.2F, -7.2F, -39.2F, 7.2F, 9.6F, 7.2F).texOffs(0, 0).addBox(6.4F, -14.4F, -29.2F, 8.0F, 8.0F, 8.8F),
                PartPose.offsetAndRotation(-1.6F, -32.0F, -12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p11 = p9.addOrReplaceChild("pf2", CubeListBuilder.create().texOffs(0, 224).addBox(-5.6F, -14.42F, -5.6F, 11.2F, 1.68F, 11.2F).texOffs(0, 0).addBox(-24.717156F, -4.8F, 11.912478F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-19.508053F, -4.8F, 7.541522F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-14.298951F, -4.8F, 3.170566F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-9.089849F, -4.8F, -1.200389F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-8.489849F, 9.4F, -0.600389F, 3.6F, 3.6F, 3.6F).texOffs(0, 0).addBox(-3.689849F, 9.4F, 4.199611F, 3.6F, 3.6F, 3.6F),
                PartPose.offsetAndRotation(-13.358222F, -48.0F, -25.711504F, 0.0F, 0.0F, 0.0F));
        p11.addOrReplaceChild("rouge2", CubeListBuilder.create().texOffs(0, 130).addBox(-11.2F, -8.4F, -11.2F, 22.4F, 16.8F, 22.4F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p12 = p11.addOrReplaceChild("pf2_petale0", CubeListBuilder.create().texOffs(0, 0).addBox(-2.8F, -23.1F, -14.0F, 5.6F, 19.6F, 28.0F).texOffs(0, 0).addBox(-13.066667F, -26.32F, -11.2F, 11.2F, 3.22F, 22.4F).texOffs(0, 0).addBox(-13.066667F, -3.5F, -11.2F, 11.2F, 3.22F, 22.4F),
                PartPose.offsetAndRotation(14.0F, 13.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p13 = p11.addOrReplaceChild("pf2_petale1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.8F, -23.1F, -14.0F, 5.6F, 19.6F, 28.0F).texOffs(0, 0).addBox(1.866667F, -26.74F, -11.2F, 11.2F, 3.22F, 22.4F).texOffs(0, 0).addBox(1.866667F, -3.08F, -11.2F, 11.2F, 3.22F, 22.4F),
                PartPose.offsetAndRotation(-14.0F, 13.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p14 = p11.addOrReplaceChild("pf2_petale2", CubeListBuilder.create().texOffs(0, 0).addBox(-14.0F, -23.1F, -2.8F, 28.0F, 19.6F, 5.6F).texOffs(0, 0).addBox(-11.2F, -27.16F, 1.866667F, 22.4F, 3.22F, 11.2F).texOffs(0, 0).addBox(-11.2F, -2.66F, 1.866667F, 22.4F, 3.22F, 11.2F),
                PartPose.offsetAndRotation(0.0F, 13.3F, -14.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p15 = p11.addOrReplaceChild("pf2_petale3", CubeListBuilder.create().texOffs(0, 0).addBox(-14.0F, -23.1F, -2.8F, 28.0F, 19.6F, 5.6F).texOffs(0, 0).addBox(-11.2F, -27.58F, -13.066667F, 22.4F, 3.22F, 11.2F).texOffs(0, 0).addBox(-11.2F, -2.24F, -13.066667F, 22.4F, 3.22F, 11.2F),
                PartPose.offsetAndRotation(0.0F, 13.3F, 14.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p16 = p2.addOrReplaceChild("branches_dos", CubeListBuilder.create().texOffs(0, 0).addBox(-22.2F, -38.0F, 0.2F, 4.4F, 18.4F, 4.4F).texOffs(0, 0).addBox(-28.2F, -46.8F, 0.6F, 3.6F, 12.0F, 3.6F).texOffs(0, 0).addBox(17.8F, -38.0F, 0.2F, 4.4F, 18.4F, 4.4F).texOffs(0, 0).addBox(24.6F, -46.8F, 0.6F, 3.6F, 12.0F, 3.6F),
                PartPose.offsetAndRotation(0.0F, -80.0F, 20.0F, 0.0F, 0.0F, 0.0F));
        p16.addOrReplaceChild("feuille_36", CubeListBuilder.create().texOffs(0, 224).addBox(-6.0F, -1.6F, -4.0F, 12.0F, 3.2F, 8.0F),
                PartPose.offsetAndRotation(-24.0F, -48.0F, 2.4F, 0.0F, 0.0F, -0.436332F));
        p16.addOrReplaceChild("feuille_37", CubeListBuilder.create().texOffs(0, 224).addBox(-4.4F, -1.6F, -3.2F, 8.8F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(-18.4F, -39.2F, 2.4F, 0.0F, 0.0F, 0.523599F));
        p16.addOrReplaceChild("feuille_38", CubeListBuilder.create().texOffs(0, 224).addBox(-6.0F, -1.6F, -4.0F, 12.0F, 3.2F, 8.0F),
                PartPose.offsetAndRotation(24.0F, -48.0F, 2.4F, 0.0F, 0.0F, 0.436332F));
        p16.addOrReplaceChild("feuille_39", CubeListBuilder.create().texOffs(0, 224).addBox(-4.4F, -1.6F, -3.2F, 8.8F, 3.2F, 6.4F),
                PartPose.offsetAndRotation(18.4F, -39.2F, 2.4F, 0.0F, 0.0F, -0.523599F));
        PartDefinition p17 = p2.addOrReplaceChild("lianes", CubeListBuilder.create().texOffs(0, 0).addBox(-25.4F, 32.0F, -3.0F, 2.8F, 14.4F, 2.8F).texOffs(0, 0).addBox(-13.4F, 34.8F, -3.0F, 2.8F, 18.4F, 2.8F).texOffs(0, 0).addBox(-1.4F, 32.0F, -3.0F, 2.8F, 14.4F, 2.8F).texOffs(0, 0).addBox(10.6F, 34.8F, -3.0F, 2.8F, 18.4F, 2.8F).texOffs(0, 0).addBox(22.6F, 32.0F, -3.0F, 2.8F, 14.4F, 2.8F),
                PartPose.offsetAndRotation(0.0F, -72.0F, -20.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p18 = p2.addOrReplaceChild("pf1", CubeListBuilder.create().texOffs(0, 224).addBox(-4.8F, -12.36F, -4.8F, 9.6F, 1.44F, 9.6F).texOffs(0, 0).addBox(-4.8F, -4.8F, 22.933333F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -4.8F, 15.68F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -4.8F, 8.426667F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.8F, -4.8F, 1.173333F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-4.2F, 7.8F, 1.773333F, 3.6F, 3.6F, 3.6F).texOffs(0, 0).addBox(0.6F, 7.8F, 6.573333F, 3.6F, 3.6F, 3.6F),
                PartPose.offsetAndRotation(0.0F, -80.0F, -42.666667F, 0.0F, 0.0F, 0.0F));
        p18.addOrReplaceChild("rouge1", CubeListBuilder.create().texOffs(0, 130).addBox(-9.6F, -7.2F, -9.6F, 19.2F, 14.4F, 19.2F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p19 = p18.addOrReplaceChild("pf1_petale0", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -19.8F, -12.0F, 4.8F, 16.8F, 24.0F).texOffs(0, 0).addBox(-11.2F, -22.56F, -9.6F, 9.6F, 2.76F, 19.2F).texOffs(0, 0).addBox(-11.2F, -3.0F, -9.6F, 9.6F, 2.76F, 19.2F),
                PartPose.offsetAndRotation(12.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p20 = p18.addOrReplaceChild("pf1_petale1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -19.8F, -12.0F, 4.8F, 16.8F, 24.0F).texOffs(0, 0).addBox(1.6F, -22.92F, -9.6F, 9.6F, 2.76F, 19.2F).texOffs(0, 0).addBox(1.6F, -2.64F, -9.6F, 9.6F, 2.76F, 19.2F),
                PartPose.offsetAndRotation(-12.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p21 = p18.addOrReplaceChild("pf1_petale2", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -19.8F, -2.4F, 24.0F, 16.8F, 4.8F).texOffs(0, 0).addBox(-9.6F, -23.28F, 1.6F, 19.2F, 2.76F, 9.6F).texOffs(0, 0).addBox(-9.6F, -2.28F, 1.6F, 19.2F, 2.76F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 11.4F, -12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p22 = p18.addOrReplaceChild("pf1_petale3", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -19.8F, -2.4F, 24.0F, 16.8F, 4.8F).texOffs(0, 0).addBox(-9.6F, -23.64F, -11.2F, 19.2F, 2.76F, 9.6F).texOffs(0, 0).addBox(-9.6F, -1.92F, -11.2F, 19.2F, 2.76F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 11.4F, 12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p23 = p2.addOrReplaceChild("pf3", CubeListBuilder.create().texOffs(0, 224).addBox(-4.8F, -12.36F, -4.8F, 9.6F, 1.44F, 9.6F).texOffs(0, 0).addBox(9.066667F, -4.8F, -28.817771F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(5.44F, -4.8F, -22.5362F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(1.813333F, -4.8F, -16.254629F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-1.813333F, -4.8F, -9.973058F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-1.213333F, 7.8F, -9.373058F, 3.6F, 3.6F, 3.6F).texOffs(0, 0).addBox(3.586667F, 7.8F, -4.573058F, 3.6F, 3.6F, 3.6F),
                PartPose.offsetAndRotation(-21.333333F, -96.0F, 36.950417F, 0.0F, 0.0F, 0.0F));
        p23.addOrReplaceChild("rouge3", CubeListBuilder.create().texOffs(0, 130).addBox(-9.6F, -7.2F, -9.6F, 19.2F, 14.4F, 19.2F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p24 = p23.addOrReplaceChild("pf3_petale0", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -19.8F, -12.0F, 4.8F, 16.8F, 24.0F).texOffs(0, 0).addBox(-11.2F, -22.56F, -9.6F, 9.6F, 2.76F, 19.2F).texOffs(0, 0).addBox(-11.2F, -3.0F, -9.6F, 9.6F, 2.76F, 19.2F),
                PartPose.offsetAndRotation(12.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p25 = p23.addOrReplaceChild("pf3_petale1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -19.8F, -12.0F, 4.8F, 16.8F, 24.0F).texOffs(0, 0).addBox(1.6F, -22.92F, -9.6F, 9.6F, 2.76F, 19.2F).texOffs(0, 0).addBox(1.6F, -2.64F, -9.6F, 9.6F, 2.76F, 19.2F),
                PartPose.offsetAndRotation(-12.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p26 = p23.addOrReplaceChild("pf3_petale2", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -19.8F, -2.4F, 24.0F, 16.8F, 4.8F).texOffs(0, 0).addBox(-9.6F, -23.28F, 1.6F, 19.2F, 2.76F, 9.6F).texOffs(0, 0).addBox(-9.6F, -2.28F, 1.6F, 19.2F, 2.76F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 11.4F, -12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p27 = p23.addOrReplaceChild("pf3_petale3", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -19.8F, -2.4F, 24.0F, 16.8F, 4.8F).texOffs(0, 0).addBox(-9.6F, -23.64F, -11.2F, 19.2F, 2.76F, 9.6F).texOffs(0, 0).addBox(-9.6F, -1.92F, -11.2F, 19.2F, 2.76F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 11.4F, 12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p28 = p2.addOrReplaceChild("pf4", CubeListBuilder.create().texOffs(0, 224).addBox(-4.8F, -12.36F, -4.8F, 9.6F, 1.44F, 9.6F).texOffs(0, 0).addBox(-18.666667F, -4.8F, -28.817771F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-15.04F, -4.8F, -22.5362F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-11.413333F, -4.8F, -16.254629F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-7.786667F, -4.8F, -9.973058F, 9.6F, 9.6F, 9.6F).texOffs(0, 0).addBox(-7.186667F, 7.8F, -9.373058F, 3.6F, 3.6F, 3.6F).texOffs(0, 0).addBox(-2.386667F, 7.8F, -4.573058F, 3.6F, 3.6F, 3.6F),
                PartPose.offsetAndRotation(21.333333F, -64.0F, 36.950417F, 0.0F, 0.0F, 0.0F));
        p28.addOrReplaceChild("rouge4", CubeListBuilder.create().texOffs(0, 130).addBox(-9.6F, -7.2F, -9.6F, 19.2F, 14.4F, 19.2F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p29 = p28.addOrReplaceChild("pf4_petale0", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -19.8F, -12.0F, 4.8F, 16.8F, 24.0F).texOffs(0, 0).addBox(-11.2F, -22.56F, -9.6F, 9.6F, 2.76F, 19.2F).texOffs(0, 0).addBox(-11.2F, -3.0F, -9.6F, 9.6F, 2.76F, 19.2F),
                PartPose.offsetAndRotation(12.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p30 = p28.addOrReplaceChild("pf4_petale1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.4F, -19.8F, -12.0F, 4.8F, 16.8F, 24.0F).texOffs(0, 0).addBox(1.6F, -22.92F, -9.6F, 9.6F, 2.76F, 19.2F).texOffs(0, 0).addBox(1.6F, -2.64F, -9.6F, 9.6F, 2.76F, 19.2F),
                PartPose.offsetAndRotation(-12.0F, 11.4F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p31 = p28.addOrReplaceChild("pf4_petale2", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -19.8F, -2.4F, 24.0F, 16.8F, 4.8F).texOffs(0, 0).addBox(-9.6F, -23.28F, 1.6F, 19.2F, 2.76F, 9.6F).texOffs(0, 0).addBox(-9.6F, -2.28F, 1.6F, 19.2F, 2.76F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 11.4F, -12.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition p32 = p28.addOrReplaceChild("pf4_petale3", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -19.8F, -2.4F, 24.0F, 16.8F, 4.8F).texOffs(0, 0).addBox(-9.6F, -23.64F, -11.2F, 19.2F, 2.76F, 9.6F).texOffs(0, 0).addBox(-9.6F, -1.92F, -11.2F, 19.2F, 2.76F, 9.6F),
                PartPose.offsetAndRotation(0.0F, 11.4F, 12.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(maillage, 512, 512);
    }
}
