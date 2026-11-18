// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/scaphandrier.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class ScaphandrierSquelette {
    private ScaphandrierSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("corps", "racine", 0.0F, -14.0F, 0.0F),
            new Squelette.Os("casque", "corps", 0.0F, -15.0F, 0.0F),
            new Squelette.Os("fissure1", "casque", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("fissure2", "casque", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("hublot_brise", "casque", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("bras_g", "corps", -8.0F, -13.0F, 0.0F),
            new Squelette.Os("cle", "bras_g", -1.0F, 15.0F, 0.0F),
            new Squelette.Os("bras_d", "corps", 8.0F, -13.0F, 0.0F),
            new Squelette.Os("harpon", "bras_d", 3.75F, 18.0F, 0.0F),
            new Squelette.Os("tuyaux", "corps", 0.0F, -13.0F, 5.0F),
            new Squelette.Os("jambe_g", "racine", -3.5F, -14.0F, 0.0F),
            new Squelette.Os("jambe_d", "racine", 3.5F, -14.0F, 0.0F)),
        Map.ofEntries(
            Map.entry("repos", new Squelette.Anim(3.0F, true, List.of(
                    new Squelette.Canal("corps", false, new float[] {0.0F, 1.5F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.3F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("casque", true, new float[] {0.0F, 1.0F, 2.0F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -5.0F, 0.0F}, {0.0F, 5.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("tuyaux", true, new float[] {0.0F, 1.5F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-6.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("marche", new Squelette.Anim(1.6F, true, List.of(
                    new Squelette.Canal("jambe_g", true, new float[] {0.0F, 0.4F, 1.2F, 1.6F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-20.0F, 0.0F, 0.0F}, {20.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("jambe_d", true, new float[] {0.0F, 0.4F, 1.2F, 1.6F}, new float[][] {{0.0F, 0.0F, 0.0F}, {20.0F, 0.0F, 0.0F}, {-20.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.4F, 1.2F, 1.6F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 3.0F}, {0.0F, 0.0F, -3.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 0.4F, 1.2F, 1.6F}, new float[][] {{0.0F, 0.0F, 0.0F}, {10.0F, 0.0F, 0.0F}, {-10.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true})))),
            Map.entry("coup", new Squelette.Anim(0.9F, false, List.of(
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 0.4F, 0.6F, 0.9F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-150.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.4F, 0.6F, 0.9F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -15.0F, 0.0F}, {0.0F, 20.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true})))),
            Map.entry("viser", new Squelette.Anim(1.0F, false, List.of(
                    new Squelette.Canal("bras_d", true, new float[] {0.0F, 0.4F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-85.0F, 0.0F, 0.0F}, {-85.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("casque", true, new float[] {0.0F, 0.4F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {5.0F, 0.0F, 0.0F}, {5.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("tirer", new Squelette.Anim(0.4F, false, List.of(
                    new Squelette.Canal("bras_d", true, new float[] {0.0F, 0.1F, 0.4F}, new float[][] {{-85.0F, 0.0F, 0.0F}, {-65.0F, 0.0F, 0.0F}, {-85.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 0.1F, 0.4F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("coince", new Squelette.Anim(1.5F, true, List.of(
                    new Squelette.Canal("bras_d", true, new float[] {0.0F, 0.4F, 0.8F, 1.2F, 1.5F}, new float[][] {{-85.0F, 0.0F, 0.0F}, {-70.0F, 0.0F, 0.0F}, {-95.0F, 0.0F, 0.0F}, {-72.0F, 0.0F, 0.0F}, {-85.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true}),
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.75F, 1.5F}, new float[][] {{10.0F, 0.0F, 0.0F}, {14.0F, 0.0F, 4.0F}, {10.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 1.5F}, new float[][] {{0.0F, 0.0F, 1.5F}, {0.0F, 0.0F, 1.5F}}, new boolean[] {true, true}),
                    new Squelette.Canal("jambe_g", true, new float[] {0.0F, 1.5F}, new float[][] {{12.0F, 0.0F, 0.0F}, {12.0F, 0.0F, 0.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("jambe_d", true, new float[] {0.0F, 1.5F}, new float[][] {{-10.0F, 0.0F, 0.0F}, {-10.0F, 0.0F, 0.0F}}, new boolean[] {true, true})))),
            Map.entry("mort", new Squelette.Anim(2.0F, false, List.of(
                    new Squelette.Canal("jambe_g", true, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-70.0F, 0.0F, 0.0F}, {-70.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("jambe_d", true, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-70.0F, 0.0F, 0.0F}, {-70.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {8.0F, 0.0F, 0.0F}, {-70.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 6.0F, 0.0F}, {0.0F, 12.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 25.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("bras_d", true, new float[] {0.0F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -25.0F}}, new boolean[] {true, true}))))));
}
