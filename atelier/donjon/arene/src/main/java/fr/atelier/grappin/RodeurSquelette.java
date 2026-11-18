// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/rodeur/rodeur.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class RodeurSquelette {
    private RodeurSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("corps", "racine", 0.0F, -20.0F, 0.0F),
            new Squelette.Os("cou", "corps", 0.0F, -4.0F, -16.0F),
            new Squelette.Os("tete", "cou", 0.0F, 0.0F, -7.0F),
            new Squelette.Os("machoire", "tete", 0.0F, 4.0F, -5.0F),
            new Squelette.Os("patte_av_g", "corps", -6.0F, 2.0F, -12.0F),
            new Squelette.Os("patte_av_d", "corps", 6.0F, 2.0F, -12.0F),
            new Squelette.Os("patte_ar_g", "corps", -6.0F, 2.0F, 10.0F),
            new Squelette.Os("patte_ar_d", "corps", 6.0F, 2.0F, 10.0F),
            new Squelette.Os("queue", "corps", 0.0F, -2.0F, 16.0F),
            new Squelette.Os("crete", "corps", 0.0F, -9.0F, 0.0F),
            new Squelette.Os("lianes", "corps", 0.0F, 7.0F, 0.0F)),
        Map.ofEntries(
            Map.entry("repos", new Squelette.Anim(3.0F, true, List.of(
                    new Squelette.Canal("corps", false, new float[] {0.0F, 1.5F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.5F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 1.2F, 1.6F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {6.0F, -4.0F, 0.0F}, {3.0F, 4.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 1.4F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {4.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("queue", true, new float[] {0.0F, 0.75F, 2.25F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -10.0F, 0.0F}, {0.0F, 10.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("lianes", true, new float[] {0.0F, 1.5F, 3.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-5.0F, 0.0F, 3.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("marche", new Squelette.Anim(1.0F, true, List.of(
                    new Squelette.Canal("patte_av_g", true, new float[] {0.0F, 0.25F, 0.75F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-25.0F, 0.0F, 0.0F}, {25.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("patte_ar_d", true, new float[] {0.0F, 0.25F, 0.75F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-25.0F, 0.0F, 0.0F}, {25.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("patte_av_d", true, new float[] {0.0F, 0.25F, 0.75F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {25.0F, 0.0F, 0.0F}, {-25.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("patte_ar_g", true, new float[] {0.0F, 0.25F, 0.75F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {25.0F, 0.0F, 0.0F}, {-25.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.5F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {4.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("queue", true, new float[] {0.0F, 0.5F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -8.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("morsure", new Squelette.Anim(0.5F, false, List.of(
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.15F, 0.3F, 0.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-12.0F, 0.0F, 0.0F}, {15.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 0.15F, 0.3F, 0.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {35.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 0.3F, 0.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -3.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("grattage", new Squelette.Anim(1.0F, true, List.of(
                    new Squelette.Canal("patte_av_d", true, new float[] {0.0F, 0.25F, 0.5F, 0.75F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {35.0F, 0.0F, 0.0F}, {-20.0F, 0.0F, 0.0F}, {35.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.5F, 1.0F}, new float[][] {{20.0F, 0.0F, 0.0F}, {24.0F, 0.0F, 0.0F}, {20.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", true, new float[] {0.0F, 1.0F}, new float[][] {{4.0F, 0.0F, 0.0F}, {4.0F, 0.0F, 0.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 1.0F, 0.0F}, {0.0F, 1.0F, 0.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("queue", true, new float[] {0.0F, 0.25F, 0.75F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -15.0F, 0.0F}, {0.0F, 15.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true})))),
            Map.entry("charge", new Squelette.Anim(0.5F, true, List.of(
                    new Squelette.Canal("patte_av_g", true, new float[] {0.0F, 0.25F, 0.5F}, new float[][] {{-40.0F, 0.0F, 0.0F}, {40.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("patte_av_d", true, new float[] {0.0F, 0.25F, 0.5F}, new float[][] {{-40.0F, 0.0F, 0.0F}, {40.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("patte_ar_g", true, new float[] {0.0F, 0.25F, 0.5F}, new float[][] {{40.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}, {40.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("patte_ar_d", true, new float[] {0.0F, 0.25F, 0.5F}, new float[][] {{40.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}, {40.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.25F, 0.5F}, new float[][] {{5.0F, 0.0F, 0.0F}, {-3.0F, 0.0F, 0.0F}, {5.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 0.25F, 0.5F}, new float[][] {{0.0F, 2.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 2.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.5F}, new float[][] {{15.0F, 0.0F, 0.0F}, {15.0F, 0.0F, 0.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("queue", true, new float[] {0.0F, 0.5F}, new float[][] {{-15.0F, 0.0F, 0.0F}, {-15.0F, 0.0F, 0.0F}}, new boolean[] {true, true})))),
            Map.entry("sonne", new Squelette.Anim(1.5F, true, List.of(
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.375F, 1.125F, 1.5F}, new float[][] {{10.0F, 0.0F, 0.0F}, {10.0F, 0.0F, 15.0F}, {10.0F, 0.0F, -15.0F}, {10.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.375F, 1.125F, 1.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 6.0F}, {0.0F, 0.0F, -6.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 1.5F}, new float[][] {{12.0F, 0.0F, 0.0F}, {12.0F, 0.0F, 0.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("crete", true, new float[] {0.0F, 0.75F, 1.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {8.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("reveil", new Squelette.Anim(2.5F, false, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 1.2F, 2.5F}, new float[][] {{20.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 1.2F, 2.5F}, new float[][] {{0.0F, 24.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 1.2F, 1.5F, 1.7F, 1.9F, 2.1F, 2.5F}, new float[][] {{25.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 20.0F}, {0.0F, 0.0F, -20.0F}, {0.0F, 0.0F, 15.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 2.1F, 2.3F, 2.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {30.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true})))),
            Map.entry("mort", new Squelette.Anim(1.0F, false, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.8F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 80.0F}, {0.0F, 0.0F, 85.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 10.0F, 0.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {20.0F, 0.0F, 0.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("patte_av_g", true, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 20.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("patte_ar_g", true, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 20.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("patte_av_d", true, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -20.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("patte_ar_d", true, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -20.0F}}, new boolean[] {true, true}),
                    new Squelette.Canal("crete", true, new float[] {0.0F, 1.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {30.0F, 0.0F, 0.0F}}, new boolean[] {true, true}))))));
}
