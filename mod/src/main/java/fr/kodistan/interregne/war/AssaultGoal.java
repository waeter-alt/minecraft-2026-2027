package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.Tuple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.EnumSet;
import java.util.List;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * The légionnaire's battering. MineColonies' stock RaiderWalkAI (live thanks to {@link AssautEvent}) marches the
 * troop into buildings and the combat state machine fights the defenders; this goal adds what stock raiders
 * never do: provoke a nearby citizen, then batter the structure of the building the troop stands at, one block
 * per blow, until {@link WarAssaults} declares it destroyed. It yields whenever a threat target exists.
 */
public class AssaultGoal extends Goal
{
    /** A blow lands every half second while in reach. */
    private static final int BATTER_EVERY = TICKS_SECOND / 2;
    /** Battering distance to the structure block being hit. */
    private static final int REACH = 5;
    /** Only batter buildings whose hut is this close: troops don't dismantle from across the street. */
    private static final double BUILDING_RANGE_SQR = 10.0D * 10.0D;

    private final EntLegionnaire troop;

    private BlockPos hut;
    private List<BlockPos> breakable = List.of();
    private int batterTick;
    private int provokeTick;

    public AssaultGoal(final EntLegionnaire troop)
    {
        this.troop = troop;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return troop.getColony() != null && WarAssaults.hasAssault(troop.getColony());
    }

    @Override
    public boolean canContinueToUse()
    {
        return troop.isAlive() && troop.getColony() != null && WarAssaults.hasAssault(troop.getColony())
          && troop.getThreatTable().getTargetMob() == null;
    }

    @Override
    public void start()
    {
        hut = null;
        breakable = List.of();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        if (--provokeTick <= 0)
        {
            provokeTick = TICKS_SECOND;
            provoke();
        }
        if (troop.getThreatTable().getTargetMob() != null)
        {
            return;
        }

        final IBuilding building = buildingAtHand();
        if (building == null)
        {
            return;
        }
        if (hut == null || !hut.equals(building.getID()))
        {
            hut = building.getID();
            breakable = List.of();
        }

        if (breakable.isEmpty())
        {
            breakable = WarAssaults.breakableBlocks((ServerLevel) troop.level(), building, hut);
        }
        if (breakable.isEmpty())
        {
            return;
        }

        final BlockPos next = breakable.stream()
          .limit(40) // top layers only: the battering eats the building from the roof down
          .filter(pos -> troop.blockPosition().distSqr(pos) <= (long) (REACH + 2) * (REACH + 2))
          .findFirst().orElse(null);
        if (next == null)
        {
            // The next blocks are out of reach: the stock walk AI keeps moving the troop around inside.
            batterTick = Math.max(batterTick, TICKS_SECOND);
            return;
        }

        if (--batterTick <= 0)
        {
            batterTick = BATTER_EVERY;
            troop.swing(InteractionHand.MAIN_HAND);
            ((ServerLevel) troop.level()).destroyBlock(next, false);
            breakable.remove(0);
            WarAssaults.onHit(troop, hut);
        }
    }

    /** The nearest colony building within arm's reach, the battering candidate. */
    private IBuilding buildingAtHand()
    {
        if (troop.getColony() == null)
        {
            return null;
        }
        IBuilding best = null;
        double bestDist = BUILDING_RANGE_SQR;
        for (final IBuilding building : troop.getColony().getBuildingManager().getBuildings().values())
        {
            final double dist = Mth.lengthSquared(
              building.getID().getX() - troop.getX(), building.getID().getY() - troop.getY(), building.getID().getZ() - troop.getZ());
            if (dist < bestDist)
            {
                bestDist = dist;
                best = building;
            }
        }
        return best;
    }

    /** Pokes the nearest citizen into the troop's threat table; the raider combat AI does the rest. */
    private void provoke()
    {
        if (troop.getThreatTable().getTargetMob() != null)
        {
            return;
        }
        final AbstractEntityCitizen foe = troop.level().getNearestEntity(AbstractEntityCitizen.class,
          TargetingConditions.forCombat().range(20.0D), null,
          troop.getX(), troop.getY(), troop.getZ(), troop.getBoundingBox().inflate(20.0D));
        if (foe != null)
        {
            // Citizens of the colony under assault fight back; anyone in the way counts.
            troop.getThreatTable().addThreat(foe, 10);
        }
    }
}
