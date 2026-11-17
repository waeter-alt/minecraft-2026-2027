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
}
