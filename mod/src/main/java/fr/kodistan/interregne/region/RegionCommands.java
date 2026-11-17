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
          .then(Commands.literal("depot").requires(source -> source.hasPermission(2))
                  .then(Commands.argument("colonie", IntegerArgumentType.integer(1)).executes(context -> depotSite(context.getSource(), IntegerArgumentType.getInteger(context, "colonie"), 1))
                          .then(Commands.argument("niveau", IntegerArgumentType.integer(1, DepotRegional.MAX_LEVEL))
                                  .executes(context -> depotSite(context.getSource(), IntegerArgumentType.getInteger(context, "colonie"),
                                    IntegerArgumentType.getInteger(context, "niveau"))))))
          .then(Commands.literal("stock").requires(source -> source.hasPermission(2))
                  .then(Commands.argument("caisse", StringArgumentType.word())
                          .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Crate.values()).map(Crate::id), builder))
                          .then(Commands.argument("quantite", IntegerArgumentType.integer(0))
                                  .executes(context -> setStock(context.getSource(), StringArgumentType.getString(context, "caisse"),
                                    IntegerArgumentType.getInteger(context, "quantite")))))));
    }

    /**
     * Test tool: places an unbuilt Dépôt Régional at the edge of the colony and asks for the construction of the blueprint
     * of the wanted level, the way the build tool does, so each of the five looks can be checked in turn.
     */
    private static int depotSite(final CommandSourceStack source, final int colonyId, final int wanted)
    {
        final net.minecraft.server.level.ServerLevel level = source.getServer().overworld();
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
        if (colony == null)
        {
            source.sendFailure(Component.literal("Colonie inconnue."));
            return 0;
        }
        final net.minecraft.core.BlockPos centre = colony.getCenter();
        final int x = centre.getX() - 24;
        final int z = centre.getZ() - 24;
        level.getChunk(x >> 4, z >> 4);
        final net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        final net.minecraft.world.level.block.state.BlockState state = Crates.DEPOT.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        final var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "depot-test"));
        if (level.getBlockEntity(pos) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding hut)
        {
            hut.setStructurePack(com.ldtteam.structurize.storage.StructurePacks.getStructurePack("Couronne Vacante"));
            hut.setBlueprintPath("regions/depot" + wanted + ".blueprint");
        }
        Crates.DEPOT.get().setPlacedBy(level, pos, state, player, net.minecraft.world.item.ItemStack.EMPTY);
        final var building = colony.getBuildingManager().getBuilding(pos);
        if (building == null)
        {
            source.sendFailure(Component.literal("Le Dépôt n'a pas été reconnu par la colonie (hors de son territoire ?) en " + pos.toShortString()));
            return 0;
        }
        // the work order builds the blueprint of the level after the current one
        building.setBuildingLevel(wanted - 1);
        final var townHall = colony.getBuildingManager().getTownHall();
        if (townHall != null && townHall.getBuildingLevel() < wanted)
        {
            townHall.setBuildingLevel(wanted);
        }
        building.requestUpgrade(player, net.minecraft.core.BlockPos.ZERO);
        source.sendSuccess(() -> Component.literal("Chantier du Dépôt demandé en " + pos.toShortString() + " (niveau " + wanted + ").").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int list(final CommandSourceStack source)
    {
        final RegionData data = RegionData.get(source.getServer());
        int shown = 0;
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final Crate crate = data.regionOf(colony);
            final RegionData.Comptoir comptoir = data.existingComptoir(colony);
            final String workshop = comptoir == null || comptoir.pos == null ? "pas de dépôt" : "dépôt niveau " + comptoir.level + ", réserve " + comptoir.stock;
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
