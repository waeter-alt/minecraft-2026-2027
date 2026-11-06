package fr.kodistan.interregne.client;

import com.minecolonies.core.client.render.mobs.barbarians.AbstractRendererBarbarian;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.war.EntLegionnaire;
import fr.kodistan.interregne.war.WarEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Slice rendering: the légionnaire borrows the barbarian skin — recognizable, zero new art.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class WarRenderers
{
    @SubscribeEvent
    public static void onRenderers(final EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(WarEntities.LEGIONNAIRE.get(), RendererLegionnaire::new);
    }

    public static final class RendererLegionnaire extends AbstractRendererBarbarian<EntLegionnaire, HumanoidModel<EntLegionnaire>>
    {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/barbarian1.png");

        public RendererLegionnaire(final EntityRendererProvider.Context context)
        {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(final EntLegionnaire entity)
        {
            return TEXTURE;
        }
    }

    private WarRenderers()
    {
    }
}
