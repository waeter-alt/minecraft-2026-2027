// [claude-build] Verifie les blocs et objets du Donjon Suspendu (portes a runes, sceaux, levier de racine, cles), fichiers generes par
// atelier/scripts/donjon_blocs.py : un fichier manquant ne donne en jeu qu'un cube violet-noir, sans erreur visible.
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class ModelesDonjonTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/grappin");
    private static final Path DATA = Path.of("src/main/resources/data/grappin");
    private static final List<String> BLOCS = List.of(   // les portes et sceaux en blocs ont ete abandonnes : la porte est une entite
            "levier_racine");
    private static final List<String> CLES = List.of("cle_bleue", "cle_jaune", "cle_rouge");

    private static JsonObject json(Path p) throws IOException {
        assertTrue(Files.exists(p), "absent : " + p);
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static void textureValide(String id) throws IOException {
        Path p = ASSETS.resolve("textures/" + id.substring(id.indexOf(':') + 1) + ".png");
        assertTrue(Files.exists(p), "texture absente : " + p);
        BufferedImage img = ImageIO.read(p.toFile());
        assertEquals(16, img.getWidth(), p.toString());
        assertEquals(16, img.getHeight(), p.toString());
    }

    /** Toutes les textures d'un modele (en suivant les parents du mod) existent et font 16 x 16. */
    private static void modeleValide(String id) throws IOException {
        if (!id.startsWith("grappin:")) {
            return;
        }
        JsonObject m = json(ASSETS.resolve("models/" + id.substring(8) + ".json"));
        if (m.has("textures")) {
            for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
                String v = t.getValue().getAsString();
                if (!v.startsWith("#")) {
                    textureValide(v);
                }
            }
        }
        if (m.has("parent")) {
            modeleValide(m.get("parent").getAsString());
        }
    }

    @Test
    void chaqueBlocAUnEtatUnModeleEtDesTextures() throws IOException {
        for (String b : BLOCS) {
            JsonObject etats = json(ASSETS.resolve("blockstates/" + b + ".json"));
            for (Map.Entry<String, JsonElement> v : etats.getAsJsonObject("variants").entrySet()) {
                modeleValide(v.getValue().getAsJsonObject().get("model").getAsString());
            }
            modeleValide("grappin:item/" + b);
        }
    }

    @Test
    void levierTousLesEtats() throws IOException {
        Set<String> attendus = new HashSet<>();
        for (String face : List.of("floor", "wall", "ceiling")) {
            for (String facing : List.of("north", "south", "east", "west")) {
                for (String powered : List.of("false", "true")) {
                    attendus.add("face=" + face + ",facing=" + facing + ",powered=" + powered);
                }
            }
        }
        assertEquals(attendus, json(ASSETS.resolve("blockstates/levier_racine.json")).getAsJsonObject("variants").keySet());
    }

    @Test
    void chaqueCleAUnModele() throws IOException {
        for (String c : CLES) {
            modeleValide("grappin:item/" + c);
        }
    }

    @Test
    void tablesDeButin() throws IOException {
        for (String b : BLOCS) {
            JsonObject t = json(DATA.resolve("loot_table/blocks/" + b + ".json"));
            String nom = t.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
            assertEquals("grappin:" + b, nom);
        }
    }
}
