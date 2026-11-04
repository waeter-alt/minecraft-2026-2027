package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.InterregneMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every construction and demolition site on the server tick, our own pace instead of Structurize's queue
 * (which drains as fast as it can, so nothing would be visible).
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ConstructionManager
{
    private static final List<Site> SITES = new ArrayList<>();

    private ConstructionManager()
    {
    }

    static void start(final Site site)
    {
        site.start();
        SITES.add(site);
        resync(site);
    }

    /**
     * The level our site on this building is heading for, or null when none runs there. MineColonies reads it as the
     * building's work order, so its views know a site is running (« Chantier », progress in the hut window).
     */
    @Nullable
    public static Integer siteLevel(final IBuilding building)
    {
        for (final Site site : SITES)
        {
            if (site.building() == building)
            {
                return site.targetLevel();
            }
        }
        return null;
    }

    /**
     * Sends the building's view again, so clients see the site start or end.
     */
    private static void resync(final Site site)
    {
        if (site.building() != null)
        {
            site.building().markDirty();
        }
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        for (final Site site : new ArrayList<>(SITES))
        {
            boolean done;
            try
            {
                done = site.tick();
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.error("Construction site failed, finishing it at once", e);
                site.finishNow();
                done = true;
            }
            if (done)
            {
                SITES.remove(site);
                resync(site);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        for (final Site site : SITES)
        {
            site.finishNow();
        }
        SITES.clear();
    }

    /**
     * A flying block saved to disk means the server died mid-flight: drop it, its block was already placed or is lost.
     */
    @SubscribeEvent
    public static void onEntityJoin(final EntityJoinLevelEvent event)
    {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(ConstructionSite.FLYING_TAG))
        {
            event.setCanceled(true);
        }
    }
}
