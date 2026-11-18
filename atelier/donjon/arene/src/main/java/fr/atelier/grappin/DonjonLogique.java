// [claude-build] Regles d'affichage de la porte du Donjon Suspendu et du levier de racine, sans Minecraft (DonjonLogiqueTest).
package fr.atelier.grappin;

public final class DonjonLogique {
    /** Ticks a ajouter au depart d'une animation pour qu'elle soit deja finie (porte deja ouverte, levier deja tire). */
    static final int DEJA_FAIT = 1000;
    /** La porte est dessinee pour une arche de 8 x 8 blocs. */
    static final float TAILLE_MODELE = 8;
    private static final String[] TEXTURES = {"porte_donjon_bleue", "porte_donjon_jaune", "porte_donjon_rouge"};

    private DonjonLogique() {
    }

    /** Echelle {largeur, hauteur} du modele pour une arche de cette taille (en blocs). */
    static float[] echelle(float largeur, float hauteur) {
        return new float[] {largeur / TAILLE_MODELE, hauteur / TAILLE_MODELE};
    }

    /** Texture de la porte : 0 bleue (porte 1), 1 jaune (porte 2), 2 rouge (porte 3). */
    static String texture(int couleur) {
        return TEXTURES[couleur >= 0 && couleur < TEXTURES.length ? couleur : 0];
    }

    /** Tick de depart de l'animation : maintenant si on la voit se faire, loin dans le passe si c'etait deja fait au chargement. */
    static int depart(boolean dejaFait, int tick) {
        return dejaFait ? tick - DEJA_FAIT : tick;
    }
}
