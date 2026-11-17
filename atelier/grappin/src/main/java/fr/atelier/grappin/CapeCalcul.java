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

    /** Tour complet adouci (en radians, 0 a 2 pi) : lent au debut et a la fin, rapide au milieu. */
    public static double tourComplet(double p) {
        return (p - Math.sin(p * Math.PI * 2) / (Math.PI * 2)) * Math.PI * 2;
    }

    /** Enveloppe 0..1 d'un a-coup : montee rapide (quart de sinus) puis retour amorti (1-r)^3, t en ticks. */
    public static double aCoup(double t, double montee, double retour) {
        if (t <= 0 || t >= montee + retour) {
            return 0;
        }
        if (t < montee) {
            return Math.sin(t / montee * Math.PI / 2);
        }
        double r = 1 - (t - montee) / retour;
        return r * r * r;
    }

    /**
     * Roulis de camera du tourbillon, en degres, pour un reglage donne : 0 = aucun, 360 = l'ancien tonneau complet,
     * entre les deux = un a-coup qui penche jusqu'a ce pic puis revient a plat.
     */
    public static double roulis(double p, double t, double pic, double montee, double retour) {
        if (pic <= 0) {
            return 0;
        }
        if (pic >= 360) {
            return Math.toDegrees(tourComplet(p));
        }
        return pic * aCoup(t, montee, retour);
    }

    /** Intensite 0..1 des effets de vitesse : vitesse actuelle rapportee a la vitesse de sprint. */
    public static double intensite(double vitesse, double vitesseSprint) {
        if (vitesseSprint <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, vitesse / vitesseSprint));
    }
}
