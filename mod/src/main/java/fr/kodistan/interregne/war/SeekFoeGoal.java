package fr.kodistan.interregne.war;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Proactive target pick for an assault troop with nothing to fight: the defender's guards first, then enemy players
 * ({@link WarAssaults#pickFoe}), within a bounded range so the band never wanders across the colony after a target.
 * Civilians and neutrals are left to HurtByTargetGoal: they only become targets by hitting first.
 */
class SeekFoeGoal extends Goal
{
    private static final int SCAN_EVERY = 10;

    private final AssaultTroop troop;
    private final double range;
    private LivingEntity found;

    SeekFoeGoal(final AssaultTroop troop, final double range)
    {
        this.troop = troop;
        this.range = range;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        if (troop.tickCount % SCAN_EVERY != 0 || troop.getColony() == null || troop.keepsChasing(troop.getTarget()))
        {
            return false;
        }
        if (troop instanceof EntArcher && WarAssaults.archersIgnoreThreats())
        {
            return false;
        }
        found = WarAssaults.pickFoe(troop, range);
        return found != null;
    }

    @Override
    public void start()
    {
        troop.setTarget(found);
        found = null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return false;
    }
}
