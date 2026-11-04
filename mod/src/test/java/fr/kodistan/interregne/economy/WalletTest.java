package fr.kodistan.interregne.economy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coins in inventories: no amount may ever be created or lost while giving, paying or repacking.
 */
class WalletTest
{
    private static final long[] AMOUNTS = {0, 1, 9, 10, 11, 99, 100, 1_234, 9_999, 10_000, 123_456, 1_000_000, 7_654_321};

    private static long overflowValue(final List<ItemStack> overflow)
    {
        return overflow.stream().mapToLong(Wallet::value).sum();
    }

    @Test
    void giveAddsExactlyTheAmount()
    {
        for (final long amount : AMOUNTS)
        {
            final ItemStackHandler inv = new ItemStackHandler(36);
            final List<ItemStack> overflow = new ArrayList<>();
            Wallet.give(inv, amount, overflow::add);
            assertEquals(amount, Wallet.value(inv), "give " + amount);
            assertTrue(overflow.isEmpty(), "a roomy inventory overflows nothing");
        }
    }

    @Test
    void giveUsesTheFewestPieces()
    {
        final ItemStackHandler inv = new ItemStackHandler(36);
        Wallet.give(inv, 123_456, s -> {});
        int pieces = 0;
        for (int slot = 0; slot < inv.getSlots(); slot++)
        {
            pieces += inv.getStackInSlot(slot).getCount();
        }
        // 1 dragon + 2 coffrets + 3 sacs + 4 lingots + 5 bourses + 6 couronnes
        assertEquals(21, pieces);
    }

    @Test
    void giveNeverExceedsTheLargestTierAsked()
    {
        final ItemStackHandler inv = new ItemStackHandler(36);
        Wallet.give(inv, 5_000, s -> {}, 100);
        assertEquals(5_000, Wallet.value(inv));
        for (int slot = 0; slot < inv.getSlots(); slot++)
        {
            final ItemStack stack = inv.getStackInSlot(slot);
            assertTrue(stack.isEmpty() || ((CoinItem) stack.getItem()).value() <= 100, "no coin above 100");
        }
    }

    @Test
    void payTakesTheExactAmountAndGivesChange()
    {
        for (final long have : AMOUNTS)
        {
            for (final long cost : AMOUNTS)
            {
                final ItemStackHandler inv = new ItemStackHandler(36);
                Wallet.give(inv, have, s -> {});
                final List<ItemStack> overflow = new ArrayList<>();
                final boolean paid = Wallet.pay(inv, cost, overflow::add);
                if (cost <= have)
                {
                    assertTrue(paid, have + " pays " + cost);
                    assertEquals(have - cost, Wallet.value(inv) + overflowValue(overflow), "change after " + have + " - " + cost);
                }
                else
                {
                    assertFalse(paid, have + " cannot pay " + cost);
                    assertEquals(have, Wallet.value(inv), "a refused payment leaves the purse alone");
                }
            }
        }
    }

    @Test
    void payKeepsOtherItems()
    {
        final ItemStackHandler inv = new ItemStackHandler(9);
        inv.setStackInSlot(0, new ItemStack(Items.BREAD, 12));
        Wallet.give(inv, 250, s -> {});
        assertTrue(Wallet.pay(inv, 99, s -> {}));
        assertEquals(151, Wallet.value(inv));
        assertEquals(12, inv.getStackInSlot(0).getCount());
        assertEquals(Items.BREAD, inv.getStackInSlot(0).getItem());
    }

    @Test
    void changeThatDoesNotFitOverflowsInsteadOfVanishing()
    {
        // One slot: a Trésor paying 1 leaves 99 999 in five kinds of coins, four of which cannot fit
        final ItemStackHandler inv = new ItemStackHandler(1);
        inv.setStackInSlot(0, new ItemStack(Couronnes.TRESOR_DU_DRAGON.get()));
        final List<ItemStack> overflow = new ArrayList<>();
        assertTrue(Wallet.pay(inv, 1, overflow::add));
        assertEquals(99_999, Wallet.value(inv) + overflowValue(overflow));
        assertFalse(overflow.isEmpty());
    }

    @Test
    void compactKeepsTheValue()
    {
        final ItemStackHandler inv = new ItemStackHandler(36);
        inv.setStackInSlot(0, new ItemStack(Couronnes.COURONNE.get(), 64));
        inv.setStackInSlot(1, new ItemStack(Couronnes.COURONNE.get(), 64));
        inv.setStackInSlot(2, new ItemStack(Couronnes.BOURSE.get(), 37));
        final long before = Wallet.value(inv);
        Wallet.compact(inv, s -> {});
        assertEquals(before, Wallet.value(inv));
    }

    @Test
    void wordsAgree()
    {
        assertEquals("1 couronne", Wallet.couronnes(1, null));
        assertEquals("1 couronne retirée", Wallet.couronnes(1, "retirée"));
        assertEquals("250 couronnes retirées", Wallet.couronnes(250, "retirée"));
        assertEquals("1250000", Wallet.format(1_250_000).replaceAll("\\D", ""));
        assertEquals(9, Wallet.format(1_250_000).length(), "digits grouped by three with one space");
    }
}
