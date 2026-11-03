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
     * Put every Couronne the player carries into the imperial treasury.
     */
    public static void fundTreasury(final MinecraftServer server, final ServerPlayer player)
    {
        final PlayerMainInvWrapper purse = new PlayerMainInvWrapper(player.getInventory());
        final long carried = Wallet.value(purse);
        if (carried <= 0 || !Wallet.pay(purse, carried, stack -> player.drop(stack, false)))
        {
            player.sendSystemMessage(Component.literal("Tu ne portes aucune couronne.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Empire.get(server).addTreasury(carried);
        player.sendSystemMessage(Component.literal(Wallet.couronnes(carried, "versée") + " au trésor impérial.").withStyle(ChatFormatting.GOLD));
    }
}
