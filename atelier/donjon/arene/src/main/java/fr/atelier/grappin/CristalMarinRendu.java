// [claude-build] Dessin du cristal des mers : le modele du cristal de l'End (memes trois pieces : cube, deux coques de verre, socle), avec la texture recolorée bleu-vert
// (textures/entity/cristal_marin.png), un cristal qui tourne et flotte comme l'original, toujours lumineux pour se voir de loin dans l'arene.
package fr.atelier.grappin;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CristalMarinRendu extends EntityRenderer<CristalMarin> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/cristal_marin.png");
    private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4);
    private final ModelPart cube;
    private final ModelPart verre;
    private final ModelPart socle;

    public CristalMarinRendu(EntityRendererProvider.Context contexte) {
        super(contexte);
        this.shadowRadius = 0.5F;
        ModelPart racine = contexte.bakeLayer(ModelLayers.END_CRYSTAL);
        this.verre = racine.getChild("glass");
        this.cube = racine.getChild("cube");
        this.socle = racine.getChild("base");
    }

    /** Le flottement du cristal de l'End (meme formule). */
    private static float hauteur(CristalMarin cristal, float partiel) {
        float f = cristal.time + partiel;
        float f1 = Mth.sin(f * 0.2F) / 2.0F + 0.5F;
        f1 = (f1 * f1 + f1) * 0.4F;
        return f1 - 1.4F;
    }

    @Override
    public void render(CristalMarin cristal, float lacet, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere) {
        pose.pushPose();
        float haut = hauteur(cristal, partiel);
        float angle = (cristal.time + partiel) * 3.0F;
        VertexConsumer tampon = tampons.getBuffer(TYPE);
        pose.pushPose();
        pose.scale(2.0F, 2.0F, 2.0F);
        pose.translate(0.0F, -0.5F, 0.0F);
        int lum = LightTexture.FULL_BRIGHT;
        int sup = OverlayTexture.NO_OVERLAY;
        if (cristal.showsBottom()) {
            socle.render(pose, tampon, lumiere, sup);
        }
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(0.0F, 1.5F + haut / 2.0F, 0.0F);
        pose.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        verre.render(pose, tampon, lum, sup);
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        verre.render(pose, tampon, lum, sup);
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        cube.render(pose, tampon, lum, sup);
        pose.popPose();
        BlockPos cible = cristal.getBeamTarget();
        if (cible != null) {                                                  // le rayon de soin, dessine comme celui du cristal de l'End
            float dx = (float) (cible.getX() + 0.5 - Mth.lerp(partiel, cristal.xo, cristal.getX()));
            float dy = (float) (cible.getY() + 0.5 - Mth.lerp(partiel, cristal.yo, cristal.getY()));
            float dz = (float) (cible.getZ() + 0.5 - Mth.lerp(partiel, cristal.zo, cristal.getZ()));
            pose.translate(dx, dy, dz);
            EnderDragonRenderer.renderCrystalBeams(-dx, -dy + haut, -dz, partiel, cristal.time, pose, tampons, lumiere);
        }
        pose.popPose();
        super.render(cristal, lacet, partiel, pose, tampons, lumiere);
    }

    @Override
    public ResourceLocation getTextureLocation(CristalMarin cristal) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(CristalMarin cristal, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;                                                          // un grand cristal : on le dessine tant que sa zone est a l'ecran
    }
}
