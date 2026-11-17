package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapeCalculTest {
    @Test
    void vitesseVoulueSuitLeRegardALaBonneVitesse() {
        double[] v = CapeCalcul.vitesseVoulue(0, 0, 2, false, 0.35, 0.55);
        assertArrayEquals(new double[] {0, 0, 0.35}, v, 1e-9);
        double[] s = CapeCalcul.vitesseVoulue(1, 1, 0, true, 0.35, 0.55);
        assertEquals(0.55, Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]), 1e-9);
        assertEquals(s[0], s[1], 1e-9);
    }

    @Test
    void regardNulDonneVitesseNulle() {
        assertArrayEquals(new double[] {0, 0, 0}, CapeCalcul.vitesseVoulue(0, 0, 0, true, 0.35, 0.55), 0);
    }

    @Test
    void lInertieConvergeVersLaVitesseVoulue() {
        double v = 0;
        for (int i = 0; i < 60; i++) {
            v = CapeCalcul.approcher(v, 0.35, 0.12);
        }
        assertEquals(0.35, v, 1e-3);
        assertEquals(0.042, CapeCalcul.approcher(0, 0.35, 0.12), 1e-9);
    }

    @Test
    void portee() {
        assertTrue(CapeCalcul.aPortee(3.5 * 3.5, 3.5));
        assertFalse(CapeCalcul.aPortee(3.6 * 3.6, 3.5));
    }

    @Test
    void recharge() {
        assertTrue(CapeCalcul.rechargee(100, -1, 16));
        assertFalse(CapeCalcul.rechargee(110, 100, 16));
        assertTrue(CapeCalcul.rechargee(116, 100, 16));
    }

    @Test
    void avancementDeLAnimation() {
        assertEquals(0.5, CapeCalcul.avancement(105, 100, 10), 1e-9);
        assertEquals(-1, CapeCalcul.avancement(99, 100, 10), 0);
        assertEquals(-1, CapeCalcul.avancement(111, 100, 10), 0);
    }
}
