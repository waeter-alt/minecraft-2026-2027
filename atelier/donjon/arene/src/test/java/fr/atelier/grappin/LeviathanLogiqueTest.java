// [claude-build] Tests de la logique de combat du Leviathan (aucune dependance a Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fr.atelier.grappin.LeviathanLogique.Attaque;

class LeviathanLogiqueTest {
    @Test
    void unJoueurProcheDeLaQueueDeclencheLeCoupDeQueue() {
        assertEquals(Attaque.QUEUE, LeviathanLogique.choisirAttaque(1, true, n -> 99));
        assertEquals(Attaque.QUEUE, LeviathanLogique.choisirAttaque(2, true, n -> 0));
    }

    @Test
    void phaseUnChoisitEntreMorsureEtRayon() {
        assertEquals(Attaque.MORSURE, LeviathanLogique.choisirAttaque(1, false, n -> 10));
        assertEquals(Attaque.MORSURE, LeviathanLogique.choisirAttaque(1, false, n -> 54));
        assertEquals(Attaque.RAYON, LeviathanLogique.choisirAttaque(1, false, n -> 55));
        assertEquals(Attaque.RAYON, LeviathanLogique.choisirAttaque(1, false, n -> 99));
    }

    @Test
    void phaseDeuxAjouteLaPlongee() {
        assertEquals(Attaque.MORSURE, LeviathanLogique.choisirAttaque(2, false, n -> 34));
        assertEquals(Attaque.RAYON, LeviathanLogique.choisirAttaque(2, false, n -> 35));
        assertEquals(Attaque.RAYON, LeviathanLogique.choisirAttaque(2, false, n -> 64));
        assertEquals(Attaque.PLONGEE, LeviathanLogique.choisirAttaque(2, false, n -> 65));
    }

    @Test
    void laPerleNEstExposeeQueQuelquesSecondesApresLeRayon() {
        assertTrue(LeviathanLogique.perleExposee(0, 120));
        assertTrue(LeviathanLogique.perleExposee(119, 120));
        assertFalse(LeviathanLogique.perleExposee(120, 120));
        assertFalse(LeviathanLogique.perleExposee(-1, 120));
    }

    @Test
    void pointSurCheminMarcheLeLongDuCheminDepuisLaTete() {
        double[][] droit = {{-1, 0, 0}, {-2, 0, 0}, {-3, 0, 0}};
        assertArrayEquals(new double[] {-2.5, 0, 0}, LeviathanLogique.pointSurChemin(new double[] {0, 0, 0}, droit, 2.5), 1e-9);
        assertArrayEquals(new double[] {-3, 0, 0}, LeviathanLogique.pointSurChemin(new double[] {0, 0, 0}, droit, 10), 1e-9);       // au-dela du chemin : le dernier point
        assertArrayEquals(new double[] {0, 0, 0}, LeviathanLogique.pointSurChemin(new double[] {0, 0, 0}, droit, 0), 1e-9);
    }

    @Test
    void pointSurCheminSuitLesVirages() {
        double[][] coude = {{-1, 0, 0}, {-1, 0, -1}};
        assertArrayEquals(new double[] {-1, 0, -0.5}, LeviathanLogique.pointSurChemin(new double[] {0, 0, 0}, coude, 1.5), 1e-9);
    }

    @Test
    void pointSurCheminSansHistoriqueRestePresDeLaTete() {
        assertArrayEquals(new double[] {4, 5, 6}, LeviathanLogique.pointSurChemin(new double[] {4, 5, 6}, new double[0][], 3), 1e-9);
    }

    @Test
    void leSerpentEstCoinceQuandLaTeteNAvancePlus() {
        assertTrue(LeviathanLogique.estCoince(0.5, 40));
        assertFalse(LeviathanLogique.estCoince(0.5, 39));
        assertFalse(LeviathanLogique.estCoince(2.0, 100));
    }

    @Test
    void phaseDeuxAccelereLaCadence() {
        assertEquals(90, LeviathanLogique.cadence(1, 90));
        assertEquals(60, LeviathanLogique.cadence(2, 90));
    }
}
