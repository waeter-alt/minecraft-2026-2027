package fr.kodistan.interregne.commerce;

import fr.kodistan.interregne.Defaults;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The escort tiers: each costs and hires more than the last, a Relais level opens exactly one tier, and a contract saved
 * before the escort existed loads as having none.
 */
class EscortTest
{
    @BeforeAll
    static void config()
    {
        Defaults.load();
    }

    @Test
    void everyTierCostsAndHiresMoreThanThePreviousOne()
    {
        for (int tier = 2; tier <= Escort.MAX_TIER; tier++)
        {
            assertTrue(Escort.cost(tier) > Escort.cost(tier - 1), "tier " + tier + " must cost more than tier " + (tier - 1));
            assertTrue(Escort.headcount(tier) > Escort.headcount(tier - 1), "tier " + tier + " must hire more than tier " + (tier - 1));
        }
        assertEquals(0, Escort.cost(0));
        assertEquals(0, Escort.headcount(0));
    }

    @Test
    void aRelaisLevelOpensOneTierEachAndNeverMoreThanFive()
    {
        assertEquals(0, Escort.maxTier(0));
        assertEquals(1, Escort.maxTier(1));
        assertEquals(5, Escort.maxTier(5));
        assertEquals(5, Escort.maxTier(9));
        assertEquals(0, Escort.maxTier(-3));
    }

    @Test
    void theEscortSurvivesASaveAndAnOldSaveHasNone()
    {
        final Contract contract = new Contract(7, 1, 2, "bois", 20, 5);
        contract.escort = 3;
        contract.escortFromTreasury = 600;
        contract.escortFromPurse = 300;
        contract.swordsmenLeft = 3;
        contract.archersLeft = 1;
        final Contract loaded = Contract.load(contract.save());
        assertEquals(3, loaded.escort);
        assertEquals(900, loaded.escortPaid());
        assertEquals(3, loaded.swordsmenLeft);
        assertEquals(1, loaded.archersLeft);

        // A save from before the escort: none of its keys
        final CompoundTag old = contract.save();
        for (final String key : new String[] {"escort", "escortTreasury", "escortPurse", "swordsmen", "archers"})
        {
            old.remove(key);
        }
        final Contract legacy = Contract.load(old);
        assertEquals(0, legacy.escort);
        assertEquals(0, legacy.escortPaid());
        assertEquals(0, legacy.swordsmenLeft + legacy.archersLeft);
    }
}
