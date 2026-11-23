// [claude-build] Tests de la logique de combat du Gardien des Racines (aucune dependance a Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GolemLogiqueTest {
    private static final java.util.function.IntUnaryOperator PREMIER = n -> 0;

    @Test
    void ecartAngulaireEstDansZeroCentQuatreVingts() {
        assertEquals(20, GolemLogique.ecartAngulaire(10, 350), 1e-9);
        assertEquals(180, GolemLogique.ecartAngulaire(0, 180), 1e-9);
        assertEquals(180, GolemLogique.ecartAngulaire(-90, 90), 1e-9);
    }

    @Test
    void tournerVersPrendLePlusCourtChemin() {
        assertEquals(30, GolemLogique.tournerVers(0, 90, 30), 1e-9);
        assertEquals(-170, GolemLogique.tournerVers(170, -170, 30), 1e-9);       // 20 degres suffisent, par le plus court chemin
        assertEquals(-160, GolemLogique.tournerVers(170, -100, 30), 1e-9);       // 90 degres a faire : plafonne a 30, en passant par 180
        assertEquals(15, GolemLogique.tournerVers(10, 15, 30), 1e-9);
    }

    @Test
    void choisirPieceSeulementDuCoteOppose() {
        assertEquals(1, GolemLogique.choisirPiece(new double[] {0, 180, 90}, 0, -1, PREMIER));
        assertEquals(2, GolemLogique.choisirPiece(new double[] {0, 170, -170}, 0, 1, PREMIER));
    }

    @Test
    void choisirPieceSansPieceOpposeePrendLaPlusOpposee() {
        assertEquals(2, GolemLogique.choisirPiece(new double[] {0, 20, -30}, 0, -1, PREMIER));
    }

    @Test
    void choisirPieceTableauVide() {
        assertEquals(-1, GolemLogique.choisirPiece(new double[0], 0, -1, PREMIER));
    }

    @Test
    void choisirPieceGardeLaDerniereSiElleEstSeule() {
        assertEquals(1, GolemLogique.choisirPiece(new double[] {0, 180}, 0, 1, PREMIER));
    }

    @Test
    void larotationEstFigeeQuandUnePieceEstOuverte() {
        assertEquals(1.5, GolemLogique.vitesseRotation(false, 1.5), 1e-9);
        assertEquals(0.15, GolemLogique.vitesseRotation(true, 1.5), 1e-9);
    }

    @Test
    void facteurDegatsSelonLaPiece() {
        assertEquals(3.0, GolemLogique.facteurDegats(true, 3, 0.05), 1e-9);
        assertEquals(0.05, GolemLogique.facteurDegats(false, 3, 0.05), 1e-9);
    }

    @Test
    void phaseDeuxRaccourcitLesDurees() {
        assertEquals(160, GolemLogique.dureeOuverture(1, 160));
        assertEquals(120, GolemLogique.dureeOuverture(2, 160));
        assertEquals(70, GolemLogique.cadenceGraines(1, 70));
        assertEquals(35, GolemLogique.cadenceGraines(2, 70));
    }
}
