// [claude-build] Regles du casier de vestiaire sans Minecraft (CasierLogiqueTest) : ses couleurs, et quelle teinture le repeint de laquelle.
package fr.atelier.grappin;

import java.util.List;

public final class CasierLogique {
    /** Les couleurs du casier, dans l'ordre de CasierCouleur ; la texture de chacune est textures/entity/casier_<couleur>.png. */
    public static final List<String> COULEURS = List.of("bleu_clair", "cyan", "vert", "jaune", "orange");
    /** Cases de l'inventaire, comme un coffre simple. */
    public static final int CASES = 27;

    private CasierLogique() {
    }

    /** Couleur de casier que donne une teinture de Minecraft (nom de DyeColor), ou null si le casier n'existe pas dans cette couleur. */
    public static String couleurDeTeinture(String teinture) {
        return switch (teinture) {
            case "light_blue" -> "bleu_clair";
            case "cyan" -> "cyan";
            case "lime", "green" -> "vert";
            case "yellow" -> "jaune";
            case "orange" -> "orange";
            default -> null;
        };
    }
}
