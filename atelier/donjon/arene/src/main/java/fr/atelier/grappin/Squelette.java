// [claude-build] Squelette de calcul d'un modele anime, sans Minecraft : ou se trouve le pivot d'un os a un instant donne des animations.
// Le serveur s'en sert pour placer les boites de collision des points faibles du Gardien la ou le client les dessine (GolemSquelette est
// genere depuis le .bbmodel par atelier/scripts/bbmodel_vers_java.py). Les calculs reprennent ceux de Minecraft 1.21.1 :
// KeyframeAnimations.animate (choix des images cles, interpolation lineaire ou Mth.catmullrom, valeurs qui s'additionnent) et
// ModelPart.translateAndRotate (decalage, puis rotation Z * Y * X). Unites : celles du modele (1/16 de bloc avant l'echelle du rendu).
package fr.atelier.grappin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Squelette {
    /** Un os : son pivot par rapport a celui de son parent (le PartPose du modele ; rotation de repos nulle). */
    public record Os(String nom, String parent, float x, float y, float z) {
    }

    /** Un canal d'animation, valeurs deja dans le repere de Minecraft (degres pour une rotation, unites pour une position). */
    public record Canal(String os, boolean rotation, float[] temps, float[][] valeurs, boolean[] catmull) {
    }

    public record Anim(float duree, boolean boucle, List<Canal> canaux) {
    }

    private final Map<String, Os> os = new HashMap<>();
    private final Map<String, Anim> anims;

    public Squelette(List<Os> liste, Map<String, Anim> anims) {
        for (Os o : liste) {
            os.put(o.nom(), o);
        }
        this.anims = anims;
    }

    /**
     * Position du pivot de l'os `nom`, avec les animations dont `temps` donne le temps ecoule en secondes (absentes : non jouees),
     * et le regard ajoute a l'os `osTete` (degres, comme GolemModele.setupAnim).
     */
    public double[] point(String nom, Map<String, Float> temps, String osTete, float lacetDeg, float tangageDeg) {
        double[] p = {0, 0, 0};
        for (Os o = os.get(nom); o != null; o = o.parent() == null ? null : os.get(o.parent())) {
            float[] rot = new float[3];
            float[] pos = new float[3];
            for (Map.Entry<String, Float> e : temps.entrySet()) {
                Anim a = anims.get(e.getKey());
                if (a == null) {
                    continue;
                }
                float t = a.boucle() ? e.getValue() % a.duree() : e.getValue();
                for (Canal c : a.canaux()) {
                    if (!c.os().equals(o.nom())) {
                        continue;
                    }
                    float[] cible = c.rotation() ? rot : pos;
                    for (int k = 0; k < 3; k++) {
                        float[] v = new float[c.valeurs().length];
                        for (int n = 0; n < v.length; n++) {
                            v[n] = c.valeurs()[n][k];
                        }
                        cible[k] += valeur(t, c.temps(), v, c.catmull());
                    }
                }
            }
            if (o.nom().equals(osTete)) {
                rot[0] += tangageDeg;
                rot[1] += lacetDeg;
            }
            p = tourner(p, Math.toRadians(rot[0]), Math.toRadians(rot[1]), Math.toRadians(rot[2]));
            p[0] += o.x() + pos[0];
            p[1] += o.y() + pos[1];
            p[2] += o.z() + pos[2];
        }
        return p;
    }

    /** Rz * Ry * Rx applique au point (ModelPart.translateAndRotate : Quaternionf.rotationZYX). */
    private static double[] tourner(double[] p, double rx, double ry, double rz) {
        double x = p[0], y = p[1], z = p[2];
        double y1 = y * Math.cos(rx) - z * Math.sin(rx), z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x2 = x * Math.cos(ry) + z1 * Math.sin(ry), z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz), y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
        return new double[] {x3, y3, z2};
    }

    /** Valeur d'une composante a l'instant t, comme KeyframeAnimations.animate (l'interpolation est celle de l'image cle d'arrivee). */
    static float valeur(float t, float[] temps, float[] vals, boolean[] catmull) {
        int premier = temps.length;
        for (int n = 0; n < temps.length; n++) {
            if (t <= temps[n]) {
                premier = n;
                break;
            }
        }
        int i = Math.max(0, premier - 1);
        int j = Math.min(temps.length - 1, i + 1);
        float alpha = j != i ? Math.max(0, Math.min(1, (t - temps[i]) / (temps[j] - temps[i]))) : 0;
        if (catmull[j]) {
            float p0 = vals[Math.max(0, i - 1)], p1 = vals[i], p2 = vals[j], p3 = vals[Math.min(vals.length - 1, j + 1)];
            return 0.5F * (2 * p1 + (p2 - p0) * alpha + (2 * p0 - 5 * p1 + 4 * p2 - p3) * alpha * alpha
                    + (3 * p1 - p0 - 3 * p2 + p3) * alpha * alpha * alpha);
        }
        return vals[i] + (vals[j] - vals[i]) * alpha;
    }
}
