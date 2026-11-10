package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.BlockPosUtil;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.production.Huts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;


import static net.minecraft.commands.Commands.literal;

/**
 * Slice commands: the assault is triggered by hand while the war table UI doesn't exist yet.
 * <pre>
 *   /war assault [legionnaires] [archers]       — assault the colony the player stands in (default 3 légionnaires, op)
 *   /war stop                                   — pull every troop out (op)
 *   /war debug archers-siege-only &lt;true|false&gt; — dev-only: archers skip their guard scan and bombard outright
 * </pre>
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class WarCommands
{
    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        final LiteralArgumentBuilder<CommandSourceStack> war = literal("war")
          .requires(source -> source.hasPermission(2))
          .then(literal("assault")
            .executes(ctx -> assault(ctx, 3, 0))
            .then(net.minecraft.commands.Commands.argument("legionnaires", IntegerArgumentType.integer(0, 12))
              .executes(ctx -> assault(ctx, IntegerArgumentType.getInteger(ctx, "legionnaires"), 0))
              .then(net.minecraft.commands.Commands.argument("archers", IntegerArgumentType.integer(0, 12))
                .executes(ctx -> assault(ctx, IntegerArgumentType.getInteger(ctx, "legionnaires"),
                  IntegerArgumentType.getInteger(ctx, "archers"))))))
          .then(literal("stop").executes(WarCommands::stop))
          .then(literal("debug")
            .then(literal("archers-siege-only")
              .then(net.minecraft.commands.Commands.argument("on", BoolArgumentType.bool())
                .executes(ctx -> debugArchersSiegeOnly(ctx, BoolArgumentType.getBool(ctx, "on"))))));
        event.getDispatcher().register(war);
    }

    private static int assault(final CommandContext<CommandSourceStack> ctx, final int legionnaires, final int archers) throws CommandSyntaxException
    {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final ServerLevel level = ctx.getSource().getLevel();
        IColony defender = null;
        for (final IColony candidate : Huts.colonies())
        {
            if (candidate.getWorld() == level && candidate.isCoordInColony(level, player.blockPosition())
                  && (defender == null || BlockPosUtil.getDistance(candidate.getCenter(), player.blockPosition())
                        < BlockPosUtil.getDistance(defender.getCenter(), player.blockPosition())))
            {
                defender = candidate;
            }
        }
        if (defender == null)
        {
            ctx.getSource().sendFailure(Component.literal("Aucune colonie ici — place-toi dedans pour l'assiéger."));
            return 0;
        }
        // The slice attacks with the defender's own treasury as payout account; real attacker colonies come with the war table.
        final String refusal = WarAssaults.launch(level, defender, defender, player, legionnaires, archers);
        if (refusal != null)
        {
            ctx.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }
        return 1;
    }

    private static int stop(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        WarAssaults.stopAll(ctx.getSource().getServer(), ctx.getSource().getPlayerOrException());
        return 1;
    }

    /** Dev-only switch: lets Duncan watch the archer bombard a building without waiting for every guard in the
      * colony to clear out first (see {@link AssaultRangedGoal#nearestThreat}). */
    private static int debugArchersSiegeOnly(final CommandContext<CommandSourceStack> ctx, final boolean on)
    {
        WarAssaults.setArchersIgnoreThreats(on);
        ctx.getSource().sendSystemMessage(Component.literal(on
          ? "§6Debug§r : les archers ignorent les gardes et bombardent directement."
          : "§6Debug§r : comportement normal des archers restauré (priorité aux gardes)."));
        return 1;
    }

    private WarCommands()
    {
    }
}
