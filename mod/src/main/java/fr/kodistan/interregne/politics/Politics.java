package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Politics wiring: Influence sync and presence rewards, the Intendants, the tithe clock, and the commands
 * (/influence, /interregne intendant, /interregne dime, /interregne edit).
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Politics
{
    private static final int MINUTE = 20 * 60;

    /**
     * Online minutes counted toward each player's next presence point.
     */
    private static final Map<UUID, Integer> MINUTES = new HashMap<>();

    private static int ticks;

    private Politics()
    {
    }

    public static boolean canRule(final CommandSourceStack source)
    {
        return source.hasPermission(2)
                 || source.getPlayer() != null && Empire.get(source.getServer()).isIntendant(source.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            Influence.sync(player);
            player.refreshDisplayName();
            player.refreshTabListName();
        }
    }

    @SubscribeEvent
    public static void onNameFormat(final PlayerEvent.NameFormat event)
    {
        if (event.getEntity() instanceof ServerPlayer player && Empire.get(player.server).isIntendant(player.getUUID()))
        {
            event.setDisplayname(Component.literal("[Intendant] ").withStyle(ChatFormatting.DARK_PURPLE).append(event.getDisplayname()));
        }
    }

    @SubscribeEvent
    public static void onTabName(final PlayerEvent.TabListNameFormat event)
    {
        if (event.getEntity() instanceof ServerPlayer player && Empire.get(player.server).isIntendant(player.getUUID()))
        {
            event.setDisplayName(Component.literal("⚜ ").withStyle(ChatFormatting.DARK_PURPLE).append(player.getName()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (++ticks % MINUTE != 0)
        {
            return;
        }
        final MinecraftServer server = event.getServer();
        rewardPresence(server);
        Tithe.tick(server);
    }

    /**
     * One Influence point per few online minutes, capped per real day so nobody grinds it by idling all night.
     */
    private static void rewardPresence(final MinecraftServer server)
    {
        final Empire empire = Empire.get(server);
        final long day = System.currentTimeMillis() / 86_400_000L;
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            final int minutes = MINUTES.merge(player.getUUID(), 1, Integer::sum);
            if (minutes < Config.PRESENCE_MINUTES.get())
            {
                continue;
            }
            MINUTES.put(player.getUUID(), 0);
            if (empire.presenceToday(player.getUUID(), day) < Config.PRESENCE_CAP.get())
            {
                empire.addPresence(player.getUUID());
                Influence.add(server, player.getUUID(), 1, "présence");
            }
        }
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("influence")
          .executes(context -> showInfluence(context.getSource()))
          .then(Commands.literal("donner").requires(Politics::canRule)
                  .then(Commands.argument("joueur", EntityArgument.player())
                          .then(Commands.argument("montant", IntegerArgumentType.integer(1))
                                  .executes(context -> adjust(context.getSource(), EntityArgument.getPlayer(context, "joueur"), IntegerArgumentType.getInteger(context, "montant"))))))
          .then(Commands.literal("retirer").requires(Politics::canRule)
                  .then(Commands.argument("joueur", EntityArgument.player())
                          .then(Commands.argument("montant", IntegerArgumentType.integer(1))
                                  .executes(context -> adjust(context.getSource(), EntityArgument.getPlayer(context, "joueur"), -IntegerArgumentType.getInteger(context, "montant")))))));

        dispatcher.register(Commands.literal("interregne")
          .then(Commands.literal("intendant").requires(source -> source.hasPermission(2))
                  .then(Commands.literal("ajouter").then(Commands.argument("joueur", EntityArgument.player())
                          .executes(context -> setIntendant(context.getSource(), EntityArgument.getPlayer(context, "joueur"), true))))
                  .then(Commands.literal("retirer").then(Commands.argument("joueur", EntityArgument.player())
                          .executes(context -> setIntendant(context.getSource(), EntityArgument.getPlayer(context, "joueur"), false))))
                  .then(Commands.literal("liste").executes(context -> listIntendants(context.getSource()))))
          .then(Commands.literal("dime")
                  .then(Commands.literal("statut").executes(context -> titheStatus(context.getSource())))
                  .then(Commands.literal("livrer").executes(context -> {
                      Tithe.deliver(context.getSource().getPlayerOrException());
                      return 1;
                  }))
                  .then(Commands.literal("lancer").requires(Politics::canRule).executes(context -> {
                      Tithe.launch(context.getSource().getServer());
                      return 1;
                  }))
                  .then(Commands.literal("annuler").requires(Politics::canRule).executes(context -> {
                      Empire.get(context.getSource().getServer()).endTithe();
                      context.getSource().sendSuccess(() -> Component.literal("Dîme annulée."), true);
                      return 1;
                  })))
          .then(Commands.literal("edit").requires(Politics::canRule)
                  .then(Commands.literal("poids").then(Commands.argument("niveau", StringArgumentType.word())
                          .suggests((context, builder) -> {
                              for (final Empire.TitheWeight weight : Empire.TitheWeight.values())
                              {
                                  builder.suggest(weight.name().toLowerCase(Locale.ROOT));
                              }
                              return builder.buildFuture();
                          })
                          .executes(context -> {
                              Edicts.setTitheWeight(context.getSource().getServer(),
                                Empire.TitheWeight.valueOf(StringArgumentType.getString(context, "niveau").toUpperCase(Locale.ROOT)));
                              return 1;
                          })))
                  .then(Commands.literal("faveur").then(Commands.argument("joueur", EntityArgument.player())
                          .then(Commands.argument("montant", IntegerArgumentType.integer(1)).executes(context -> {
                              Edicts.favor(context.getSource().getServer(), EntityArgument.getPlayer(context, "joueur"), IntegerArgumentType.getInteger(context, "montant"));
                              return 1;
                          }))))
                  .then(Commands.literal("disgrace").then(Commands.argument("joueur", EntityArgument.player())
                          .then(Commands.argument("montant", IntegerArgumentType.integer(1)).executes(context -> {
                              Edicts.disgrace(context.getSource().getServer(), EntityArgument.getPlayer(context, "joueur"), IntegerArgumentType.getInteger(context, "montant"));
                              return 1;
                          }))))));
    }

    private static int showInfluence(final CommandSourceStack source)
    {
        final MinecraftServer server = source.getServer();
        final StringBuilder text = new StringBuilder("✦ Influence");
        if (source.getPlayer() != null)
        {
            text.append(" — la tienne : ").append(Influence.get(server, source.getPlayer().getUUID()));
        }
        final List<Map.Entry<UUID, Long>> ranking = Influence.ranking(server);
        for (int i = 0; i < Math.min(10, ranking.size()); i++)
        {
            final Map.Entry<UUID, Long> entry = ranking.get(i);
            final String name = server.getProfileCache() == null ? entry.getKey().toString()
                                  : server.getProfileCache().get(entry.getKey()).map(profile -> profile.getName()).orElse("?");
            text.append("\n ").append(i + 1).append(". ").append(name).append(" : ").append(entry.getValue());
        }
        source.sendSuccess(() -> Component.literal(text.toString()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int adjust(final CommandSourceStack source, final ServerPlayer target, final int amount)
    {
        final long now = Influence.add(source.getServer(), target.getUUID(), amount, "ajusté par " + source.getTextName());
        source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " a maintenant " + now + " d'Influence."), true);
        return 1;
    }

    private static int setIntendant(final CommandSourceStack source, final ServerPlayer target, final boolean add)
    {
        final Empire empire = Empire.get(source.getServer());
        if (add)
        {
            empire.intendants().add(target.getUUID());
        }
        else
        {
            empire.intendants().remove(target.getUUID());
        }
        empire.markDirty();
        target.refreshDisplayName();
        target.refreshTabListName();
        source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + (add ? " devient Intendant." : " n'est plus Intendant.")), true);
        return 1;
    }

    private static int listIntendants(final CommandSourceStack source)
    {
        final MinecraftServer server = source.getServer();
        final StringBuilder text = new StringBuilder("Intendants :");
        for (final UUID uuid : Empire.get(server).intendants())
        {
            text.append(' ').append(server.getProfileCache() == null ? uuid : server.getProfileCache().get(uuid).map(profile -> profile.getName()).orElse(uuid.toString()));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int titheStatus(final CommandSourceStack source)
    {
        final MinecraftServer server = source.getServer();
        final Empire empire = Empire.get(server);
        final StringBuilder text = new StringBuilder("⚜ Dîme : ");
        if (!empire.titheActive())
        {
            text.append("aucune en cours.");
        }
        else
        {
            text.append(empire.titheWeight().label()).append(", échéance dans ")
              .append(Proclamations.formatDelay(empire.titheDueAt() - System.currentTimeMillis()));
            if (source.getPlayer() != null)
            {
                final IColony colony = Tithe.colonyOf(source.getPlayer());
                if (colony != null)
                {
                    text.append("\nReste à livrer pour ").append(colony.getName()).append(" : ").append(Tithe.describe(Tithe.remaining(server, colony)));
                }
            }
        }
        text.append("\nTrésor impérial : ").append(empire.treasury());
        source.sendSuccess(() -> Component.literal(text.toString()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }
}
