// [claude-build] Calculs purs de la cape (sans classe Minecraft), pour pouvoir les tester en JUnit.
package fr.atelier.grappin;

public final class CapeCalcul {
    private CapeCalcul() {
    }

    /** Vitesse voulue de la nage rapide : la direction du regard (normalisee) multipliee par la vitesse choisie. */
    public static double[] vitesseVoulue(double rx, double ry, double rz, boolean sprint, double vitesse, double vitesseSprint) {
        double n = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (n < 1.0E-9) {
            return new double[] {0, 0, 0};
        }
        double v = (sprint ? vitesseSprint : vitesse) / n;
        return new double[] {rx * v, ry * v, rz * v};
    }

    /** Inertie facon elytres : on rattrape chaque tick une part de l'ecart entre la vitesse actuelle et la vitesse voulue. */
    public static double approcher(double actuelle, double voulue, double part) {
        return actuelle + (voulue - actuelle) * part;
    }

    /** La cible est-elle a portee ? Distance au carre comparee pour eviter une racine. */
    public static boolean aPortee(double distanceCarree, double portee) {
        return distanceCarree <= portee * portee;
    }

    /** La recharge est-elle finie ? dernier < 0 = jamais utilise. */
    public static boolean rechargee(long maintenant, long dernier, int recharge) {
        return dernier < 0 || maintenant - dernier >= recharge;
    }

    /** Avancement 0..1 d'une animation commencee a debut (ticks), ou -1 si finie ou pas commencee. */
    public static double avancement(double maintenant, double debut, double duree) {
        double p = (maintenant - debut) / duree;
        return p < 0 || p > 1 ? -1 : p;
    }
}
