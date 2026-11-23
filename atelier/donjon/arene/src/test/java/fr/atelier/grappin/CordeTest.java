// [claude-build] Tests de la forme de la corde du grappin (aucune dependance a Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CordeTest {
    @Test
    void extremitesExactes() {
        double[][] p = Corde.points(3, 1, -2, 0.5, 24);
        assertEquals(25, p.length);
        assertArrayEquals(new double[] {0, 0, 0}, p[0], 1e-9);
        assertArrayEquals(new double[] {3, 1, -2}, p[24], 1e-9);
    }

    @Test
    void sansMouLigneDroite() {
        assertArrayEquals(new double[] {1.5, 0.5, -1}, Corde.points(3, 1, -2, 0, 24)[12], 1e-9);
    }

    @Test
    void mouAuMilieu() {
        assertEquals(-0.5, Corde.points(4, 0, 0, 0.5, 24)[12][1], 1e-9);
    }

    @Test
    void tendueQuandAccrochee() {
        assertEquals(0, Corde.mou(true, 20), 1e-9);
    }

    @Test
    void mouLimite() {
        assertEquals(0.3, Corde.mou(false, 5), 1e-9);
        assertEquals(1.0, Corde.mou(false, 30), 1e-9);
    }

    @Test
    void longueurNulleSansNaN() {
        for (double[] p : Corde.points(0, 0, 0, Corde.mou(false, 0), 24)) {
            for (double v : p) {
                assertTrue(Double.isFinite(v));
            }
        }
    }
}
