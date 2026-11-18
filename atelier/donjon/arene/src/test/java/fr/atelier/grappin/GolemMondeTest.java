// [claude-build] Passage du repere du modele du Gardien au monde (meme chaine que le rendu) : au repos, les boites des points faibles
// doivent tomber exactement la ou l'ancien calcul de Golem.placerPieces les posait.
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class GolemMondeTest {
    // Copie de Golem.AZIMUTS, RAYONS, HAUTEURS (voir GolemSqueletteTest).
    private static final double[] AZIMUTS = {0, 0, -50, 150, -150};
    private static final double[] RAYONS = {8, 8, 7.5, 8, 8};
    private static final double[] HAUTEURS = {34, 26, 40, 30, 22};

    @Test
    void reposDonneLesAnciennesPositions() {
        for (double lacet : new double[] {0, 90, -135}) {
            for (int i = 0; i < 5; i++) {
                double[] p = GolemSquelette.SQUELETTE.point("pf" + i, Map.of(), "tete", 0, 0);
                double[] d = GolemAnimationsEtat.versMonde(p, lacet);
                double a = Math.toRadians(lacet + AZIMUTS[i]);
                // le rendu decale le modele de 1,501 bloc au lieu de 1,5 (x4) : 4 millièmes de bloc d'ecart en hauteur
                assertEquals(-Math.sin(a) * RAYONS[i], d[0], 1e-3, "x pf" + i + " lacet " + lacet);
                assertEquals(HAUTEURS[i], d[1], 1e-2, "y pf" + i + " lacet " + lacet);
                assertEquals(Math.cos(a) * RAYONS[i], d[2], 1e-3, "z pf" + i + " lacet " + lacet);
            }
        }
    }
}
