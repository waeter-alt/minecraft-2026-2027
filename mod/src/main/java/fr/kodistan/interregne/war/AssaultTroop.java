package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderType;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Shared base for every assault troop ({@link EntLegionnaire}, {@link EntArcher}). Riding the raider base buys the
 * whole defense story for free (guards, citizens and threat tables already know how to fight a raider), and both
 * march/fight entirely under their own goals rather than MineColonies' MobAIRegistry/RaiderWalkAI (see
 * MobAiRegistryMixin, RaiderWalkAiMixin) — only the stuck handler, swim factor and skin faction are genuinely
 * shared between the two, which is what lives here.
 */
public abstract class AssaultTroop extends AbstractEntityMinecoloniesRaider
{
    protected AssaultTroop(final EntityType<? extends AbstractEntityMinecoloniesRaider> type, final Level world)
    {
        super(type, world);
        this.setPersistenceRequired();
    }

    /**
     * The stock raider navigator gets a stuck handler that, with MineColonies' default config
     * (dobarbariansbreakthroughwalls), silently sets blocks to air around a stuck raider — building blocks vanished
     * with no troop battering them. Same handler minus the block breaking: only the troops' own counted blows wreck.
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
