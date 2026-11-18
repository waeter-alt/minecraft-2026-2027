package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.mobs.barbarians.IMeleeBarbarianEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The assault's melee troop. Riding the raider base buys the whole defense story for free: guards spot it through
 * CombatUtils' raider branch and the threat tables, citizens fight back, notifyGuardsOfTarget rallies guard towers.
 * <p>
 * Deliberately NOT registered with a raid event (registerWithColony would discard it: eventID 0): the stock
 * RaiderWalkAI idles on a missing event, and {@link AssaultGoal} owns the march and the battering instead.
 * A man in red and steel with his own Blockbench model (art/legionnaire/legionnaire.bbmodel), five models one per
 * {@link #getTroopLevel() level}, animated through GeckoLib. Sword and shield are part of the model, so the weapon
 * item {@code setEquipment} hands out is never drawn. Two controllers: "locomotion" (idle, the guarded "ready" stance
 * while the troop is in combat, walk, run) and "action", which wins over it on the bones it drives (the sword blow,
 * the flinch, death).
 */
public class EntLegionnaire extends AssaultTroop implements IMeleeBarbarianEntity
{
    private static final String ANIM = "animation.legionnaire.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation READY = RawAnimation.begin().thenLoop(ANIM + "ready");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(ANIM + "run");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay(ANIM + "attack");
    private static final RawAnimation HIT = RawAnimation.begin().thenPlay(ANIM + "hit");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(ANIM + "death");

    /** Blocks per tick (squared) above which the walk cycle gives way to the run cycle — a march at the fast speed. */
    private static final double RUN_SPEED_SQ = 0.02D;

    public EntLegionnaire(final EntityType<? extends AbstractEntityMinecoloniesRaider> type, final Level world)
    {
        super(type, world);
        RaiderMobUtils.setEquipment(this);
        // MobAIRegistry attaches nothing to a légionnaire (MobAiRegistryMixin): its whole AI is here, vanilla and
        // ours. Fight back against whoever hits it — never a proactive scan — and otherwise march and batter.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RetaliateGoal(this));
        this.goalSelector.addGoal(2, new AssaultGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** Every blow — a melee hit or a strike at the wall (AssaultGoal) — goes through swing(): one place to play the animation. */
    @Override
    public void swing(final InteractionHand hand, final boolean updateSelf)
    {
        super.swing(hand, updateSelf);
        if (!level().isClientSide)
        {
            triggerAnim("action", "attack");
            // A war cry now and then, not on every blow: a wall is struck twice a second.
            if (random.nextInt(4) == 0)
            {
                playSound(WarSounds.LEGIONNAIRE_ATTACK.get(), 1.0F, getVoicePitch());
            }
        }
    }

    @Override
    protected SoundEvent troopHurtSound()
    {
        return WarSounds.LEGIONNAIRE_HURT.get();
    }

    @Override
    protected SoundEvent troopDeathSound()
    {
        return WarSounds.LEGIONNAIRE_DEATH.get();
    }

    /** Mail and plate clink with every step (levels 3+ wear metal). */
    @Override
    protected void playStepSound(final BlockPos pos, final BlockState state)
    {
        super.playStepSound(pos, state);
        if (getTroopLevel() >= 3)
        {
            playSound((getTroopLevel() >= 4 ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_CHAIN).value(), 0.25F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "locomotion", 4, this::locomotion));
        controllers.add(new AnimationController<>(this, "action", 2, this::action).triggerableAnim("attack", ATTACK));
    }

    private PlayState locomotion(final AnimationState<EntLegionnaire> state)
    {
        if (isDeadOrDying())
        {
            return PlayState.STOP;
        }
        if (state.isMoving())
        {
            return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > RUN_SPEED_SQ ? RUN : WALK);
        }
        return state.setAndContinue(isAggressive() ? READY : IDLE);
    }

    private PlayState action(final AnimationState<EntLegionnaire> state)
    {
        if (isDeadOrDying())
        {
            return state.setAndContinue(DEATH);
        }
        if (hurtTime > 0)
        {
            return state.setAndContinue(HIT);
        }
        return PlayState.STOP;
    }

    /**
     * Vanilla melee hits with ATTACK_DAMAGE; the raider damage scaled to the colony lives in MOB_ATTACK_DAMAGE
     * (what the stock RaiderMeleeAI dealt). Same blow strength as a stock barbarian.
     */
    @Override
    public boolean doHurtTarget(final Entity target)
    {
        final boolean hit = target.hurt(this.damageSources().mobAttack(this), (float) this.getAttributeValue(RaiderMobUtils.MOB_ATTACK_DAMAGE));
        if (hit && target instanceof LivingEntity living)
        {
            living.setLastHurtByMob(this);
        }
        return hit;
    }

    /**
     * Vanilla melee chase, started from the vanilla target alone (set only by HurtByTargetGoal). The stock
     * MeleeAttackGoal.canUse needs createPath(), which the MineColonies navigator always answers null (async
     * pathing): a troop hit from out of arm's reach would never fight back, and stand frozen with a target set.
     * The chase itself goes through navigation.moveTo(entity), which the navigator does support.
     */
    private static final class RetaliateGoal extends MeleeAttackGoal
    {
        private RetaliateGoal(final PathfinderMob troop)
        {
            super(troop, 1.2D, true);
        }

        @Override
        public boolean canUse()
        {
            final LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive();
        }
    }
}
