package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * The archer's ranged behaviour. Every tick it first scans around its own feet for a guard in bow range and, if it
 * finds one, shoots it from wherever it already stands (no vanilla target/chase goal, no new navigation request —
 * the "citizen-chasing bug" {@link AssaultGoal} cut for the légionnaire never gets a chance to repeat here). Plain
 * civilians are never a target, armed or not: the war rule is troops only ever fight guards. Only once no guard is
 * in range does it fall back to the building: same {@link WarAssaults#assignedTarget} and structure-point hit
 * through {@link WarAssaults#onHit} as the légionnaire, but a stand point several blocks back from the wall
 * ({@link WarAssaults#rangedSiegeSpot}) — an archer bombards from range, it doesn't press up against the building
 * like a pillager.
 */
public class AssaultRangedGoal extends Goal
{
    /** An arrow looses every half second, whether at a threat or at the wall — same rhythm as the légionnaire. */
    private static final int FIRE_EVERY = TICKS_SECOND / 2;
    /** Bow range: far enough to matter, short enough that the archer never has to leave its siege spot to use it. */
    private static final double THREAT_RANGE = 16.0D;
    private static final double THREAT_RANGE_SQ = THREAT_RANGE * THREAT_RANGE;
    /** Close enough counts as "arrived at the firing spot" — measured against {@code standAt}, never against the
      * wall itself (see {@link WarAssaults#rangedSiegeSpot}). */
    private static final double REACH_SQ = 2.5D * 2.5D;
    private static final double SPOT_RADIUS_SQ = 24.0D * 24.0D;
    private static final int STALL_BATTER = TICKS_SECOND * 3;
    private static final double STALL_REACH_SQ = 4.0D * 4.0D;
    private static final int STALL_RESPOT = TICKS_SECOND * 10;

    private final EntArcher troop;

    private BlockPos hut;
    private BlockPos anchor;
    private BlockPos standAt;
    private WarAssaults.SiegeSpot spot;
    private BlockPos marching;
    private int fireTick;
    private int stalled;
    private double closest;
    private boolean struck;
    private AbstractEntityCitizen lastThreat;

    public AssaultRangedGoal(final EntArcher troop)
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
        return troop.isAlive() && troop.getColony() != null && WarAssaults.hasAssault(troop.getColony());
    }

    @Override
    public void start()
    {
        hut = null;
        anchor = null;
        standAt = null;
        marching = null;
        lastThreat = null;
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        final AbstractEntityCitizen threat = nearestThreat();
        if (threat != null)
        {
            engage(threat);
            return;
        }

        final BlockPos assigned = WarAssaults.assignedTarget(troop);
        if (assigned == null)
        {
            return;
        }
        if (hut == null || !hut.equals(assigned))
        {
            hut = assigned;
            anchor = null;
            standAt = null;
        }
        final boolean wallTarget = WarAssaults.isWallTarget(troop, hut);
        final IBuilding building = wallTarget ? null : troop.getColony().getBuildingManager().getBuilding(hut);
        if (!wallTarget && building == null)
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
            spot = wallTarget ? WarAssaults.wallSiegeSpot(troop, WarAssaults.RANGED_STANDOFF)
              : WarAssaults.rangedSiegeSpot((ServerLevel) troop.level(), building, hut, troop);
            anchor = spot.wall();
            standAt = spot.stand();
            marching = null;
            stalled = 0;
            closest = Double.MAX_VALUE;
            struck = false;
        }

        // Reach, for the archer, means "arrived at the firing spot" — standAt sits RANGED_STANDOFF blocks back
        // from the wall on purpose, so unlike the légionnaire this never checks distance to the wall itself.
        if (horizontalDistSqr(standAt) > REACH_SQ)
        {
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
            if (stalled < STALL_BATTER || horizontalDistSqr(standAt) > STALL_REACH_SQ)
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
        troop.getLookControl().setLookAt(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);

        if (--fireTick <= 0)
        {
            fireTick = FIRE_EVERY;
            final ServerLevel level = (ServerLevel) troop.level();
            shootAt(level, anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);
            final BlockState state = level.getBlockState(anchor);
            if (!state.isAir())
            {
                level.levelEvent(2001, anchor, Block.getId(state));
            }
            if (WarAssaults.onHit(troop, hut))
            {
                level.sendParticles(ParticleTypes.CRIT, anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D,
                  12, 0.3D, 0.3D, 0.3D, 0.15D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D,
                  5, 0.25D, 0.25D, 0.25D, 0.02D);
                level.playSound(null, anchor, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE,
                  2.0F, 0.8F + level.random.nextFloat() * 0.3F);
            }
        }
    }

    /**
     * The nearest living guard of the troop's own colony within bow range — never a plain civilian, see the class
     * doc. Line-of-sight gated, so an archer never shoots through the very wall it's besieging. Debug-only escape
     * hatch: {@code /war debug archers-siege-only} ({@link WarAssaults#archersIgnoreThreats}) skips this entirely
     * so the building bombardment can be eyeballed on its own, without waiting for every guard to clear out.
     */
    private AbstractEntityCitizen nearestThreat()
    {
        if (WarAssaults.archersIgnoreThreats())
        {
            return null;
        }
        final List<AbstractEntityCitizen> nearby = troop.level().getEntitiesOfClass(AbstractEntityCitizen.class,
          troop.getBoundingBox().inflate(THREAT_RANGE), c -> c.isAlive() && isGuard(c)
            && c.getCitizenColonyHandler().getColonyId() == troop.getColony().getID());
        AbstractEntityCitizen nearest = null;
        double best = THREAT_RANGE_SQ;
        for (final AbstractEntityCitizen citizen : nearby)
        {
            final double dist = troop.distanceToSqr(citizen);
            if (dist <= best && troop.hasLineOfSight(citizen))
            {
                best = dist;
                nearest = citizen;
            }
        }
        return nearest;
    }

    /** Guards only — never a plain civilian (the war rule), armed or not. */
    private static boolean isGuard(final AbstractEntityCitizen citizen)
    {
        final var data = citizen.getCitizenData();
        return data != null && data.getJob() != null && data.getJob().isGuard();
    }

    /** Plants feet and shoots: a threat in range always takes priority over the siege, but never pulls the archer
      * off the spot it's currently standing at — that's what kept a proactive scan safe here (see the class doc). */
    private void engage(final AbstractEntityCitizen threat)
    {
        if (threat != lastThreat)
        {
            lastThreat = threat;
            InterregneMod.LOGGER.info("[war] archer {} engage le garde {} ({}) avant de reprendre le siège",
              troop.getId(), threat.getId(), threat.getCitizenData() != null ? threat.getCitizenData().getName() : "?");
        }
        if (!troop.getNavigation().isDone())
        {
            troop.getNavigation().stop();
        }
        marching = null;
        troop.getLookControl().setLookAt(threat.getX(), threat.getEyeY(), threat.getZ());

        if (--fireTick <= 0)
        {
            fireTick = FIRE_EVERY;
            shootAt((ServerLevel) troop.level(), threat.getX(), threat.getY(0.3333D), threat.getZ());
        }
    }

    /** A real flying arrow, scaled to the same raider damage the légionnaire's blow uses — physics decide a hit on
      * a living target (dodgeable behind cover), while a wall just takes the particle/sound and the SP decrement
      * from {@link WarAssaults#onHit} regardless, same as a melee blow. */
    private void shootAt(final ServerLevel level, final double x, final double y, final double z)
    {
        final Arrow arrow = new Arrow(level, troop, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(troop.getAttributeValue(RaiderMobUtils.MOB_ATTACK_DAMAGE));
        final double dx = x - troop.getX();
        final double dy = y - arrow.getY();
        final double dz = z - troop.getZ();
        final double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, 2.0F);
        level.addFreshEntity(arrow);
        level.playSound(null, troop.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE,
          1.0F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    private void march(final BlockPos to)
    {
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
