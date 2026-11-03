package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * /regions : who produces what, and the imperial market's stock. Operators may reassign a colony or set a stock.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class RegionCommands
{
    private RegionCommands()
    {
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("regions")
          .executes(context -> list(context.getSource()))
          .then(Commands.literal("attribuer").requires(source -> source.hasPermission(2))
                  .then(Commands.argument("colonie", IntegerArgumentType.integer(1))
                          .then(Commands.argument("caisse", StringArgumentType.word())
                                  .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Crate.values()).map(Crate::id), builder))
                                  .executes(context -> reassign(context.getSource(), IntegerArgumentType.getInteger(context, "colonie"),
                                    StringArgumentType.getString(context, "caisse"))))))
          .then(Commands.literal("stock").requires(source -> source.hasPermission(2))
                  .then(Commands.argument("caisse", StringArgumentType.word())
                          .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Crate.values()).map(Crate::id), builder))
                          .then(Commands.argument("quantite", IntegerArgumentType.integer(0))
                                  .executes(context -> setStock(context.getSource(), StringArgumentType.getString(context, "caisse"),
                                    IntegerArgumentType.getInteger(context, "quantite")))))));
    }

    private static int list(final CommandSourceStack source)
    {
        final RegionData data = RegionData.get(source.getServer());
        int shown = 0;
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final Crate crate = data.regionOf(colony);
            final RegionData.Comptoir comptoir = data.existingComptoir(colony);
            final String workshop = comptoir == null || comptoir.pos == null ? "pas de comptoir" : "comptoir niveau " + comptoir.level + ", réserve " + comptoir.stock;
            source.sendSuccess(() -> Component.literal("#" + colony.getID() + " " + colony.getName() + " : "
                                                         + (crate == null ? "pas encore de région" : crate.regionName()) + " (" + workshop + ")"), false);
            shown++;
        }
        if (shown == 0)
        {
            source.sendSuccess(() -> Component.literal("Aucune colonie pour l'instant."), false);
        }
        final ImperialMarket market = ImperialMarket.get(source.getServer());
        final StringBuilder stock = new StringBuilder("Marché impérial :");
        for (final Crate crate : Crate.values())
        {
            stock.append(' ').append(crate.id()).append(' ').append(market.stock(crate))
              .append(" (").append(market.buyPrice(crate)).append('/').append(market.sellPrice(crate)).append(')');
        }
        source.sendSuccess(() -> Component.literal(stock.toString()).withStyle(ChatFormatting.GOLD), false);
        return shown;
    }

    private static int reassign(final CommandSourceStack source, final int colonyId, final String crateId)
    {
        final Crate crate = Crate.byId(crateId);
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
        if (crate == null || colony == null)
        {
            source.sendFailure(Component.literal(crate == null ? "Caisse inconnue : bois, brique, ble, pierre ou minerai." : "Pas de colonie #" + colonyId + " dans cette dimension."));
            return 0;
        }
        Regions.assign(source.getServer(), colony, crate);
        return 1;
    }

    private static int setStock(final CommandSourceStack source, final String crateId, final int amount)
    {
        final Crate crate = Crate.byId(crateId);
        if (crate == null)
        {
            source.sendFailure(Component.literal("Caisse inconnue : bois, brique, ble, pierre ou minerai."));
            return 0;
        }
        ImperialMarket.get(source.getServer()).setStock(crate, amount);
        source.sendSuccess(() -> Component.literal("Stock impérial de " + crate.pluralName() + " : " + amount + "."), true);
        return 1;
    }
}
