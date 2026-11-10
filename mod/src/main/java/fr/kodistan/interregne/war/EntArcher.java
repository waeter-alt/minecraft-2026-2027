package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.mobs.barbarians.IArcherBarbarianEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;

/**
 * The assault's ranged troop. Implementing {@link IArcherBarbarianEntity} buys the bow from
 * {@code RaiderMobUtils.setEquipment} the same way {@code IMeleeBarbarianEntity} buys the sword for
 * {@link EntLegionnaire}, and keeps it inside the raider threat tables guards and citizens already know how to
 * read.
 * <p>
 * No vanilla target/retaliate goal: unlike the légionnaire, which only ever reacts to being hit (see AssaultGoal's
 * "citizen-chasing bug" note), {@link AssaultRangedGoal} proactively scans for a nearby guard every tick — never a
 * plain civilian — and shoots it on sight, but never moves off its current spot to do so, so that scan can't
 * repeat the old bug and send it wandering across the colony. Once the area is clear it falls back to bombarding
 * the assigned building from range, same structure-point contract as the légionnaire's battering but stood back
 * from the wall instead of pressed up against it.
 */
public class EntArcher extends AssaultTroop implements IArcherBarbarianEntity
{
    public EntArcher(final EntityType<? extends AbstractEntityMinecoloniesRaider> type, final Level world)
    {
        super(type, world);
        RaiderMobUtils.setEquipment(this);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AssaultRangedGoal(this));
    }
}
