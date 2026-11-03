package fr.kodistan.interregne.pvp;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * /interregne zone sure ajouter|retirer|liste : operators draw the PvP-free areas.
 * Names with spaces go in quotes: /interregne zone sure ajouter "Cité impériale" -100 -64 -100 100 320 100
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class SafeZoneCommands
{
    private SafeZoneCommands()
    {
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("interregne")
          .then(Commands.literal("zone").requires(source -> source.hasPermission(2))
                  .then(Commands.literal("sure")
                          .then(Commands.literal("ajouter")
                                  .then(Commands.argument("nom", StringArgumentType.string())
                                          .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                  .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                          .executes(SafeZoneCommands::add)))))
                          .then(Commands.literal("retirer")
                                  .then(Commands.argument("nom", StringArgumentType.string())
                                          .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                            SafeZones.get(context.getSource().getServer()).all().stream().map(zone -> StringArgumentType.escapeIfRequired(zone.name())), builder))
                                          .executes(SafeZoneCommands::remove)))
                          .then(Commands.literal("liste").executes(SafeZoneCommands::list)))));
    }

    private static int add(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final String name = StringArgumentType.getString(context, "nom").trim();
        if (name.isEmpty())
        {
            source.sendFailure(Component.literal("Le nom de la zone ne peut pas être vide."));
            return 0;
        }
        final BlockPos pos1 = BlockPosArgument.getBlockPos(context, "pos1");
        final BlockPos pos2 = BlockPosArgument.getBlockPos(context, "pos2");
        final SafeZones.Zone zone = new SafeZones.Zone(name, source.getLevel().dimension(), BoundingBox.fromCorners(pos1, pos2));
        final SafeZones.Zone previous = SafeZones.get(source.getServer()).put(zone);
        source.sendSuccess(() -> Component.literal("Zone sûre « " + name + " » " + (previous == null ? "créée" : "redéfinie") + " : " + describe(zone)), true);
        return 1;
    }

    private static int remove(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final String name = StringArgumentType.getString(context, "nom");
        final SafeZones.Zone removed = SafeZones.get(source.getServer()).remove(name);
        if (removed == null)
        {
            source.sendFailure(Component.literal("Aucune zone sûre nommée « " + name + " »."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Zone sûre « " + removed.name() + " » retirée : le JcJ y est de nouveau ouvert."), true);
        return 1;
    }

    private static int list(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final Collection<SafeZones.Zone> zones = SafeZones.get(source.getServer()).all();
        if (zones.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("Aucune zone sûre : le JcJ est ouvert partout.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        final MutableComponent text = Component.literal("Zones sûres (" + zones.size() + ") :").withStyle(ChatFormatting.GREEN);
        for (final SafeZones.Zone zone : zones)
        {
            text.append(Component.literal("\n  " + zone.name() + " — " + describe(zone)).withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return zones.size();
    }

    private static String describe(final SafeZones.Zone zone)
    {
        final BoundingBox box = zone.box();
        return zone.dimension().location() + " de " + box.minX() + " " + box.minY() + " " + box.minZ()
                 + " à " + box.maxX() + " " + box.maxY() + " " + box.maxZ();
    }
}
