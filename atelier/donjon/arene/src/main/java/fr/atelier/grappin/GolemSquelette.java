// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/golem/golem.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import java.util.List;
import java.util.Map;

public final class GolemSquelette {
    private GolemSquelette() {
    }

    public static final Squelette SQUELETTE = new Squelette(
        List.of(
            new Squelette.Os("racine", null, 0.0F, 24.0F, 0.0F),
            new Squelette.Os("base", "racine", 0.0F, 0.0F, 0.0F),
            new Squelette.Os("corps", "racine", 0.0F, -24.0F, 0.0F),
            new Squelette.Os("tete", "corps", 0.0F, -100.0F, -8.0F),
            new Squelette.Os("machoire", "tete", 0.0F, 0.0F, 4.0F),
            new Squelette.Os("pf0", "tete", 0.0F, -12.0F, -34.666667F),
            new Squelette.Os("pf0_masque", "pf0", 0.0F, -12.0F, -1.6F),
            new Squelette.Os("bras_g", "corps", -44.0F, -88.0F, 0.0F),
            new Squelette.Os("main_g", "bras_g", -4.0F, 72.0F, -8.0F),
            new Squelette.Os("bras_d", "corps", 44.0F, -88.0F, 0.0F),
            new Squelette.Os("poing_d", "bras_d", -1.6F, -32.0F, -12.0F),
            new Squelette.Os("pf2", "bras_d", -13.358222F, -48.0F, -25.711504F),
            new Squelette.Os("pf2_petale0", "pf2", 14.0F, 13.3F, 0.0F),
            new Squelette.Os("pf2_petale1", "pf2", -14.0F, 13.3F, 0.0F),
            new Squelette.Os("pf2_petale2", "pf2", 0.0F, 13.3F, -14.0F),
            new Squelette.Os("pf2_petale3", "pf2", 0.0F, 13.3F, 14.0F),
            new Squelette.Os("branches_dos", "corps", 0.0F, -80.0F, 20.0F),
            new Squelette.Os("lianes", "corps", 0.0F, -72.0F, -20.0F),
            new Squelette.Os("pf1", "corps", 0.0F, -80.0F, -42.666667F),
            new Squelette.Os("pf1_petale0", "pf1", 12.0F, 11.4F, 0.0F),
            new Squelette.Os("pf1_petale1", "pf1", -12.0F, 11.4F, 0.0F),
            new Squelette.Os("pf1_petale2", "pf1", 0.0F, 11.4F, -12.0F),
            new Squelette.Os("pf1_petale3", "pf1", 0.0F, 11.4F, 12.0F),
            new Squelette.Os("pf3", "corps", -21.333333F, -96.0F, 36.950417F),
            new Squelette.Os("pf3_petale0", "pf3", 12.0F, 11.4F, 0.0F),
            new Squelette.Os("pf3_petale1", "pf3", -12.0F, 11.4F, 0.0F),
            new Squelette.Os("pf3_petale2", "pf3", 0.0F, 11.4F, -12.0F),
            new Squelette.Os("pf3_petale3", "pf3", 0.0F, 11.4F, 12.0F),
            new Squelette.Os("pf4", "corps", 21.333333F, -64.0F, 36.950417F),
            new Squelette.Os("pf4_petale0", "pf4", 12.0F, 11.4F, 0.0F),
            new Squelette.Os("pf4_petale1", "pf4", -12.0F, 11.4F, 0.0F),
            new Squelette.Os("pf4_petale2", "pf4", 0.0F, 11.4F, -12.0F),
            new Squelette.Os("pf4_petale3", "pf4", 0.0F, 11.4F, 12.0F)),
        Map.ofEntries(
            Map.entry("repos", new Squelette.Anim(4.0F, true, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 2.0F, 4.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {2.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 2.0F, 4.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.5F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 1.0F, 3.0F, 4.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, -3.0F, 0.0F}, {0.0F, 3.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 2.0F, 4.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {2.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("branches_dos", true, new float[] {0.0F, 1.0F, 3.0F, 4.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 4.0F}, {0.0F, 0.0F, -4.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("lianes", true, new float[] {0.0F, 2.0F, 4.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-4.0F, 0.0F, 2.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 2.0F, 4.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-2.0F, 0.0F, 1.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("emergence", new Squelette.Anim(5.0F, false, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 1.5F, 3.0F, 5.0F}, new float[][] {{20.0F, 0.0F, 0.0F}, {14.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 3.0F, 3.2F, 4.2F, 5.0F}, new float[][] {{15.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {-20.0F, 0.0F, 0.0F}, {-20.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 3.0F, 3.2F, 4.2F, 5.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {25.0F, 0.0F, 0.0F}, {25.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 3.0F, 3.2F, 4.2F, 5.0F}, new float[][] {{-25.0F, 0.0F, -10.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 20.0F}, {0.0F, 0.0F, 20.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true})))),
            Map.entry("lancer", new Squelette.Anim(1.5F, false, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.6F, 0.9F, 1.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {8.0F, 0.0F, 0.0F}, {-10.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.6F, 0.9F, 1.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {5.0F, 0.0F, 0.0F}, {-15.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 0.6F, 0.9F, 1.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {15.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 0.6F, 0.9F, 1.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {30.0F, 0.0F, 0.0F}, {-40.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true})))),
            Map.entry("douleur", new Squelette.Anim(0.75F, false, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.15F, 0.4F, 0.75F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-8.0F, 0.0F, 5.0F}, {-6.0F, 0.0F, -3.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.15F, 0.75F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-25.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 0.15F, 0.75F}, new float[][] {{0.0F, 0.0F, 0.0F}, {20.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 0.15F, 0.75F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 15.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("bras_d", true, new float[] {0.0F, 0.15F, 0.75F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -15.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true})))),
            Map.entry("rage", new Squelette.Anim(2.5F, false, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.6F, 1.8F, 2.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-15.0F, 0.0F, 0.0F}, {-15.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.6F, 1.8F, 2.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-30.0F, 0.0F, 0.0F}, {-30.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 0.6F, 1.8F, 2.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {30.0F, 0.0F, 0.0F}, {30.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 0.6F, 1.8F, 2.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 45.0F}, {0.0F, 0.0F, 45.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("bras_d", true, new float[] {0.0F, 0.6F, 1.8F, 2.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, -30.0F}, {0.0F, 0.0F, -30.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true}),
                    new Squelette.Canal("branches_dos", true, new float[] {0.0F, 0.6F, 1.2F, 1.8F, 2.5F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 8.0F}, {0.0F, 0.0F, -8.0F}, {0.0F, 0.0F, 8.0F}, {0.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true, true, true})))),
            Map.entry("mort", new Squelette.Anim(2.0F, false, List.of(
                    new Squelette.Canal("corps", true, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-8.0F, 0.0F, 0.0F}, {25.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("corps", false, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {0.0F, 48.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("tete", true, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {-25.0F, 0.0F, 0.0F}, {30.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("machoire", true, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {30.0F, 0.0F, 0.0F}, {10.0F, 0.0F, 0.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("bras_g", true, new float[] {0.0F, 0.8F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 20.0F}, {0.0F, 0.0F, -20.0F}}, new boolean[] {true, true, true}),
                    new Squelette.Canal("bras_d", true, new float[] {0.0F, 2.0F}, new float[][] {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 20.0F}}, new boolean[] {true, true}))))));
}
