// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/valve_station.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class ValveStationSquelette {
    private ValveStationSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("socle", "racine", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("roue", "socle", 0.0F, -12.5F, 0.0F)),
        Map.ofEntries(
            Map.entry("tourner", new Squelette.Anim(2.0F, false, List.of(
                    new Squelette.Canal("roue", true, new float[] {0.0F, 0.3F, 1.6F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -90.0F, 0.0F}, {0.0F, -900.0F, 0.0F}, {0.0F, -1080.0F, 0.0F}}, new boolean[] {true, true, true, true}))))));
}
