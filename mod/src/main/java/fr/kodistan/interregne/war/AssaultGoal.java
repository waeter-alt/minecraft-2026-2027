package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * The légionnaire's battering. Each troop is handed ONE building ({@link WarAssaults#assignedTarget}): it walks
 * there and batters exactly that structure, one block per blow, roof first, until {@link WarAssaults} declares it
 * destroyed — the charge and the demolition read as the same act. It yields whenever a threat target exists.
 */
public class AssaultGoal extends Goal
{
    /** A blow lands every half second while in reach. */
    private static final int BATTER_EVERY = TICKS_SECOND / 2;
    /** Battering distance to the structure block being hit. */
    private static final int REACH = 5;

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

        final BlockPos assigned = WarAssaults.assignedTarget(troop);
        if (assigned == null)
        {
            return;
        }
        if (hut == null || !hut.equals(assigned))
        {
            clearCracks();
            hut = assigned;
            breakable = List.of();
        }
        final IBuilding building = troop.getColony().getBuildingManager().getBuilding(hut);
        if (building == null)
        {
            return;
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
            // Nothing in arm's reach yet: march to the assigned building itself.
            EntityNavigationUtils.walkToPos(troop, hut, REACH, false,
              troop.blockPosition().distManhattan(hut) > 50 ? 1.8D : 1.1D);
            return;
        }
        // Face the spot being wrecked so the blows read as aimed.
        troop.getLookControl().setLookAt(next.getX() + 0.5D, next.getY() + 0.5D, next.getZ() + 0.5D);

        if (--batterTick <= 0)
        {
            batterTick = BATTER_EVERY;
            final ServerLevel level = (ServerLevel) troop.level();
            final BlockState state = level.getBlockState(next);
            troop.swing(InteractionHand.MAIN_HAND);
            // Vanilla dig sound + crack particles for the blow, and a mining-crack overlay building up
            // on the structure as it weakens.
            level.levelEvent(2001, next, Block.getId(state));
            level.destroyBlockProgress(troop.getId(), hut, WarAssaults.crackStage(troop, hut));
            level.destroyBlock(next, false);
            breakable.remove(0);
            WarAssaults.onHit(troop, hut);
        }
    }

    /** Drops the troop's mining-crack overlay, e.g. when its target changes. */
    private void clearCracks()
    {
        if (hut != null && troop.level() instanceof ServerLevel level)
        {
            level.destroyBlockProgress(troop.getId(), hut, -1);
        }
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
