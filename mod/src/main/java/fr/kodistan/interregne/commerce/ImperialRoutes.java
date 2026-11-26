package fr.kodistan.interregne.commerce;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.politics.Diplomacy;
import fr.kodistan.interregne.routes.RouteBuilder;
import fr.kodistan.interregne.routes.RouteEnds;
import fr.kodistan.interregne.routes.RoutePlanning;
import fr.kodistan.interregne.routes.RouteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The roads from colonies to the Empire (see {@link ImperialGate} for where they end). A colony opens one from its
 * Relais commercial and pays it from its treasury; it is traced and built like the war road, and like every road it
 * stays for good. Caravans to the Empire ({@link Contracts#sellImperial}) walk it.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ImperialRoutes extends SavedData
{
    private static final String NAME = "interregne_imperial_routes";

    /** Routes being studied or built: one site at a time per colony and destination. Not saved: a restart drops the site, the money is not given back. */
    private static final Set<String> BUILDING = new HashSet<>();

    private record Route(int colony, long market, long[] path)
    {
    }

    private final List<Route> routes = new ArrayList<>();

    public static ImperialRoutes get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ImperialRoutes::new, ImperialRoutes::load, null), NAME);
    }

    private static String key(final int colony, final BlockPos market)
    {
        return colony + "|" + market.asLong();
    }

    /**
     * The road's cells from the colony to the destination, null when none is open.
     */
    @Nullable
    public long[] pathOf(final int colony, final BlockPos market)
    {
        for (final Route route : routes)
        {
            if (route.colony == colony && route.market == market.asLong())
            {
                return route.path;
            }
        }
        return null;
    }

    public boolean isOpen(final int colony, final BlockPos market)
    {
        return pathOf(colony, market) != null;
    }

    public static boolean isBuilding(final int colony, final BlockPos market)
    {
        return BUILDING.contains(key(colony, market));
    }

    /**
     * Records a finished road.
     */
    public void complete(final int colony, final BlockPos market, final long[] path)
    {
        BUILDING.remove(key(colony, market));
        if (!isOpen(colony, market))
        {
            routes.add(new Route(colony, market.asLong(), path));
            setDirty();
        }
    }

    /**
     * The checks and the payment of opening a road: the colony's treasury pays {@code imperialRoadCost}, and the road
     * counts as being built until {@link #complete} or {@link #abandon}.
     *
     * @return a refusal for the player, or null once paid.
     */
    @Nullable
    public String pay(final MinecraftServer server, final IColony colony, final BlockPos market)
    {
        if (isOpen(colony.getID(), market))
        {
            return "La route impériale est déjà ouverte.";
        }
        if (isBuilding(colony.getID(), market))
        {
            return "La route impériale est déjà en chantier.";
        }
        final long cost = Config.IMPERIAL_ROAD_COST.get();
        if (!RegionalTreasury.get(server).spend(colony, cost))
        {
            return "Il faut " + cost + " au trésor régional pour ouvrir la route impériale.";
        }
        BUILDING.add(key(colony.getID(), market));
        return null;
    }

    /**
     * No road could be traced: the colony gets its money back.
     */
    public void abandon(final MinecraftServer server, final IColony colony, final BlockPos market)
    {
        BUILDING.remove(key(colony.getID(), market));
        RegionalTreasury.get(server).credit(colony, Config.IMPERIAL_ROAD_COST.get());
    }

    /**
     * Opens the road from this colony to the destination: pays, then traces and builds it with the same machinery as
     * the war road.
     *
     * @return a refusal for the player, or null once the work is under way.
     */
    @Nullable
    public String open(final ServerLevel level, final IColony colony, final BlockPos market)
    {
        if (Contracts.relaisLevel(colony) < 1)
        {
            return "Il faut un Relais commercial construit.";
        }
        final String refusal = pay(level.getServer(), colony, market);
        if (refusal != null)
        {
            return refusal;
        }
        final int id = colony.getID();
        final RouteEnds.End endA = RouteEnds.of(level, colony, colony.getCenter(), market);
        final RouteEnds.End endB = RouteEnds.of(level, null, market, colony.getCenter());
        final boolean started = RoutePlanning.study(level, endA, endB, null, plan -> {
            if (plan == null)
            {
                fail(level, id, market);
                return;
            }
            final int seconds = Config.IMPERIAL_ROAD_SECONDS.get();
            RouteSite.launch(level, new RouteBuilder(level, plan), seconds, Component.literal("⚒ Route impériale"), () -> {
                final RouteRegistry registry = RouteRegistry.get(level.getServer());
                registry.record(plan, endA, endB);
                registry.broadcast();
                final BlockPos[] cells = registry.path(registry.roadCount() - 1);
                final long[] path = new long[cells.length];
                for (int i = 0; i < cells.length; i++)
                {
                    path[i] = cells[i].asLong();
                }
                get(level.getServer()).complete(id, market, path);
                tell(level, id, "⚒ La route impériale est achevée : vos caravanes peuvent partir vendre à l'Empire.");
            });
            tell(level, id, "⚒ Chantier de la route impériale : " + plan.length() + " blocs, " + seconds + " s.");
        });
        if (!started)
        {
            fail(level, id, market);
            return "Le Marché impérial est trop loin : aucune route possible.";
        }
        return null;
    }

    private static void fail(final ServerLevel level, final int colonyId, final BlockPos market)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level.getServer().overworld());
        if (colony == null)
        {
            BUILDING.remove(key(colonyId, market));
            return;
        }
        get(level.getServer()).abandon(level.getServer(), colony, market);
        tell(level, colonyId, "Aucune route possible jusqu'au Marché impérial : " + Config.IMPERIAL_ROAD_COST.get() + " couronnes rendues au trésor.");
    }

    private static void tell(final ServerLevel level, final int colonyId, final String text)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level.getServer().overworld());
        if (colony != null)
        {
            Diplomacy.notifyMembers(level.getServer(), colony, text);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        BUILDING.clear();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ListTag list = new ListTag();
        for (final Route route : routes)
        {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("colony", route.colony);
            entry.putLong("market", route.market);
            entry.putLongArray("path", route.path);
            list.add(entry);
        }
        tag.put("routes", list);
        return tag;
    }

    private static ImperialRoutes load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ImperialRoutes data = new ImperialRoutes();
        for (final Tag entry : tag.getList("routes", Tag.TAG_COMPOUND))
        {
            final CompoundTag route = (CompoundTag) entry;
            data.routes.add(new Route(route.getInt("colony"), route.getLong("market"), route.getLongArray("path")));
        }
        return data;
    }
}
