package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Influence earned through dealings between players: an assault repelled, the clan aid that helped, an alliance signed
 * and an alliance that lasts. Like the victory, it goes to the colony's owner. Trade never gives any.
 * <p>
 * The rules take a colony lookup so they can be tested without a real colony manager.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class InfluenceSources
{
    public static final long DAY_TICKS = 24000L;
    private static final int CHECK_EVERY = 200;

    private InfluenceSources()
    {
    }

    private static IntFunction<IColony> lookup(final MinecraftServer server)
    {
        return id -> IColonyManager.getInstance().getColonyByWorld(id, server.overworld());
    }

    /** Gives {@code amount} to the colony's owner; false when there is nobody to give it to. */
    private static boolean give(final MinecraftServer server, final IColony colony, final long amount, final String reason)
    {
        final UUID owner = colony == null ? null : colony.getPermissions().getOwner();
        if (owner == null)
        {
            return false;
        }
        if (amount > 0)
        {
            Influence.add(server, owner, amount, reason);
        }
        return true;
    }

    /** An assault on {@code defender} was repelled: the defender earns, and so does each ally whose contingent stands at its side. */
    public static void repelled(final MinecraftServer server, final IColony attacker, final IColony defender)
    {
        repelled(server, attacker, defender, AllyAid.get(server), lookup(server));
    }

    public static void repelled(final MinecraftServer server, final IColony attacker, final IColony defender, final AllyAid aid, final IntFunction<IColony> colonies)
    {
        give(server, defender, Config.INFLUENCE_REPELLED.get(), "assaut de " + attacker.getName() + " repoussé");
        // engagedHelpers lists each helper once, however many soldiers it sent: one gain per ally and per assault
        for (final int helperId : aid.engagedHelpers(defender.getID()))
        {
            final IColony helper = colonies.apply(helperId);
            if (helper != null)
            {
                give(server, helper, Config.INFLUENCE_ALLY_AID.get(), "aide de clan utile à " + defender.getName());
            }
        }
    }

    /** {@code a} and {@code b} are allied: both owners earn the signing, only the first time for the pair. */
    public static void allianceSigned(final MinecraftServer server, final Alliances alliances, final IColony a, final IColony b)
    {
        if (alliances.firstSigning(a.getID(), b.getID()))
        {
            give(server, a, Config.INFLUENCE_ALLIANCE_SIGNED.get(), "alliance signée avec " + b.getName());
            give(server, b, Config.INFLUENCE_ALLIANCE_SIGNED.get(), "alliance signée avec " + a.getName());
        }
    }

    /** Pays a day to every allied pair that completed one since its last. */
    public static void dailyBonus(final MinecraftServer server, final Alliances alliances, final long now, final IntFunction<IColony> colonies)
    {
        for (final int[] pair : alliances.alliedPairs())
        {
            if (!alliances.claimDay(pair[0], pair[1], now, DAY_TICKS))
            {
                continue;
            }
            final IColony a = colonies.apply(pair[0]);
            final IColony b = colonies.apply(pair[1]);
            if (a != null && b != null)
            {
                give(server, a, Config.INFLUENCE_ALLIANCE_DAILY.get(), "alliance avec " + b.getName() + " : un jour de plus");
                give(server, b, Config.INFLUENCE_ALLIANCE_DAILY.get(), "alliance avec " + a.getName() + " : un jour de plus");
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 0 || server.overworld() == null)
        {
            return;
        }
        dailyBonus(server, Alliances.get(server), server.overworld().getGameTime(), lookup(server));
    }
}
