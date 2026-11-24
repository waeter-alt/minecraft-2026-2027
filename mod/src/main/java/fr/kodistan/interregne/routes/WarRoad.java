package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.politics.Diplomacy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * The road a declaration of war lays between two colonies that have none: same tracing and building as a trade road,
 * dressed for war (deepslate, blackstone, soul lanterns). The declarer's treasury pays for it; if no road can be traced,
 * the price goes back to that treasury and the troops will have to come without one.
 * <p>
 * Once built it is an ordinary road of the registry: it stays after the war, like every road.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class WarRoad
{
    /** Pairs whose war road is being studied or built: one site at a time. */
    private static final Set<RouteRegistry.Pair> BUILDING = new HashSet<>();

    /** Pairs for which no road can be traced: troops of these wars come without one. */
    private static final Set<RouteRegistry.Pair> IMPOSSIBLE = new HashSet<>();

    private WarRoad()
    {
    }

    public static boolean building(final int a, final int b)
    {
        return BUILDING.contains(RouteRegistry.Pair.ofColonies(a, b));
    }

    public static boolean impossible(final int a, final int b)
    {
        return IMPOSSIBLE.contains(RouteRegistry.Pair.ofColonies(a, b));
    }

    /**
     * Starts the road from the declarer to the target; {@code paid} couronnes go back to the declarer's treasury if
     * it cannot be traced.
     */
    public static void start(final ServerLevel level, final IColony declarer, final IColony target, final long paid)
    {
        final RouteRegistry.Pair pair = RouteRegistry.Pair.ofColonies(declarer.getID(), target.getID());
        if (!BUILDING.add(pair))
        {
            return;
        }
        IMPOSSIBLE.remove(pair);
        final RouteEnds.End endA = RouteEnds.of(level, declarer, declarer.getCenter(), target.getCenter());
        final RouteEnds.End endB = RouteEnds.of(level, target, target.getCenter(), declarer.getCenter());
        final int a = declarer.getID();
        final int b = target.getID();
        final boolean started = RoutePlanning.study(level, endA, endB, null, plan -> {
            if (plan == null)
            {
                fail(level, pair, a, b, paid);
                return;
            }
            final int seconds = Config.WAR_ROAD_SECONDS.get();
            RouteSite.launch(level, new RouteBuilder(level, plan, true), seconds, Component.literal("⚔ Route de guerre"), () -> {
                BUILDING.remove(pair);
                final RouteRegistry registry = RouteRegistry.get(level.getServer());
                registry.linkColonies(a, b);
                registry.record(plan, endA, endB);
                registry.broadcast();
                tell(level, a, "⚔ La route de guerre est achevée : les troupes peuvent marcher.");
                tell(level, b, "⚔ La route de guerre de l'ennemi atteint vos terres.");
            });
            tell(level, a, "⚒ Chantier de la route de guerre : " + plan.length() + " blocs, " + seconds + " s.");
            tell(level, b, "⚒ L'ennemi trace une route de guerre jusqu'à vous.");
        });
        if (!started)
        {
            fail(level, pair, a, b, paid);
        }
    }

    private static void fail(final ServerLevel level, final RouteRegistry.Pair pair, final int a, final int b, final long paid)
    {
        BUILDING.remove(pair);
        IMPOSSIBLE.add(pair);
        final IColony declarer = colony(level, a);
        if (declarer != null && paid > 0)
        {
            RegionalTreasury.get(level.getServer()).credit(declarer, paid);
        }
        tell(level, a, "Aucune route de guerre possible jusqu'à l'ennemi" + (paid > 0 ? " : " + paid + " couronnes rendues au trésor." : ".")
          + " Les troupes partiront sans route.");
    }

    private static IColony colony(final ServerLevel level, final int id)
    {
        return IColonyManager.getInstance().getColonyByWorld(id, level.getServer().overworld());
    }

    private static void tell(final ServerLevel level, final int id, final String text)
    {
        final IColony colony = colony(level, id);
        if (colony != null)
        {
            Diplomacy.notifyMembers(level.getServer(), colony, text);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        BUILDING.clear();
        IMPOSSIBLE.clear();
    }
}
