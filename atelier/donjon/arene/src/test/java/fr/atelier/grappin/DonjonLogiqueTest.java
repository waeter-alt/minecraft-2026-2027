// [claude-build] Regles d'affichage de la porte du donjon et du levier de racine (sans Minecraft).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DonjonLogiqueTest {
    @Test
    void echelleSelonLArche() {
        assertArrayEquals(new float[] {1.0F, 1.0F}, DonjonLogique.echelle(8, 8), 1e-6F);
        assertArrayEquals(new float[] {1.5F, 1.0F}, DonjonLogique.echelle(12, 8), 1e-6F);
        assertArrayEquals(new float[] {0.875F, 1.0F}, DonjonLogique.echelle(7, 8), 1e-6F);
    }

    @Test
    void textureSelonLaCouleur() {
        assertEquals("porte_donjon_bleue", DonjonLogique.texture(0));
        assertEquals("porte_donjon_jaune", DonjonLogique.texture(1));
        assertEquals("porte_donjon_rouge", DonjonLogique.texture(2));
        assertEquals("porte_donjon_bleue", DonjonLogique.texture(7));        // valeur inconnue : bleue plutot que rien
    }

    @Test
    void dejaOuverteAuChargementSeDessineOuverte() {
        assertEquals(500, DonjonLogique.depart(false, 500));                 // on la voit s'ouvrir : l'animation part maintenant
        assertEquals(500 - DonjonLogique.DEJA_FAIT, DonjonLogique.depart(true, 500));     // deja ouverte : l'animation est finie
    }
}
