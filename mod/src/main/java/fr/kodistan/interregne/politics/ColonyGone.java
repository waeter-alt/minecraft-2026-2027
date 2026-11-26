package fr.kodistan.interregne.politics;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.commerce.Contracts;
import fr.kodistan.interregne.garrison.Garrison;
import fr.kodistan.interregne.routes.RouteRegistry;
import fr.kodistan.interregne.war.WarAssaults;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * What follows when a MineColonies colony is deleted: everything of ours that names it is closed or forgotten, with no
 * winner, no Influence, no loot and no shield. The roads stay in the world (they never disappear), only the registry
 * links that name the gone colony go.
 * <p>
 * Triggered by MineColonies' own {@code ColonyDeletedModEvent}, which it posts while the colony is still in its list:
 * the clean-up therefore waits for the next server tick. At server start, it also catches colonies deleted while the
 * server was off or by a tool that bypassed the event.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ColonyGone
{
    /** Colonies deleted and not yet cleaned up, by id, with their name. */
    private static final Map<Integer, String> PENDING = new LinkedHashMap<>();
    private static boolean subscribed;

    private ColonyGone()
    {
    }

    /**
     * Cleans up after a colony that was deleted.
     *
     * @param name the colony's name, for the other colonies' members
     */
    public static void purge(final MinecraftServer server, final int colony, final String name)
    {
        purge(server, colony, name, id -> IColonyManager.getInstance().getColonyByWorld(id, server.overworld()));
    }

    /**
     * {@link #purge(MinecraftServer, int, String)} with the colonies found by {@code colonies}.
     */
    public static void purge(final MinecraftServer server, final int colony, final String name, final IntFunction<IColony> colonies)
    {
        final IntFunction<IColony> others = id -> id == colony ? null : colonies.apply(id);
        // Assaults first: the troops must leave before the war is erased
        WarAssaults.colonyGone(server, colony);

        final Set<Integer> touched = new HashSet<>();
        final Set<Integer> opponents = Wars.get(server).removeColony(colony);
        touched.addAll(opponents);

        final List<Map.Entry<Integer, AllyAid.Guest>> guests = AllyAid.get(server).removeColony(colony);
        for (final Map.Entry<Integer, AllyAid.Guest> entry : guests)
        {
            touched.add(entry.getKey());
            final IColony helped = others.apply(entry.getKey());
            if (helped == null)
            {
                continue;
            }
            final ICitizenData citizen = helped.getCitizenManager().getCivilian(entry.getValue().citizen());
            if (citizen != null && citizen.getUUID().equals(entry.getValue().uuid()))
            {
                Garrison.dismiss(server, helped, citizen);
            }
        }

        touched.addAll(Alliances.get(server).removeColony(colony));
        Contracts.get(server).cancelOf(server, colony, others);
        RouteRegistry.get(server).forgetColony(colony);

        for (final int other : opponents)
        {
            final IColony survivor = others.apply(other);
            if (survivor != null)
            {
                Diplomacy.notifyMembers(server, survivor, "La colonie " + name + " a disparu : la guerre prend fin.");
            }
        }
        for (final int other : touched)
        {
            final IColony survivor = others.apply(other);
            if (survivor != null)
            {
                PacketDistributor.sendToAllPlayers(Alliances.syncOf(server, survivor, server.overworld()));
            }
        }
        InterregneMod.LOGGER.info("[colonie supprimée] #{} ({}) : guerres, assauts, aides, alliances, contrats et liens de routes effacés", colony, name);
    }

    /**
     * Every colony id our saved data still names, to find those that no longer exist.
     */
    static Set<Integer> referenced(final MinecraftServer server)
    {
        final Set<Integer> ids = new HashSet<>();
        ids.addAll(Alliances.get(server).colonyIds());
        ids.addAll(Wars.get(server).colonyIds());
        ids.addAll(AllyAid.get(server).colonyIds());
        ids.addAll(Contracts.get(server).colonyIds());
        ids.addAll(RouteRegistry.get(server).colonyIds());
        return ids;
    }

    /**
     * Colonies deleted while the server was off (or around MineColonies' own event) leave their ids in our saved data:
     * purged once the colonies are loaded. Skipped when no colony is loaded at all, which is not proof that they were
     * all deleted.
     */
    @SubscribeEvent
    public static void onServerStarted(final ServerStartedEvent event)
    {
        if (!subscribed)
        {
            subscribed = true;
            IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyDeletedModEvent.class, deleted -> {
                final IColony colony = deleted.getColony();
                synchronized (PENDING)
                {
                    PENDING.put(colony.getID(), colony.getName());
                }
            });
        }
        final MinecraftServer server = event.getServer();
        if (server.overworld() == null || IColonyManager.getInstance().getColonies(server.overworld()).isEmpty())
        {
            return;
        }
        purgeUnknown(server, id -> IColonyManager.getInstance().getColonyByWorld(id, server.overworld()) != null);
    }

    /**
     * Purges every colony our saved data names that {@code exists} does not know.
     *
     * @return how many colonies were purged
     */
    public static int purgeUnknown(final MinecraftServer server, final java.util.function.IntPredicate exists)
    {
        int count = 0;
        for (final int id : referenced(server))
        {
            if (!exists.test(id))
            {
                purge(server, id, "Une colonie");
                count++;
            }
        }
        return count;
    }

    /** The colonies MineColonies deleted since the last tick are out of its list by now: clean up after them. */
    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final Map<Integer, String> gone;
        synchronized (PENDING)
        {
            if (PENDING.isEmpty())
            {
                return;
            }
            gone = new LinkedHashMap<>(PENDING);
            PENDING.clear();
        }
        gone.forEach((id, name) -> purge(event.getServer(), id, name));
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event)
    {
        synchronized (PENDING)
        {
            PENDING.clear();
        }
    }
}
