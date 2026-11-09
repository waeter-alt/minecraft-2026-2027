package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderType;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.mobs.barbarians.IMeleeBarbarianEntity;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;

/**
 * The assault's melee troop. Riding the raider base buys the whole defense story for free: guards spot it through
 * CombatUtils' raider branch and the threat tables, citizens fight back, notifyGuardsOfTarget rallies guard towers.
 * <p>
 * Deliberately NOT registered with a raid event (registerWithColony would discard it: eventID 0): the stock
 * RaiderWalkAI idles on a missing event, and {@link AssaultGoal} owns the march and the battering instead.
 * Skin is the plain barbarian for the slice; a proper look comes with the Caserne round.
 */
public class EntLegionnaire extends AbstractEntityMinecoloniesRaider implements IMeleeBarbarianEntity
{
    public EntLegionnaire(final EntityType<? extends AbstractEntityMinecoloniesRaider> type, final Level world)
    {
        super(type, world);
        RaiderMobUtils.setEquipment(this);
        this.setPersistenceRequired();
        // MobAIRegistry attaches nothing to a légionnaire (MobAiRegistryMixin): its whole AI is here, vanilla and
        // ours. Fight back against whoever hits it — never a proactive scan — and otherwise march and batter.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RetaliateGoal(this));
        this.goalSelector.addGoal(2, new AssaultGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /**
     * The stock raider navigator gets a stuck handler that, with MineColonies' default config
     * (dobarbariansbreakthroughwalls), silently sets blocks to air around a stuck raider — building blocks vanished
     * with no troop battering them. Same handler minus the block breaking: only AssaultGoal's counted blows wreck.
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
