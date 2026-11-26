package fr.kodistan.interregne.dev.gametest;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.garrison.Garrison;
import fr.kodistan.interregne.politics.AllyAid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

/**
 * Aide de clan rules on a fresh {@link AllyAid}, without colonies: the treasury is the real one, reached through a
 * stand-in colony that only knows its id and dimension. Spawning in a real colony is left to playtests.
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class AllyAidTests
{
    private static final int A = 9101;
    private static final int B = 9102;
    private static final int C = 9103;

    private AllyAidTests()
    {
    }

    /** A colony as the treasury sees it: an id in the overworld. */
    private static IColony colony(final int id)
    {
        return (IColony) Proxy.newProxyInstance(AllyAidTests.class.getClassLoader(), new Class<?>[] {IColony.class}, (proxy, method, args) -> switch (method.getName())
        {
            case "getID" -> id;
            case "getDimension" -> Level.OVERWORLD;
            case "getName", "toString" -> "colonie-test-" + id;
            case "hashCode" -> id;
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    /** Spawns every soldier asked, numbering the citizens from {@code first}. */
    private static AllyAid.Spawner spawning(final int[] next)
    {
        return (helper, kind) -> new AllyAid.Guest(helper, next[0]++, new UUID(0, next[0]), kind);
    }

    private static void expect(final GameTestHelper helper, final long expected, final long actual, final String what)
    {
        if (expected != actual)
        {
            helper.fail(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(final GameTestHelper helper, final boolean ok, final String what)
    {
        if (!ok)
        {
            helper.fail(what);
        }
    }

    /** Filling the reserve takes the price of the missing soldiers from the treasury, never above the maximum. */
    @GameTest(template = "empty")
    public static void refillPaysFromTheTreasury(final GameTestHelper helper)
    {
        final RegionalTreasury treasury = RegionalTreasury.get(helper.getLevel().getServer());
        final IColony a = colony(A);
        final AllyAid aid = new AllyAid();
        treasury.credit(a, AllyAid.max() * AllyAid.price() + 7);
        final long before = treasury.balance(a);

        check(helper, aid.refill(A, B, true, cost -> treasury.spend(a, cost)) == null, "a first refill must go through");
        expect(helper, AllyAid.max(), aid.reserve(A, B), "reserve after the refill");
        expect(helper, before - AllyAid.max() * AllyAid.price(), treasury.balance(a), "treasury after the refill");

        check(helper, aid.refill(A, B, true, cost -> treasury.spend(a, cost)) != null, "a full contingent must refuse a refill");
        expect(helper, before - AllyAid.max() * AllyAid.price(), treasury.balance(a), "treasury after a refused refill");
        expect(helper, 0, aid.reserve(B, A), "the reserve goes one way only");
        treasury.spend(a, treasury.balance(a));
        helper.succeed();
    }

    /** No refill for a non-ally, nor without the money: nothing is taken, nothing is reserved. */
    @GameTest(template = "empty")
    public static void refillRefusals(final GameTestHelper helper)
    {
        final RegionalTreasury treasury = RegionalTreasury.get(helper.getLevel().getServer());
        final IColony a = colony(A);
        treasury.spend(a, treasury.balance(a));
        treasury.credit(a, AllyAid.price());
        final AllyAid aid = new AllyAid();

        check(helper, aid.refill(A, B, false, cost -> treasury.spend(a, cost)) != null, "no contingent for a colony that is not an ally");
        check(helper, AllyAid.max() == 1 || aid.refill(A, B, true, cost -> treasury.spend(a, cost)) != null, "a refill the treasury cannot pay must be refused");
        expect(helper, AllyAid.price(), treasury.balance(a), "treasury after the refusals");
        expect(helper, AllyAid.max() == 1 ? 1 : 0, aid.reserve(A, B), "reserve after the refusals");
        treasury.spend(a, treasury.balance(a));
        helper.succeed();
    }

    /** Several allies help the same colony: each contingent appears, half légionnaires, the odd one a légionnaire. */
    @GameTest(template = "empty")
    public static void severalHelpersEngage(final GameTestHelper helper)
    {
        final AllyAid aid = new AllyAid();
        aid.refill(A, C, true, cost -> true);
        aid.refill(B, C, true, cost -> true);
        final int[] legion = {0};
        final int[] next = {1};
        final List<AllyAid.Sent> sent = aid.engage(C, helperId -> true, (helperId, kind) -> {
            legion[0] += kind == Garrison.LEGIONNAIRE ? 1 : 0;
            return spawning(next).spawn(helperId, kind);
        });

        expect(helper, 2, sent.size(), "contingents engaged");
        expect(helper, 2L * ((AllyAid.max() + 1) / 2), legion[0], "légionnaires among them");
        expect(helper, 2L * AllyAid.max(), aid.tally(C)[0], "soldiers received");
        check(helper, aid.isEngaged(A, C) && aid.isEngaged(B, C), "both contingents must be out");
        expect(helper, 0, aid.reserve(A, C), "reserve left once out");
        check(helper, aid.isGuest(C, 1, new UUID(0, 2)), "the first soldier is a guest of the helped colony");
        check(helper, aid.refill(A, C, true, cost -> true) != null, "no refill while the contingent is out");
        expect(helper, 0, aid.engage(C, helperId -> true, spawning(next)).stream().filter(s -> s.spawned() > 0).count(), "a second assault brings no one more");
        helper.succeed();
    }

    /** A colony without towers takes no one: the reserve stays whole and the contingent is not out. */
    @GameTest(template = "empty")
    public static void noTowerKeepsTheReserve(final GameTestHelper helper)
    {
        final AllyAid aid = new AllyAid();
        aid.refill(A, C, true, cost -> true);
        aid.engage(C, helperId -> true, (helperId, kind) -> null);
        expect(helper, AllyAid.max(), aid.reserve(A, C), "reserve after an assault with no tower");
        check(helper, !aid.isEngaged(A, C), "nothing appeared: the contingent is not out");
        helper.succeed();
    }

    /** At the end of the war the survivors become the new reserve, and the war's figures are forgotten. */
    @GameTest(template = "empty")
    public static void survivorsBecomeTheReserve(final GameTestHelper helper)
    {
        final AllyAid aid = new AllyAid();
        aid.refill(A, C, true, cost -> true);
        final int[] next = {1};
        aid.engage(C, helperId -> true, spawning(next));
        final int dead = Math.min(4, AllyAid.max());
        for (int citizen = 1; citizen <= dead; citizen++)
        {
            check(helper, aid.fell(C, citizen, new UUID(0, citizen + 1)), "soldier " + citizen + " must fall");
        }
        expect(helper, dead, aid.tally(C)[1], "soldiers fallen");
        expect(helper, AllyAid.max() - dead, aid.alive(A, C), "soldiers still standing");

        final List<AllyAid.Back> back = aid.release(C, helperId -> true, guest -> true);
        expect(helper, 1, back.size(), "contingents back");
        expect(helper, AllyAid.max(), back.get(0).sent(), "soldiers sent");
        expect(helper, AllyAid.max() - dead, back.get(0).survivors(), "survivors");
        expect(helper, AllyAid.max() - dead, aid.reserve(A, C), "new reserve");
        check(helper, !aid.isEngaged(A, C) && aid.guestsOf(C).isEmpty(), "nobody is out any more");
        expect(helper, 0, aid.tally(C)[0], "the war's figures are forgotten");
        expect(helper, (long) dead * AllyAid.price(), aid.refillCost(A, C), "refilling costs only the fallen");
        helper.succeed();
    }

    /** A broken alliance loses both reserves; a contingent already out stays, and its survivors do not come back. */
    @GameTest(template = "empty")
    public static void breakingLosesTheReserve(final GameTestHelper helper)
    {
        final AllyAid aid = new AllyAid();
        aid.refill(A, B, true, cost -> true);
        aid.refill(B, A, true, cost -> true);
        aid.forget(A, B);
        expect(helper, 0, aid.reserve(A, B) + aid.reserve(B, A), "reserves after the break");

        aid.refill(A, C, true, cost -> true);
        final int[] next = {1};
        aid.engage(C, helperId -> true, spawning(next));
        aid.forget(A, C);
        check(helper, aid.isGuest(C, 1, new UUID(0, 2)), "the contingent out stays after the break");
        final List<AllyAid.Back> back = aid.release(C, helperId -> false, guest -> true);
        check(helper, back.size() == 1 && !back.get(0).allied(), "the contingent comes back to a broken alliance");
        expect(helper, 0, aid.reserve(A, C), "no reserve after the break");
        helper.succeed();
    }
}
