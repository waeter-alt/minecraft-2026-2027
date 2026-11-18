// [claude-build] Rendu du Scaphandrier rouille (son modele Blockbench et sa texture) et de son harpon a chaine.
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

public class ScaphandrierRendu extends MobRenderer<Scaphandrier, ScaphandrierModele> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/scaphandrier.png");

    public ScaphandrierRendu(EntityRendererProvider.Context contexte) {
        super(contexte, new ScaphandrierModele(contexte.bakeLayer(ScaphandrierModele.COUCHE)), 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(Scaphandrier s) {
        return TEXTURE;
    }

    @Override
    protected float getFlipDegrees(Scaphandrier s) {
        return 0;                                          // il ne bascule pas sur le cote en mourant : l'animation de mort s'en charge
    }

    /** Le harpon en vol ou plante : la tige et les griffes du crochet du grappin (en barbelures), et une chaine jusqu'au lance-harpon. */
    public static class Harpon extends EntityRenderer<HarponScaphandrier> {
        private static final float CHARNIERE_X = 2 / 16F, CHARNIERE_Y = 1 / 16F;

        public Harpon(EntityRendererProvider.Context contexte) {
            super(contexte);
        }

        @Override
        public ResourceLocation getTextureLocation(HarponScaphandrier h) {
            return TextureAtlas.LOCATION_BLOCKS;
        }

        @Override
        public void render(HarponScaphandrier h, float lacet, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere) {
            if (h.plante() != ScaphandrierLogique.Harpon.JOUEUR) {          // plante dans le joueur : seule la chaine se voit
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partiel, h.yRotO, h.getYRot()) - 90.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partiel, h.xRotO, h.getXRot())));
                pose.scale(0.8F, 0.8F, 0.8F);
                var modeles = Minecraft.getInstance().getModelManager();
                var objets = Minecraft.getInstance().getItemRenderer();
                objets.render(h.getItem(), ItemDisplayContext.NONE, false, pose, tampons, lumiere, OverlayTexture.NO_OVERLAY,
                        modeles.getModel(GrappinClient.Rendus.MODELE_TIGE));
                for (int k = 0; k < 4; k++) {
                    pose.pushPose();
                    pose.mulPose(Axis.XP.rotationDegrees(90 * k + 45));
                    pose.translate(CHARNIERE_X, CHARNIERE_Y, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(Griffes.ANGLE_REPLIE * 0.7F));     // barbelures couchees vers l'arriere
                    pose.translate(-CHARNIERE_X, -CHARNIERE_Y, 0);
                    objets.render(h.getItem(), ItemDisplayContext.NONE, false, pose, tampons, lumiere, OverlayTexture.NO_OVERLAY,
                            modeles.getModel(GrappinClient.Rendus.MODELE_GRIFFE));
                    pose.popPose();
                }
                pose.popPose();
            }
            super.render(h, lacet, partiel, pose, tampons, lumiere);
            if (!(h.getOwner() instanceof Scaphandrier s)) {
                return;
            }
            Vec3 ici = h.getPosition(partiel);
            Vec3 d = s.boutDuHarpon(partiel).subtract(ici);
            boolean tendue = h.plante() != ScaphandrierLogique.Harpon.EN_VOL;
            double[][] pts = Corde.points(d.x, d.y, d.z, tendue ? 0 : Corde.mou(false, d.length()) * 0.5, Corde.SEGMENTS);
            BlockPos bh = BlockPos.containing(ici), bs = BlockPos.containing(ici.add(d));
            int blocH = h.level().getBrightness(LightLayer.BLOCK, bh), blocS = h.level().getBrightness(LightLayer.BLOCK, bs);
            int cielH = h.level().getBrightness(LightLayer.SKY, bh), cielS = h.level().getBrightness(LightLayer.SKY, bs);
            double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            float demi = 0.05F;
            float ox = horiz < 1.0E-4 ? demi : (float) (-d.z / horiz) * demi;
            float oz = horiz < 1.0E-4 ? 0 : (float) (d.x / horiz) * demi;
            VertexConsumer chaine = tampons.getBuffer(RenderType.leash());
            Matrix4f m = pose.last().pose();
            bande(chaine, m, pts, ox, 0, oz, blocH, blocS, cielH, cielS, false);
            bande(chaine, m, pts, 0, demi, 0, blocH, blocS, cielH, cielS, true);
        }

        /** Une bande de la chaine (triangle strip), maillons gris fonce et gris clair en alternance. */
        private static void bande(VertexConsumer c, Matrix4f m, double[][] pts, float ox, float oy, float oz, int blocH, int blocS, int cielH,
                int cielS, boolean envers) {
            for (int k = 0; k < pts.length; k++) {
                int i = envers ? pts.length - 1 - k : k;
                float t = (float) i / (pts.length - 1);
                int lum = LightTexture.pack((int) Mth.lerp(t, blocH, blocS), (int) Mth.lerp(t, cielH, cielS));
                int g = i % 2 == 0 ? 128 : 74;
                float x = (float) pts[i][0], y = (float) pts[i][1], z = (float) pts[i][2];
                c.addVertex(m, x - ox, y - oy, z - oz).setColor(g, g, g + 8, 255).setLight(lum);
                c.addVertex(m, x + ox, y + oy, z + oz).setColor(g, g, g + 8, 255).setLight(lum);
            }
        }
    }
}
