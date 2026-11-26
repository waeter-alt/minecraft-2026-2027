package fr.kodistan.interregne.dev.gametest;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.commerce.Contract;
import fr.kodistan.interregne.commerce.Contracts;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.politics.AllyAid;
import fr.kodistan.interregne.politics.ColonyGone;
import fr.kodistan.interregne.politics.Diplomacy;
import fr.kodistan.interregne.politics.Wars;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;

/**
 * A colony deleted in the middle of a war, and a war declared on a trade partner: wars closed with no winner, clan aid
 * forgotten, contracts refunded. Colonies are stand-ins that only know their id and dimension; the saved data are the
 * real ones, under ids of their own so nothing else running on the server is touched. Assaults need a real colony
 * and are left to playtests.
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class ColonyGoneTests
{
    private ColonyGoneTests()
    {
    }

    /** A colony as the treasury sees it: an id in the overworld. */
    private static IColony colony(final int id)
    {
        return (IColony) Proxy.newProxyInstance(ColonyGoneTests.class.getClassLoader(), new Class<?>[] {IColony.class}, (proxy, method, args) -> switch (method.getName())
        {
            case "getID" -> id;
            case "getDimension" -> Level.OVERWORLD;
            case "getName", "toString" -> "colonie-test-" + id;
            case "hashCode" -> id;
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static void check(final GameTestHelper helper, final boolean ok, final String what)
    {
        if (!ok)
        {
            helper.fail(what);
        }
    }

    private static void expect(final GameTestHelper helper, final long expected, final long actual, final String what)
    {
        if (expected != actual)
        {
            helper.fail(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void drain(final RegionalTreasury treasury, final IColony colony)
    {
        treasury.spend(colony, treasury.balance(colony));
    }

    private static Contract contract(final Contracts contracts, final long id, final int seller, final int buyer, final int state)
    {
        final Contract contract = new Contract(id, seller, buyer, Crate.BOIS.id(), 20, 5);
        contract.state = state;
        contract.escrow = 100;
        contract.path = new long[] {0L, 1L};
        contracts.all().add(contract);
        return contract;
    }

    /**
     * The colony disappears at war on both sides: both wars close with no shield and no loot, the aid it gave, received
     * or had out in an ally is forgotten, and what the others own is untouched.
     */
    @GameTest(template = "empty")
    public static void aColonyDeletedAtWarClosesEverythingItHeld(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final int gone = 9201;
        final int foe = 9202;
        final int victim = 9203;
        final int ally = 9204;
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        final IColony foeColony = colony(foe);
        final IColony victimColony = colony(victim);
        treasury.credit(foeColony, 700);
        treasury.credit(victimColony, 300);
        final Wars wars = Wars.get(server);
        wars.declare(gone, victim, 10);
        wars.declare(foe, gone, 10);
        wars.declare(foe, victim, 10);
        wars.shield(gone, 999_999);
        wars.shield(victim, 999_999);

        final AllyAid aid = AllyAid.get(server);
        aid.refill(ally, gone, true, cost -> true);
        aid.refill(gone, ally, true, cost -> true);
        aid.refill(gone, victim, true, cost -> true);
        final int[] next = {1};
        aid.engage(victim, helperId -> true, (helperId, kind) -> new AllyAid.Guest(helperId, next[0]++, new java.util.UUID(9, next[0]), kind));
        check(helper, aid.isEngaged(gone, victim), "set-up: the colony's contingent must be out in its ally");

        ColonyGone.purge(server, gone, "Disparue", id -> null);

        check(helper, !wars.between(gone, victim) && !wars.between(foe, gone), "every war the colony was in must be closed");
        check(helper, wars.declaredBy(foe, victim), "a war between two other colonies must stay");
        expect(helper, 0, wars.shieldLeft(gone, 0), "the gone colony's shield");
        check(helper, wars.shieldLeft(victim, 0) > 0, "another colony's shield must stay");
        expect(helper, 0, aid.reserve(ally, gone) + aid.reserve(gone, ally) + aid.reserve(gone, victim), "reserves naming the colony");
        check(helper, !aid.isEngaged(gone, victim) && aid.guestsOf(victim).isEmpty(), "the contingent it had out in an ally must be gone");
        check(helper, !aid.colonyIds().contains(gone) && !wars.colonyIds().contains(gone), "no saved data may name the colony any more");
        expect(helper, 700, treasury.balance(foeColony), "the opponent's treasury: no loot, no tribute");
        expect(helper, 300, treasury.balance(victimColony), "the other opponent's treasury");

        wars.end(foe, victim);
        wars.shield(victim, 0);
        drain(treasury, foeColony);
        drain(treasury, victimColony);
        helper.succeed();
    }

    /**
     * The contracts of the gone colony: the buyer still there gets its price back, the crates are lost when the seller
     * is the one gone, an offer gives its escort money back to the seller still there.
     */
    @GameTest(template = "empty")
    public static void theContractsOfADeletedColonyAreRefunded(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        final Contracts contracts = Contracts.get(server);
        final int gone = 9211;
        final int buyer = 9212;
        final int seller = 9213;
        final int other = 9214;
        final IColony buyerColony = colony(buyer);
        final IColony sellerColony = colony(seller);

        final Contract onTheRoad = contract(contracts, 9_211_001, gone, buyer, Contract.TRANSIT);
        final Contract offer = contract(contracts, 9_211_002, seller, gone, Contract.OFFER);
        offer.escortFromTreasury = 40;
        final Contract unrelated = contract(contracts, 9_211_003, seller, other, Contract.OFFER);
        final long buyerBefore = treasury.balance(buyerColony);
        final long sellerBefore = treasury.balance(sellerColony);

        contracts.cancelOf(server, gone, id -> id == buyer ? buyerColony : id == seller ? sellerColony : null);

        check(helper, contracts.byId(onTheRoad.id) == null && contracts.byId(offer.id) == null, "the colony's contracts must be cancelled");
        check(helper, contracts.byId(unrelated.id) != null, "a contract between two other colonies must stay");
        expect(helper, buyerBefore + 100, treasury.balance(buyerColony), "the buyer still there is refunded the price held");
        expect(helper, sellerBefore + 40, treasury.balance(sellerColony), "the seller still there gets its escort money back");

        contracts.all().remove(unrelated);
        drain(treasury, buyerColony);
        drain(treasury, sellerColony);
        helper.succeed();
    }

    /** A purge at server start finds the colonies our data names that MineColonies no longer knows, and only those. */
    @GameTest(template = "empty")
    public static void theStartPurgeFindsOnlyTheUnknownColonies(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final int vanished = 9221;
        final int alive = 9222;
        final Wars wars = Wars.get(server);
        wars.declare(vanished, alive, 10);
        final Contracts contracts = Contracts.get(server);
        final Contract contract = contract(contracts, 9_221_001, alive, vanished, Contract.OFFER);

        final int purged = ColonyGone.purgeUnknown(server, id -> id != vanished);

        expect(helper, 1, purged, "colonies purged");
        check(helper, !wars.between(vanished, alive), "the war with the vanished colony must be closed");
        check(helper, contracts.byId(contract.id) == null, "its contract must be cancelled");
        expect(helper, 0, ColonyGone.purgeUnknown(server, id -> id != vanished), "a second purge finds nothing");
        helper.succeed();
    }

    /**
     * Declaring war on a trade partner cancels the contracts between the two, like a broken alliance; a contract
     * between two other colonies is left alone.
     */
    @GameTest(template = "empty")
    public static void aWarDeclarationCancelsTheContractsBetweenTheTwo(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final Contracts contracts = Contracts.get(server);
        final int a = 9231;
        final int b = 9232;
        final int c = 9233;
        final Contract onTheRoad = contract(contracts, 9_231_001, a, b, Contract.TRANSIT);
        final Contract reverse = contract(contracts, 9_231_002, b, a, Contract.OFFER);
        final Contract elsewhere = contract(contracts, 9_231_003, b, c, Contract.TRANSIT);

        Diplomacy.cutTies(server, a, b);

        check(helper, contracts.byId(onTheRoad.id) == null && contracts.byId(reverse.id) == null, "the contracts between the two must be cancelled");
        check(helper, contracts.byId(elsewhere.id) != null, "a caravan between two other colonies must stay on the road");
        contracts.all().remove(elsewhere);
        helper.succeed();
    }
}
