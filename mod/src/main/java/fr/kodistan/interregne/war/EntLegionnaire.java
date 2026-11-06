package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderType;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.mobs.barbarians.IMeleeBarbarianEntity;
import net.minecraft.world.entity.EntityType;
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
        this.goalSelector.addGoal(2, new AssaultGoal(this));
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
