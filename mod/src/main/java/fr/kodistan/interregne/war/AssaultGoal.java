package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * The légionnaire's battering. Each troop is handed ONE building ({@link WarAssaults#assignedTarget}): it walks to
 * a single fixed point just outside that building's nearest exterior wall or door ({@link WarAssaults#siegeSpot})
 * and batters that wall from there until {@link WarAssaults} declares the building destroyed — it never steps onto
 * the footprint. It yields whenever the troop has a vanilla target (it was hit: RetaliateGoal fights back).
 */
public class AssaultGoal extends Goal
{
    /** A blow lands every half second while in reach. */
    private static final int BATTER_EVERY = TICKS_SECOND / 2;
    /**
     * Close enough to land a blow: horizontal distance to the wall block's centre, squared. Horizontal only — the wall
     * block sits at body height and terrain steps would otherwise eat the budget. 2.5 blocks = right in front of it.
     */
    private static final double REACH_SQ = 2.5D * 2.5D;
    /** Wall spot is only worked out near the building: far off, the troop just heads for the hut. */
    private static final double SPOT_RADIUS_SQ = 24.0D * 24.0D;
    /** Navigation gave up this long while still a hair short of the stand point: batter from where it is. */
    private static final int STALL_BATTER = TICKS_SECOND * 3;
    private static final double STALL_REACH_SQ = 4.0D * 4.0D;
    /** Navigation gave up this long and the spot is plainly out of reach: pick a wall again from here. */
    private static final int STALL_RESPOT = TICKS_SECOND * 10;

    private final EntLegionnaire troop;

    private BlockPos hut;
    private BlockPos anchor;
    private BlockPos standAt;
    private WarAssaults.SiegeSpot spot;
    private BlockPos marching;
    private int batterTick;
    private int stalled;
    private double closest;
    private boolean struck;

    public AssaultGoal(final EntLegionnaire troop)
    {
        this.troop = troop;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return troop.getColony() != null && WarAssaults.hasAssault(troop.getColony()) && !isTargetActive();
    }

    @Override
    public boolean canContinueToUse()
    {
        return troop.isAlive() && troop.getColony() != null && WarAssaults.hasAssault(troop.getColony())
          && !isTargetActive();
    }

    /**
     * Whether the troop is genuinely still fighting something. Nothing here re-scans for a new target (that's the
     * citizen-chasing bug we cut) — the only thing that ever sets {@link EntLegionnaire#getTarget()} is
     * HurtByTargetGoal, reacting once per hit. Once the target dies, nothing clears that reference on its own:
     * checking only for null left a troop permanently idle after its fight ended (the target stayed non-null, just
     * dead), which read as "stopped attacking" with no way back into AssaultGoal.
     */
    private boolean isTargetActive()
    {
        final LivingEntity target = troop.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start()
    {
        hut = null;
        anchor = null;
        standAt = null;
        marching = null;
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        if (isTargetActive())
        {
            return;
        }
        if (troop.getTarget() != null)
        {
            // Dead, nothing else clears it (no continuous target-seeking goal left on this entity) — tidy it so
            // nothing downstream keeps reading a corpse as "still fighting".
            troop.setTarget(null);
        }

        final BlockPos assigned = WarAssaults.assignedTarget(troop);
        if (assigned == null)
        {
            return;
        }
        if (hut == null || !hut.equals(assigned))
        {
            // Only happens right after the old building fell (assignedTarget only drops a held target once it's
            // gone) — destroyBuilding() already cleared that building's crack overlay for every troop, this one
            // included, so there's nothing left to tidy up here.
            hut = assigned;
            anchor = null;
            standAt = null;
        }
        final IBuilding building = troop.getColony().getBuildingManager().getBuilding(hut);
        if (building == null)
        {
            return;
        }

        if (anchor == null)
        {
            if (horizontalDistSqr(hut) > SPOT_RADIUS_SQ)
            {
                march(hut);
                return;
            }
            spot = WarAssaults.siegeSpot((ServerLevel) troop.level(), building, hut, troop.blockPosition());
            anchor = spot.wall();
            standAt = spot.stand();
            marching = null;
            stalled = 0;
            closest = Double.MAX_VALUE;
            struck = false;
        }

        if (horizontalDistSqr(anchor) > REACH_SQ)
        {
            // Progress, not nav state: walkToPos re-requests whenever the nav is done, so isDone() flickers.
            final double left = horizontalDistSqr(standAt);
            if (left < closest - 0.5D)
            {
                closest = left;
                stalled = 0;
            }
            else
            {
                stalled++;
            }
            if (stalled >= STALL_RESPOT)
            {
                anchor = null;
                return;
            }
            if (stalled < STALL_BATTER || horizontalDistSqr(anchor) > STALL_REACH_SQ)
            {
                march(standAt);
                return;
            }
        }
        marching = null;
        stalled = 0;
        if (!struck)
        {
            struck = true;
            WarAssaults.recordFirstBlow(troop, hut, spot, Math.sqrt(horizontalDistSqr(anchor)));
        }
        // Face the wall being battered so the blows read as aimed.
        troop.getLookControl().setLookAt(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);

        if (--batterTick <= 0)
        {
            batterTick = BATTER_EVERY;
            final ServerLevel level = (ServerLevel) troop.level();
            troop.swing(InteractionHand.MAIN_HAND);
            final BlockState state = level.getBlockState(anchor);
            if (!state.isAir())
            {
                // Matches the wall's own material when there's a real block there to hit.
                level.levelEvent(2001, anchor, Block.getId(state));
            }
            // The crack overlay now lives on the whole building (WarAssaults.onHit → updateCracks), not this one
            // troop's hut-keyed spot — the hut itself is excluded there, it's meant to survive regardless.
            // onHit tells us whether THIS blow is the one allowed to show effects for the building right now —
            // several troops battering the same building land hits far faster than one ever would, so only one
            // of them gets to add particles/sound per short window (WarAssaults.EFFECT_COOLDOWN_TICKS); a lone
            // attacker's own rhythm is well under that window, so it never notices the cap.
            if (WarAssaults.onHit(troop, hut))
            {
                // A dusty impact the player reads as "under attack" at a glance, every half second, not just at
                // the final collapse.
                level.sendParticles(ParticleTypes.CRIT, anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D,
                  12, 0.3D, 0.3D, 0.3D, 0.15D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D,
                  5, 0.25D, 0.25D, 0.25D, 0.02D);
                level.playSound(null, anchor, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE,
                  2.0F, 0.8F + level.random.nextFloat() * 0.3F);
            }
        }
    }

    private void march(final BlockPos to)
    {
        // A correct path already in flight is left alone — re-requesting every tick floods the pathfinder queue.
        if (!troop.getNavigation().isDone() && to.equals(marching))
        {
            return;
        }
        marching = to;
        EntityNavigationUtils.walkToPos(troop, to, 1, false, troop.blockPosition().distManhattan(to) > 50 ? 1.8D : 1.1D);
    }

    private double horizontalDistSqr(final BlockPos pos)
    {
        final double dx = troop.getX() - (pos.getX() + 0.5D);
        final double dz = troop.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }
}
