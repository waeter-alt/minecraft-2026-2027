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

    /**
     * Enveloppe 0..1 d'un a-coup, t en ticks : montee rapide (quart de sinus) puis retour en demi-cosinus.
     * Les deux raccords ont une pente nulle (au pic et a la fin) : pas de cassure visible dans le champ de vision.
     */
    public static double aCoup(double t, double montee, double retour) {
        if (t <= 0 || t >= montee + retour) {
            return 0;
        }
        if (t < montee) {
            return Math.sin(t / montee * Math.PI / 2);
        }
        return (1 + Math.cos((t - montee) / retour * Math.PI)) / 2;
    }

    /**
     * Vitesse de projection hors de l'eau : toujours la meme vitesse verticale (hauteur), et vers l'avant du regard la vitesse horizontale
     * (portee) entiere tant qu'on vise a moins de 45 degres environ ; au dela elle diminue, pour qu'un regard a la verticale donne un bond sur place.
     */
    public static double[] projection(double rx, double ry, double rz, double hauteur, double portee) {
        double horizontal = Math.sqrt(rx * rx + rz * rz);
        double n = Math.sqrt(horizontal * horizontal + ry * ry);
        if (horizontal < 1.0E-9 || n < 1.0E-9) {
            return new double[] {0, hauteur, 0};
        }
        double part = Math.min(1, horizontal / n / 0.7);
        double v = portee * part / horizontal;
        return new double[] {rx * v, hauteur, rz * v};
    }

    /** Intensite 0..1 des effets de vitesse : vitesse actuelle rapportee a la vitesse de sprint. */
    public static double intensite(double vitesse, double vitesseSprint) {
        if (vitesseSprint <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, vitesse / vitesseSprint));
    }
}
