package fr.kodistan.interregne.dev.gametest;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.network.WarRecapPayload;
import fr.kodistan.interregne.politics.WarRules;
import fr.kodistan.interregne.politics.Wars;
import fr.kodistan.interregne.war.RuinCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * The war rules that sit around assaults, on a fresh {@link Wars} and stand-in colonies (see {@link AllyAidTests}): the
 * defender's lock, the conditions and the tribute of a demanded surrender, and the taking of a ruined town hall.
 * Launching a real assault is left to playtests.
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class WarRulesTests
{
    private static final int A = 9301;
    private static final int B = 9302;
    private static final int D = 9303;
    private static final long NOTICE = 3600L;
    private static final long LOCK = 120L * 60 * 20;
    private static final long IDLE = 6L * 3600 * 20;

    private WarRulesTests()
    {
    }

    private static IColony colony(final int id)
    {
        return (IColony) Proxy.newProxyInstance(WarRulesTests.class.getClassLoader(), new Class<?>[] {IColony.class}, (proxy, method, args) -> switch (method.getName())
        {
            case "getID" -> id;
            case "getDimension" -> Level.OVERWORLD;
            case "getName", "toString" -> "colonie-test-" + id;
            case "hashCode" -> id;
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
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

    /** The first colony to assault takes the defender for two hours; the others are refused, the holder is not. */
    @GameTest(template = "empty")
    public static void lockReservesTheDefender(final GameTestHelper helper)
    {
        final Wars wars = new Wars();
        wars.declare(A, D, 0);
        wars.declare(B, D, 0);
        expect(helper, -1, wars.lockedAgainst(B, D, 100), "no lock before any assault");

        wars.startLock(A, D, 100, LOCK);
        expect(helper, A, wars.lockedAgainst(B, D, 200), "B must be blocked by A");
        expect(helper, -1, wars.lockedAgainst(A, D, 200), "A is never blocked by its own lock");
        expect(helper, LOCK - 100, wars.lockLeft(D, 200), "the window runs from the first assault");

        wars.startLock(A, D, 5000, LOCK);
        expect(helper, LOCK - 5000, wars.lockLeft(D, 5100), "a further assault of the holder does not extend the window");
        wars.startLock(B, D, 5000, LOCK);
        expect(helper, A, wars.lockHolder(D, 5100), "B cannot take a defender that is held");
        expect(helper, -1, wars.lockedAgainst(B, 9399, 5100), "another defender is free");
        helper.succeed();
    }

    /** Once the window ran out the next assault starts a new one; the war between holder and defender ending frees it at once. */
    @GameTest(template = "empty")
    public static void lockExpiresAndFallsWithTheWar(final GameTestHelper helper)
    {
        final Wars wars = new Wars();
        wars.declare(A, D, 0);
        wars.declare(B, D, 0);
        wars.startLock(A, D, 0, LOCK);
        expect(helper, A, wars.lockedAgainst(B, D, LOCK - 1), "still held one tick before the end");
        expect(helper, -1, wars.lockedAgainst(B, D, LOCK), "free once the window is over");
        expect(helper, 0, wars.lockLeft(D, LOCK + 10), "nothing left after the end");

        wars.startLock(B, D, LOCK + 10, LOCK);
        expect(helper, B, wars.lockHolder(D, LOCK + 20), "the first to assault after the end takes a new window");
        expect(helper, B, wars.lockedAgainst(A, D, LOCK + 20), "and now A is the one blocked");

        wars.end(A, D);
        expect(helper, B, wars.lockHolder(D, LOCK + 20), "the end of another war leaves the lock alone");
        wars.end(B, D);
        expect(helper, -1, wars.lockHolder(D, LOCK + 20), "the end of the holder's war frees the defender");

        wars.declare(A, D, 0);
        wars.startLock(A, D, 0, LOCK);
        wars.end(A, D);
        expect(helper, -1, wars.lockedAgainst(B, D, 10), "peace frees the defender at once");
        helper.succeed();
    }

    /** The lock, the repelled assaults and the idle clock survive a save. */
    @GameTest(template = "empty")
    public static void lockAndSurrenderStateSurviveASave(final GameTestHelper helper)
    {
        final Wars wars = new Wars();
        wars.declare(A, D, 0);
        wars.startLock(A, D, 100, LOCK);
        wars.finishAssault(A, D, 700, true);
        final HolderLookup.Provider registries = helper.getLevel().registryAccess();
        final CompoundTag tag = wars.save(new CompoundTag(), registries);
        final Wars loaded;
        try
        {
            final var load = Wars.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
            load.setAccessible(true);
            loaded = (Wars) load.invoke(null, tag, registries);
        }
        catch (final ReflectiveOperationException e)
        {
            helper.fail("Wars.load is not reachable: " + e);
            return;
        }
        expect(helper, A, loaded.lockHolder(D, 800), "lock holder after a reload");
        expect(helper, LOCK - 700, loaded.lockLeft(D, 800), "lock time left after a reload");
        expect(helper, 1, loaded.repelled(A, D), "repelled assaults after a reload");
        expect(helper, 0, loaded.surrenderWait(A, D, NOTICE + 800, NOTICE, IDLE, true), "surrender still open after a reload");
        helper.succeed();
    }

    /** Condition (a): one repelled assault is enough, even with troops out again. */
    @GameTest(template = "empty")
    public static void surrenderAfterARepelledAssault(final GameTestHelper helper)
    {
        final Wars wars = new Wars();
        wars.declare(A, D, 0);
        expect(helper, -1, wars.surrenderWait(A, D, NOTICE - 1, NOTICE, IDLE, false), "no surrender during the notice");
        expect(helper, -1, wars.surrenderWait(B, D, NOTICE + 10, NOTICE, IDLE, false), "no surrender without a war");
        check(helper, wars.surrenderWait(A, D, NOTICE + 10, NOTICE, IDLE, false) > 0, "an open war with no assault yet is not enough");

        wars.finishAssault(A, D, NOTICE + 500, false);
        check(helper, wars.surrenderWait(A, D, NOTICE + 600, NOTICE, IDLE, false) > 0, "an assault that was not repelled does not open it");
        wars.finishAssault(A, D, NOTICE + 900, true);
        expect(helper, 0, wars.surrenderWait(A, D, NOTICE + 1000, NOTICE, IDLE, false), "a repelled assault opens it");
        expect(helper, 0, wars.surrenderWait(A, D, NOTICE + 1000, NOTICE, IDLE, true), "even with troops out again");

        wars.end(A, D);
        wars.declare(A, D, 5000);
        check(helper, wars.surrenderWait(A, D, 5000 + NOTICE + 10, NOTICE, IDLE, false) > 0, "a new war starts from nothing");
        helper.succeed();
    }

    /** Condition (b): six hours without an assault, counted from the end of the notice or of the last assault. */
    @GameTest(template = "empty")
    public static void surrenderAfterIdleness(final GameTestHelper helper)
    {
        final Wars wars = new Wars();
        wars.declare(A, D, 0);
        expect(helper, 1000, wars.surrenderWait(A, D, NOTICE + IDLE - 1000, NOTICE, IDLE, false), "time left counted from the end of the notice");
        expect(helper, 0, wars.surrenderWait(A, D, NOTICE + IDLE, NOTICE, IDLE, false), "open once the idleness is over");
        expect(helper, -1, wars.surrenderWait(A, D, NOTICE + IDLE, NOTICE, IDLE, true), "never while the troops are out");

        final long lastEnd = NOTICE + 10000;
        wars.finishAssault(A, D, lastEnd, false);
        expect(helper, IDLE, wars.surrenderWait(A, D, lastEnd, NOTICE, IDLE, false), "then counted from the end of the last assault");
        expect(helper, 0, wars.surrenderWait(A, D, lastEnd + IDLE, NOTICE, IDLE, false), "open again six hours after it");
        helper.succeed();
    }

    /** The tribute is the capitulation's amount at most and never takes the loser's treasury under the reserve. */
    @GameTest(template = "empty")
    public static void tributeIsCappedAndKeepsTheReserve(final GameTestHelper helper)
    {
        expect(helper, 500, WarRules.tribute(500, 10_000, 200), "capped by the tribute");
        expect(helper, 400, WarRules.tribute(500, 600, 200), "capped by the reserve");
        expect(helper, 0, WarRules.tribute(500, 150, 200), "nothing under the reserve");

        final RegionalTreasury treasury = RegionalTreasury.get(helper.getLevel().getServer());
        final IColony loser = colony(9311);
        final IColony winner = colony(9312);
        treasury.spend(loser, treasury.balance(loser));
        treasury.spend(winner, treasury.balance(winner));
        treasury.credit(loser, 1000);

        expect(helper, 500, WarRules.takeTribute(treasury, loser, winner, 500, 200), "first tribute");
        expect(helper, 500, treasury.balance(loser), "loser's treasury after the first");
        expect(helper, 500, treasury.balance(winner), "winner's treasury after the first");
        expect(helper, 300, WarRules.takeTribute(treasury, loser, winner, 500, 200), "second tribute stops at the reserve");
        expect(helper, 200, treasury.balance(loser), "the reserve is left");
        expect(helper, 0, WarRules.takeTribute(treasury, loser, winner, 500, 200), "nothing more to take");
        expect(helper, 800, treasury.balance(winner), "winner's treasury at the end");
        treasury.spend(loser, treasury.balance(loser));
        treasury.spend(winner, treasury.balance(winner));
        helper.succeed();
    }

    /** After a demanded surrender the defender wins and the attacker gets the shield; the other outcomes are unchanged. */
    @GameTest(template = "empty")
    public static void surrenderOutcomeWinnerAndShield(final GameTestHelper helper)
    {
        check(helper, WarRules.defenderWins(WarRecapPayload.REDDITION), "the defender wins a demanded surrender");
        check(helper, WarRules.defenderWins(WarRecapPayload.ABANDON), "the defender still wins an abandon");
        check(helper, !WarRules.defenderWins(WarRecapPayload.CONQUEST) && !WarRules.defenderWins(WarRecapPayload.CAPITULATION), "the attacker wins the others");
        expect(helper, A, WarRules.shielded(WarRecapPayload.REDDITION, A, D), "the attacker is shielded after a surrender demand");
        expect(helper, D, WarRules.shielded(WarRecapPayload.CAPITULATION, A, D), "the defender after a capitulation");
        expect(helper, D, WarRules.shielded(WarRecapPayload.CONQUEST, A, D), "the defender after a conquest");
        expect(helper, D, WarRules.shielded(WarRecapPayload.ABANDON, A, D), "the defender after an abandon");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockRefusalNamesBothColoniesAndTheMinutes(final GameTestHelper helper)
    {
        final String text = WarRules.lockMessage("Valdorée", "Hautebrume", 90L * 60 * 20);
        check(helper, text.equals("Valdorée est aux prises avec Hautebrume : encore 90 min avant de pouvoir l'attaquer."), "wording: " + text);
        check(helper, WarRules.lockMessage("V", "H", 1).contains("encore 1 min"), "a started minute counts as one");
        helper.succeed();
    }

    /** A ruined town hall is the last target of the assault, to be taken; a standing one stays a normal target. */
    @GameTest(template = "empty")
    public static void ruinedTownHallIsPlannedAsTheLastTarget(final GameTestHelper helper)
    {
        final BlockPos hall = new BlockPos(0, 64, 0);
        final List<BlockPos> targets = new ArrayList<>(List.of(new BlockPos(10, 64, 0), new BlockPos(20, 64, 5)));

        check(helper, RuinCapture.plan(targets, hall, false, 1200) == null && targets.size() == 2, "a standing town hall is not planned as a capture");
        final RuinCapture capture = RuinCapture.plan(targets, hall, true, 1200);
        check(helper, capture != null, "a ruined town hall is planned as a capture");
        expect(helper, 3, targets.size(), "the ruin joins the targets");
        check(helper, targets.get(2).equals(hall), "the ruin is the last target");
        check(helper, RuinCapture.plan(targets, hall, true, 1200) == null && targets.size() == 3, "never planned twice");
        helper.succeed();
    }

    /** Cumulated presence: it grows with troops around, waits without, never falls back, and completes at the needed time. */
    @GameTest(template = "empty")
    public static void captureProgressPausesAndNeverResets(final GameTestHelper helper)
    {
        final RuinCapture capture = new RuinCapture(60L * 20);
        check(helper, !capture.advance(0, 200) && capture.doneTicks() == 0, "nobody around: no progress");
        capture.advance(2, 600);
        expect(helper, 600, capture.doneTicks(), "10 s with two troops count once");
        capture.advance(0, 300);
        expect(helper, 600, capture.doneTicks(), "the progress waits while the zone is empty");
        check(helper, capture.progress() == 0.5F, "half done");
        check(helper, !capture.advance(1, 590), "not done a step before the end");
        check(helper, capture.advance(1, 10) && capture.complete(), "done once 60 s have been spent around the ruin");
        capture.advance(0, 100);
        check(helper, capture.complete() && capture.progress() == 1F, "a taken ruin stays taken");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void captureZone(final GameTestHelper helper)
    {
        check(helper, RuinCapture.inZone(0, 0, 0), "the centre is inside");
        check(helper, RuinCapture.inZone(RuinCapture.RADIUS - 0.5, 3, 0), "inside near the edge");
        check(helper, !RuinCapture.inZone(RuinCapture.RADIUS + 0.5, 0, 0), "outside past the radius");
        check(helper, !RuinCapture.inZone(4, 0, 4.5), "the radius is measured on the diagonal too");
        check(helper, !RuinCapture.inZone(0, RuinCapture.HEIGHT + 1, 0), "too high above counts as outside");
        helper.succeed();
    }
}
