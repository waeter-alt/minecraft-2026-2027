// [claude-build] Verifie les modeles du grappin (fichiers generes par atelier/scripts/grappin_apparence.py) sans lancer le jeu :
// un modele casse ne donne qu'un cube violet-noir en jeu, sans erreur visible.
package fr.atelier.grappin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class ModelesGrappinTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/grappin");
    private static final List<String> MODELES = List.of("grappin", "grappin_tire", "grappin_main", "grappin_main_vide", "grappin_icone", "crochet_tige", "crochet_griffe");
    private static final Set<Double> ANGLES = Set.of(-45.0, -22.5, 0.0, 22.5, 45.0);

    private static JsonObject modele(String nom) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/" + nom + ".json"))).getAsJsonObject();
    }

    private static BufferedImage texture(String id) throws IOException {
        String chemin = id.substring(id.indexOf(':') + 1);
        Path p = ASSETS.resolve("textures/" + chemin + ".png");
        assertTrue(Files.exists(p), "texture absente : " + p);
        return ImageIO.read(p.toFile());
    }

    @Test
    void grappinUtiliseSeparateTransforms() throws IOException {
        JsonObject m = modele("grappin");
        assertEquals("neoforge:separate_transforms", m.get("loader").getAsString());
        assertEquals("grappin:item/grappin_main", m.getAsJsonObject("base").get("parent").getAsString());
        assertEquals("grappin:item/grappin_icone", m.getAsJsonObject("perspectives").getAsJsonObject("gui").get("parent").getAsString());
    }

    @Test
    void grappinTireMontreLeLanceurVide() throws IOException {
        JsonObject override = modele("grappin").getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals(1, override.getAsJsonObject("predicate").get("grappin:tire").getAsInt());
        assertEquals("grappin:item/grappin_tire", override.get("model").getAsString());
        JsonObject tire = modele("grappin_tire");
        assertEquals("neoforge:separate_transforms", tire.get("loader").getAsString());
        assertEquals("grappin:item/grappin_main_vide", tire.getAsJsonObject("base").get("parent").getAsString());
        assertEquals("grappin:item/grappin_icone", tire.getAsJsonObject("perspectives").getAsJsonObject("gui").get("parent").getAsString());
    }

    @Test
    void lanceurVideSansTete() throws IOException {
        for (JsonElement el : modele("grappin_main_vide").getAsJsonArray("elements")) {
            String nom = el.getAsJsonObject().get("name").getAsString();
            assertFalse(nom.startsWith("griffe") || nom.equals("collier") || nom.equals("pointe") || nom.equals("tige"), nom);
        }
    }

    @Test
    void iconeEstPlate() throws IOException {
        JsonObject m = modele("grappin_icone");
        assertEquals("minecraft:item/handheld", m.get("parent").getAsString());
        assertEquals("grappin:item/grappin", m.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void modeles3dSansParentGenere() throws IOException {
        for (String nom : List.of("grappin_main", "grappin_main_vide", "crochet_tige", "crochet_griffe")) {
            JsonObject m = modele(nom);
            assertFalse(m.has("parent"), nom + " ne doit pas avoir de parent");
            JsonArray taille = m.getAsJsonArray("texture_size");
            assertEquals(32, taille.get(0).getAsInt(), nom);
            assertEquals(32, taille.get(1).getAsInt(), nom);
            assertTrue(m.getAsJsonArray("elements").size() >= 3, nom + " : au moins 3 cubes");
        }
    }

    @Test
    void texturesExistentEtOntLaBonneTaille() throws IOException {
        for (String nom : MODELES) {
            JsonObject m = modele(nom);
            if (m.has("textures")) {
                for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("textures").entrySet()) {
                    assertNotNull(texture(e.getValue().getAsString()));
                }
            }
        }
        BufferedImage icone = texture("grappin:item/grappin");
        assertEquals(16, icone.getWidth());
        assertEquals(16, icone.getHeight());
        BufferedImage atlas = texture("grappin:item/grappin_modele");
        assertEquals(32, atlas.getWidth());
        assertEquals(32, atlas.getHeight());
    }

    @Test
    void cubesDansLesLimites() throws IOException {
        for (String nom : List.of("grappin_main", "grappin_main_vide", "crochet_tige", "crochet_griffe")) {
            for (JsonElement el : modele(nom).getAsJsonArray("elements")) {
                JsonObject cube = el.getAsJsonObject();
                for (String bout : List.of("from", "to")) {
                    for (JsonElement v : cube.getAsJsonArray(bout)) {
                        double x = v.getAsDouble();
                        assertTrue(x >= -16 && x <= 32, nom + " : " + bout + " hors limites " + x);
                    }
                }
                if (cube.has("rotation")) {
                    double angle = cube.getAsJsonObject("rotation").get("angle").getAsDouble();
                    assertTrue(ANGLES.contains(angle), nom + " : angle interdit " + angle);
                }
                for (Map.Entry<String, JsonElement> face : cube.getAsJsonObject("faces").entrySet()) {
                    for (JsonElement v : face.getValue().getAsJsonObject().getAsJsonArray("uv")) {
                        double u = v.getAsDouble();
                        assertTrue(u >= 0 && u <= 16, nom + " : uv hors limites " + u);
                    }
                }
            }
        }
    }

    @Test
    void affichageEnMainDefini() throws IOException {
        for (String main : List.of("grappin_main", "grappin_main_vide")) {
        JsonObject display = modele(main).getAsJsonObject("display");
        for (String vue : List.of("thirdperson_righthand", "firstperson_righthand", "ground", "fixed", "head")) {
            assertTrue(display.has(vue), main + " : display." + vue + " manquant");
        }
        }
    }
}
