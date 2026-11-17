// [claude-build] Forme de la corde du grappin : une courbe du crochet a la main, qui pend un peu pendant le vol et se tend
// quand elle tire le joueur. Calcul pur, teste sans Minecraft (CordeTest) ; le dessin est dans GrappinClient.RenduCrochet.
package fr.atelier.grappin;

public final class Corde {
    static final int SEGMENTS = 24;
    static final float LARGEUR = 0.06F;

    private Corde() {
    }

    /** Combien la corde pend en son milieu (en blocs). */
    static double mou(boolean accroche, double longueur) {
        return accroche ? 0 : Math.min(0.06 * longueur, 1.0);
    }

    /** segments + 1 points {x, y, z}, du crochet (0, 0, 0) a la main (dx, dy, dz), avec un creux en parabole. */
    static double[][] points(double dx, double dy, double dz, double mou, int segments) {
        double[][] p = new double[segments + 1][];
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            p[i] = new double[] {dx * t, dy * t - mou * 4 * t * (1 - t), dz * t};
        }
        return p;
    }
}
