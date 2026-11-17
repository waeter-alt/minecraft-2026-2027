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

    @Test
    void aCoupMonteAuPicPuisRevientAPlat() {
        assertEquals(0, CapeCalcul.aCoup(0, 1.2, 5), 0);
        assertEquals(1, CapeCalcul.aCoup(1.2, 1.2, 5), 1e-9);
        assertEquals(0, CapeCalcul.aCoup(6.2, 1.2, 5), 0);
        assertEquals(0, CapeCalcul.aCoup(20, 1.2, 5), 0);
        double avant = 1;
        for (double t = 1.3; t < 6.2; t += 0.2) {
            double e = CapeCalcul.aCoup(t, 1.2, 5);
            assertTrue(e <= avant, "le retour ne doit jamais remonter");
            avant = e;
        }
    }

    @Test
    void roulisSelonLeReglage() {
        assertEquals(0, CapeCalcul.roulis(0.12, 1.2, 0, 1.2, 5), 0);
        assertEquals(14, CapeCalcul.roulis(0.12, 1.2, 14, 1.2, 5), 1e-9);
        assertEquals(0, CapeCalcul.roulis(0.9, 9, 14, 1.2, 5), 0);
        assertEquals(360, CapeCalcul.roulis(1, 10, 360, 1.2, 5), 1e-9);
        assertEquals(180, CapeCalcul.roulis(0.5, 5, 360, 1.2, 5), 1e-9);
    }

    @Test
    void intensiteBornee() {
        assertEquals(0.5, CapeCalcul.intensite(0.275, 0.55), 1e-9);
        assertEquals(1, CapeCalcul.intensite(2, 0.55), 0);
        assertEquals(0, CapeCalcul.intensite(-1, 0.55), 0);
        assertEquals(0, CapeCalcul.intensite(1, 0), 0);
    }
}
