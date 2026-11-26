// [claude-build] Les touches des objets de donjon, toutes reglables dans Options > Commandes, section « Donjon » (client). Les reglages par defaut sont les gestes d'avant :
// lest sur V, dash (tourbillon) de la cape sur le clic gauche, lacher le grappin et deployer la paravoile sur Espace. Une meme touche peut servir a plusieurs (NeoForge les previent toutes).
package fr.atelier.grappin;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;

public final class TouchesDonjon {
    private static final String CATEGORIE = "key.categories.donjon";

    // V par defaut : a verifier contre les touches de sorts des mods RPG Series du modpack (reaffectable dans Options > Commandes).
    static final KeyMapping LEST = new KeyMapping("key.donjon.lest", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORIE);
    static final KeyMapping DASH = new KeyMapping("key.donjon.dash", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CATEGORIE);
    static final KeyMapping LACHER = new KeyMapping("key.donjon.lacher", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORIE);
    static final KeyMapping PARAVOILE = new KeyMapping("key.donjon.paravoile", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORIE);

    private TouchesDonjon() {
    }
}
