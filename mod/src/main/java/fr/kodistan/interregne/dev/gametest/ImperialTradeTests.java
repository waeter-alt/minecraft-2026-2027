package fr.kodistan.interregne.dev.gametest;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.commerce.Contract;
import fr.kodistan.interregne.commerce.Contracts;
import fr.kodistan.interregne.commerce.ImperialRoutes;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.region.Crate;
import fr.kodistan.interregne.region.CrateEncumbrance;
import fr.kodistan.interregne.region.ImperialMarket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Selling to the Empire: the road is paid by the colony's treasury, a caravan is paid at the Marché's price when it
 * arrives, and carried crates slow the player in proportion, up to a ceiling. There is no colony on the test server, so
 * the colony here is a stand-in that only knows its id and dimension, which is all the treasury needs.
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class ImperialTradeTests
{
    /** Each test its own colony: the tests of a batch share one server and one treasury. */
    private static final AtomicInteger NEXT_COLONY = new AtomicInteger(9_100);

    private ImperialTradeTests()
    {
    }

    private static IColony colony(final int id)
    {
        return (IColony) Proxy.newProxyInstance(ImperialTradeTests.class.getClassLoader(), new Class<?>[] {IColony.class}, (proxy, method, args) -> switch (method.getName())
        {
            case "getID" -> id;
            case "getDimension" -> Level.OVERWORLD;
            case "getName" -> "Colonie d'essai " + id;
            case "getCenter" -> BlockPos.ZERO;
            case "hashCode" -> id;
            case "equals" -> proxy == args[0];
            case "toString" -> "colonie d'essai " + id;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static long[] road(final GameTestHelper helper, final int cells)
    {
        final long[] path = new long[cells];
        for (int i = 0; i < cells; i++)
        {
            path[i] = helper.absolutePos(new BlockPos(i / 2, 1, 2)).asLong();
        }
        return path;
    }

    private static void floor(final GameTestHelper helper)
    {
        for (int x = -2; x < 14; x++)
        {
            for (int z = -2; z < 12; z++)
            {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    @GameTest(template = "empty")
    public static void openingTheImperialRoadIsPaidByTheTreasury(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final IColony colony = colony(NEXT_COLONY.incrementAndGet());
        final BlockPos market = helper.absolutePos(new BlockPos(5, 1, 5));
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        final ImperialRoutes routes = ImperialRoutes.get(server);
        final long cost = Config.IMPERIAL_ROAD_COST.get();
        treasury.credit(colony, cost + 50);

        final String refusal = routes.pay(server, colony, market);
        if (refusal != null || treasury.balance(colony) != 50)
        {
            helper.fail("the road must be paid by the treasury: refusal " + refusal + ", balance " + treasury.balance(colony));
            return;
        }
        // A site under way is not paid twice
        treasury.credit(colony, cost);
        if (routes.pay(server, colony, market) == null || treasury.balance(colony) != 50 + cost)
        {
            helper.fail("a road already under way must be refused without taking anything");
            return;
        }
        routes.complete(colony.getID(), market, road(helper, 4));
        if (!routes.isOpen(colony.getID(), market) || ImperialRoutes.isBuilding(colony.getID(), market))
        {
            helper.fail("a finished road must be open and no longer a site");
            return;
        }
        // An open road is reused: nothing to pay again
        if (routes.pay(server, colony, market) == null || treasury.balance(colony) != 50 + cost)
        {
            helper.fail("an open road must be reused, not paid again");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theImperialRoadIsRefusedWithoutFunds(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final IColony colony = colony(NEXT_COLONY.incrementAndGet());
        final BlockPos market = helper.absolutePos(new BlockPos(6, 1, 6));
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        final long cost = Config.IMPERIAL_ROAD_COST.get();
        if (cost < 1)
        {
            helper.fail("the default road cost must be above zero for this test");
            return;
        }
        treasury.credit(colony, cost - 1);
        final ImperialRoutes routes = ImperialRoutes.get(server);
        if (routes.pay(server, colony, market) == null)
        {
            helper.fail("a treasury one couronne short must be refused");
            return;
        }
        if (treasury.balance(colony) != cost - 1 || ImperialRoutes.isBuilding(colony.getID(), market) || routes.isOpen(colony.getID(), market))
        {
            helper.fail("a refusal must take nothing and start nothing");
            return;
        }
        // A road that cannot be traced gives the money back
        treasury.credit(colony, 1);
        if (routes.pay(server, colony, market) != null || treasury.balance(colony) != 0)
        {
            helper.fail("paying with exactly the price must work");
            return;
        }
        routes.abandon(server, colony, market);
        if (treasury.balance(colony) != cost || ImperialRoutes.isBuilding(colony.getID(), market))
        {
            helper.fail("an abandoned road must give the price back, got " + treasury.balance(colony));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCaravanIsPaidAtThePriceOfItsArrival(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final IColony colony = colony(NEXT_COLONY.incrementAndGet());
        final ImperialMarket market = ImperialMarket.get(server);
        final Contracts contracts = Contracts.get(server);
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        final Crate crate = Crate.BOIS;
        final int base = Config.MARKET_BASE_STOCK.get();
        final int before = market.stock(crate);
        market.setStock(crate, base);
        final int quantity = 20;

        final long atDeparture = market.sellTotal(crate, quantity);
        final Contract contract = contracts.dispatchImperial(server, colony.getID(), crate, quantity, road(helper, 6));
        if (!contract.imperial() || contract.state != Contract.TRANSIT || contract.total() < 1)
        {
            contracts.all().remove(contract);
            market.setStock(crate, before);
            helper.fail("an imperial caravan must be on the road at once, with an estimate");
            return;
        }
        // Other sellers fill the Empire's stock while the caravan walks: the price falls
        market.setStock(crate, base + 60);
        final long atArrival = market.sellTotal(crate, quantity);
        final long paid = Contracts.settle(server, colony, contract);
        final long credited = treasury.balance(colony);
        final int stockAfter = market.stock(crate);
        contracts.all().remove(contract);
        market.setStock(crate, before);
        if (atArrival >= atDeparture)
        {
            helper.fail("the price must fall with the stock for this test to mean anything: " + atArrival + " vs " + atDeparture);
            return;
        }
        if (paid != atArrival || credited != atArrival)
        {
            helper.fail("the caravan must be paid " + atArrival + " (price on arrival), not " + atDeparture + " (price at departure): paid " + paid + ", treasury " + credited);
            return;
        }
        if (stockAfter != base + 60 + quantity || contract.left != 0)
        {
            helper.fail("the crates must join the Empire's stock: " + stockAfter);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aCaravanThatReachesTheMarketSettlesAndDisappears(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final ImperialMarket market = ImperialMarket.get(server);
        final Contracts contracts = Contracts.get(server);
        // Its own crate type: no other test touches it, so the stock tells what the tick did
        final Crate crate = Crate.MINERAI;
        final int before = market.stock(crate);
        market.setStock(crate, 100);
        final Contract contract = contracts.dispatchImperial(server, NEXT_COLONY.incrementAndGet(), crate, 5, road(helper, 6));
        contract.progress = contract.path.length - 2;
        helper.succeedWhen(() -> {
            helper.assertTrue(contracts.byId(contract.id) == null, "the caravan must have arrived and been settled");
            helper.assertTrue(market.stock(crate) == 105, "the 5 crates must be in the Empire's stock after the tick, got " + market.stock(crate));
            market.setStock(crate, before);
        });
    }

    @GameTest(template = "empty")
    public static void killingAnImperialCaravanDropsTheCrates(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final Contracts contracts = Contracts.get(level.getServer());
        floor(helper);
        final Contract contract = contracts.dispatchImperial(level.getServer(), NEXT_COLONY.incrementAndGet(), Crate.PIERRE, 12, road(helper, 20));
        contract.progress = 4.5;
        contracts.showOrHide(level.getServer(), level, contract, true);
        final Entity animal = contract.puppet == null ? null : level.getEntity(contract.puppet);
        if (animal == null)
        {
            contracts.all().remove(contract);
            helper.fail("no animal on the imperial road");
            return;
        }
        // Nobody is at war with the seller: a blow does nothing, so the caravan is pillable only by an enemy
        animal.hurt(level.damageSources().generic(), 1000);
        if (!animal.isAlive())
        {
            contracts.all().remove(contract);
            helper.fail("an imperial caravan must resist a blow from nobody at war with its seller");
            return;
        }
        animal.kill();
        final AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20);
        final int crates = level.getEntitiesOfClass(ItemEntity.class, around).stream().filter(item -> Crate.of(item.getItem()) == Crate.PIERRE)
                             .mapToInt(item -> item.getItem().getCount()).sum();
        final boolean gone = contracts.byId(contract.id) == null;
        contracts.all().remove(contract);
        if (!gone || crates != 12)
        {
            helper.fail("a pillaged imperial caravan must end and drop its 12 crates: gone " + gone + ", crates " + crates);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void carriedCratesSlowInProportionUpToACeiling(final GameTestHelper helper)
    {
        final int free = Config.CRATES_SLOWNESS_1.get();
        final int per = Config.CRATES_PER_SLOW_LEVEL.get();
        final int max = Config.CRATES_SLOW_MAX.get();
        if (CrateEncumbrance.slowLevel(0) != 0 || CrateEncumbrance.slowLevel(free) != 0)
        {
            helper.fail("the free load must not slow");
            return;
        }
        if (max < 2 || CrateEncumbrance.slowLevel(free + 1) != 1 || CrateEncumbrance.slowLevel(free + per) != 1 || CrateEncumbrance.slowLevel(free + per + 1) != 2)
        {
            helper.fail("each " + per + " crates beyond the free " + free + " must add a level: " + CrateEncumbrance.slowLevel(free + 1) + ", " + CrateEncumbrance.slowLevel(free + per)
                          + ", " + CrateEncumbrance.slowLevel(free + per + 1));
            return;
        }
        int last = 0;
        for (int carried = 0; carried <= 5_000; carried++)
        {
            final int level = CrateEncumbrance.slowLevel(carried);
            if (level < last || level > max)
            {
                helper.fail("the slowness must never decrease nor pass its ceiling " + max + ": " + level + " at " + carried);
                return;
            }
            last = level;
        }
        if (CrateEncumbrance.slowLevel(5_000) != max)
        {
            helper.fail("a huge load must reach the ceiling " + max + ", got " + CrateEncumbrance.slowLevel(5_000));
            return;
        }
        helper.succeed();
    }
}
