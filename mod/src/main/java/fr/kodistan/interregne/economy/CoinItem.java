package fr.kodistan.interregne.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.List;

/**
 * A Couronne tier, minted by the Empire. Right-click packs every coin you carry into the fewest pieces;
 * sneak + right-click breaks one piece into ten of the tier below.
 */
public class CoinItem extends Item
{
    private final long    value;
    private final boolean shiny;

    public CoinItem(final long value, final boolean shiny, final Properties properties)
    {
        super(properties);
        this.value = value;
        this.shiny = shiny;
    }

    public long value()
    {
        return value;
    }

    @Override
    public boolean isFoil(final ItemStack stack)
    {
        return shiny;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final List<Component> tooltip, final TooltipFlag flag)
    {
        tooltip.add(Component.literal("Vaut " + Wallet.format(value) + (value > 1 ? " couronnes" : " couronne")).withStyle(ChatFormatting.GOLD));
        if (stack.getCount() > 1)
        {
            tooltip.add(Component.literal("Pile : " + Wallet.format(value * stack.getCount()) + " couronnes").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Clic droit : regrouper · Accroupi : faire la monnaie").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand)
    {
        final ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResultHolder.success(held);
        }

        final PlayerMainInvWrapper inventory = new PlayerMainInvWrapper(player.getInventory());
        if (player.isShiftKeyDown())
        {
            if (value <= 1)
            {
                return InteractionResultHolder.fail(held);
            }
            held.shrink(1);
            Wallet.give(inventory, value, stack -> player.drop(stack, false), Couronnes.lowerTier(value));
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        else
        {
            Wallet.compact(inventory, stack -> player.drop(stack, false));
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        serverPlayer.displayClientMessage(Component.literal("Bourse : " + Wallet.format(Wallet.value(inventory)) + " couronnes").withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
}
