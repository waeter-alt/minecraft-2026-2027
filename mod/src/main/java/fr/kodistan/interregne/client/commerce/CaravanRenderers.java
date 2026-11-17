package fr.kodistan.interregne.client.commerce;

import com.minecolonies.core.event.ClientRegistryHandler;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.commerce.CaravanEntities;
import fr.kodistan.interregne.commerce.EntConducteur;
import fr.kodistan.interregne.commerce.EntEscorte;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The caravan's people wear MineColonies' own citizen models and textures: the generic citizen for the driver, the guard
 * models for the soldiers (knight with a sword, archer with a bow). The models are baked from MineColonies' layers but
 * driven by a plain humanoid model, because MineColonies' own model classes only animate its citizen entities.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class CaravanRenderers
{
    private static final ResourceLocation DRIVER = ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/entity/citizen/medieval/citizenmale1_a.png");
    private static final ResourceLocation KNIGHT = ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/entity/citizen/medieval/knightmale1_a.png");
    private static final ResourceLocation ARCHER = ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/entity/citizen/medieval/archermale1_a.png");

    private CaravanRenderers()
    {
    }

    @SubscribeEvent
    public static void onRenderers(final EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(CaravanEntities.CONDUCTEUR.get(), context -> new Folk<EntConducteur>(context, ClientRegistryHandler.MALE_CITIZEN, null, entity -> DRIVER, false));
        event.registerEntityRenderer(CaravanEntities.ESCORTE.get(),
          context -> new Folk<EntEscorte>(context, ClientRegistryHandler.MALE_KNIGHT, ClientRegistryHandler.MALE_ARCHER, entity -> entity.isArcher() ? ARCHER : KNIGHT, true));
    }

    /**
     * One renderer per entity type: the soldiers swap to the archer model at draw time.
     */
    private static final class Folk<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>>
    {
        private final java.util.function.Function<T, ResourceLocation> texture;
        private final HumanoidModel<T> base;
        private final HumanoidModel<T> archerModel;

        private Folk(final EntityRendererProvider.Context context, final net.minecraft.client.model.geom.ModelLayerLocation layer,
          @org.jetbrains.annotations.Nullable final net.minecraft.client.model.geom.ModelLayerLocation archerLayer,
          final java.util.function.Function<T, ResourceLocation> texture, final boolean armor)
        {
            super(context, model(context, layer), 0.5F);
            this.base = this.model;
            this.archerModel = archerLayer == null ? null : model(context, archerLayer);
            this.texture = texture;
            if (armor)
            {
                addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                  new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
            }
        }

        private static <T extends Mob> HumanoidModel<T> model(final EntityRendererProvider.Context context, final net.minecraft.client.model.geom.ModelLayerLocation layer)
        {
            final ModelPart root = context.bakeLayer(layer);
            final HumanoidModel<T> model = new HumanoidModel<>(root);
            // MineColonies' models carry hats and beards as children of the head, shown or hidden by its own animation code
            for (final String extra : new String[] {"SailorHat1", "FancyHat", "shortBeard"})
            {
                if (root.getChild("head").hasChild(extra))
                {
                    root.getChild("head").getChild(extra).visible = false;
                }
            }
            return model;
        }

        @Override
        public void render(final T entity, final float yaw, final float partialTick, final com.mojang.blaze3d.vertex.PoseStack pose,
          final net.minecraft.client.renderer.MultiBufferSource buffers, final int light)
        {
            this.model = archerModel != null && entity instanceof EntEscorte soldier && soldier.isArcher() ? archerModel : base;
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(final T entity)
        {
            return texture.apply(entity);
        }
    }
}
