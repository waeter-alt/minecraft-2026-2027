package fr.kodistan.interregne.colony;

import fr.kodistan.interregne.Defaults;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Neglect must cost: hunger and sickness slow villagers down, more when they add up, and the windows say by how much.
 */
class MalusTest
{
    @BeforeAll
    static void settings()
    {
        Defaults.load();
    }

    @Test
    void fedAndHealthyWorkAtFullSpeed()
    {
        assertEquals(1.0, Malus.factor(20, false), 1e-9);
        assertEquals(1.0, Malus.factor(Malus.HUNGRY, false), 1e-9, "the limit itself is not hungry");
        assertTrue(Malus.kinds(15, false).isEmpty());
    }

    @Test
    void neglectIsPunishing()
    {
        final double hungry = Malus.factor(3, false);
        final double starving = Malus.factor(0, false);
        final double sick = Malus.factor(20, true);
        assertTrue(hungry >= 2, "hungry at least halves the work, got " + hungry);
        assertTrue(starving > hungry, "starving is worse than hungry");
        assertTrue(sick >= 2, "sick at least halves the work, got " + sick);
        assertEquals(starving * sick, Malus.factor(0, true), 1e-9, "maluses multiply");
        assertEquals(List.of(Malus.Kind.STARVING, Malus.Kind.SICK), Malus.kinds(0, true), "starving replaces hungry");
    }

    @Test
    void amountsRead()
    {
        assertEquals("−50 %", Malus.percent(2));
        assertEquals("−67 %", Malus.percent(3));
        assertEquals("−92 %", Malus.percent(12));
        assertEquals("3×", Malus.times(3));
        assertEquals("1,5×", Malus.times(1.5));
    }
}
