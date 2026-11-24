package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.network.DiplomacyActionPayload;
import fr.kodistan.interregne.routes.RouteRegistry;
import fr.kodistan.interregne.routes.WarRoad;
import fr.kodistan.interregne.war.WarAssaults;
import fr.kodistan.interregne.war.WarMarches;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The rules between colonies: what one may do to another (ally, break, declare war, make peace), what it costs, and
 * what follows. {@link Alliances} and {@link Wars} only keep the state.
 * <p>
 * An alliance and a war exclude each other: war cannot be declared on an ally (break first), and no alliance is
 * proposed or accepted while at war. Breaking an alliance lets the road between the two decay.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Diplomacy
{
    private static final int CHECK_EVERY = 20;

    private Diplomacy()
    {
    }

    public static long noticeTicks()
    {
        return Config.WAR_NOTICE_SECONDS.get() * 20L;
    }

    /**
     * The tie from {@code me} to {@code you} as {status, seconds of notice left} ({@link Alliances} constants).
     */
    public static int[] relation(final MinecraftServer server, final Alliances alliances, final int me, final int you)
    {
        final int status = alliances.status(me, you);
        if (status != Alliances.NONE)
        {
            return new int[] {status, 0};
        }
        final Wars wars = Wars.get(server);
        final long now = server.overworld().getGameTime();
        final boolean out = wars.declaredBy(me, you);
        if (out || wars.declaredBy(you, me))
        {
            final long left = out ? wars.noticeLeft(me, you, now, noticeTicks()) : wars.noticeLeft(you, me, now, noticeTicks());
            final int seconds = (int) ((left + 19) / 20);
            final int base = out ? Alliances.WAR_OUT : Alliances.WAR_IN;
            return new int[] {left > 0 ? base - 1 : base, seconds};
        }
        return new int[] {Alliances.NONE, 0};
    }

    /**
     * Whether {@code attacker} may assault {@code defender}: a war it declared, its notice over.
     */
    public static boolean mayAssault(final MinecraftServer server, final int attacker, final int defender)
    {
        return Wars.get(server).isOpen(attacker, defender, server.overworld().getGameTime(), noticeTicks());
    }

    /**
     * Applies an action of the player's colony toward another.
     *
     * @return a refusal for the player, or null when it went through (or changed nothing)
     */
    @Nullable
    public static String apply(final ServerPlayer player, final IColony colony, final IColony other, final int action)
    {
        final MinecraftServer server = player.server;
        final Alliances alliances = Alliances.get(server);
        final Wars wars = Wars.get(server);
        final int me = colony.getID();
        final int you = other.getID();
        final int before = relation(server, alliances, me, you)[0];
        switch (action)
        {
            case DiplomacyActionPayload.PROPOSE ->
            {
                if (wars.between(me, you))
                {
                    return "Pas d'alliance en temps de guerre : faites d'abord la paix.";
                }
                alliances.doPropose(me, you);
            }
            case DiplomacyActionPayload.ACCEPT ->
            {
                if (wars.between(me, you))
                {
                    return "Pas d'alliance en temps de guerre : faites d'abord la paix.";
                }
                alliances.doAccept(me, you);
            }
            case DiplomacyActionPayload.WITHDRAW -> alliances.doWithdraw(me, you);
            // Les routes restent après une rupture (décision 19/11) : le lien est gardé pour une réalliance.
            case DiplomacyActionPayload.BREAK -> alliances.doBreak(me, you);
            case DiplomacyActionPayload.DECLARE_WAR ->
            {
                final String refusal = declareWar(player, wars, alliances, colony, other);
                if (refusal != null)
                {
                    return refusal;
                }
            }
            case DiplomacyActionPayload.MAKE_PEACE ->
            {
                if (!wars.end(me, you))
                {
                    return wars.declaredBy(you, me) ? "Seul celui qui a déclaré la guerre peut y mettre fin." : null;
                }
            }
            case DiplomacyActionPayload.CAPITULATE ->
            {
                return capitulate(server, wars, colony, other);
            }
            default ->
            {
            }
        }
        final int after = relation(server, alliances, me, you)[0];
        if (after != before)
        {
            tell(server, colony, other, after);
            Alliances.broadcast(server, colony, other);
        }
        return null;
    }

    @Nullable
    private static String declareWar(final ServerPlayer player, final Wars wars, final Alliances alliances, final IColony colony, final IColony other)
    {
        final int me = colony.getID();
        final int you = other.getID();
        if (alliances.areAllied(me, you))
        {
            return "Vous êtes alliés : rompez l'alliance avant de déclarer la guerre.";
        }
        if (wars.between(me, you))
        {
            return "Il y a déjà une guerre entre ces deux colonies.";
        }
        final long shield = wars.shieldLeft(you, player.server.overworld().getGameTime());
        if (shield > 0)
        {
            return other.getName() + " vient de perdre une guerre : elle est sous bouclier encore " + (int) Math.ceil(shield / 1200.0) + " min.";
        }
        final long cost = Config.WAR_DECLARATION_COST.get();
        if (Influence.get(player.server, player.getUUID()) < cost)
        {
            return "Déclarer la guerre coûte " + cost + " d'Influence : il vous en manque.";
        }
        // Sans route entre les deux colonies, la déclaration en trace une (payée par le trésor du déclarant).
        final boolean needsRoad = RouteRegistry.get(player.server).roadBetween(me, you) < 0 && !WarRoad.building(me, you);
        final long roadCost = needsRoad ? Config.WAR_ROAD_COST.get() : 0L;
        final RegionalTreasury treasury = RegionalTreasury.get(player.server);
        if (roadCost > 0 && !treasury.spend(colony, roadCost))
        {
            return "Il faut " + roadCost + " couronnes au trésor de la colonie pour la route de guerre.";
        }
        Influence.remove(player.server, player.getUUID(), cost, "déclaration de guerre");
        alliances.clearBetween(me, you);
        wars.declare(me, you, player.server.overworld().getGameTime());
        if (needsRoad)
        {
            WarRoad.start(player.serverLevel(), colony, other, roadCost);
        }
        return null;
    }

    /**
     * The defender ends the war it suffers by paying the tribute from its treasury to the attacker's. Counts as a loss:
     * the attacker's troops go home and the defender gets the shield.
     */
    @Nullable
    private static String capitulate(final MinecraftServer server, final Wars wars, final IColony colony, final IColony attacker)
    {
        if (!wars.declaredBy(attacker.getID(), colony.getID()))
        {
            return "Seule la colonie attaquée peut capituler.";
        }
        final long tribute = Config.WAR_TRIBUTE.get();
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        final long balance = treasury.balance(colony);
        if (tribute > 0 && !treasury.spend(colony, tribute))
        {
            return "Le tribut est de " + tribute + " couronnes : il en manque " + (tribute - balance) + " au trésor.";
        }
        treasury.credit(attacker, tribute);
        if (WarMarches.marching(attacker.getID(), colony.getID()))
        {
            WarMarches.recall(server, attacker, colony.getID());
        }
        if (WarAssaults.assaulting(attacker.getID(), colony.getID()))
        {
            WarAssaults.recall(attacker, colony.getID());
        }
        settle(server, attacker, colony, "§e" + colony.getName() + " capitule§r devant " + attacker.getName() + " et lui verse " + tribute + " couronnes de tribut.");
        return null;
    }

    /**
     * The attacker won by force: called when the defender's town hall falls.
     */
    public static void victory(final MinecraftServer server, final IColony winner, final IColony loser)
    {
        settle(server, winner, loser, "§c" + loser.getName() + " est tombée§r : " + winner.getName() + " a pris son hôtel de ville.");
    }

    /**
     * Ends a war that the attacker won: shield for the loser, Influence for the winner's owner, news for everyone.
     */
    private static void settle(final MinecraftServer server, final IColony winner, final IColony loser, final String news)
    {
        final Wars wars = Wars.get(server);
        if (!wars.end(winner.getID(), loser.getID()))
        {
            return;
        }
        final long shieldTicks = Config.WAR_SHIELD_SECONDS.get() * 20L;
        wars.shield(loser.getID(), server.overworld().getGameTime() + shieldTicks);
        final long influence = Config.WAR_VICTORY_INFLUENCE.get();
        final java.util.UUID owner = winner.getPermissions().getOwner();
        if (influence > 0 && owner != null)
        {
            Influence.add(server, owner, influence, "victoire sur " + loser.getName());
        }
        Proclamations.announce(server, news);
        final int minutes = (int) Math.ceil(shieldTicks / 1200.0);
        notifyMembers(server, winner, "Guerre gagnée contre " + loser.getName() + (influence > 0 ? " : +" + influence + " d'Influence." : "."));
        notifyMembers(server, loser, "Guerre perdue contre " + winner.getName() + " : personne ne peut vous déclarer la guerre pendant " + minutes + " min.");
        Alliances.broadcast(server, winner, loser);
    }

    /**
     * Tells both colonies' online members what just changed.
     */
    private static void tell(final MinecraftServer server, final IColony colony, final IColony other, final int status)
    {
        final String toOther;
        final String toMe;
        switch (status)
        {
            case Alliances.SENT ->
            {
                toOther = colony.getName() + " vous propose une alliance. Répondez-lui depuis l'onglet Diplomatie de l'hôtel de ville.";
                toMe = null;
            }
            case Alliances.ALLIED ->
            {
                toOther = "Alliance conclue entre " + colony.getName() + " et " + other.getName() + ".";
                toMe = toOther;
            }
            case Alliances.WAR_OUT_NOTICE, Alliances.WAR_OUT ->
            {
                final int minutes = (int) Math.ceil(Config.WAR_NOTICE_SECONDS.get() / 60.0);
                toOther = "§c" + colony.getName() + " vous déclare la guerre§r : assauts possibles dans " + minutes + " min.";
                toMe = "Guerre déclarée à " + other.getName() + " : assauts possibles dans " + minutes + " min.";
            }
            default ->
            {
                toOther = "Les liens avec " + colony.getName() + " ont changé : plus d'alliance, de proposition ni de guerre.";
                toMe = null;
            }
        }
        notifyMembers(server, other, toOther);
        if (toMe != null)
        {
            notifyMembers(server, colony, toMe);
        }
    }

    public static void notifyMembers(final MinecraftServer server, final IColony colony, final String text)
    {
        for (final ServerPlayer member : server.getPlayerList().getPlayers())
        {
            if (colony.getPermissions().isColonyMember(member))
            {
                member.sendSystemMessage(Component.literal(text));
            }
        }
    }

    /**
     * Announces a war when its notice runs out, and refreshes both colonies' pages.
     */
    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 0 || server.overworld() == null)
        {
            return;
        }
        final Wars wars = Wars.get(server);
        for (final int[] war : wars.takeNewlyOpen(server.overworld().getGameTime(), noticeTicks()))
        {
            final IColony attacker = IColonyManager.getInstance().getColonyByWorld(war[0], server.overworld());
            final IColony defender = IColonyManager.getInstance().getColonyByWorld(war[1], server.overworld());
            if (attacker == null || defender == null)
            {
                continue;
            }
            notifyMembers(server, attacker, "§cLa guerre contre " + defender.getName() + " est ouverte§r : vous pouvez l'assaillir.");
            notifyMembers(server, defender, "§cLa guerre avec " + attacker.getName() + " est ouverte§r : préparez vos défenses.");
            Alliances.broadcast(server, attacker, defender);
        }
    }

    /**
     * Test and admin shortcuts, bypassing rank and cost:
     * {@code /diplomatie allier|rompre|guerre|paix|etat <colonie> <colonie>}; {@code guerre} takes an optional
     * {@code immediat} flag that skips the notice.
     */
    @SubscribeEvent
    public static void onCommands(final RegisterCommandsEvent event)
    {
        final var root = literal("diplomatie").requires(source -> source.hasPermission(2));
        for (final String verb : new String[] {"allier", "rompre", "guerre", "paix", "etat"})
        {
            root.then(literal(verb)
              .then(argument("a", IntegerArgumentType.integer(1))
                .then(argument("b", IntegerArgumentType.integer(1))
                  .executes(ctx -> admin(ctx, verb, false))
                  .then(argument("immediat", BoolArgumentType.bool()).executes(ctx -> admin(ctx, verb, BoolArgumentType.getBool(ctx, "immediat")))))));
        }
        event.getDispatcher().register(root);
    }

    private static int admin(final CommandContext<CommandSourceStack> ctx, final String verb, final boolean immediate)
    {
        final MinecraftServer server = ctx.getSource().getServer();
        final var level = server.overworld();
        final IColony a = IColonyManager.getInstance().getColonyByWorld(IntegerArgumentType.getInteger(ctx, "a"), level);
        final IColony b = IColonyManager.getInstance().getColonyByWorld(IntegerArgumentType.getInteger(ctx, "b"), level);
        if (a == null || b == null || a.getID() == b.getID())
        {
            ctx.getSource().sendFailure(Component.literal("Deux colonies différentes de l'overworld sont attendues (numéros)."));
            return 0;
        }
        final Alliances alliances = Alliances.get(server);
        final Wars wars = Wars.get(server);
        final int me = a.getID();
        final int you = b.getID();
        switch (verb)
        {
            case "allier" ->
            {
                wars.end(me, you);
                wars.end(you, me);
                alliances.doPropose(me, you);
                alliances.doAccept(you, me);
            }
            case "rompre" -> alliances.doBreak(me, you);
            case "guerre" ->
            {
                alliances.clearBetween(me, you);
                wars.declare(me, you, immediate ? level.getGameTime() - noticeTicks() : level.getGameTime());
            }
            case "paix" ->
            {
                wars.end(me, you);
                wars.end(you, me);
            }
            default ->
            {
            }
        }
        Alliances.broadcast(server, a, b);
        final int[] relation = relation(server, alliances, me, you);
        final String[] names = {"aucun lien", "alliés", "proposition envoyée", "proposition reçue", "guerre déclarée (préavis)", "en guerre (attaquant)",
          "guerre subie (préavis)", "en guerre (défenseur)"};
        ctx.getSource().sendSuccess(() -> Component.literal(a.getName() + " → " + b.getName() + " : " + names[relation[0]]
                                                              + (relation[1] > 0 ? " (" + relation[1] + " s)" : "")).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
