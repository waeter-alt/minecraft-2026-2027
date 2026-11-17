package fr.kodistan.interregne.commerce;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The man who walks beside a caravan's mule. A plain mob without AI that the contract places on the road, drawn on the
 * client with MineColonies' generic citizen model and texture: a real MineColonies citizen cannot exist without a colony
 * and its citizen data, which a caravan between two colonies has neither of.
 */
public class EntConducteur extends PathfinderMob
{
    public EntConducteur(final EntityType<? extends PathfinderMob> type, final Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes()
    {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand)
    {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed()
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(final double distanceToClosestPlayer)
    {
        return false;
    }
}
