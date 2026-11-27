package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
import fr.kodistan.interregne.politics.Diplomacy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * The trade road a signed alliance lays between the two town halls when the colonies have none: free, same tracing and
 * animated site as {@link WarRoad} but an ordinary trade road (level 1, raised later like any other), which the Relais
 * commercial needs for every contract. It stays after a break, like every road.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class AllianceRoad
{
    private static final Set<RouteRegistry.Pair> BUILDING = new HashSet<>();

    private AllianceRoad()
    {
    }

    public static boolean building(final int a, final int b)
    {
        return BUILDING.contains(RouteRegistry.Pair.ofColonies(a, b));
    }

    /**
     * Starts the road between two colonies that just became allies; does nothing if they already have a road or the
     * site is already open.
     */
    public static void start(final MinecraftServer server, final IColony first, final IColony second)
    {
        final ServerLevel level = server.overworld();
        final int a = first.getID();
        final int b = second.getID();
        final RouteRegistry.Pair pair = RouteRegistry.Pair.ofColonies(a, b);
        final RouteRegistry existing = RouteRegistry.get(server);
        final int road = existing.roadBetween(a, b);
        if (road >= 0)
        {
            if (existing.war(road))
            {
                // A standing war road becomes the trade road of the new allies (level 1, raised like any other)
                existing.makePeace(road);
                existing.broadcast();
                tell(server, a, "⚒ La route de guerre qui vous relie devient votre route commerciale.");
                tell(server, b, "⚒ La route de guerre qui vous relie devient votre route commerciale.");
            }
            return;
        }
        if (WarRoad.building(a, b) || !BUILDING.add(pair))
        {
            return;
        }
        final RouteEnds.End endA = RouteEnds.of(level, first, first.getCenter(), second.getCenter());
        final RouteEnds.End endB = RouteEnds.of(level, second, second.getCenter(), first.getCenter());
        final boolean started = RoutePlanning.study(level, endA, endB, null, plan -> {
            if (plan == null)
            {
                fail(level, pair, a, b);
                return;
            }
            final int seconds = Config.WAR_ROAD_SECONDS.get();
            RouteSite.launch(level, new RouteBuilder(level, plan), seconds, Component.literal("⚒ Route commerciale"), () -> {
                BUILDING.remove(pair);
                final RouteRegistry registry = RouteRegistry.get(server);
                registry.linkColonies(a, b);
                registry.record(plan, endA, endB);
                registry.broadcast();
                tell(server, a, "⚒ La route de l'alliance est achevée : vos contrats peuvent partir.");
                tell(server, b, "⚒ La route de l'alliance est achevée : vos contrats peuvent partir.");
            });
            tell(server, a, "⚒ Alliance signée : chantier de la route commerciale, " + plan.length() + " blocs, " + seconds + " s.");
            tell(server, b, "⚒ Alliance signée : chantier de la route commerciale, " + plan.length() + " blocs, " + seconds + " s.");
        });
        if (!started)
        {
            fail(level, pair, a, b);
        }
    }

    private static void fail(final ServerLevel level, final RouteRegistry.Pair pair, final int a, final int b)
    {
        BUILDING.remove(pair);
        final String text = "Aucune route possible entre les deux colonies alliées : pas de contrats commerciaux tant qu'une route n'est pas tracée.";
        tell(level.getServer(), a, text);
        tell(level.getServer(), b, text);
    }

    private static void tell(final MinecraftServer server, final int id, final String text)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(id, server.overworld());
        if (colony != null)
        {
            Diplomacy.notifyMembers(server, colony, text);
        }
    }

    /** A crash or stop during a site loses it: allied pairs still without a road get theirs again. */
    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event)
    {
        // MineColonies has not loaded its colonies yet at this point: look a little later
        relaunchIn = 200;
    }

    private static int relaunchIn = -1;

    @SubscribeEvent
    public static void onServerTick(final net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)
    {
        if (relaunchIn < 0 || --relaunchIn > 0)
        {
            return;
        }
        relaunchIn = -1;
        final MinecraftServer server = event.getServer();
        for (final int[] pair : fr.kodistan.interregne.politics.Alliances.get(server).alliedPairs())
        {
            final IColony a = IColonyManager.getInstance().getColonyByWorld(pair[0], server.overworld());
            final IColony b = IColonyManager.getInstance().getColonyByWorld(pair[1], server.overworld());
            if (a != null && b != null)
            {
                start(server, a, b);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        BUILDING.clear();
    }
}
