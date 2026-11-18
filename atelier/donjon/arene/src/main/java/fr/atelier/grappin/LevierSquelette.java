// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/donjon/levier_racine.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class LevierSquelette {
    private LevierSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("socle", "racine", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("cliquet", "socle", -6.0F, -14.0F, 0.0F),
            new Squelette.Os("manche", "socle", 0.0F, -14.0F, 0.0F),
            new Squelette.Os("cristal", "manche", 0.0F, -16.0F, 0.0F)),
        Map.ofEntries(
            Map.entry("tirer", new Squelette.Anim(0.6F, false, List.of(
                    new Squelette.Canal("manche", true, new float[] {0.0F, 0.45F, 0.6F}, new float[][] {{35.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}, {-35.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("cliquet", true, new float[] {0.0F, 0.6F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-90.0F, 0.0F, 0.0F}}, new boolean[] {true, true})))),
            Map.entry("relacher", new Squelette.Anim(0.6F, false, List.of(
                    new Squelette.Canal("manche", true, new float[] {0.0F, 0.45F, 0.6F}, new float[][] {{-35.0F, 0.0F, 0.0F}, {40.0F, 0.0F, 0.0F}, {35.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("cliquet", true, new float[] {0.0F, 0.6F}, new float[][] {{-90.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true}))))));
}
