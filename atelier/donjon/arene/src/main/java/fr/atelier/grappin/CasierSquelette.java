// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/casier.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class CasierSquelette {
    private CasierSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("caisse", "racine", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("porte", "caisse", 7.0F, -16.0F, 8.0F)),
        Map.ofEntries(
            Map.entry("ouvrir", new Squelette.Anim(0.5F, false, List.of(
                    new Squelette.Canal("porte", true, new float[] {0.0F, 0.3F, 0.42F, 0.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 118.0F, 0.0F}, {0.0F, 106.0F, 0.0F}, {0.0F, 110.0F, 0.0F}}, new boolean[] {true, true, true, true})))),
            Map.entry("fermer", new Squelette.Anim(0.5F, false, List.of(
                    new Squelette.Canal("porte", true, new float[] {0.0F, 0.32F, 0.42F, 0.5F}, new float[][] {{0.0F, 110.0F, 0.0F}, {0.0F, -4.0F, 0.0F}, {0.0F, 2.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}))))));
}
