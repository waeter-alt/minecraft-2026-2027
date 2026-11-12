// [claude-build] Tests de la logique de combat du Seraphin (aucune dependance a Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SeraphinLogiqueTest {
    @Test
    void phaseBasculeALaMoitieDeLaVie() {
        assertEquals(1, SeraphinLogique.phase(300f, 300f));
        assertEquals(1, SeraphinLogique.phase(151f, 300f));
        assertEquals(2, SeraphinLogique.phase(150f, 300f));
        assertEquals(2, SeraphinLogique.phase(0f, 300f));
    }

    @Test
    void ailesFermeesReduisentLesDegats() {
        assertEquals(1.0, SeraphinLogique.facteurDegats(true, 0.3), 1e-9);
        assertEquals(0.3, SeraphinLogique.facteurDegats(false, 0.3), 1e-9);
    }

    @Test
    void phaseDeuxDoubleLesRayonsEtAccelere() {
        assertEquals(1, SeraphinLogique.nombreRayons(1));
        assertEquals(2, SeraphinLogique.nombreRayons(2));
        assertEquals(90, SeraphinLogique.recharge(1, 90));
        assertEquals(60, SeraphinLogique.recharge(2, 90));
    }

    @Test
    void eventailSymetrique() {
        assertArrayEquals(new double[] {0}, SeraphinLogique.anglesEventail(1, 60), 1e-9);
        assertEquals(0, SeraphinLogique.anglesEventail(0, 60).length);
        assertEquals(0, SeraphinLogique.anglesEventail(-3, 60).length);
        assertArrayEquals(new double[] {-30, -15, 0, 15, 30}, SeraphinLogique.anglesEventail(5, 60), 1e-9);
    }
}
