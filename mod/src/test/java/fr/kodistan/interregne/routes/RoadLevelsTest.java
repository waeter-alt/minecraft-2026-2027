package fr.kodistan.interregne.routes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The road palettes: every block leads to a single block one level up, every id exists, the decay knows every block a
 * road can be made of, and the costs and effects read from the config strings as expected.
 */
class RoadLevelsTest
{
    private static List<String> allIds()
    {
        final List<String> out = new ArrayList<>();
        for (int level = 1; level <= RoadLevels.MAX; level++)
        {
            for (final RoadLevels.Role role : RoadLevels.Role.values())
            {
                out.add(role.id(level));
            }
            if (level > 1)
            {
                out.add(RoadLevels.curb(level));
                out.add(RoadLevels.post(level));
            }
        }
        return out;
    }

    @Test
    void everyStepUpIsUnambiguous()
    {
        for (int level = 1; level < RoadLevels.MAX; level++)
        {
            final int at = level;
            final Map<String, String> next = assertDoesNotThrow(() -> RoadLevels.next(at), "level " + at);
            assertFalse(next.isEmpty(), "level " + at + " changes nothing");
            next.forEach((from, to) -> assertFalse(from.equals(to)));
        }
        assertThrows(IllegalArgumentException.class, () -> RoadLevels.next(0));
        assertThrows(IllegalArgumentException.class, () -> RoadLevels.next(RoadLevels.MAX));
    }

    @Test
    void everyIdIsARealBlock()
    {
        for (final String id : allIds())
        {
            assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.withDefaultNamespace(id)), id);
        }
    }

    @Test
    void decayKnowsEveryBlock()
    {
        final Set<String> ground = RoadLevels.groundIds();
        final Set<String> raised = RoadLevels.raisedIds();
        for (final String id : ground)
        {
            assertFalse(raised.contains(id), id + " is both ground and raised");
        }
        for (final String id : allIds())
        {
            assertTrue(ground.contains(id) || raised.contains(id), id + " would survive the decay");
        }
        // What the route builder lays at level 1 must still go, as before the levels existed
        for (final String id : List.of("dirt_path", "coarse_dirt", "cobblestone", "stone_bricks", "spruce_planks", "spruce_log", "spruce_fence", "lantern"))
        {
            assertTrue(ground.contains(id) || raised.contains(id), id);
        }
    }

    @Test
    void levelFiveIsRoman()
    {
        assertEquals("smooth_stone", RoadLevels.Role.PATH.id(5));
        assertEquals("quartz_pillar", RoadLevels.Role.TIMBER.id(5));
        assertEquals("iron_bars", RoadLevels.Role.RAIL.id(5));
        assertEquals("polished_deepslate", RoadLevels.curb(5));
    }

    @Test
    void lampsAndCurbsGrowWithTheLevel()
    {
        assertNull(RoadLevels.curb(1));
        assertNull(RoadLevels.post(1));
        assertEquals(0, RoadLevels.lampEvery(1));
        for (int level = 2; level <= RoadLevels.MAX; level++)
        {
            assertNotNull(RoadLevels.curb(level));
            assertNotNull(RoadLevels.post(level));
            assertTrue(RoadLevels.postHeight(level) >= RoadLevels.postHeight(level - 1));
            if (level > 2)
            {
                assertTrue(RoadLevels.lampEvery(level) < RoadLevels.lampEvery(level - 1), "lamps closer at level " + level);
            }
        }
    }

    @Test
    void curbsAreFlatSurface()
    {
        for (int level = 2; level <= RoadLevels.MAX; level++)
        {
            assertTrue(RoadLevels.flatSurface(level, RoadLevels.curb(level)));
            assertTrue(RoadLevels.flatSurface(level, RoadLevels.Role.PATH.id(level)));
            assertFalse(RoadLevels.flatSurface(level, RoadLevels.Role.STEP.id(level)));
        }
    }

    @Test
    void costsRiseAndParse()
    {
        final String costs = "500,1500,4000,10000";
        long before = 0;
        for (int from = 1; from < RoadLevels.MAX; from++)
        {
            final long cost = RoadLevels.upgradeCost(costs, from);
            assertTrue(cost > before, "level " + from);
            before = cost;
        }
        assertEquals(500, RoadLevels.upgradeCost(costs, 1));
        assertEquals(10000, RoadLevels.upgradeCost(" 500 , 1500 ,4000, 10000 ", 4));
        assertEquals(-1, RoadLevels.upgradeCost(costs, RoadLevels.MAX));
        assertEquals(-1, RoadLevels.upgradeCost(costs, 0));
        assertEquals(-1, RoadLevels.upgradeCost("500,1500", 3));
        assertEquals(-1, RoadLevels.upgradeCost("500,oops,4000", 2));
        assertEquals(0, RoadLevels.upgradeCost("-5,1,1,1", 1));
    }

    @Test
    void perLevelValues()
    {
        final String speed = "0.10,0.20,0.30,0.40,0.50";
        assertEquals(0.10, RoadLevels.perLevel(speed, 1), 1e-9);
        assertEquals(0.50, RoadLevels.perLevel(speed, 5), 1e-9);
        assertEquals(0, RoadLevels.perLevel(speed, 0), 1e-9);
        assertEquals(0, RoadLevels.perLevel(speed, 6), 1e-9);
        assertEquals(0, RoadLevels.perLevel("0.1,0.2", 4), 1e-9);
        assertEquals(0, RoadLevels.perLevel("", 1), 1e-9);
    }
}
