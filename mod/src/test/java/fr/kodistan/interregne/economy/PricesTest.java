package fr.kodistan.interregne.economy;

import fr.kodistan.interregne.Defaults;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.colony.IntendanceDesk;
import fr.kodistan.interregne.colony.Yields;
import fr.kodistan.interregne.politics.Proclamations;
import fr.kodistan.interregne.region.ImperialMarket;
import fr.kodistan.interregne.region.Regions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The price rules, read with the default settings: what players could exploit or would find absurd.
 */
class PricesTest
{
    @BeforeAll
    static void settings()
    {
        Defaults.load();
    }

    @Test
    void marketCannotBeFarmed()
    {
        // Buying a crate then selling it straight back must never earn anything, at any stock
        for (int stock = 1; stock <= 500; stock++)
        {
            assertTrue(ImperialMarket.buyPriceAt(stock) >= ImperialMarket.sellPriceAt(stock - 1),
              "buy at " + stock + " = " + ImperialMarket.buyPriceAt(stock) + " < resell " + ImperialMarket.sellPriceAt(stock - 1));
        }
    }

    @Test
    void marketPricesFollowScarcity()
    {
        for (int stock = 1; stock <= 500; stock++)
        {
            assertTrue(ImperialMarket.factor(stock) <= ImperialMarket.factor(stock - 1), "scarcer is never cheaper at " + stock);
            assertTrue(ImperialMarket.sellPriceAt(stock) >= 1 && ImperialMarket.buyPriceAt(stock) >= 1, "never free at " + stock);
        }
    }

    @Test
    void withdrawalFee()
    {
        assertEquals(0, ImperialBank.feeFor(0));
        assertEquals(0, ImperialBank.feeFor(10), "small withdrawals stay free");
        for (long amount = 0; amount <= 100_000; amount += 137)
        {
            final long fee = ImperialBank.feeFor(amount);
            assertTrue(fee >= 0 && fee <= amount, "fee " + fee + " on " + amount);
            assertTrue(fee <= ImperialBank.feeFor(amount + 137), "more never costs less");
        }
    }

    @Test
    void upgradesCostMoreEachLevel()
    {
        for (int level = 3; level <= 5; level++)
        {
            assertTrue(RegionalTreasury.upgradeCostTo(level) > RegionalTreasury.upgradeCostTo(level - 1), "bank level " + level);
            assertTrue(RegionalTreasury.capacityFor(level) > RegionalTreasury.capacityFor(level - 1), "bank capacity " + level);
            assertTrue(Regions.upgradeCost(level) > Regions.upgradeCost(level - 1), "comptoir level " + level);
        }
    }

    @Test
    void buildingsGetCostlierSlowerAndBetter()
    {
        for (int level = 2; level <= BuildCosts.MAX_LEVEL; level++)
        {
            assertTrue(BuildCosts.forLevel(level) > BuildCosts.forLevel(level - 1), "cost at " + level);
            assertTrue(BuildCosts.secondsForLevel(level) >= BuildCosts.secondsForLevel(level - 1), "time at " + level);
            assertTrue(Yields.outputMultiplier(level) > Yields.outputMultiplier(level - 1), "output at " + level);
            assertTrue(Yields.speedMultiplier(level) >= Yields.speedMultiplier(level - 1), "speed at " + level);
        }
        assertEquals(1.0, Yields.speedMultiplier(1), 1e-9, "level 1 works at normal speed");
    }

    @Test
    void intendanceInfluence()
    {
        assertEquals(0, IntendanceDesk.influenceFor(0));
        assertEquals(1, IntendanceDesk.influenceFor(1), "any payment earns some Influence");
        for (long gold = 1; gold <= 10_000; gold += 7)
        {
            assertTrue(IntendanceDesk.influenceFor(gold) <= IntendanceDesk.influenceFor(gold + 7));
        }
    }

    @Test
    void delaysRead()
    {
        assertEquals("45 s", BuildCosts.formatSeconds(45));
        assertEquals("1 min", BuildCosts.formatSeconds(60));
        assertEquals("1 min 05", BuildCosts.formatSeconds(65));
        assertEquals("20 h 12 min", BuildCosts.formatSeconds(20 * 3600 + 12 * 60));
        assertEquals("2 h", BuildCosts.formatSeconds(7200));
        assertEquals("12 min", Proclamations.formatDelay(12 * 60_000));
        assertEquals("5 h", Proclamations.formatDelay(5 * 3_600_000));
        assertEquals("2 j 4 h", Proclamations.formatDelay((48 + 4) * 3_600_000L));
    }
}
