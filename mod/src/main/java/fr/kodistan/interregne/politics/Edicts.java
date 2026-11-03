package fr.kodistan.interregne.politics;

import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.economy.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * The edicts the Intendants can enact from the Trône impérial. Each one is proclaimed to the whole server.
 */
public final class Edicts
{
    private Edicts()
    {
    }

    public static void setTitheWeight(final MinecraftServer server, final Empire.TitheWeight weight)
    {
        final Empire empire = Empire.get(server);
        if (empire.titheWeight() == weight)
        {
            return;
        }
        empire.setTitheWeight(weight);
        Proclamations.announce(server, "le poids de la dîme est désormais " + weight.label().toLowerCase() + ".");
    }

    public static void favor(final MinecraftServer server, final ServerPlayer target, final int amount)
    {
        final int granted = Math.clamp(amount, 1, Config.FAVOR_CAP.get());
        Influence.add(server, target.getUUID(), granted, "faveur impériale");
        Proclamations.announce(server, target.getGameProfile().getName() + " reçoit la faveur impériale (+" + granted + " d'Influence).");
    }

    public static void disgrace(final MinecraftServer server, final ServerPlayer target, final int amount)
    {
        final int removed = Math.clamp(amount, 1, Config.FAVOR_CAP.get());
        Influence.remove(server, target.getUUID(), removed, "disgrâce impériale");
        Proclamations.announce(server, target.getGameProfile().getName() + " tombe en disgrâce (−" + removed + " d'Influence).");
    }


    /**
     * Put Couronnes the player carries into the imperial treasury; 0 or less means all of them.
     */
    public static void fundTreasury(final MinecraftServer server, final ServerPlayer player, final long requested)
    {
        final PlayerMainInvWrapper purse = new PlayerMainInvWrapper(player.getInventory());
        final long carried = Wallet.value(purse);
        final long amount = requested <= 0 ? carried : requested;
        if (amount <= 0 || amount > carried || !Wallet.pay(purse, amount, stack -> player.drop(stack, false)))
        {
            player.sendSystemMessage(Component.literal("Tu ne portes pas assez de couronnes (" + Wallet.format(carried) + ").").withStyle(ChatFormatting.RED));
            return;
        }
        Empire.get(server).addTreasury(amount);
        player.sendSystemMessage(Component.literal(Wallet.couronnes(amount, "versée") + " au trésor impérial.").withStyle(ChatFormatting.GOLD));
    }

    /**
     * Take Couronnes out of the imperial treasury (Intendants only); 0 or less means everything.
     */
    public static void withdrawTreasury(final MinecraftServer server, final ServerPlayer player, final long requested)
    {
        final Empire empire = Empire.get(server);
        final long amount = requested <= 0 ? empire.treasury() : requested;
        if (amount <= 0 || amount > empire.treasury())
        {
            player.sendSystemMessage(Component.literal("Le trésor impérial ne contient que " + Wallet.couronnes(empire.treasury(), null) + ".").withStyle(ChatFormatting.RED));
            return;
        }
        empire.addTreasury(-amount);
        Wallet.give(new PlayerMainInvWrapper(player.getInventory()), amount, stack -> player.drop(stack, false));
        player.sendSystemMessage(Component.literal(Wallet.couronnes(amount, "prélevée") + " sur le trésor impérial.").withStyle(ChatFormatting.GOLD));
    }
}
