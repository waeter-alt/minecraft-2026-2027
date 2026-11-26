// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/paravoile/paravoile_voile.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class ParavoileSquelette {
    private ParavoileSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("voile", "racine", 0.0F, -25.0F, 0.0F),
            new Squelette.Os("aile_g", "voile", 2.9F, -27.0F, 0.0F),
            new Squelette.Os("stabilo_g", "aile_g", 17.194274F, 10.300632F, 0.0F),
            new Squelette.Os("aile_d", "voile", -2.9F, -27.0F, 0.0F),
            new Squelette.Os("stabilo_d", "aile_d", -17.194274F, 10.300632F, 0.0F)),
        Map.ofEntries(
            Map.entry("ouverture", new Squelette.Anim(0.7F, false, List.of(
                    new Squelette.Canal("voile", true, new float[] {0.0F, 0.4F, 0.55F, 0.7F}, new float[][] {{70.0F, 0.0F, 0.0F}, {-6.0F, 0.0F, 0.0F}, {2.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("voile", false, new float[] {0.0F, 0.4F, 0.55F, 0.7F}, new float[][] {{0.0F, 16.0F, 6.0F}, {0.0F, -1.5F, 0.0F}, {0.0F, 0.5F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("aile_g", true, new float[] {0.0F, 0.15F, 0.5F, 0.7F}, new float[][] {{0.0F, 0.0F, 80.0F}, {0.0F, 0.0F, 80.0F}, {0.0F, 0.0F, -6.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("aile_d", true, new float[] {0.0F, 0.15F, 0.5F, 0.7F}, new float[][] {{0.0F, 0.0F, -80.0F}, {0.0F, 0.0F, -80.0F}, {0.0F, 0.0F, 6.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true})))),
            Map.entry("fermeture", new Squelette.Anim(0.5F, false, List.of(
                    new Squelette.Canal("voile", true, new float[] {0.0F, 0.2F, 0.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-4.0F, 0.0F, 0.0F}, {70.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("voile", false, new float[] {0.0F, 0.2F, 0.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 16.0F, 6.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("aile_g", true, new float[] {0.0F, 0.3F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 80.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("aile_d", true, new float[] {0.0F, 0.3F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -80.0F}}, new boolean[] {true, true})))),
            Map.entry("vol", new Squelette.Anim(2.4F, true, List.of(
                    new Squelette.Canal("voile", true, new float[] {0.0F, 0.6F, 1.2F, 1.8F, 2.4F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-2.0F, 0.0F, 3.0F}, {0.0F, 0.0F, 0.0F}, {2.0F, 0.0F, -3.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true}),
                    new Squelette.Canal("voile", false, new float[] {0.0F, 1.2F, 2.4F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -0.6F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("aile_g", true, new float[] {0.0F, 1.2F, 2.4F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 2.5F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("aile_d", true, new float[] {0.0F, 1.2F, 2.4F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -2.5F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("stabilo_g", true, new float[] {0.0F, 0.3F, 0.6F, 0.9F, 1.2F, 1.5F, 1.8F, 2.1F, 2.4F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 8.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -5.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 8.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -5.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true, true, true, true, true}),
                    new Squelette.Canal("stabilo_d", true, new float[] {0.0F, 0.3F, 0.6F, 0.9F, 1.2F, 1.5F, 1.8F, 2.1F, 2.4F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -6.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 7.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -6.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 7.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true, true, true, true, true}))))));
}
