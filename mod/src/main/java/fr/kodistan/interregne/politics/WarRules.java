package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.war.WarAssaults;
import fr.kodistan.interregne.war.WarMarches;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Rules of an open war that are not a diplomatic move of their own: the lock that reserves a defender to the colony
 * that first assaulted it, and what lets a defender demand its attacker's surrender. {@link Wars} keeps their state.
 */
public final class WarRules
{
    private static final long TICKS_PER_MINUTE = 20L * 60;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;

    private WarRules()
    {
    }

    public static long lockTicks()
    {
        return Config.WAR_ASSAULT_LOCK_MINUTES.get() * TICKS_PER_MINUTE;
    }

    public static long idleTicks()
    {
        return Config.WAR_IDLE_SURRENDER_HOURS.get() * TICKS_PER_HOUR;
    }

    /**
     * Why {@code attacker} cannot assault {@code defender} right now because another colony holds it, or null when the
     * way is free.
     */
    @Nullable
    public static String lockRefusal(final MinecraftServer server, final int attacker, final int defender)
    {
        final long now = server.overworld().getGameTime();
        final Wars wars = Wars.get(server);
        final int holder = wars.lockedAgainst(attacker, defender, now);
        if (holder < 0)
        {
            return null;
        }
        final IColony target = IColonyManager.getInstance().getColonyByWorld(defender, server.overworld());
        final IColony other = IColonyManager.getInstance().getColonyByWorld(holder, server.overworld());
        return lockMessage(target == null ? "Cette colonie" : target.getName(), other == null ? "une autre colonie" : other.getName(),
          wars.lockLeft(defender, now));
    }

    /**
     * {@link #surrenderWait} in seconds for the sync to the clients (rounded up), -1 when it cannot be demanded for now.
     */
    public static int surrenderSeconds(final MinecraftServer server, final int attacker, final int defender)
    {
        final long wait = surrenderWait(server, attacker, defender);
        return wait < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, (wait + 19) / 20);
    }

    /**
     * The name of the colony that has {@code defender} reserved against {@code attacker}, "" when the way is free.
     */
    public static String lockHolderName(final MinecraftServer server, final int attacker, final int defender)
    {
        final int holder = Wars.get(server).lockedAgainst(attacker, defender, server.overworld().getGameTime());
        final IColony other = holder < 0 ? null : IColonyManager.getInstance().getColonyByWorld(holder, server.overworld());
        return holder < 0 ? "" : other == null ? "une autre colonie" : other.getName();
    }

    /**
     * Seconds {@code defender} stays reserved against {@code attacker}, 0 when the way is free.
     */
    public static int lockSeconds(final MinecraftServer server, final int attacker, final int defender)
    {
        final long now = server.overworld().getGameTime();
        final Wars wars = Wars.get(server);
        return wars.lockedAgainst(attacker, defender, now) < 0 ? 0 : (int) ((wars.lockLeft(defender, now) + 19) / 20);
    }

    /**
     * The refusal's wording: how many minutes (rounded up) the defender stays reserved.
     */
    public static String lockMessage(final String defender, final String holder, final long ticksLeft)
    {
        return defender + " est aux prises avec " + holder + " : encore " + Math.max(1, (ticksLeft + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE)
                 + " min avant de pouvoir l'attaquer.";
    }

    /**
     * An assault of {@code attacker} on {@code defender} was launched: starts the defender's lock when it has none.
     */
    public static void onAssaultLaunched(final MinecraftServer server, final int attacker, final int defender)
    {
        Wars.get(server).startLock(attacker, defender, server.overworld().getGameTime(), lockTicks());
    }

    /**
     * Ticks before {@code defender} may demand the surrender of {@code attacker}: 0 when it may, -1 when it cannot
     * (see {@link Wars#surrenderWait}).
     */
    public static long surrenderWait(final MinecraftServer server, final int attacker, final int defender)
    {
        final boolean campaign = WarMarches.marching(attacker, defender) || WarAssaults.assaulting(attacker, defender);
        return Wars.get(server).surrenderWait(attacker, defender, server.overworld().getGameTime(), Diplomacy.noticeTicks(), idleTicks(), campaign);
    }

    /**
     * The tribute a treasury of {@code balance} pays: {@code percent} of it, and never what brings the treasury under
     * {@code reserve} (the capitulation passes 0, the surrender demand the reserve that loot leaves untouched).
     */
    public static long tribute(final int percent, final long balance, final long reserve)
    {
        return Math.max(0L, Math.min(balance * percent / 100, balance - reserve));
    }

    /**
     * Moves the surrender tribute from the loser's treasury to the winner's.
     *
     * @return the couronnes moved
     */
    public static long takeTribute(final RegionalTreasury treasury, final IColony from, final IColony to, final int percent, final long reserve)
    {
        final long tribute = tribute(percent, treasury.balance(from), reserve);
        if (tribute <= 0 || !treasury.spend(from, tribute))
        {
            return 0L;
        }
        treasury.credit(to, tribute);
        return tribute;
    }

    /**
     * Which outcomes are won by the defender: it abandons or is demanded to surrender.
     */
    public static boolean defenderWins(final int outcome)
    {
        return outcome == fr.kodistan.interregne.network.WarRecapPayload.ABANDON || outcome == fr.kodistan.interregne.network.WarRecapPayload.REDDITION;
    }

    /**
     * The colony that gets the shield: the defender, except after a surrender demand, where the attacker has lost and is
     * the one a new war would hit.
     */
    public static int shielded(final int outcome, final int attacker, final int defender)
    {
        return outcome == fr.kodistan.interregne.network.WarRecapPayload.REDDITION ? attacker : defender;
    }
}
