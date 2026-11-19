// [claude-build] La paravoile (paravoile_apparence.py) : l'objet montre l'icone dans l'inventaire et le paquetage 3D ailleurs, chaque modele a
// ses textures, et la texture de la voile deployee a la taille que ParavoileGeometrie attend (256 x 256). Un fichier manquant ne donne en jeu
// qu'un cube violet-noir, sans erreur visible.
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class ParavoileModelesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/donjon");

    private static JsonObject json(String chemin) throws IOException {
        Path p = ASSETS.resolve(chemin);
        assertTrue(Files.exists(p), "absent : " + p);
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static BufferedImage image(String chemin) throws IOException {
        Path p = ASSETS.resolve(chemin);
        assertTrue(Files.exists(p), "absent : " + p);
        return ImageIO.read(p.toFile());
    }

    @Test
    void iconeEnInventairePaquetageAilleurs() throws IOException {
        JsonObject m = json("models/item/paravoile.json");
        assertEquals("neoforge:separate_transforms", m.get("loader").getAsString());
        assertEquals("donjon:item/paravoile_main", m.getAsJsonObject("base").get("parent").getAsString());
        assertEquals("donjon:item/paravoile_icone", m.getAsJsonObject("perspectives").getAsJsonObject("gui").get("parent").getAsString());
        json("models/item/paravoile_main.json");
        json("models/item/paravoile_icone.json");
    }

    @Test
    void textures() throws IOException {
        assertEquals(16, image("textures/item/paravoile.png").getWidth());
        assertEquals(32, image("textures/item/paravoile_modele.png").getWidth());
        BufferedImage voile = image("textures/entity/paravoile.png");
        assertEquals(256, voile.getWidth());
        assertEquals(256, voile.getHeight());
    }

    /** La paravoile se porte dans la case « dos » d'Accessories (le tag accessories:back), comme la cape dans la sienne. */
    @Test
    void caseDuDos() throws IOException {
        Path p = Path.of("src/main/resources/data/accessories/tags/item/back.json");
        assertTrue(Files.exists(p), "absent : " + p);
        assertTrue(JsonParser.parseString(Files.readString(p)).getAsJsonObject().getAsJsonArray("values").toString().contains("donjon:paravoile"));
    }
}
