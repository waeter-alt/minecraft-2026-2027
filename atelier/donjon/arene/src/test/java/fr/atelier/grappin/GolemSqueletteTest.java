// [claude-build] Tests du squelette de calcul du Gardien (position des points faibles cote serveur, sans Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class GolemSqueletteTest {
    // Copie de Golem.AZIMUTS, RAYONS, HAUTEURS, FACTEUR_LARGEUR (Golem charge Minecraft, on ne peut pas le lire ici) :
    // si on les change dans Golem, il faut regenerer golem.bbmodel et mettre ces valeurs a jour.
    private static final double[] AZIMUTS = {0, 0, -50, 150, -150};
    private static final double[] RAYONS = {8, 8, 7.5, 8, 8};
    private static final double[] HAUTEURS = {34, 26, 40, 30, 22};
    private static final double LARGEUR = 0.75;

    private static final Squelette S = GolemSquelette.SQUELETTE;

    private static double[] repos(String os) {
        return S.point(os, Map.of(), "tete", 0, 0);
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    @Test
    void auReposLesPointsFaiblesSontALeurPlace() {
        for (int i = 0; i < 5; i++) {
            double rm = RAYONS[i] / LARGEUR;
            double a = Math.toRadians(AZIMUTS[i]);
            double[] attendu = {-rm * Math.sin(a) * 4, 24 - HAUTEURS[i] * 4, -rm * Math.cos(a) * 4};
            double[] p = repos("pf" + i);
            for (int k = 0; k < 3; k++) {
                assertEquals(attendu[k], p[k], 1e-4, "pf" + i + " axe " + k);
            }
        }
    }

    @Test
    void catmullromCommeMinecraft() {
        float[] temps = {0, 1, 2, 3};
        float[] vals = {0, 10, 0, 10};
        // Mth.catmullrom(0.5, 0, 10, 0, 10) = 0.5 * (20 + 0 - 60 * 0.25 + 40 * 0.125) = 5
        assertEquals(5.0F, Squelette.valeur(1.5F, temps, vals, new boolean[] {true, true, true, true}), 1e-5);
    }

    @Test
    void lineaireAuMilieu() {
        assertEquals(2.5F, Squelette.valeur(0.5F, new float[] {0, 2}, new float[] {0, 10}, new boolean[] {false, false}), 1e-5);
        assertEquals(10F, Squelette.valeur(5F, new float[] {0, 2}, new float[] {0, 10}, new boolean[] {false, false}), 1e-5);
    }

    @Test
    void lancerDeplaceLeMasque() {
        assertTrue(distance(repos("pf0"), S.point("pf0", Map.of("lancer", 0.9F), "tete", 0, 0)) > 1.0);
    }

    @Test
    void regardTourneLeMasque() {
        double[] tete = repos("tete");
        double[] avant = repos("pf0");
        double[] apres = S.point("pf0", Map.of(), "tete", 30, 0);
        assertEquals(distance(tete, avant), distance(tete, apres), 1e-4);
        assertTrue(Math.abs(apres[0] - avant[0]) > 1.0);
    }
}
