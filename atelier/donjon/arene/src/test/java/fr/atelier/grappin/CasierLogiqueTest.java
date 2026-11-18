// [claude-build] Le casier de vestiaire : teintures qui le repeignent, et une texture par couleur (sinon cube violet-noir en jeu, sans erreur).
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class CasierLogiqueTest {
    @Test
    void teintures() {
        assertEquals("bleu_clair", CasierLogique.couleurDeTeinture("light_blue"));
        assertEquals("cyan", CasierLogique.couleurDeTeinture("cyan"));
        assertEquals("vert", CasierLogique.couleurDeTeinture("lime"));
        assertEquals("vert", CasierLogique.couleurDeTeinture("green"));
        assertEquals("jaune", CasierLogique.couleurDeTeinture("yellow"));
        assertEquals("orange", CasierLogique.couleurDeTeinture("orange"));
        assertNull(CasierLogique.couleurDeTeinture("red"));
    }

    @Test
    void chaqueTeintureDonneUneCouleurDuCasier() {
        for (String t : new String[] {"light_blue", "cyan", "lime", "green", "yellow", "orange"}) {
            assertTrue(CasierLogique.COULEURS.contains(CasierLogique.couleurDeTeinture(t)), t);
        }
    }

    @Test
    void uneTextureParCouleur() throws IOException {
        for (String c : CasierLogique.COULEURS) {
            Path p = Path.of("src/main/resources/assets/donjon/textures/entity/casier_" + c + ".png");
            assertTrue(Files.exists(p), "texture absente : " + p);
            BufferedImage img = ImageIO.read(p.toFile());
            assertEquals(256, img.getWidth(), p.toString());              // CasierGeometrie.creerCouche : texture 256 x 256
            assertEquals(256, img.getHeight(), p.toString());
        }
    }
}
