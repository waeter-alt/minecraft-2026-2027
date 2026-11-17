// [claude-build] Tests du choix des animations du Gardien des Racines (aucune dependance a Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GolemAnimationsTest {
    @Test
    void rageAuPassageEnPhase2() {
        assertTrue(GolemAnimationsEtat.jouerRage(false, true, 500));
    }

    @Test
    void rageAuChargementNeSeJouePas() {
        assertFalse(GolemAnimationsEtat.jouerRage(false, true, 5));
        assertFalse(GolemAnimationsEtat.jouerRage(true, true, 500));
    }

    @Test
    void emergenceSansEvenementResteAuRepos() {
        assertTrue(GolemAnimationsEtat.jouerRepos(false, false));
        assertFalse(GolemAnimationsEtat.jouerRepos(true, false));
        assertFalse(GolemAnimationsEtat.jouerRepos(false, true));
    }

    @Test
    void regardBorne() {
        assertEquals(35f, GolemAnimationsEtat.lacetTete(80f));
        assertEquals(-35f, GolemAnimationsEtat.lacetTete(-80f));
        assertEquals(10f, GolemAnimationsEtat.lacetTete(10f));
        assertEquals(20f, GolemAnimationsEtat.tangageTete(45f));
        assertEquals(-20f, GolemAnimationsEtat.tangageTete(-45f));
    }
}
