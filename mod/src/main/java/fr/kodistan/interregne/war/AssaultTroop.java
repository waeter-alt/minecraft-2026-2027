package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderType;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Shared base for every assault troop ({@link EntLegionnaire}, {@link EntArcher}). Riding the raider base buys the
 * whole defense story for free (guards, citizens and threat tables already know how to fight a raider), and both
 * march/fight entirely under their own goals rather than MineColonies' MobAIRegistry/RaiderWalkAI (see
 * MobAiRegistryMixin, RaiderWalkAiMixin) — only the stuck handler, swim factor and skin faction are genuinely
 * shared between the two, which is what lives here.
 * <p>
 * Both troops are GeckoLib entities with a Blockbench model per upgrade level (1-5: same skeleton and same
 * animation file, a different look). What decides a troop's level in play (Archerie / Caserne level?) is not wired
 * yet: for now the level is set by hand ({@code /war debug troop-level}, the level wand) or through the
 * {@code TroopLevel} NBT tag.
 * <p>
 * A troop spawned by hand (creative egg, {@code /summon}) has no colony and no raid event, which a MineColonies raider
 * cannot live with: its {@code aiStep} discards it ({@code registerWithColony}) or crashes the server on a null colony
 * ({@code onEnterChunk}), and {@code hurt} dereferences the colony too (see RaiderColonylessMixin). Such a "free"
 * troop runs the plain vanilla loop instead (see {@link #aiStep()}): it stands, defends itself, and is kept in the
 * save, which is all the test/creative use needs.
 */
public abstract class AssaultTroop extends AbstractEntityMinecoloniesRaider implements GeoEntity
{
    public static final int MAX_LEVEL = 5;

    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(AssaultTroop.class, EntityDataSerializers.INT);

    /** Dev-only: level given to every troop spawned from now on (see {@code /war debug troop-level}). */
    private static int defaultLevel = 1;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected AssaultTroop(final EntityType<? extends AbstractEntityMinecoloniesRaider> type, final Level world)
    {
        super(type, world);
        this.setPersistenceRequired();
        this.entityData.set(LEVEL, defaultLevel);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(LEVEL, 1);
    }

    /** Upgrade level 1-5; only the look changes for now. */
    public int getTroopLevel()
    {
        return entityData.get(LEVEL);
    }

    public void setTroopLevel(final int level)
    {
        entityData.set(LEVEL, Math.max(1, Math.min(MAX_LEVEL, level)));
    }

    public static void setDefaultLevel(final int level)
    {
        defaultLevel = Math.max(1, Math.min(MAX_LEVEL, level));
    }

    /**
     * Free troops (no colony) skip the whole raider tick, which would discard them or crash on the missing colony:
     * only what a spawned mob needs is kept — physics, goals (a légionnaire still fights back) and sword swing time.
     * Troops of a real assault (colony set) go through the raider logic untouched.
     */
    @Override
    public void aiStep()
    {
        if (level().isClientSide || getColony() != null)
        {
            super.aiStep();
            return;
        }
        updateSwingTime();
        setInvulnerable(false);
        if (isEffectiveAi())
        {
            serverAiStep();
        }
        travel(new Vec3(xxa, yya, zza));
    }

    @Override
    public void addAdditionalSaveData(final CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("TroopLevel", getTroopLevel());
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TroopLevel"))
        {
            setTroopLevel(tag.getInt("TroopLevel"));
        }
        // The raider base discards anything read without a colony event. A free troop (saved with colony id 0, or
        // summoned with NBT that carries none) is meant to live, so it is brought back; a real assault troop (saved
        // with its colony id) stays discarded, as before: assaults are not kept across a reload.
        if (isRemoved() && getColony() == null && tag.getInt("colony") == 0)
        {
            revive();
        }
    }

    /** The troop's own voice when hurt / dying: MineColonies' generic barbarian lines are never used (see WarSounds). */
    protected abstract SoundEvent troopHurtSound();

    protected abstract SoundEvent troopDeathSound();

    @Override
    protected SoundEvent getHurtSound(final DamageSource damageSource)
    {
        return troopHurtSound();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return troopDeathSound();
    }

    /** Soldiers on a march don't chatter: no ambient line (the barbarian "who wants to fight" one is gone). */
    @Override
    protected SoundEvent getAmbientSound()
    {
        return null;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return cache;
    }

    /**
     * The stock raider navigator gets a stuck handler that, with MineColonies' default config
     * (dobarbariansbreakthroughwalls), silently sets blocks to air around a stuck raider — building blocks vanished
     * with no troop battering them. Same handler minus the block breaking: only the troops' own counted blows wreck.
     */
    @Override
    public AbstractAdvancedPathNavigate getNavigation()
    {
        final boolean fresh = this.newNavigator == null;
        final AbstractAdvancedPathNavigate navigator = super.getNavigation();
        if (fresh)
        {
            navigator.setStuckHandler(PathingStuckHandler.createStuckHandler()
              .withTakeDamageOnStuck(0.4f)
              .withBuildLeafBridges()
              .withChanceToByPassMovingAway(0.20)
              .withPlaceLadders());
        }
        return navigator;
    }

    @Override
    public RaiderType getRaiderType()
    {
        return RaiderType.BARBARIAN;
    }

    @Override
    public double getSwimSpeedFactor()
    {
        return 0.7D;
    }
}
