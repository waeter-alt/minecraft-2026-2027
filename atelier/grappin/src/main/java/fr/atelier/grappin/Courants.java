package fr.atelier.grappin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Zones de courant des donjons : un marker nomme « courant vx vy vz dx dy dz » (vitesse en blocs par seconde ; boite de dx*dy*dz blocs depuis sa position) pousse les joueurs dans l'eau.
 * Un nageur est emporte ; un joueur leste qui marche au fond n'est presque pas pousse : c'est ce qui rend les drains et les torrents remontables avec la cape lestee seulement.
 */
@EventBusSubscriber(modid = Grappin.MODID)
public final class Courants {
    private static final double PRISE_NAGE = 0.12;
    private static final double PRISE_LESTE_AU_SOL = 0.018;
    private static final double PORTEE_RECHERCHE = 96;

    private Courants() {
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post evenement) {
        if (!(evenement.getEntity() instanceof ServerPlayer j) || j.isSpectator() || !j.isInWater() || j.getAbilities().flying) {
            return;
        }
        AABB corps = j.getBoundingBox();
        for (Marker m : j.level().getEntitiesOfClass(Marker.class, corps.inflate(PORTEE_RECHERCHE), Courants::estCourant)) {
            double[] v = lire(m.getCustomName());
            if (v == null) {
                continue;
            }
            AABB zone = new AABB(m.getX(), m.getY(), m.getZ(), m.getX() + v[3], m.getY() + v[4], m.getZ() + v[5]);
            if (!zone.intersects(corps)) {
                continue;
            }
            double prise = CapeEvenements.leste(j) && j.onGround() ? PRISE_LESTE_AU_SOL : PRISE_NAGE;
            Vec3 cible = new Vec3(v[0], v[1], v[2]).scale(1 / 20.0);
            Vec3 actuel = j.getDeltaMovement();
            // On ne tire que sur les axes ou le courant souffle : un courant horizontal laisse le joueur libre de monter ou descendre.
            double nx = v[0] != 0 ? actuel.x + (cible.x - actuel.x) * prise : actuel.x;
            double ny = v[1] != 0 ? actuel.y + (cible.y - actuel.y) * prise : actuel.y;
            double nz = v[2] != 0 ? actuel.z + (cible.z - actuel.z) * prise : actuel.z;
            j.setDeltaMovement(nx, ny, nz);
            j.hurtMarked = true;
            return;
        }
    }

    private static boolean estCourant(Marker m) {
        Component nom = m.getCustomName();
        return nom != null && nom.getString().startsWith("courant ");
    }

    static double[] lire(Component nom) {
        String[] mots = nom.getString().trim().split("\s+");
        if (mots.length != 7) {
            return null;
        }
        double[] v = new double[6];
        try {
            for (int i = 0; i < 6; i++) {
                v[i] = Double.parseDouble(mots[i + 1]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return v;
    }
}
