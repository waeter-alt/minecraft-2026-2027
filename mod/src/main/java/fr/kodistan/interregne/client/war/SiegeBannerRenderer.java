package fr.kodistan.interregne.client.war;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.war.SiegeBannerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The gonfanon (Blockbench source in art/etendard): its cloth takes the attacker's colony colour, its soul lanterns
 * glow ({@code _glowmask}), and the whole flag slides down the mast and gathers as the defenders take the banner.
 */
public final class SiegeBannerRenderer extends GeoEntityRenderer<SiegeBannerEntity>
{
    /** Pixels the flag's top comes down at a full taking, and how much of its height is left then. */
    private static final float DROP = 100F;
    private static final float GATHERED = 0.45F;

    public SiegeBannerRenderer(final EntityRendererProvider.Context context)
    {
        super(context, new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.4F;
    }

    @Override
    public boolean shouldRender(final SiegeBannerEntity entity, final Frustum frustum, final double x, final double y, final double z)
    {
        // The hitbox is only the plinth; the mast and flag stand 12 blocks above it
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().expandTowards(0, 13, 0).inflate(2));
    }

    @Override
    public void renderRecursively(final PoseStack poseStack, final SiegeBannerEntity animatable, final GeoBone bone, final RenderType renderType,
      final MultiBufferSource bufferSource, final VertexConsumer buffer, final boolean isReRender, final float partialTick, final int packedLight,
      final int packedOverlay, final int colour)
    {
        final int tinted = "flag_cloth".equals(bone.getName()) ? (colour & 0xFF000000) | (multiply(colour, animatable.color())) : colour;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, tinted);
    }

    private static int multiply(final int a, final int b)
    {
        final int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        final int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        final int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    private static final class Model extends GeoModel<SiegeBannerEntity>
    {
        private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "geo/entity/etendard_siege.geo.json");
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/entity/war/etendard_siege.png");
        private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "animations/entity/etendard_siege.animation.json");

        @Override
        public ResourceLocation getModelResource(final SiegeBannerEntity animatable)
        {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(final SiegeBannerEntity animatable)
        {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(final SiegeBannerEntity animatable)
        {
            return ANIMATION;
        }

        @Override
        public void setCustomAnimations(final SiegeBannerEntity animatable, final long instanceId, final AnimationState<SiegeBannerEntity> state)
        {
            final GeoBone flag = getAnimationProcessor().getBone("flag");
            if (flag != null)
            {
                final float p = animatable.progress();
                flag.setPosY(-p * DROP);
                flag.setScaleY(1F - p * (1F - GATHERED));
            }
        }
    }
}
