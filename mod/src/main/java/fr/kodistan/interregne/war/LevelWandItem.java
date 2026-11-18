package fr.kodistan.interregne.war;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shift + right-click a troop with it to raise its upgrade level by one (1 → 2 → … → 5, then back to 1), so every
 * model can be looked at in turn. A test tool for now: the level a troop really has in play is not wired yet.
 */
public class LevelWandItem extends Item
{
    public LevelWandItem(final Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(final ItemStack stack, final Player player, final LivingEntity target, final InteractionHand hand)
    {
        if (!(target instanceof AssaultTroop troop) || !player.isShiftKeyDown())
        {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide)
        {
            final int next = troop.getTroopLevel() % AssaultTroop.MAX_LEVEL + 1;
            troop.setTroopLevel(next);
            final String name = troop instanceof EntArcher ? "Archère" : "Légionnaire";
            player.displayClientMessage(Component.literal(name + " : niveau " + next + "/" + AssaultTroop.MAX_LEVEL), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
