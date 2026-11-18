// [claude-build] Tests des regles du combat du Rodeur des Racines (charge, sonne), sans Minecraft.
package fr.atelier.grappin;

import static fr.atelier.grappin.RodeurLogique.Etat.CHARGE;
import static fr.atelier.grappin.RodeurLogique.Etat.CHASSE;
import static fr.atelier.grappin.RodeurLogique.Etat.GRATTE;
import static fr.atelier.grappin.RodeurLogique.Etat.SONNE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RodeurLogiqueTest {
    private static final RodeurLogique.Parametres P = new RodeurLogique.Parametres(20, 60, 120, 24.0);

    private static RodeurLogique.Etat suivant(RodeurLogique.Etat etat, int ticks, boolean cible, boolean portee, boolean bloque,
            boolean touche, double distance, int depuisCharge) {
        return RodeurLogique.suivant(etat, ticks, cible, portee, bloque, touche, distance, depuisCharge, P);
    }

    @Test
    void chasseVersGratteQuandPretEtAPortee() {
        assertEquals(GRATTE, suivant(CHASSE, 50, true, true, false, false, 0, 120));
    }

    @Test
    void pasDeChargeAvantLeDelai() {
        assertEquals(CHASSE, suivant(CHASSE, 50, true, true, false, false, 0, 119));
        assertEquals(CHASSE, suivant(CHASSE, 50, true, false, false, false, 0, 500));
    }

    @Test
    void gratteAnnuleSiCiblePerdue() {
        assertEquals(CHASSE, suivant(GRATTE, 5, false, false, false, false, 0, 0));
    }

    @Test
    void gratteVersChargeApresGrattage() {
        assertEquals(GRATTE, suivant(GRATTE, 19, true, true, false, false, 0, 0));
        assertEquals(CHARGE, suivant(GRATTE, 20, true, true, false, false, 0, 0));
    }

    @Test
    void chargeBloqueeSonne() {
        assertEquals(SONNE, suivant(CHARGE, 8, true, true, true, false, 7.0, 0));
    }

    @Test
    void chargeQuiToucheNeSonnePas() {
        assertEquals(CHASSE, suivant(CHARGE, 8, true, true, true, true, 7.0, 0));
    }

    @Test
    void chargeSArreteAuBoutDeLaPortee() {
        assertEquals(CHARGE, suivant(CHARGE, 20, true, true, false, false, 23.9, 0));
        assertEquals(CHASSE, suivant(CHARGE, 27, true, true, false, false, 24.0, 0));
    }

    @Test
    void sonneFiniApresDuree() {
        assertEquals(SONNE, suivant(SONNE, 59, true, true, true, false, 0, 0));
        assertEquals(CHASSE, suivant(SONNE, 60, true, true, true, false, 0, 0));
    }

    @Test
    void delaiApresSonne() {
        assertEquals(CHASSE, suivant(CHASSE, 1, true, true, true, false, 0, 61));
    }

    @Test
    void degatsDoublesQuandSonne() {
        assertEquals(10.0F, RodeurLogique.degats(SONNE, 5.0F, 2.0), 1e-6);
    }

    @Test
    void degatsNormauxSinon() {
        for (RodeurLogique.Etat e : new RodeurLogique.Etat[] {CHASSE, GRATTE, CHARGE}) {
            assertEquals(5.0F, RodeurLogique.degats(e, 5.0F, 2.0), 1e-6);
        }
    }
}
