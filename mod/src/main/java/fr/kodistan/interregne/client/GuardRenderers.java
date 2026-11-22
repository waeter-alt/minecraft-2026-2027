package fr.kodistan.interregne.client;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.garrison.Garrison;
import fr.kodistan.interregne.garrison.GuardLookPayload;
import fr.kodistan.interregne.war.AssaultTroop;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Bought guards are MineColonies citizens (native patrol and defence AI) but wear the same GeckoLib models as our assault
 * troops: one look for a légionnaire or an archère, in peace, in defence and in attack. The server says which entities are
 * guards ({@link GuardLookPayload}); RenderBipedCitizenMixin draws those with {@link GuardRenderer}.
 */
public final class GuardRenderers
{
    public record Look(int kind, int level)
    {
    }

    private static final Int2ObjectOpenHashMap<Look> LOOKS = new Int2ObjectOpenHashMap<>();

    private GuardRenderers()
    {
    }

    public static void receive(final GuardLookPayload payload)
    {
        LOOKS.put(payload.entityId(), new Look(payload.kind(), Math.max(1, Math.min(AssaultTroop.MAX_LEVEL, payload.level()))));
    }

    @Nullable
    public static Look look(final AbstractEntityCitizen entity)
    {
        return LOOKS.get(entity.getId());
    }

    public static GuardRenderer create(final EntityRendererProvider.Context context, final int kind)
    {
        return new GuardRenderer(context, kind == Garrison.ARCHERE ? "archere" : "legionnaire");
    }

    /**
     * Stands in for every guard of one kind; GeckoLib keeps the animation state per entity id.
     */
    public static final class GuardAnimatable implements GeoAnimatable
    {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private final RawAnimation idle;
        private final RawAnimation walk;
        private final RawAnimation run;
        private final RawAnimation hit;
        private final RawAnimation death;

        GuardAnimatable(final String name)
        {
            final String anim = "animation." + name + ".";
            idle = RawAnimation.begin().thenLoop(anim + "idle");
            walk = RawAnimation.begin().thenLoop(anim + "walk");
            run = RawAnimation.begin().thenLoop(anim + "run");
            hit = RawAnimation.begin().thenPlay(anim + "hit");
            death = RawAnimation.begin().thenPlayAndHold(anim + "death");
        }

        @Override
        public void registerControllers(final AnimatableManager.ControllerRegistrar controllers)
        {
            controllers.add(new AnimationController<>(this, "locomotion", 4, this::locomotion));
            controllers.add(new AnimationController<>(this, "action", 2, this::action));
        }

        private PlayState locomotion(final AnimationState<GuardAnimatable> state)
        {
            if (!(state.getData(DataTickets.ENTITY) instanceof AbstractEntityCitizen entity) || entity.isDeadOrDying())
            {
                return PlayState.STOP;
            }
            if (state.isMoving())
            {
                return state.setAndContinue(entity.isSprinting() ? run : walk);
            }
            return state.setAndContinue(idle);
        }

        private PlayState action(final AnimationState<GuardAnimatable> state)
        {
            if (!(state.getData(DataTickets.ENTITY) instanceof AbstractEntityCitizen entity))
            {
                return PlayState.STOP;
            }
            if (entity.isDeadOrDying())
            {
                return state.setAndContinue(death);
            }
            if (entity.hurtTime > 0)
            {
                return state.setAndContinue(hit);
            }
            return PlayState.STOP;
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache()
        {
            return cache;
        }

        @Override
        public double getTick(final Object object)
        {
            return object instanceof AbstractEntityCitizen entity ? entity.tickCount : 0;
        }
    }

    public static final class GuardModel extends GeoModel<GuardAnimatable>
    {
        private static final float DEG = (float) (Math.PI / 180.0D);

        private final ResourceLocation[] models = new ResourceLocation[AssaultTroop.MAX_LEVEL];
        private final ResourceLocation[] textures = new ResourceLocation[AssaultTroop.MAX_LEVEL];
        private final ResourceLocation animations;
        /** Level of the guard being drawn, set by the renderer right before each render. */
        int level = 1;

        GuardModel(final String name)
        {
            for (int i = 0; i < AssaultTroop.MAX_LEVEL; i++)
            {
                models[i] = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "geo/entity/" + name + "_" + (i + 1) + ".geo.json");
                textures[i] = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/entity/war/" + name + "_" + (i + 1) + ".png");
            }
            animations = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "animations/entity/" + name + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(final GuardAnimatable animatable)
        {
            return models[level - 1];
        }

        @Override
        public ResourceLocation getTextureResource(final GuardAnimatable animatable)
        {
            return textures[level - 1];
        }

        @Override
        public ResourceLocation getAnimationResource(final GuardAnimatable animatable)
        {
            return animations;
        }

        @Override
        public void setCustomAnimations(final GuardAnimatable animatable, final long instanceId, final AnimationState<GuardAnimatable> state)
        {
            super.setCustomAnimations(animatable, instanceId, state);
            final GeoBone head = getAnimationProcessor().getBone("head");
            final EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && look != null)
            {
                head.setRotX(head.getRotX() + look.headPitch() * DEG);
                head.setRotY(head.getRotY() + look.netHeadYaw() * DEG);
            }
        }
    }

    public static final class GuardRenderer extends GeoReplacedEntityRenderer<AbstractEntityCitizen, GuardAnimatable>
    {
        private final GuardModel guardModel;

        GuardRenderer(final EntityRendererProvider.Context context, final String name)
        {
            this(context, new GuardModel(name), name);
        }

        private GuardRenderer(final EntityRendererProvider.Context context, final GuardModel model, final String name)
        {
            super(context, model, new GuardAnimatable(name));
            this.guardModel = model;
            this.shadowRadius = 0.5F;
        }

        public void renderGuard(final AbstractEntityCitizen entity, final int level, final float yaw, final float partialTick,
          final PoseStack poseStack, final MultiBufferSource buffers, final int light)
        {
            guardModel.level = level;
            render(entity, yaw, partialTick, poseStack, buffers, light);
        }
    }
}
