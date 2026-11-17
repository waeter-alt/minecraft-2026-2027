// [claude-build] Quelles animations du Gardien des Racines jouer, et jusqu'ou tourner sa tete (logique pure, testee sans Minecraft
// dans GolemAnimationsTest). Le declenchement lui-meme est dans Golem (evenements d'entite) et GolemModele (setupAnim).
package fr.atelier.grappin;

public final class GolemAnimationsEtat {
    static final float LACET_MAX = 35;
    static final float TANGAGE_MAX = 20;

    private GolemAnimationsEtat() {
    }

    /** La rage se joue au passage en phase 2, mais pas au chargement d'un boss deja en phase 2 (meme regle que l'annonce). */
    static boolean jouerRage(boolean avant, boolean apres, int tickCount) {
        return !avant && apres && tickCount > 20;
    }

    /** Le repos tourne tout le temps, sauf pendant l'emergence et la mort. */
    static boolean jouerRepos(boolean emergenceEnCours, boolean mort) {
        return !emergenceEnCours && !mort;
    }

    static float lacetTete(float netHeadYaw) {
        return Math.max(-LACET_MAX, Math.min(LACET_MAX, netHeadYaw));
    }

    static float tangageTete(float headPitch) {
        return Math.max(-TANGAGE_MAX, Math.min(TANGAGE_MAX, headPitch));
    }

    /**
     * Point du modele (unites de 1/16, repere du modele) -> decalage en blocs depuis les pieds du Gardien, par la meme chaine que le rendu :
     * LivingEntityRenderer (rotation 180 - lacet du corps, retournement (-1, -1, 1)), GolemRendu.scale (x4, x0,75 en largeur),
     * puis le decalage de -1,501 bloc du modele. Sert a poser les boites des points faibles la ou ils sont dessines.
     */
    static double[] versMonde(double[] p, double lacetCorpsDeg) {
        double x = p[0] / 16, y = p[1] / 16 - 1.501, z = p[2] / 16;
        x *= 4 * Golem.FACTEUR_LARGEUR;
        y *= 4;
        z *= 4 * Golem.FACTEUR_LARGEUR;
        x = -x;
        y = -y;
        double t = Math.toRadians(180 - lacetCorpsDeg);
        return new double[] {x * Math.cos(t) + z * Math.sin(t), y, -x * Math.sin(t) + z * Math.cos(t)};
    }
}
