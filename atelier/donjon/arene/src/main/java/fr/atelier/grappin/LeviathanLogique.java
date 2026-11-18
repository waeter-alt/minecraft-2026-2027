// [claude-build] Les decisions chiffrees du combat du Leviathan, sans dependance a Minecraft pour pouvoir etre testees (LeviathanLogiqueTest).
// La phase (1 ou 2) vient de SeraphinLogique.phase ; le facteur de degats de GolemLogique.facteurDegats.
package fr.atelier.grappin;

import java.util.function.IntUnaryOperator;

public final class LeviathanLogique {
    private LeviathanLogique() {
    }

    public enum Attaque { MORSURE, RAYON, QUEUE, PLONGEE }

    /**
     * Choisit la prochaine attaque : le coup de queue des qu'un joueur est pres de la queue, sinon un tirage entre morsure et rayon (phase 1)
     * ou entre morsure, rayon et plongee (phase 2). `tirage.applyAsInt(100)` renvoie un entier dans [0, 100).
     */
    public static Attaque choisirAttaque(int phase, boolean joueurProcheQueue, IntUnaryOperator tirage) {
        if (joueurProcheQueue) {
            return Attaque.QUEUE;
        }
        int t = tirage.applyAsInt(100);
        if (phase >= 2) {
            return t < 35 ? Attaque.MORSURE : t < 65 ? Attaque.RAYON : Attaque.PLONGEE;
        }
        return t < 55 ? Attaque.MORSURE : Attaque.RAYON;
    }

    /** La perle n'est atteignable que pendant `dureeExposition` ticks apres le rayon. */
    public static boolean perleExposee(int ticksDepuisRayon, int dureeExposition) {
        return ticksDepuisRayon >= 0 && ticksDepuisRayon < dureeExposition;
    }

    /**
     * Point situe a `distance` blocs de la tete en suivant le chemin deja parcouru par elle (`chemin` : ses positions passees, la plus recente en premier). Un corps place a distance fixe ne s'ecrase pas sur
     * lui-meme quand la tete s'arrete. Au-dela du chemin connu, le dernier point ; sans chemin, la tete.
     */
    public static double[] pointSurChemin(double[] tete, double[][] chemin, double distance) {
        double[] courant = tete;
        double reste = distance;
        for (double[] p : chemin) {
            double dx = p[0] - courant[0], dy = p[1] - courant[1], dz = p[2] - courant[2];
            double seg = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (seg >= reste) {
                double f = seg == 0 ? 0 : reste / seg;
                return new double[] {courant[0] + dx * f, courant[1] + dy * f, courant[2] + dz * f};
            }
            reste -= seg;
            courant = p;
        }
        return courant;
    }

    /** Vrai si la tete a parcouru moins d'un bloc en 40 ticks ou plus : le serpent est coince contre un obstacle. */
    public static boolean estCoince(double deplacement, int ticks) {
        return ticks >= 40 && deplacement < 1.0;
    }

    /** Delai entre deux attaques, en ticks : un tiers plus court en phase 2. */
    public static int cadence(int phase, int baseTicks) {
        return phase >= 2 ? baseTicks * 2 / 3 : baseTicks;
    }
}
