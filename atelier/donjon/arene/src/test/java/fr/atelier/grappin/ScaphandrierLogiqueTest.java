// [claude-build] Tests des regles du combat du Scaphandrier rouille (harpon, coince, hublot), sans Minecraft.
package fr.atelier.grappin;

import static fr.atelier.grappin.ScaphandrierLogique.Etat.CHASSE;
import static fr.atelier.grappin.ScaphandrierLogique.Etat.COINCE;
import static fr.atelier.grappin.ScaphandrierLogique.Etat.TIR;
import static fr.atelier.grappin.ScaphandrierLogique.Etat.TRACTION;
import static fr.atelier.grappin.ScaphandrierLogique.Etat.VISEE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import fr.atelier.grappin.ScaphandrierLogique.Harpon;

class ScaphandrierLogiqueTest {
    private static final ScaphandrierLogique.Parametres P = new ScaphandrierLogique.Parametres(160, 20, 30, 60, 60);

    private static ScaphandrierLogique.Etat suivant(ScaphandrierLogique.Etat etat, int ticks, boolean cible, boolean portee, int depuis,
            Harpon harpon, boolean arrive) {
        return ScaphandrierLogique.suivant(etat, ticks, cible, portee, depuis, harpon, arrive, P);
    }

    @Test
    void viseQuandPretEtAPortee() {
        assertEquals(VISEE, suivant(CHASSE, 5, true, true, 160, Harpon.AUCUN, false));
        assertEquals(CHASSE, suivant(CHASSE, 5, true, true, 159, Harpon.AUCUN, false));
        assertEquals(CHASSE, suivant(CHASSE, 5, true, false, 500, Harpon.AUCUN, false));
        assertEquals(CHASSE, suivant(CHASSE, 5, false, true, 500, Harpon.AUCUN, false));
    }

    @Test
    void viseeAnnuleeSiCiblePerdueSinonTir() {
        assertEquals(CHASSE, suivant(VISEE, 5, false, false, 0, Harpon.AUCUN, false));
        assertEquals(VISEE, suivant(VISEE, 19, true, true, 0, Harpon.AUCUN, false));
        assertEquals(TIR, suivant(VISEE, 20, true, false, 0, Harpon.AUCUN, false));
    }

    @Test
    void harponQuiToucheLeJoueurLeTire() {
        assertEquals(TIR, suivant(TIR, 5, true, true, 0, Harpon.EN_VOL, false));
        assertEquals(TRACTION, suivant(TIR, 5, true, true, 0, Harpon.JOUEUR, false));
    }

    @Test
    void harponQuiRateUnBlocLeCoince() {
        assertEquals(COINCE, suivant(TIR, 5, true, true, 0, Harpon.BLOC, false));
    }

    @Test
    void harponPerduOuTropLongRepartEnChasse() {
        assertEquals(CHASSE, suivant(TIR, 5, true, true, 0, Harpon.AUCUN, false));
        assertEquals(CHASSE, suivant(TIR, 60, true, true, 0, Harpon.EN_VOL, false));
    }

    @Test
    void tractionFinieOuJoueurArrive() {
        assertEquals(TRACTION, suivant(TRACTION, 29, true, true, 0, Harpon.JOUEUR, false));
        assertEquals(CHASSE, suivant(TRACTION, 30, true, true, 0, Harpon.JOUEUR, false));
        assertEquals(CHASSE, suivant(TRACTION, 3, true, true, 0, Harpon.JOUEUR, true));
        assertEquals(CHASSE, suivant(TRACTION, 3, true, true, 0, Harpon.AUCUN, false));
    }

    @Test
    void coinceTroisSecondesQuoiQuIlArrive() {
        assertEquals(COINCE, suivant(COINCE, 59, false, false, 0, Harpon.BLOC, false));
        assertEquals(CHASSE, suivant(COINCE, 60, true, true, 0, Harpon.BLOC, false));
    }

    @Test
    void degatsDoublesSeulementCoince() {
        assertEquals(20F, ScaphandrierLogique.degats(COINCE, 10F, 2.0));
        assertEquals(10F, ScaphandrierLogique.degats(TIR, 10F, 2.0));
        assertEquals(10F, ScaphandrierLogique.degats(CHASSE, 10F, 2.0));
    }

    @Test
    void hublotSeFissurePuisEclate() {
        assertEquals(0, ScaphandrierLogique.hublot(1.0F));
        assertEquals(0, ScaphandrierLogique.hublot(0.67F));
        assertEquals(1, ScaphandrierLogique.hublot(0.66F));
        assertEquals(2, ScaphandrierLogique.hublot(0.33F));
        assertEquals(3, ScaphandrierLogique.hublot(0.12F));
        assertEquals(3, ScaphandrierLogique.hublot(0F));
    }
}
