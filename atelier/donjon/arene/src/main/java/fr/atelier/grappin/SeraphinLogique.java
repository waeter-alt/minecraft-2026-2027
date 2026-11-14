// [claude-build] Les decisions chiffrees du combat du Seraphin, sans aucune dependance a Minecraft pour pouvoir etre testees (SeraphinLogiqueTest).
package fr.atelier.grappin;

public final class SeraphinLogique {
    private SeraphinLogique() {
    }

    /** 1 tant qu'il a plus de la moitie de sa vie, 2 ensuite. */
    public static int phase(float vie, float vieMax) {
        return vie <= vieMax / 2 ? 2 : 1;
    }

    /** Facteur applique aux degats recus : plein quand l'oeil est ouvert, reduit quand les ailes sont fermees. */
    public static double facteurDegats(boolean oeilOuvert, double facteurAilesFermees) {
        return oeilOuvert ? 1.0 : facteurAilesFermees;
    }

    public static int nombreRayons(int phase) {
        return phase >= 2 ? 2 : 1;
    }

    /** Delai entre deux attaques, en ticks : un tiers plus court en phase 2. */
    public static int recharge(int phase, int baseTicks) {
        return phase >= 2 ? baseTicks * 2 / 3 : baseTicks;
    }

    /** `n` angles en degres, symetriques autour de 0, de -ouverture/2 a +ouverture/2. */
    public static double[] anglesEventail(int n, double ouvertureDegres) {
        if (n <= 0) {
            return new double[0];
        }
        double[] angles = new double[n];
        if (n == 1) {
            return angles;
        }
        for (int i = 0; i < n; i++) {
            angles[i] = -ouvertureDegres / 2 + ouvertureDegres * i / (n - 1);
        }
        return angles;
    }
}
