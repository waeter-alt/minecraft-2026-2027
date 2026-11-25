package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.mobs.barbarians.IArcherBarbarianEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The assault's ranged troop. Implementing {@link IArcherBarbarianEntity} buys the bow from
 * {@code RaiderMobUtils.setEquipment} the same way {@code IMeleeBarbarianEntity} buys the sword for
 * {@link EntLegionnaire}, and keeps it inside the raider threat tables guards and citizens already know how to
 * read.
 * <p>
 * Same targeting as the légionnaire (HurtByTargetGoal, {@link SeekFoeGoal}: guards and enemy players, never a
 * plain civilian unless it hits first), within a bounded range and leash so she never wanders across the colony.
 * {@link AssaultRangedGoal} shoots the target, closing in when it's out of bow range or sight. Once the area is clear it falls back to bombarding
 * the assigned building from range, same structure-point contract as the légionnaire's battering but stood back
 * from the wall instead of pressed up against it.
 * <p>
 * The archer is the first troop with a real Blockbench model (art/archere/archere.bbmodel), animated through
 * GeckoLib (five models, one per {@link #getTroopLevel() level}): the bow, string and arrow are part of the model, so the equipment item {@code setEquipment} hands out is
 * never drawn. Animation lives in two controllers: "locomotion" (idle / walk / run, from the entity's speed) and
 * "action", which wins over it on the bones it drives (aim loop, the one-shot shot, flinch, death).
 */
public class EntArcher extends AssaultTroop implements IArcherBarbarianEntity
{
    private static final String ANIM = "animation.archere.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(ANIM + "run");
    private static final RawAnimation AIM = RawAnimation.begin().thenLoop(ANIM + "aim");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay(ANIM + "shoot");
    private static final RawAnimation HIT = RawAnimation.begin().thenPlay(ANIM + "hit");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(ANIM + "death");

    /** Blocks per tick (squared) above which the walk cycle gives way to the run cycle — a march at the fast speed. */
    private static final double RUN_SPEED_SQ = 0.02D;
    /** Ticks the bow stays drawn after a shot: longer than the 10-tick fire rhythm, so aiming never flickers off between two arrows. */
    private static final int AIM_GRACE = 25;

    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(EntArcher.class, EntityDataSerializers.BOOLEAN);

    private int aimUntil;

    public EntArcher(final EntityType<? extends AbstractEntityMinecoloniesRaider> type, final Level world)
    {
        super(type, world);
        RaiderMobUtils.setEquipment(this);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AssaultRangedGoal(this));
        // Rides back at whoever hits her, and goes after nearby guards and enemy players; AssaultRangedGoal reads
        // the target and shoots it, moving closer when it's out of bow range or out of sight.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new SeekFoeGoal(this, SEEK_RANGE));
    }

    /** How far an archer looks for a guard or an enemy player to shoot. */
    private static final double SEEK_RANGE = 24.0D;

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(AIMING, false);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!level().isClientSide)
        {
            final boolean aiming = tickCount < aimUntil;
            if (aiming != entityData.get(AIMING))
            {
                entityData.set(AIMING, aiming);
            }
        }
    }

    /** Called by the goal at the moment an arrow leaves: keeps the bow drawn and plays the release on every client. */
    public void onShoot()
    {
        aimUntil = tickCount + AIM_GRACE;
        triggerAnim("action", "shoot");
        // A breath of effort on some of the shots, not every arrow (one is loosed every half second).
        if (random.nextInt(3) == 0)
        {
            playSound(WarSounds.ARCHER_SHOOT.get(), 0.9F, getVoicePitch());
        }
    }

    @Override
    protected SoundEvent troopHurtSound()
    {
        return WarSounds.ARCHER_HURT.get();
    }

    @Override
    protected SoundEvent troopDeathSound()
    {
        return WarSounds.ARCHER_DEATH.get();
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "locomotion", 4, this::locomotion));
        controllers.add(new AnimationController<>(this, "action", 2, this::action).triggerableAnim("shoot", SHOOT));
    }

    private PlayState locomotion(final AnimationState<EntArcher> state)
    {
        if (isDeadOrDying())
        {
            return PlayState.STOP;
        }
        if (state.isMoving())
        {
            return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > RUN_SPEED_SQ ? RUN : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    private PlayState action(final AnimationState<EntArcher> state)
    {
        if (isDeadOrDying())
        {
            return state.setAndContinue(DEATH);
        }
        if (entityData.get(AIMING))
        {
            return state.setAndContinue(AIM);
        }
        if (hurtTime > 0)
        {
            return state.setAndContinue(HIT);
        }
        return PlayState.STOP;
    }
}
