package fr.kodistan.interregne.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.Collection;

/**
 * /couronnes : anyone checks their fortune; operators mint coins and adjust bank accounts.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class EconomyCommands
{
    private EconomyCommands()
    {
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("couronnes")
          .executes(context -> showFortune(context.getSource(), context.getSource().getPlayerOrException()))
          .then(Commands.literal("frapper").requires(source -> source.hasPermission(2))
                  .then(Commands.argument("joueurs", EntityArgument.players())
                          .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                  .executes(context -> mint(context.getSource(), EntityArgument.getPlayers(context, "joueurs"),
                                    LongArgumentType.getLong(context, "montant"))))))
          .then(Commands.literal("banque").requires(source -> source.hasPermission(2))
                  .then(Commands.argument("joueurs", EntityArgument.players())
                          .then(Commands.argument("montant", LongArgumentType.longArg())
                                  .executes(context -> adjustBank(context.getSource(), EntityArgument.getPlayers(context, "joueurs"),
                                    LongArgumentType.getLong(context, "montant")))))));
    }

    private static int showFortune(final CommandSourceStack source, final ServerPlayer player)
    {
        final long carried = Wallet.value(new PlayerMainInvWrapper(player.getInventory()));
        final ImperialBank bank = ImperialBank.get(player.server);
        final long banked = bank.balance(player.getUUID());
        final IColony colony = ImperialBank.colonyOf(player);
        final String treasury = colony == null ? "" : " · Trésor de " + colony.getName() + " : " + Wallet.format(bank.balance(ImperialBank.treasuryOf(colony)));
        source.sendSuccess(() -> Component.literal("Sur toi : " + Wallet.format(carried) + " · À la Banque impériale : " + Wallet.format(banked) + treasury)
                                   .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int mint(final CommandSourceStack source, final Collection<ServerPlayer> players, final long amount)
    {
        for (final ServerPlayer player : players)
        {
            Wallet.give(new PlayerMainInvWrapper(player.getInventory()), amount, stack -> player.drop(stack, false));
        }
        source.sendSuccess(() -> Component.literal(Wallet.format(amount) + " couronnes frappées pour " + players.size() + " joueur(s)."), true);
        return players.size();
    }

    private static int adjustBank(final CommandSourceStack source, final Collection<ServerPlayer> players, final long amount)
    {
        final ImperialBank bank = ImperialBank.get(source.getServer());
        for (final ServerPlayer player : players)
        {
            bank.credit(player.getUUID(), amount);
        }
        source.sendSuccess(() -> Component.literal("Comptes ajustés de " + Wallet.format(amount) + " couronnes pour " + players.size() + " joueur(s)."), true);
        return players.size();
    }
}
