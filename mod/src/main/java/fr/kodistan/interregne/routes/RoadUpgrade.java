package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
import fr.kodistan.interregne.routes.plan.RoutePlan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Raising a road one level ({@link RoadDresser}) as a timed site, and the admin command {@code /routes} to list the
 * roads, read or set their level and raise one for free.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class RoadUpgrade
{
    /** Roads whose works are under way: a second upgrade would read a half-dressed road with the wrong palette. */
    private static final Set<Integer> UNDER_WAY = new HashSet<>();

    private RoadUpgrade()
    {
    }

    public static boolean underWay(final int road)
    {
        return UNDER_WAY.contains(road);
    }

    /**
     * Starts the works raising this road one level; its stored level goes up once every cell is done.
     *
     * @return false when the road is gone, already at the top or already being raised
     */
    public static boolean start(final ServerLevel level, final int road)
    {
        final RouteRegistry registry = RouteRegistry.get(level.getServer());
        if (!registry.standing(road) || registry.war(road) || UNDER_WAY.contains(road) || registry.level(road) >= RoadLevels.MAX)
        {
            return false;
        }
        final int from = registry.level(road);
        UNDER_WAY.add(road);
        // A road that kept its plan is laid again whole in its next style; an older one is dressed in place
        final RoutePlan plan = registry.plan(road);
        final RouteWork work = plan == null ? new RoadDresser(level, registry.path(road), from) : new RouteBuilder(level, plan, RoadStyle.of(from + 1), road);
        RouteSite.launch(level, work, Config.ROAD_UPGRADE_SECONDS.get(),
          Component.literal("⚒ Route : niveau " + (from + 1)), () -> {
              UNDER_WAY.remove(road);
              final RouteRegistry now = RouteRegistry.get(level.getServer());
              // Broken off while the works went on: the decay already took the road back
              if (now.standing(road))
              {
                  now.setLevel(road, from + 1);
                  now.broadcast();
              }
          });
        return true;
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        UNDER_WAY.clear();
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("routes").requires(source -> source.hasPermission(2))
          .then(Commands.literal("liste").executes(context -> list(context.getSource())))
          .then(Commands.literal("niveau")
                  .then(Commands.argument("route", IntegerArgumentType.integer(0))
                          .executes(context -> show(context.getSource(), IntegerArgumentType.getInteger(context, "route")))))
          .then(Commands.literal("ameliorer")
                  .then(Commands.argument("route", IntegerArgumentType.integer(0))
                          .executes(context -> raise(context.getSource(), IntegerArgumentType.getInteger(context, "route")))))
          .then(Commands.literal("fixer")
                  .then(Commands.argument("route", IntegerArgumentType.integer(0))
                          .then(Commands.argument("niveau", IntegerArgumentType.integer(1, RoadLevels.MAX))
                                  .executes(context -> set(context.getSource(), IntegerArgumentType.getInteger(context, "route"),
                                    IntegerArgumentType.getInteger(context, "niveau")))))));
    }

    private static int list(final CommandSourceStack source)
    {
        final ServerLevel level = source.getServer().overworld();
        final RouteRegistry registry = RouteRegistry.get(source.getServer());
        if (registry.roadCount() == 0)
        {
            source.sendSuccess(() -> Component.literal("Aucune route construite."), false);
            return 0;
        }
        for (int road = 0; road < registry.roadCount(); road++)
        {
            final int[] ends = registry.colonies(road);
            final String text = String.format("#%d %s ↔ %s : niveau %d/%d, %d blocs%s%s", road, name(level, ends[0]), name(level, ends[1]),
              registry.level(road), RoadLevels.MAX, registry.path(road).length, registry.standing(road) ? "" : ", effacée",
              UNDER_WAY.contains(road) ? ", travaux en cours" : "");
            source.sendSuccess(() -> Component.literal(text), false);
        }
        return registry.roadCount();
    }

    private static String name(final ServerLevel level, final int colony)
    {
        final IColony found = IColonyManager.getInstance().getColonyByWorld(colony, level);
        return found == null ? "#" + colony : found.getName();
    }

    private static boolean exists(final CommandSourceStack source, final RouteRegistry registry, final int road)
    {
        if (road >= registry.roadCount())
        {
            source.sendFailure(Component.literal("Pas de route n°" + road + " (voir /routes liste)."));
            return false;
        }
        return true;
    }

    private static int show(final CommandSourceStack source, final int road)
    {
        final RouteRegistry registry = RouteRegistry.get(source.getServer());
        if (!exists(source, registry, road))
        {
            return 0;
        }
        final int level = registry.level(road);
        source.sendSuccess(() -> Component.literal("Route #" + road + " : niveau " + level + "/" + RoadLevels.MAX), false);
        return level;
    }

    private static int raise(final CommandSourceStack source, final int road)
    {
        final RouteRegistry registry = RouteRegistry.get(source.getServer());
        if (!exists(source, registry, road))
        {
            return 0;
        }
        if (!start(source.getServer().overworld(), road))
        {
            source.sendFailure(Component.literal("Route #" + road + " : effacée, déjà au niveau " + RoadLevels.MAX + " ou déjà en travaux."));
            return 0;
        }
        final int to = registry.level(road) + 1;
        source.sendSuccess(() -> Component.literal("Route #" + road + " : travaux vers le niveau " + to + " lancés (gratuits), "
                                                    + Config.ROAD_UPGRADE_SECONDS.get() + " s.").withStyle(ChatFormatting.GOLD), true);
        return to;
    }

    private static int set(final CommandSourceStack source, final int road, final int value)
    {
        final RouteRegistry registry = RouteRegistry.get(source.getServer());
        if (!exists(source, registry, road))
        {
            return 0;
        }
        registry.setLevel(road, value);
        registry.broadcast();
        source.sendSuccess(() -> Component.literal("Route #" + road + " : niveau enregistré " + value
                                                    + " (les blocs ne changent pas ; /routes ameliorer pour bâtir)."), true);
        return value;
    }
}
