package fr.kodistan.interregne.economy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Couronnes held in any inventory (a player, a town hall, a chest): count them, pay with change, pack them up.
 * Paying takes every coin and hands the change back in the fewest pieces, so the player never has to make change.
 */
public final class Wallet
{
    private Wallet()
    {
    }

    public static long value(final ItemStack stack)
    {
        return stack.getItem() instanceof CoinItem coin ? coin.value() * stack.getCount() : 0;
    }

    public static long value(final IItemHandler handler)
    {
        long total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            total += value(handler.getStackInSlot(slot));
        }
        return total;
    }

    /**
     * Pay this amount from the inventory, giving change back.
     *
     * @param overflow receives the change that does not fit back in.
     * @return false, leaving the inventory untouched, when there is not enough.
     */
    public static boolean pay(final IItemHandler handler, final long amount, final Consumer<ItemStack> overflow)
    {
        final long total = value(handler);
        if (total < amount)
        {
            return false;
        }
        if (amount <= 0)
        {
            return true;
        }
        removeAll(handler);
        give(handler, total - amount, overflow);
        return true;
    }

    /**
     * Repack every coin into the fewest pieces.
     */
    public static void compact(final IItemHandler handler, final Consumer<ItemStack> overflow)
    {
        final long total = value(handler);
        removeAll(handler);
        give(handler, total, overflow);
    }

    public static void give(final IItemHandler handler, final long amount, final Consumer<ItemStack> overflow)
    {
        give(handler, amount, overflow, Long.MAX_VALUE);
    }

    /**
     * Add this amount in the fewest pieces no bigger than the given tier.
     */
    public static void give(final IItemHandler handler, final long amount, final Consumer<ItemStack> overflow, final long largestTier)
    {
        long left = amount;
        for (final DeferredItem<CoinItem> tier : Couronnes.TIERS_DESCENDING)
        {
            final long value = tier.get().value();
            if (value > largestTier || left < value)
            {
                continue;
            }
            long count = left / value;
            left -= count * value;
            while (count > 0)
            {
                final int size = (int) Math.min(count, tier.get().getDefaultMaxStackSize());
                count -= size;
                final ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, new ItemStack(tier.get(), size), false);
                if (!rest.isEmpty())
                {
                    overflow.accept(rest);
                }
            }
        }
    }

    /**
     * "1 250 000"
     */
    public static String format(final long amount)
    {
        return String.format(Locale.FRANCE, "%,d", amount).replace(' ', ' ').replace(' ', ' ');
    }

    /**
     * "1 couronne retirée", "250 couronnes retirées", or just "250 couronnes" without a participle.
     */
    public static String couronnes(final long amount, final String participle)
    {
        final boolean plural = amount > 1;
        final String words = format(amount) + (plural ? " couronnes" : " couronne");
        return participle == null ? words : words + " " + participle + (plural ? "s" : "");
    }

    private static void removeAll(final IItemHandler handler)
    {
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() instanceof CoinItem)
            {
                handler.extractItem(slot, stack.getCount(), false);
            }
        }
    }
}
