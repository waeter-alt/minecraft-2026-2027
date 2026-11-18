// [claude-build] Ouverture des griffes du crochet lance (sans Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GriffesTest {
    @Test
    void replieesAuDepart() {
        assertEquals(0F, Griffes.ouverture(0, false, 0), 1e-6);
    }

    @Test
    void ouvertesApresQuatreTicks() {
        assertEquals(0.5F, Griffes.ouverture(2, false, 0), 1e-6);
        assertEquals(1F, Griffes.ouverture(4, false, 0), 1e-6);
        assertEquals(1F, Griffes.ouverture(40, false, 0), 1e-6);
    }

    @Test
    void serreesAMoitieApresLAccroche() {
        assertEquals(1F, Griffes.ouverture(10, true, 0), 1e-6);
        assertEquals(0.5F, Griffes.ouverture(10, true, 3), 1e-6);
        assertEquals(0.5F, Griffes.ouverture(10, true, 30), 1e-6);
    }
}
