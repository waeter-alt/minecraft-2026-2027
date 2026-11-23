// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/levier_station.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class LevierStationSquelette {
    private LevierStationSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("socle", "racine", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("manche", "socle", 0.0F, -9.0F, 0.0F)),
        Map.ofEntries(
            Map.entry("tirer", new Squelette.Anim(0.6F, false, List.of(
                    new Squelette.Canal("manche", true, new float[] {0.0F, 0.45F, 0.6F}, new float[][] {{35.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}, {-35.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("relacher", new Squelette.Anim(0.6F, false, List.of(
                    new Squelette.Canal("manche", true, new float[] {0.0F, 0.45F, 0.6F}, new float[][] {{-35.0F, 0.0F, 0.0F}, {40.0F, 0.0F, 0.0F}, {35.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}))))));
}
