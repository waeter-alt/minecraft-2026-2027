// [claude-build] Les decisions chiffrees du combat du Gardien des Racines, sans dependance a Minecraft pour pouvoir etre testees (GolemLogiqueTest).
// La phase (1 ou 2) vient de SeraphinLogique.phase.
package fr.atelier.grappin;

import java.util.function.IntUnaryOperator;

public final class GolemLogique {
    private GolemLogique() {
    }

    /** Ecart entre deux angles, en degres, dans [0, 180]. */
    public static double ecartAngulaire(double a, double b) {
        double e = Math.abs(a - b) % 360.0;
        return e > 180.0 ? 360.0 - e : e;
    }

    /** Fait tourner `actuel` vers `voulu` par le plus court chemin, d'au plus `maxDegres` ; le resultat est dans (-180, 180]. */
    public static double tournerVers(double actuel, double voulu, double maxDegres) {
        double diff = ((voulu - actuel) % 360.0 + 540.0) % 360.0 - 180.0;      // dans [-180, 180)
        double pas = Math.max(-maxDegres, Math.min(maxDegres, diff));
        double r = ((actuel + pas) % 360.0 + 540.0) % 360.0 - 180.0;
        return r == -180.0 ? 180.0 : r;
    }

    /**
     * Choisit la piece faible a ouvrir : une piece a plus de 90 degres de la direction du joueur (donc dans le dos du golem par rapport a lui),
     * differente de la derniere s'il y en a une autre ; si aucune n'est assez opposee, la plus opposee ; -1 s'il n'y a aucune piece.
     * `tirage.applyAsInt(n)` renvoie un entier dans [0, n).
     */
    public static int choisirPiece(double[] anglesMonde, double angleCible, int derniere, IntUnaryOperator tirage) {
        if (anglesMonde.length == 0) {
            return -1;
        }
        int[] eligibles = new int[anglesMonde.length];
        int n = 0;
        for (int i = 0; i < anglesMonde.length; i++) {
            if (ecartAngulaire(anglesMonde[i], angleCible) > 90.0) {
                eligibles[n++] = i;
            }
        }
        if (n == 0) {
            int meilleure = 0;
            for (int i = 1; i < anglesMonde.length; i++) {
                if (ecartAngulaire(anglesMonde[i], angleCible) > ecartAngulaire(anglesMonde[meilleure], angleCible)) {
                    meilleure = i;
                }
            }
            return meilleure;
        }
        int[] autres = new int[n];
        int m = 0;
        for (int k = 0; k < n; k++) {
            if (eligibles[k] != derniere) {
                autres[m++] = eligibles[k];
            }
        }
        return m > 0 ? autres[tirage.applyAsInt(m)] : eligibles[tirage.applyAsInt(n)];
    }

    /** Vitesse de rotation du golem, en degres par tick : presque a l'arret (un dixieme) tant qu'une piece est ouverte, pour qu'on ait le temps de passer dans son dos. */
    public static double vitesseRotation(boolean pieceOuverte, double pasNormal) {
        return pieceOuverte ? pasNormal * 0.1 : pasNormal;
    }

    /** Facteur applique aux degats recus : plein sur une piece ouverte, tres reduit sur le reste du corps. */
    public static double facteurDegats(boolean pieceOuverte, double facteurPiece, double facteurCorps) {
        return pieceOuverte ? facteurPiece : facteurCorps;
    }

    /** Duree d'ouverture d'une piece faible, en ticks : un quart plus courte en phase 2. */
    public static int dureeOuverture(int phase, int baseTicks) {
        return phase >= 2 ? baseTicks * 3 / 4 : baseTicks;
    }

    /** Delai entre deux graines, en ticks : deux fois plus court en phase 2. */
    public static int cadenceGraines(int phase, int baseTicks) {
        return phase >= 2 ? baseTicks / 2 : baseTicks;
    }
}
