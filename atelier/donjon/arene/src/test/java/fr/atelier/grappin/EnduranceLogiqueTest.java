// [claude-build] La jauge d'endurance de la paravoile (EnduranceLogique) : couleurs, fondu, forme de l'anneau.
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnduranceLogiqueTest {
    @Test
    void resteSelonUsure() {
        assertEquals(1F, EnduranceLogique.reste(0, 2000), 1e-6F);
        assertEquals(0.25F, EnduranceLogique.reste(1500, 2000), 1e-6F);
        assertEquals(0F, EnduranceLogique.reste(2500, 2000), 1e-6F);
    }

    @Test
    void couleurs() {
        assertEquals(EnduranceLogique.VERT_RGB, EnduranceLogique.couleur(0.8F));
        assertEquals(EnduranceLogique.ORANGE_RGB, EnduranceLogique.couleur(0.3F));
        assertEquals(EnduranceLogique.ROUGE_RGB, EnduranceLogique.couleur(0.1F));
    }

    @Test
    void visibleEnVolOuEnRecharge() {
        assertTrue(EnduranceLogique.visible(true, 1F));
        assertTrue(EnduranceLogique.visible(false, 0.6F));
        assertFalse(EnduranceLogique.visible(false, 1F));                   // pleine et rangee : elle s'efface
    }

    @Test
    void apparaitViteDisparaitEnFondu() {
        assertEquals(1F, EnduranceLogique.opacite(0F, true, 0.2F), 1e-6F);
        float apres = EnduranceLogique.opacite(1F, false, 0.2F);
        assertTrue(apres > 0.6F && apres < 0.8F, "le fondu dure ~0,8 s");
        assertEquals(0F, EnduranceLogique.opacite(0.1F, false, 0.5F), 1e-6F);
    }

    @Test
    void anneauSeVideDansLeSensDesAiguilles() {
        assertEquals(-1F, EnduranceLogique.position(0, 0, 5, 8), 1e-6F);          // le trou au milieu
        assertEquals(-1F, EnduranceLogique.position(9, 0, 5, 8), 1e-6F);          // dehors
        assertEquals(0F, EnduranceLogique.position(0, -6, 5, 8), 1e-3F);          // en haut : le debut
        assertEquals(0.25F, EnduranceLogique.position(6, 0, 5, 8), 1e-3F);        // a droite : un quart
        assertEquals(0.5F, EnduranceLogique.position(0, 6, 5, 8), 1e-3F);         // en bas : la moitie
        assertEquals(0.75F, EnduranceLogique.position(-6, 0, 5, 8), 1e-3F);       // a gauche : trois quarts
    }
}
