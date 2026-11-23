// [claude-build] Nage ondulante de la Cape lestee, vue a la 3e personne (et par les autres joueurs) : bras plaques le long du corps,
// une onde parcourt le corps de la tete aux pieds (buste, bassin, jambes jointes qui battent ensemble). Amplitude et frequence suivent
// la vitesse ; a l'arret, ondulation lente ; hors de l'eau ou leste, retour en douceur a la pose normale.
// Appelee a la fin de PlayerModel.setupAnim (mixin client), donc par-dessus la pose de nage du jeu ; la premiere personne n'est pas touchee.
package fr.atelier.grappin;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CapeOndulation {
    /** Vitesse (blocs par tick) a laquelle l'onde atteint son amplitude et sa frequence maximales : celle de la nage rapide en sprint. */
    private static final float VITESSE_PLEINE = 0.7F;
    private static final float FREQUENCE_LENTE = 0.10F;
    private static final float FREQUENCE_RAPIDE = 0.55F;
    private static final float AMPLITUDE_LENTE = 0.10F;
    private static final float AMPLITUDE_RAPIDE = 0.30F;
    /** Retard de phase entre deux segments successifs (tete, buste, jambes) : c'est lui qui fait voyager l'onde vers les pieds. */
    private static final float RETARD = 1.1F;

    private static final class Etat {
        float poids;
        float phase;
        float amplitude = AMPLITUDE_LENTE;
        float frequence = FREQUENCE_LENTE;
        float dernierAge = Float.NaN;
    }

    private static final Map<LivingEntity, Etat> ETATS = new WeakHashMap<>();

    private CapeOndulation() {
    }

    private static boolean active(Player j) {
        if (!CapeLesteeItem.porte(j) || !j.isInWater() || j.isPassenger()) {
            return false;
        }
        if (j == Minecraft.getInstance().player && CapeClient.Nage.leste) {
            return false;
        }
        return j.isSwimming() || !j.onGround();
    }

    public static void appliquer(PlayerModel<?> m, LivingEntity entite, float ageInTicks) {
        // Le bras de la premiere personne est pose par un setupAnim a zero (age compris) : on le laisse tel quel.
        if (ageInTicks == 0 || !(entite instanceof Player j)) {
            return;
        }
        boolean active = active(j);
        Etat e = ETATS.get(entite);
        if (e == null) {
            if (!active) {
                return;
            }
            e = new Etat();
            ETATS.put(entite, e);
        }
        float dt = Float.isNaN(e.dernierAge) ? 0 : Mth.clamp(ageInTicks - e.dernierAge, 0, 2);
        e.dernierAge = ageInTicks;

        double dx = j.getX() - j.xo;
        double dy = j.getY() - j.yo;
        double dz = j.getZ() - j.zo;
        float f = Mth.clamp((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / VITESSE_PLEINE, 0, 1);
        float lissage = 1 - (float) Math.exp(-dt * 0.12F);
        e.poids += ((active ? 1 : 0) - e.poids) * (1 - (float) Math.exp(-dt * 0.15F));
        e.amplitude += (Mth.lerp(f, AMPLITUDE_LENTE, AMPLITUDE_RAPIDE) - e.amplitude) * lissage;
        e.frequence += (Mth.lerp(f, FREQUENCE_LENTE, FREQUENCE_RAPIDE) - e.frequence) * lissage;
        e.phase = (e.phase + e.frequence * dt) % (Mth.TWO_PI * 64);
        if (!active && e.poids < 0.01F) {
            ETATS.remove(entite);
            return;
        }

        float w = e.poids;
        float a = e.amplitude;
        float phi = e.phase;
        // Le corps entier monte et descend un peu avec l'onde (axe z du modele : dos / ventre).
        float houle = 1.2F * a / AMPLITUDE_RAPIDE * Mth.cos(phi);

        float tete = 0.35F * a * Mth.sin(phi);
        float buste = a * Mth.sin(phi - RETARD);
        float jambes = 1.7F * a * Mth.sin(phi - 2 * RETARD);

        m.head.xRot += w * tete;
        m.head.z = Mth.lerp(w, m.head.z, houle);
        m.hat.copyFrom(m.head);

        poser(m.body, w, 0, 0, houle, buste, 0, 0);

        // Hanches et epaules suivent la flexion du buste (pivot au cou, 12 pixels jusqu'aux hanches, 2 jusqu'aux epaules).
        float cos = Mth.cos(buste);
        float sin = Mth.sin(buste);
        poser(m.rightLeg, w, -1.9F, 12 * cos, houle + 12 * sin, jambes, 0, 0);
        poser(m.leftLeg, w, 1.9F, 12 * cos, houle + 12 * sin, jambes, 0, 0);
        poser(m.rightArm, w, -5, 2 * cos, houle + 2 * sin, buste, 0, 0.06F);
        poser(m.leftArm, w, 5, 2 * cos, houle + 2 * sin, buste, 0, -0.06F);

        m.jacket.copyFrom(m.body);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
    }

    private static void poser(ModelPart p, float w, float x, float y, float z, float xRot, float yRot, float zRot) {
        p.x = Mth.lerp(w, p.x, x);
        p.y = Mth.lerp(w, p.y, y);
        p.z = Mth.lerp(w, p.z, z);
        p.xRot = Mth.lerp(w, p.xRot, xRot);
        p.yRot = Mth.lerp(w, p.yRot, yRot);
        p.zRot = Mth.lerp(w, p.zRot, zRot);
    }
}
