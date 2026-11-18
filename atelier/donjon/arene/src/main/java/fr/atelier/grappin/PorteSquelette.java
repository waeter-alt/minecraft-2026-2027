// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/donjon/porte_donjon.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class PorteSquelette {
    private PorteSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("dalle", "racine", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("sceau", "dalle", 0.0F, -96.0F, 0.0F)),
        Map.ofEntries(
            Map.entry("ouverture", new Squelette.Anim(3.0F, false, List.of(
                    new Squelette.Canal("sceau", false, new float[] {0.0F, 0.2F, 0.4F, 0.6F, 0.8F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-0.6F, 0.0F, 0.0F}, {0.6F, 0.0F, 0.0F}, {-0.5F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true, true}),
                    new Squelette.Canal("dalle", false, new float[] {0.0F, 0.9F, 1.05F, 1.2F, 1.35F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {-1.0F, 1.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, -136.0F, 0.0F}}, new boolean[] {true, true, true, true, true, true}))))));
}
