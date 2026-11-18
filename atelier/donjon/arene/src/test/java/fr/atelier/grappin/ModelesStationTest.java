// [claude-build] Blocs de decor du Donjon de la Station (pompe, chaudiere, cadran) : chaque etat a son modele, chaque modele ses textures,
// chaque objet son modele, chaque bloc son butin et ses traductions (un fichier manquant ne donne en jeu qu'un cube violet-noir, sans
// erreur visible). Verifie aussi la regle des cubes lumineux (texture *_allume) et les formes tournees.
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

class ModelesStationTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/grappin");
    private static final Path DATA = Path.of("src/main/resources/data/grappin");
    /** bloc -> sa propriete booleenne en plus de facing (ou null). */
    private static final Map<String, String> BLOCS = Map.of("pompe_station", "en_marche", "chaudiere", "allumee", "cadran", "");

    private static JsonObject json(Path p) throws IOException {
        assertTrue(Files.exists(p), "absent : " + p);
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static JsonObject modele(String id) throws IOException {
        return json(ASSETS.resolve("models/" + id.substring(id.indexOf(':') + 1) + ".json"));
    }

    /** Les textures du modele existent, en 16 de large (16 de haut, ou une bande animee de 16 x 16n). */
    private static void texturesValides(JsonObject m) throws IOException {
        for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
            String v = t.getValue().getAsString();
            if (v.startsWith("#")) {
                continue;
            }
            Path p = ASSETS.resolve("textures/" + v.substring(v.indexOf(':') + 1) + ".png");
            assertTrue(Files.exists(p), "texture absente : " + p);
            BufferedImage img = ImageIO.read(p.toFile());
            assertEquals(16, img.getWidth(), p.toString());
            assertEquals(0, img.getHeight() % 16, p.toString());
        }
    }

    @Test
    void chaqueEtatAUnModeleValide() throws IOException {
        for (Map.Entry<String, String> b : BLOCS.entrySet()) {
            JsonObject variantes = json(ASSETS.resolve("blockstates/" + b.getKey() + ".json")).getAsJsonObject("variants");
            Set<String> attendus = new HashSet<>();
            for (String f : List.of("north", "east", "south", "west")) {
                if (b.getValue().isEmpty()) {
                    attendus.add("facing=" + f);
                } else {
                    attendus.add("facing=" + f + "," + b.getValue() + "=false");
                    attendus.add("facing=" + f + "," + b.getValue() + "=true");
                }
            }
            assertEquals(attendus, variantes.keySet(), b.getKey());
            for (Map.Entry<String, JsonElement> v : variantes.entrySet()) {
                texturesValides(modele(v.getValue().getAsJsonObject().get("model").getAsString()));
            }
        }
        texturesValides(modele("grappin:block/station/pompe_station_volant"));
    }

    @Test
    void chaqueObjetAUnModele() throws IOException {
        for (String b : BLOCS.keySet()) {
            JsonObject objet = json(ASSETS.resolve("models/item/" + b + ".json"));
            texturesValides(modele(objet.get("parent").getAsString()));
        }
    }

    @Test
    void lueurSeulementAllume() throws IOException {
        assertEquals(0, lumineux("pompe_station_arret"));
        assertTrue(lumineux("pompe_station_marche") > 0);
        assertEquals(0, lumineux("chaudiere_eteinte"));
        assertTrue(lumineux("chaudiere_allumee") > 0);
    }

    private static int lumineux(String nom) throws IOException {
        int n = 0;
        for (JsonElement e : modele("grappin:block/station/" + nom).getAsJsonArray("elements")) {
            n += e.getAsJsonObject().has("neoforge_data") ? 1 : 0;
        }
        return n;
    }

    @Test
    void butinEtTraductions() throws IOException {
        JsonObject fr = json(ASSETS.resolve("lang/fr_fr.json"));
        JsonObject en = json(ASSETS.resolve("lang/en_us.json"));
        for (String b : BLOCS.keySet()) {
            JsonObject t = json(DATA.resolve("loot_table/blocks/" + b + ".json"));
            String nom = t.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
            assertEquals("grappin:" + b, nom);
            assertTrue(fr.has("block.grappin." + b), "fr : " + b);
            assertTrue(en.has("block.grappin." + b), "en : " + b);
        }
    }

    @Test
    void formesTournees() {
        double[] nord = {3, 0, 12.5, 13, 13.5, 16};                                  // le cadran, contre le mur sud
        assertArrayEquals(new double[] {0, 0, 3, 3.5, 13.5, 13}, StationLogique.tourner(nord, 1), 1e-9);    // mur a l'ouest
        assertArrayEquals(new double[] {3, 0, 0, 13, 13.5, 3.5}, StationLogique.tourner(nord, 2), 1e-9);   // mur au nord
        assertArrayEquals(new double[] {12.5, 0, 3, 16, 13.5, 13}, StationLogique.tourner(nord, 3), 1e-9);  // mur a l'est
    }
}
