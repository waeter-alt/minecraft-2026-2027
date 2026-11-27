package fr.kodistan.interregne.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The siege banner standing in the world: only its look (the gonfanon model, its flag lowered by the capture) and a
 * solid footprint. Everything that matters lives in {@link SiegeBanners}, which puts this entity back whenever its
 * chunk is loaded: it is never saved, never hurt, and nobody can push, ride or break it.
 */
public class SiegeBannerEntity extends Entity implements GeoEntity
{
    private static final EntityDataAccessor<Float>   PROGRESS = SynchedEntityData.defineId(SiegeBannerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR    = SynchedEntityData.defineId(SiegeBannerEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.etendard_siege.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SiegeBannerEntity(final EntityType<? extends SiegeBannerEntity> type, final Level level)
    {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder)
    {
        builder.define(PROGRESS, 0F);
        builder.define(COLOR, 0xB02228);
    }

    public float progress()
    {
        return entityData.get(PROGRESS);
    }

    public int color()
    {
        return entityData.get(COLOR);
    }

    void show(final float progress, final int color)
    {
        if (Math.abs(progress() - progress) > 0.001F)
        {
            entityData.set(PROGRESS, progress);
        }
        if (color() != color)
        {
            entityData.set(COLOR, color);
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level() instanceof ServerLevel server && !SiegeBanners.owns(server, this))
        {
            discard();
        }
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    public boolean hurt(final DamageSource source, final float amount)
    {
        return false;
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag)
    {
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "flag", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return cache;
    }
}
