package fr.kodistan.interregne.garrison;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.kodistan.interregne.InterregneMod;
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
 * Test shortcut: {@code /garnison poser <colonie> <academie|tir|caserne|tour> <niveau>} sets a finished combat academy,
 * archery, barracks or guard tower near the colony's centre, so their pages can be tried without building one.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class GarrisonCommands
{
    private GarrisonCommands()
    {
    }

    @SubscribeEvent
    public static void onCommands(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(literal("garnison").requires(source -> source.hasPermission(2))
          .then(literal("poser").then(argument("colonie", IntegerArgumentType.integer(1)).then(argument("hutte", StringArgumentType.word())
            .then(argument("niveau", IntegerArgumentType.integer(0, 5)).executes(GarrisonCommands::place))))));
    }

    private static int place(final CommandContext<CommandSourceStack> ctx)
    {
        final ServerLevel level = ctx.getSource().getServer().overworld();
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(IntegerArgumentType.getInteger(ctx, "colonie"), level);
        final String hut = StringArgumentType.getString(ctx, "hutte");
        final boolean archery = hut.equals("tir");
        final boolean barracks = hut.equals("caserne");
        final boolean tower = hut.equals("tour");
        if (colony == null || !(archery || barracks || tower || hut.equals("academie")))
        {
            ctx.getSource().sendFailure(Component.literal("Colonie inconnue, ou hutte autre que academie / tir / caserne / tour."));
            return 0;
        }
        final int wanted = IntegerArgumentType.getInteger(ctx, "niveau");
        final String type = archery ? "archery" : barracks ? "barracks" : tower ? "guardtower" : "combatacademy";
        IBuilding building = colony.getBuildingManager().getBuildings().values().stream()
          .filter(b -> type.equals(b.getBuildingType().getRegistryName().getPath())).findFirst().orElse(null);
        if (building == null)
        {
            final AbstractBlockHut<?> block = archery ? ModBlocks.blockHutArchery : barracks ? ModBlocks.blockHutBarracks
              : tower ? ModBlocks.blockHutGuardTower : ModBlocks.blockHutCombatAcademy;
            final BlockPos centre = colony.getCenter();
            final int x = centre.getX() + (archery || tower ? -10 : 10);
            final int z = centre.getZ() + (barracks || tower ? 10 : -10);
            level.getChunk(x >> 4, z >> 4);
            final BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            final BlockState state = block.defaultBlockState();
            level.setBlock(pos, state, 3);
            block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
            building = colony.getBuildingManager().getBuilding(pos);
            if (building == null)
            {
                ctx.getSource().sendFailure(Component.literal("La hutte n'a pas été reconnue par la colonie en " + pos.toShortString()));
                return 0;
            }
        }
        // a building cannot outgrow the town hall: test shortcut, raise it when it is too low
        final var townHall = colony.getBuildingManager().getTownHall();
        if (townHall != null && townHall.getBuildingLevel() < wanted)
        {
            townHall.setBuildingLevel(wanted);
        }
        building.setBuildingLevel(wanted);
        final String where = building.getPosition().toShortString();
        ctx.getSource().sendSuccess(() -> Component.literal(type + " de " + colony.getName() + " niveau " + wanted + " en " + where + ".")
          .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
