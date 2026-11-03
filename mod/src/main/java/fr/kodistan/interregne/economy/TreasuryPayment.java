package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.IColony;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * A colony expense paid from its regional treasury first, the player's carried coins making up the rest.
 * Remembers both parts so it can be refunded exactly where it came from.
 */
public record TreasuryPayment(long fromTreasury, long fromPurse)
{
    public static final TreasuryPayment FREE = new TreasuryPayment(0, 0);

    /**
     * Pay this cost, or nothing at all.
     *
     * @param player who completes the payment; may be null only when the cost is zero.
     * @return the payment, or null, changing nothing, when treasury and purse together fall short.
     */
    @Nullable
    public static TreasuryPayment pay(@Nullable final ServerPlayer player, @Nullable final IColony colony, final long cost)
    {
        if (cost <= 0 || player == null)
        {
            return cost <= 0 ? FREE : null;
        }
        final RegionalTreasury treasury = RegionalTreasury.get(player.server);
        final long fromTreasury = colony == null ? 0 : Math.min(cost, treasury.balance(colony));
        final long fromPurse = cost - fromTreasury;
        if (!Wallet.pay(purse(player), fromPurse, stack -> player.drop(stack, false)))
        {
            return null;
        }
        if (fromTreasury > 0)
        {
            treasury.spend(colony, fromTreasury);
        }
        return new TreasuryPayment(fromTreasury, fromPurse);
    }

    /**
     * What the player and their colony can spend together.
     */
    public static long available(final ServerPlayer player, @Nullable final IColony colony)
    {
        return Wallet.value(purse(player)) + (colony == null ? 0 : RegionalTreasury.get(player.server).balance(colony));
    }

    /**
     * Give each part back to where it came from.
     */
    public void refund(@Nullable final ServerPlayer player, @Nullable final IColony colony)
    {
        if (fromTreasury > 0 && colony != null && player != null)
        {
            RegionalTreasury.get(player.server).credit(colony, fromTreasury);
        }
        if (fromPurse > 0 && player != null)
        {
            Wallet.give(purse(player), fromPurse, stack -> player.drop(stack, false));
        }
    }

    /**
     * "30 du trésor régional + 10 sur toi", for telling the player where the money came from.
     */
    public String describe()
    {
        if (fromTreasury == 0)
        {
            return Wallet.couronnes(fromPurse, null) + " sur toi";
        }
        if (fromPurse == 0)
        {
            return Wallet.couronnes(fromTreasury, null) + " du trésor régional";
        }
        return Wallet.format(fromTreasury) + " du trésor régional + " + Wallet.format(fromPurse) + " sur toi";
    }

    private static IItemHandler purse(final ServerPlayer player)
    {
        return new PlayerMainInvWrapper(player.getInventory());
    }
}
