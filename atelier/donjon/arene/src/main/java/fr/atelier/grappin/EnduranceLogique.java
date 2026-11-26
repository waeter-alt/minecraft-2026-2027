// [claude-build] Regles de la jauge d'endurance de la paravoile, sans Minecraft (EnduranceLogique test) : la couleur selon ce qui reste,
// le fondu (apparait vite, disparait doucement une fois pleine), et quels points de l'ecran sont dans l'anneau rempli.
package fr.atelier.grappin;

public final class EnduranceLogique {
    /** En dessous : orange ; en dessous de ROUGE : rouge qui clignote en vol. */
    static final float ORANGE = 0.4F, ROUGE = 0.2F;
    static final int VERT_RGB = 0x5CE06E, ORANGE_RGB = 0xFFA126, ROUGE_RGB = 0xEC3B2C;
    /** Secondes pour apparaitre, et pour disparaitre en fondu. */
    static final float APPARITION = 0.15F, FONDU = 0.8F;

    private EnduranceLogique() {
    }

    /** Ce qui reste (0 : vide, 1 : pleine) d'apres l'usure de l'objet. */
    static float reste(int usure, int capacite) {
        return Math.max(0F, Math.min(1F, 1F - usure / (float) capacite));
    }

    static int couleur(float reste) {
        return reste < ROUGE ? ROUGE_RGB : reste < ORANGE ? ORANGE_RGB : VERT_RGB;
    }

    /** Opacite a l'image suivante : vers 1 tant que la jauge doit se voir (vol, recharge), vers 0 sinon. */
    static float opacite(float actuelle, boolean visible, float secondes) {
        float pas = secondes / (visible ? APPARITION : FONDU);
        return visible ? Math.min(1F, actuelle + pas) : Math.max(0F, actuelle - pas);
    }

    /** La jauge se voit en vol, et tant qu'elle n'est pas pleine (elle se recharge). */
    static boolean visible(boolean enVol, float reste) {
        return enVol || reste < 1F;
    }

    /**
     * Le point (dx, dy) du centre de l'anneau (y vers le bas, comme l'ecran) : -1 hors de l'anneau, sinon sa position sur le tour,
     * de 0 (en haut) a 1 en tournant dans le sens des aiguilles d'une montre. La partie remplie est celle ou la position est < reste.
     */
    static float position(float dx, float dy, float rayonInterieur, float rayonExterieur) {
        float r = (float) Math.hypot(dx, dy);
        if (r < rayonInterieur || r > rayonExterieur) {
            return -1F;
        }
        double a = Math.atan2(dx, -dy);                                 // 0 en haut, croissant vers la droite
        return (float) ((a < 0 ? a + 2 * Math.PI : a) / (2 * Math.PI));
    }
}
