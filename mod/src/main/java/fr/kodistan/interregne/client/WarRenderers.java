package fr.kodistan.interregne.client;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.war.AssaultTroop;
import fr.kodistan.interregne.war.EntArcher;
import fr.kodistan.interregne.war.EntLegionnaire;
import fr.kodistan.interregne.war.WarEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Each troop has its own look, told apart at a glance and never mistaken for a MineColonies barbarian: the
 * légionnaire a man in red and steel, the archer a woman in green and leather. Both are GeckoLib entities: one
 * Blockbench model per upgrade level (1-5) sharing a skeleton and one animation file, all under assets/interregne
 * ({@code geo/entity/<troop>_<level>.geo.json}, {@code textures/entity/war/<troop>_<level>.png},
 * {@code animations/entity/<troop>.animation.json}); the sources are in art/.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class WarRenderers
{
    @SubscribeEvent
    public static void onRenderers(final EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(WarEntities.LEGIONNAIRE.get(), context -> new TroopRenderer<>(context, "legionnaire"));
        event.registerEntityRenderer(WarEntities.ARCHER.get(), context -> new TroopRenderer<>(context, "archere"));
    }

    public static final class TroopRenderer<T extends AssaultTroop> extends GeoEntityRenderer<T>
    {
        public TroopRenderer(final EntityRendererProvider.Context context, final String name)
        {
            super(context, new TroopModel<>(name));
            this.shadowRadius = 0.5F;
        }
    }

    public static final class TroopModel<T extends AssaultTroop> extends GeoModel<T>
    {
        private static final float DEG = (float) (Math.PI / 180.0D);

        /** Index = level - 1. */
        private final ResourceLocation[] models = new ResourceLocation[AssaultTroop.MAX_LEVEL];
        private final ResourceLocation[] textures = new ResourceLocation[AssaultTroop.MAX_LEVEL];
        private final ResourceLocation animations;

        public TroopModel(final String name)
        {
            for (int i = 0; i < AssaultTroop.MAX_LEVEL; i++)
            {
                models[i] = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "geo/entity/" + name + "_" + (i + 1) + ".geo.json");
                textures[i] = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/entity/war/" + name + "_" + (i + 1) + ".png");
            }
            animations = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "animations/entity/" + name + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(final T animatable)
        {
            return models[animatable.getTroopLevel() - 1];
        }

        @Override
        public ResourceLocation getTextureResource(final T animatable)
        {
            return textures[animatable.getTroopLevel() - 1];
        }

        @Override
        public ResourceLocation getAnimationResource(final T animatable)
        {
            return animations;
        }

        /** Adds the entity's look direction on top of the animated head, so the troop follows its target. */
        @Override
        public void setCustomAnimations(final T animatable, final long instanceId, final AnimationState<T> state)
        {
            super.setCustomAnimations(animatable, instanceId, state);
            final GeoBone head = getAnimationProcessor().getBone("head");
            if (head != null)
            {
                final EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
                head.setRotX(head.getRotX() + look.headPitch() * DEG);
                head.setRotY(head.getRotY() + look.netHeadYaw() * DEG);
            }
        }
    }

    private WarRenderers()
    {
    }
}
