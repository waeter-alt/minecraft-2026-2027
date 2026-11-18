// [claude-build] Regles des blocs de decor de la Station sans Minecraft (ModelesStationTest) : formes tournees comme le blockstate.
package fr.atelier.grappin;

public final class StationLogique {
    private StationLogique() {
    }

    /**
     * Boite {x0, y0, z0, x1, y1, z1} (pixels) donnee pour l'orientation nord, tournee de quarts de tour comme le blockstate
     * (1 : y = 90, l'est ; 2 : y = 180, le sud ; 3 : y = 270, l'ouest).
     */
    public static double[] tourner(double[] b, int quarts) {
        return switch (((quarts % 4) + 4) % 4) {
            case 1 -> new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
            case 2 -> new double[] {16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]};
            case 3 -> new double[] {b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]};
            default -> b;
        };
    }
}
