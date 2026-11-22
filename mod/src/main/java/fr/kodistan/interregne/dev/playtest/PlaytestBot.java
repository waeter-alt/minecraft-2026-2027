package fr.kodistan.interregne.dev.playtest;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.commerce.Contract;
import fr.kodistan.interregne.commerce.Contracts;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Playtest bot: plays one colony so a human can trade against it without a second client.
 * <p>
 * It is a "logical actor": it never logs in. It reuses the admin paths of {@code /commerce} and {@code /diplomatie}, so rules those skip
 * (influence cost, rank checks) are not tested here and stay a human check. Commands: {@code /playtest start <humain> <bot>}, {@code stop}, {@code etat}.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class PlaytestBot
{
    private static final int PERIOD_TICKS = 100;
    private static final int MAX_EVENTS = 20;
    private static final long TREASURY = 5000;
    private static final long UNIT_PRICE = 10;
    private static final int OFFER_QUANTITY = 5;
    // 1 only: a Relais level 1 allows 2 contracts per colony and the bot's open offers count against the human's slots too
    private static final int MAX_OPEN_OFFERS = 1;
    private static final int MIN_STOCK = 10;
    private static final int RESTOCK = 30;
    private static final Crate[] CRATES = {Crate.byId("bois"), Crate.byId("ble"), Crate.byId("pierre")};

    private static final Deque<String> EVENTS = new ArrayDeque<>();
    private static int human = -1;
    private static int bot = -1;
    private static int offersMade;
    private static String lastRefusal = "";
    private static int tick;

    private PlaytestBot()
    {
    }

    @SubscribeEvent
    public static void onCommands(final RegisterCommandsEvent event)
    {
        final var root = literal("playtest").requires(source -> source.hasPermission(2));
        root.then(literal("start").then(argument("humain", IntegerArgumentType.integer(1)).then(argument("bot", IntegerArgumentType.integer(1)).executes(ctx ->
          start(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "humain"), IntegerArgumentType.getInteger(ctx, "bot"), ctx.getSource())))));
        root.then(literal("stop").executes(ctx ->
        {
            human = -1;
            bot = -1;
            ctx.getSource().sendSuccess(() -> Component.literal("Bot arrêté."), false);
            return 1;
        }));
        root.then(literal("hutte").then(argument("colonie", IntegerArgumentType.integer(1)).then(argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
          .then(argument("niveau", IntegerArgumentType.integer(1, 5)).executes(ctx -> hut(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "colonie"),
            com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "niveau")))))));
        root.then(literal("fonder").then(argument("nom", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx ->
          found(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "nom")))));
        root.then(literal("inspecter").then(argument("colonie", IntegerArgumentType.integer(1)).executes(ctx -> inspect(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "colonie")))));
        root.then(literal("etat").executes(ctx ->
        {
            ctx.getSource().sendSuccess(() -> Component.literal(report(ctx.getSource().getServer())), false);
            return 1;
        }));
        event.getDispatcher().register(root);
    }

    private static final String TOWNHALL_PACK = "Minecolonies Original";
    private static final String TOWNHALL_PATH = "fundamentals/townhall1.blueprint";
    /** The dev player of the test client: the colony is theirs, so the client opens it as its owner. */
    private static final com.mojang.authlib.GameProfile DEV = new com.mojang.authlib.GameProfile(java.util.UUID.fromString("380df991-f603-344c-a090-369bad2a924a"), "Dev");

    private static net.minecraft.core.BlockPos foundAt;
    private static String foundName;
    private static int foundWait;

    /**
     * Sets a town hall hut block at the world spawn and founds a colony on it at level 1, owned by the dev player, for a test world from zero.
     */
    private static int found(final net.minecraft.commands.CommandSourceStack source, final String name)
    {
        final var world = source.getServer().overworld();
        final var spawn = world.getSharedSpawnPos();
        world.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        final var pos = new net.minecraft.core.BlockPos(spawn.getX(), world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ());
        // Bare hut block only: Structurize's placement queue does nothing for a fake player on a dedicated server.
        final var block = com.minecolonies.api.blocks.ModBlocks.blockHutTownHall;
        final var state = block.defaultBlockState();
        world.setBlock(pos, state, 3);
        block.setPlacedBy(world, pos, state, null, net.minecraft.world.item.ItemStack.EMPTY);
        foundAt = pos;
        foundName = name;
        foundWait = 0;
        source.sendSuccess(() -> Component.literal("Hôtel de ville en construction en " + pos.toShortString() + ", fondation dès qu'il est posé."), false);
        return 1;
    }

    private static void tickFounding(final MinecraftServer server)
    {
        final var world = server.overworld();
        if (!(world.getBlockEntity(foundAt) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding hut))
        {
            if (++foundWait > 1200)
            {
                InterregneMod.LOGGER.warn("[Playtest] Pas d'hôtel de ville en {} après 60 s, fondation abandonnée", foundAt);
                foundAt = null;
            }
            return;
        }
        final var pos = foundAt;
        foundAt = null;
        hut.setStructurePack(com.ldtteam.structurize.storage.StructurePacks.getStructurePack(TOWNHALL_PACK));
        hut.setBlueprintPath(TOWNHALL_PATH);
        final var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(world, DEV);
        final IColony colony = IColonyManager.getInstance().createColony(world, pos, player, foundName, TOWNHALL_PACK);
        final var building = colony.getBuildingManager().addNewBuilding(hut, world);
        if (building != null && building.getBuildingLevel() < 1)
        {
            building.setBuildingLevel(1);
        }
        com.minecolonies.api.IMinecoloniesAPI.getInstance().getEventBus().post(new com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent(colony));
        InterregneMod.LOGGER.info("[Playtest] Colonie {} fondée en {} (id {}), hôtel de ville niveau {}", foundName, pos, colony.getID(),
          building == null ? "?" : building.getBuildingLevel());
    }

    /**
     * Sets a bank, a depot or a relais of this level near the colony's centre, registered as a building (to see their hut windows).
     */
    private static int hut(final net.minecraft.commands.CommandSourceStack source, final int colonyId, final String type, final int level)
    {
        final var server = source.getServer();
        final var world = server.overworld();
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, world);
        final net.minecraft.world.level.block.Block block = switch (type)
        {
            case "banque" -> fr.kodistan.interregne.economy.RegionalBanks.BANK.get();
            case "depot" -> fr.kodistan.interregne.region.Crates.DEPOT.get();
            case "relais" -> fr.kodistan.interregne.commerce.Commerce.RELAIS_BLOCK.get();
            default -> null;
        };
        if (colony == null || block == null)
        {
            source.sendFailure(Component.literal("Colonie inconnue ou type inconnu (banque, depot, relais)."));
            return 0;
        }
        final int dx = switch (type) { case "banque" -> 8; case "depot" -> -8; default -> 8; };
        final int dz = switch (type) { case "banque" -> -8; case "depot" -> 8; default -> 8; };
        final var centre = colony.getCenter();
        final int x = centre.getX() + dx;
        final int z = centre.getZ() + dz;
        world.getChunk(x >> 4, z >> 4);
        final net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        final var state = block.defaultBlockState();
        world.setBlock(pos, state, 3);
        block.setPlacedBy(world, pos, state, null, net.minecraft.world.item.ItemStack.EMPTY);
        final var building = colony.getBuildingManager().getBuilding(pos);
        if (building == null)
        {
            source.sendFailure(Component.literal("Pas reconnu par la colonie en " + pos.toShortString()));
            return 0;
        }
        building.setBuildingLevel(level);
        source.sendSuccess(() -> Component.literal(type + " posé en " + pos.toShortString() + ", niveau " + level), false);
        return 1;
    }

    /**
     * Lists the buildings of a colony with their class, level and position, and what the bank and depot lookups find.
     */
    private static int inspect(final net.minecraft.commands.CommandSourceStack source, final int colonyId)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getServer().overworld());
        if (colony == null)
        {
            source.sendFailure(Component.literal("Colonie inconnue."));
            return 0;
        }
        for (final var building : colony.getBuildingManager().getBuildings().values())
        {
            final String line = building.getBuildingType().getRegistryName().getPath() + " " + building.getClass().getSimpleName() + " niv " + building.getBuildingLevel()
                                  + " en " + building.getPosition().toShortString();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        final var bank = fr.kodistan.interregne.economy.RegionalBank.of(colony);
        final var depot = fr.kodistan.interregne.region.DepotRegional.of(colony);
        source.sendSuccess(() -> Component.literal("RegionalBank.of -> " + (bank == null ? "null" : bank.getPosition().toShortString()) + " ; DepotRegional.of -> "
                                                     + (depot == null ? "null" : depot.getPosition().toShortString())), false);
        return 1;
    }

    @SubscribeEvent
    public static void onStopping(final ServerStoppingEvent event)
    {
        human = -1;
        bot = -1;
        EVENTS.clear();
    }

    private static int start(final MinecraftServer server, final int humanId, final int botId, final net.minecraft.commands.CommandSourceStack source)
    {
        final var level = server.overworld();
        final IColony h = IColonyManager.getInstance().getColonyByWorld(humanId, level);
        final IColony b = IColonyManager.getInstance().getColonyByWorld(botId, level);
        if (h == null || b == null || humanId == botId)
        {
            source.sendFailure(Component.literal("Deux colonies différentes de l'overworld sont attendues (numéros)."));
            return 0;
        }
        human = humanId;
        bot = botId;
        offersMade = 0;
        EVENTS.clear();
        // contracts survive in the world: drop the bot's stale open offers so every run starts from the same state
        final Contracts contracts = Contracts.get(server);
        for (final Contract stale : new java.util.ArrayList<>(contracts.all()))
        {
            if (stale.state == Contract.OFFER && stale.seller == botId)
            {
                contracts.cancel(null, b, stale.id);
            }
        }
        // alliance and relais are prerequisites of trade; the road is not built here (use a world that already has one)
        run(server, "diplomatie allier " + humanId + " " + botId + " true");
        for (final IColony colony : new IColony[] {h, b})
        {
            if (Contracts.relaisLevel(colony) < 1)
            {
                run(server, "commerce relais " + colony.getID() + " 1");
            }
            run(server, "commerce tresor " + colony.getID() + " " + TREASURY);
        }
        if (!Contracts.mayTrade(server, humanId, botId))
        {
            event("ATTENTION : alliance posée mais pas de route entre " + humanId + " et " + botId + " : aucun contrat possible.");
        }
        event("Bot lancé : il joue la colonie " + botId + " (" + b.getName() + ") face à la colonie " + humanId + " (" + h.getName() + ").");
        source.sendSuccess(() -> Component.literal("Bot lancé. Suivi : /playtest etat"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onTick(final ServerTickEvent.Post event)
    {
        if (foundAt != null)
        {
            tickFounding(event.getServer());
        }
        if (bot < 0 || ++tick % PERIOD_TICKS != 0)
        {
            return;
        }
        final MinecraftServer server = event.getServer();
        final IColony botColony = IColonyManager.getInstance().getColonyByWorld(bot, server.overworld());
        final IColony humanColony = IColonyManager.getInstance().getColonyByWorld(human, server.overworld());
        if (botColony == null || humanColony == null)
        {
            event("Colonie introuvable : bot arrêté.");
            bot = -1;
            return;
        }
        final Contracts contracts = Contracts.get(server);
        int open = 0;
        for (final Contract contract : contracts.all())
        {
            if (contract.state != Contract.OFFER)
            {
                continue;
            }
            if (contract.buyer == bot)
            {
                // the human offers to the bot: it always accepts, which also exercises the buyer-side flow
                final String refusal = contracts.accept(null, server, botColony, contract.id);
                event(refusal == null ? "A accepté l'offre #" + contract.id + " (" + contract.quantity + " " + contract.crate + ")."
                        : "N'a pas pu accepter #" + contract.id + " : " + refusal);
            }
            else if (contract.seller == bot)
            {
                open++;
            }
        }
        final Crate crate = CRATES[offersMade % CRATES.length];
        // the game counts every contract of a colony (offers, caravans, both directions) against its capacity: don't even try when full
        final boolean room = contracts.of(bot).size() < Contracts.capacity(botColony) && contracts.of(human).size() < Contracts.capacity(humanColony);
        if (open < MAX_OPEN_OFFERS && room && crate != null)
        {
            if (Contracts.stockOf(botColony, crate) < MIN_STOCK)
            {
                Contracts.store(botColony, crate, RESTOCK);
            }
            final String refusal = contracts.offer(null, server, botColony, humanColony, crate, OFFER_QUANTITY, UNIT_PRICE, 0);
            if (refusal == null)
            {
                offersMade++;
                event("A proposé " + OFFER_QUANTITY + " " + crate.id() + " à " + UNIT_PRICE + " pièce l'unité (offre n° " + offersMade + ").");
            }
            else
            {
                offersMade++;
                // same refusal as last time: stay quiet, otherwise the ops' chat is flooded every 5 s
                if (!refusal.equals(lastRefusal))
                {
                    event("Offre refusée par le jeu : " + refusal);
                    lastRefusal = refusal;
                }
            }
        }
    }

    private static String report(final MinecraftServer server)
    {
        final StringBuilder out = new StringBuilder();
        out.append(bot < 0 ? "Bot inactif." : "Bot actif sur la colonie " + bot + " face à " + human + ".");
        for (final Contract c : Contracts.get(server).all())
        {
            if (c.seller == bot || c.buyer == bot)
            {
                out.append("\n#").append(c.id).append(' ').append(c.seller).append("→").append(c.buyer).append(' ').append(c.quantity).append('×').append(c.crate)
                  .append(" état ").append(c.state);
            }
        }
        for (final String line : EVENTS)
        {
            out.append("\n- ").append(line);
        }
        return out.toString();
    }

    private static void event(final String text)
    {
        if (EVENTS.size() >= MAX_EVENTS)
        {
            EVENTS.removeFirst();
        }
        EVENTS.addLast(text);
        InterregneMod.LOGGER.info("[Playtest] {}", text);
        final MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null)
        {
            final Component message = Component.literal("[Playtest] " + text).withStyle(ChatFormatting.AQUA);
            for (final ServerPlayer player : server.getPlayerList().getPlayers())
            {
                if (player.hasPermissions(2))
                {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    private static void run(final MinecraftServer server, final String command)
    {
        try
        {
            server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
        }
        catch (final CommandSyntaxException e)
        {
            event("Commande échouée : /" + command + " : " + e.getMessage());
        }
    }
}
