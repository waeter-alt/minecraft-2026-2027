package fr.kodistan.interregne.commerce;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.region.Crate;
import fr.kodistan.interregne.region.CrateStores;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Admin and test shortcuts for trade, bypassing membership and the player's purse:
 * {@code /commerce relais|caisses|tresor|offre|accepter|etat}; the offer takes an escort tier (0 to 5) as a last argument, colonies given by number.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class CommerceCommands
{
    private CommerceCommands()
    {
    }

    @SubscribeEvent
    public static void onCommands(final RegisterCommandsEvent event)
    {
        final var root = literal("commerce").requires(source -> source.hasPermission(2));
        root.then(literal("relais").then(argument("colonie", IntegerArgumentType.integer(1)).then(argument("niveau", IntegerArgumentType.integer(1, 5))
          .executes(CommerceCommands::relais))));
        root.then(literal("chantier").then(argument("colonie", IntegerArgumentType.integer(1)).executes(ctx -> site(ctx, 1))
          .then(argument("niveau", IntegerArgumentType.integer(1, 5)).executes(ctx -> site(ctx, IntegerArgumentType.getInteger(ctx, "niveau"))))));
        root.then(literal("caisses").then(argument("colonie", IntegerArgumentType.integer(1)).then(argument("type", StringArgumentType.word())
          .then(argument("nombre", IntegerArgumentType.integer(1, 4096)).executes(CommerceCommands::crates)))));
        root.then(literal("tresor").then(argument("colonie", IntegerArgumentType.integer(1)).then(argument("montant", LongArgumentType.longArg(0))
          .executes(CommerceCommands::treasury))));
        root.then(literal("offre").then(argument("vendeur", IntegerArgumentType.integer(1)).then(argument("acheteur", IntegerArgumentType.integer(1))
          .then(argument("type", StringArgumentType.word()).then(argument("nombre", IntegerArgumentType.integer(1, Contracts.MAX_QUANTITY))
            .then(argument("prix", LongArgumentType.longArg(1)).executes(CommerceCommands::offer)
              .then(argument("escorte", IntegerArgumentType.integer(0, Escort.MAX_TIER)).executes(CommerceCommands::offer))))))));
        root.then(literal("accepter").then(argument("acheteur", IntegerArgumentType.integer(1)).then(argument("contrat", LongArgumentType.longArg(1))
          .executes(CommerceCommands::accept))));
        root.then(literal("retirer").then(argument("colonie", IntegerArgumentType.integer(1)).then(argument("contrat", LongArgumentType.longArg(1))
          .executes(CommerceCommands::withdraw))));
        root.then(literal("empire").then(literal("marches").executes(CommerceCommands::markets))
          .then(literal("poser").executes(ctx -> placeMarket(ctx, null))
            .then(argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
              .executes(ctx -> placeMarket(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "pos")))))
          .then(argument("colonie", IntegerArgumentType.integer(1)).then(literal("route").executes(CommerceCommands::imperialRoute))
            .then(literal("vendre").then(argument("type", StringArgumentType.word()).then(argument("nombre", IntegerArgumentType.integer(1, Contracts.MAX_QUANTITY))
              .executes(CommerceCommands::sellImperial))))));
        root.then(literal("etat").executes(CommerceCommands::state));
        event.getDispatcher().register(root);
    }

    private static IColony colony(final CommandContext<CommandSourceStack> ctx, final String name)
    {
        return IColonyManager.getInstance().getColonyByWorld(IntegerArgumentType.getInteger(ctx, name), ctx.getSource().getServer().overworld());
    }

    private static int say(final CommandContext<CommandSourceStack> ctx, final String text)
    {
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int fail(final CommandContext<CommandSourceStack> ctx, final String text)
    {
        ctx.getSource().sendFailure(Component.literal(text));
        return 0;
    }

    /**
     * Sets a Relais commercial of this level near the colony's centre, registered as a building.
     */
    private static int relais(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony colony = colony(ctx, "colonie");
        if (colony == null)
        {
            return fail(ctx, "Colonie inconnue.");
        }
        final ServerLevel level = ctx.getSource().getServer().overworld();
        final int wanted = IntegerArgumentType.getInteger(ctx, "niveau");
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingRelaisCommercial)
            {
                building.setBuildingLevel(wanted);
                return say(ctx, "Relais de " + colony.getName() + " mis au niveau " + wanted + ".");
            }
        }
        final BlockPos centre = colony.getCenter();
        final int x = centre.getX() + 8;
        final int z = centre.getZ() + 8;
        level.getChunk(x >> 4, z >> 4);
        final BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        final BlockState state = Commerce.RELAIS_BLOCK.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        Commerce.RELAIS_BLOCK.get().setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
        final IBuilding building = colony.getBuildingManager().getBuilding(pos);
        if (building == null)
        {
            return fail(ctx, "Le Relais n'a pas été reconnu par la colonie (hors de son territoire ?) en " + pos.toShortString());
        }
        building.setBuildingLevel(wanted);
        return say(ctx, "Relais posé pour " + colony.getName() + " en " + pos.toShortString() + ", niveau " + wanted + ".");
    }

    /**
     * Places an unbuilt Relais commercial with the blueprint pack and asks for its construction, the way the build tool does. The level (1 by default) picks which blueprint is built, for testing the five looks.
     */
    private static int site(final CommandContext<CommandSourceStack> ctx, final int wanted)
    {
        final IColony colony = colony(ctx, "colonie");
        if (colony == null)
        {
            return fail(ctx, "Colonie inconnue.");
        }
        final ServerLevel level = ctx.getSource().getServer().overworld();
        final BlockPos centre = colony.getCenter();
        final int x = centre.getX() - 12;
        final int z = centre.getZ() - 12;
        level.getChunk(x >> 4, z >> 4);
        final BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        final BlockState state = Commerce.RELAIS_BLOCK.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        final var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "commerce-test"));
        if (level.getBlockEntity(pos) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding hut)
        {
            hut.setStructurePack(com.ldtteam.structurize.storage.StructurePacks.getStructurePack("Couronne Vacante"));
            hut.setBlueprintPath("commerce/relais" + wanted + ".blueprint");
        }
        Commerce.RELAIS_BLOCK.get().setPlacedBy(level, pos, state, player, ItemStack.EMPTY);
        final IBuilding building = colony.getBuildingManager().getBuilding(pos);
        if (building == null)
        {
            return fail(ctx, "Le Relais n'a pas été reconnu par la colonie en " + pos.toShortString());
        }
        // the work order builds the blueprint of the level after the current one: start just below the wanted level
        building.setBuildingLevel(wanted - 1);
        // a building cannot outgrow the town hall: test shortcut, raise it when it is too low
        final var townHall = colony.getBuildingManager().getTownHall();
        if (townHall != null && townHall.getBuildingLevel() < wanted)
        {
            townHall.setBuildingLevel(wanted);
        }
        building.requestUpgrade(player, BlockPos.ZERO);
        return say(ctx, "Chantier du Relais demandé en " + pos.toShortString() + " (niveau actuel " + building.getBuildingLevel() + ").");
    }

    private static int crates(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony colony = colony(ctx, "colonie");
        final Crate crate = Crate.byId(StringArgumentType.getString(ctx, "type"));
        if (colony == null || crate == null)
        {
            return fail(ctx, "Colonie ou type de caisse inconnu (bois, brique, ble, pierre, minerai).");
        }
        // Contracts loads the warehouse chunk: reuse its access
        final int count = IntegerArgumentType.getInteger(ctx, "nombre");
        final int stored = Contracts.store(colony, crate, count);
        return say(ctx, stored + " / " + count + " caisses rangées à l'entrepôt de " + colony.getName() + ".");
    }

    /**
     * Sets a Marché impérial: at the given position, or on the ground a few blocks east of the world spawn (so it works from rcon, which has no player).
     * The block registers itself in {@link MarketSites} when placed.
     */
    private static int placeMarket(final CommandContext<CommandSourceStack> ctx, @org.jetbrains.annotations.Nullable final BlockPos given)
    {
        final ServerLevel level = ctx.getSource().getServer().overworld();
        final BlockPos target;
        if (given == null)
        {
            final BlockPos spawn = level.getSharedSpawnPos();
            // Outside spawn protection: there a non-op player's right-click on the block never reaches it.
            final int x = spawn.getX() + ctx.getSource().getServer().getSpawnProtectionRadius() + 4;
            final int z = spawn.getZ();
            level.getChunk(x >> 4, z >> 4);
            target = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        else
        {
            level.getChunk(given.getX() >> 4, given.getZ() >> 4);
            target = given;
        }
        level.setBlock(target, fr.kodistan.interregne.region.Crates.MARCHE.get().defaultBlockState(), 3);
        fr.kodistan.interregne.region.MarketSites.get(ctx.getSource().getServer()).add(target.immutable());
        return say(ctx, "Marché impérial posé en " + target.toShortString() + ".");
    }

    private static int markets(final CommandContext<CommandSourceStack> ctx)
    {
        final var docks = ImperialGate.docks(ctx.getSource().getServer(), BlockPos.containing(ctx.getSource().getPosition()));
        if (docks.isEmpty())
        {
            return say(ctx, "Aucun Marché impérial connu (pose-en un, ou ouvre-en un une fois).");
        }
        for (final ImperialGate.Dock dock : docks)
        {
            say(ctx, "Marché en " + dock.pos().toShortString() + ", à " + dock.distance() + " blocs.");
        }
        return docks.size();
    }

    /**
     * Opens the colony's road to the nearest Marché impérial, paid by its treasury like in the Relais.
     */
    private static int imperialRoute(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony colony = colony(ctx, "colonie");
        if (colony == null)
        {
            return fail(ctx, "Colonie inconnue.");
        }
        final var server = ctx.getSource().getServer();
        final BlockPos market = ImperialGate.find(server, colony, 0);
        if (market == null)
        {
            return fail(ctx, "Aucun Marché impérial connu.");
        }
        final String refusal = ImperialRoutes.get(server).open(server.overworld(), colony, market);
        return refusal == null ? say(ctx, "Route impériale lancée vers " + market.toShortString() + ".") : fail(ctx, refusal);
    }

    private static int sellImperial(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony colony = colony(ctx, "colonie");
        final Crate crate = Crate.byId(StringArgumentType.getString(ctx, "type"));
        if (colony == null || crate == null)
        {
            return fail(ctx, "Colonie ou type de caisse inconnu.");
        }
        final var server = ctx.getSource().getServer();
        final BlockPos market = ImperialGate.find(server, colony, 0);
        if (market == null)
        {
            return fail(ctx, "Aucun Marché impérial connu.");
        }
        final String refusal = Contracts.get(server).sellImperial(null, server, colony, market, crate, IntegerArgumentType.getInteger(ctx, "nombre"));
        return refusal == null ? say(ctx, "Caravane impériale partie.") : fail(ctx, refusal);
    }

    private static int treasury(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony colony = colony(ctx, "colonie");
        if (colony == null)
        {
            return fail(ctx, "Colonie inconnue.");
        }
        final long amount = LongArgumentType.getLong(ctx, "montant");
        RegionalTreasury.get(ctx.getSource().getServer()).credit(colony, amount);
        return say(ctx, amount + " versés au trésor régional de " + colony.getName() + ".");
    }

    private static int offer(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony seller = colony(ctx, "vendeur");
        final IColony buyer = colony(ctx, "acheteur");
        final Crate crate = Crate.byId(StringArgumentType.getString(ctx, "type"));
        if (seller == null || buyer == null || crate == null)
        {
            return fail(ctx, "Colonie ou type de caisse inconnu.");
        }
        final String refusal = Contracts.get(ctx.getSource().getServer()).offer(null, ctx.getSource().getServer(), seller, buyer, crate,
          IntegerArgumentType.getInteger(ctx, "nombre"), LongArgumentType.getLong(ctx, "prix"), optional(ctx, "escorte"));
        return refusal == null ? say(ctx, "Offre faite.") : fail(ctx, refusal);
    }

    private static int optional(final CommandContext<CommandSourceStack> ctx, final String name)
    {
        try
        {
            return IntegerArgumentType.getInteger(ctx, name);
        }
        catch (final IllegalArgumentException e)
        {
            return 0;
        }
    }

    private static int withdraw(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony colony = colony(ctx, "colonie");
        if (colony == null)
        {
            return fail(ctx, "Colonie inconnue.");
        }
        final String refusal = Contracts.get(ctx.getSource().getServer()).cancel(null, colony, LongArgumentType.getLong(ctx, "contrat"));
        return refusal == null ? say(ctx, "Offre retirée (l'escorte payée est rendue au trésor du vendeur).") : fail(ctx, refusal);
    }

    private static int accept(final CommandContext<CommandSourceStack> ctx)
    {
        final IColony buyer = colony(ctx, "acheteur");
        if (buyer == null)
        {
            return fail(ctx, "Colonie inconnue.");
        }
        final String refusal = Contracts.get(ctx.getSource().getServer()).accept(null, ctx.getSource().getServer(), buyer, LongArgumentType.getLong(ctx, "contrat"));
        return refusal == null ? say(ctx, "Offre acceptée : la caravane part.") : fail(ctx, refusal);
    }

    private static int state(final CommandContext<CommandSourceStack> ctx)
    {
        final Contracts contracts = Contracts.get(ctx.getSource().getServer());
        for (final IColony colony : IColonyManager.getInstance().getColonies(ctx.getSource().getServer().overworld()))
        {
            final StringBuilder crates = new StringBuilder();
            for (final Crate crate : Crate.values())
            {
                crates.append(' ').append(crate.id()).append('=').append(Contracts.stockOf(colony, crate));
            }
            say(ctx, "Colonie " + colony.getID() + " " + colony.getName() + " : relais niv " + Contracts.relaisLevel(colony) + ", trésor "
                       + RegionalTreasury.get(ctx.getSource().getServer()).balance(colony) + ", entrepôt" + crates);
        }
        if (contracts.all().isEmpty())
        {
            return say(ctx, "Aucun contrat.");
        }
        for (final Contract c : contracts.all())
        {
            final String progress = c.path.length < 2 ? "-" : String.format("%.0f/%d", c.progress, c.path.length - 1);
            say(ctx, "#" + c.id + " " + c.seller + "→" + c.buyer + " " + c.quantity + "×" + c.crate + " à " + c.unitPrice + " état " + c.state + " route " + progress
                       + " animal " + (c.puppet != null) + " conducteur " + (c.driver != null) + " escorte palier " + c.escort + " (" + c.swordsmenLeft + " épéistes, " + c.archersLeft
                       + " archers, " + c.escorts.size() + " sur la route)" + (c.engaged ? " EN COMBAT" : ""));
        }
        return contracts.all().size();
    }
}
