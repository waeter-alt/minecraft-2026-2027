// [claude-build] Le comportement du Seraphin : il tourne autour de sa cible (Orbite), puis enchaine rayon de l'oeil, volee de plumes et balayage d'ailes (Combat).
// Les durees et les chiffres viennent de Reglages et de SeraphinLogique ; l'etat de combat n'est pas sauvegarde : apres un chargement il repart oeil ferme.
package fr.atelier.grappin;

import java.util.EnumSet;
import java.util.List;

import com.mojang.logging.LogUtils;
import org.joml.Vector3f;
import org.slf4j.Logger;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SeraphinAttaques {
    private SeraphinAttaques() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final DustParticleOptions OR = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 2.5F);
    private static final double RAYON_ORBITE = 12.0;
    private static final double HAUTEUR_ORBITE = 9.0;
    private static final double PORTEE_BALAYAGE = 6.0;
    private static final double PORTEE_RAYON = 48.0;
    private static final int RECHARGE_BASE = 90;
    private static final int RECHARGE_BALAYAGE = 100;

    /** Distance d'un point a un segment. */
    static double distanceAuSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / l2));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Les déplacements du Seraphin : il en change toutes les 5 a 8 s (orbite, vague, montee, plongeon), et quand l'oeil est ouvert il descend pres des joueurs pour etre atteignable. */
    public static class Orbite extends Goal {
        private enum Mode { ORBITE, VAGUE, MONTEE, PLONGEON }

        private final Seraphin boss;
        private double angle;
        private double sens = 1;
        private Mode mode = Mode.ORBITE;
        private int duree = 100;
        private int etape = 0;              // minuteur du plongeon
        private boolean contact = false;    // le plongeon a deja touche sa cible

        public Orbite(Seraphin boss) {
            this.boss = boss;
            this.angle = boss.getRandom().nextDouble() * Math.PI * 2;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity c = boss.getTarget();
            return c != null && c.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private void choisirMode() {
            boolean p2 = boss.phase() == 2;
            int[] poids = p2 ? new int[] {15, 35, 15, 35} : new int[] {40, 30, 20, 10};       // orbite, vague, montee, plongeon
            int tirage = boss.getRandom().nextInt(100);
            int i = 0;
            while (i < 3 && (tirage -= poids[i]) >= 0) {
                i++;
            }
            mode = Mode.values()[i];
            duree = 100 + boss.getRandom().nextInt(60);
            sens = boss.getRandom().nextBoolean() ? 1 : -1;
            etape = 0;
            contact = false;
            LOG.info("[Seraphin] deplacement : {}", mode);
        }

        /** Se dirige vers `point` : acceleration proportionnelle a la distance, vitesse plafonnee a `vmax`. */
        private void allerVers(Vec3 point, double vmax) {
            Vec3 v = boss.getDeltaMovement().scale(0.85).add(point.subtract(boss.position()).scale(0.02));
            boss.setDeltaMovement(v.length() > vmax ? v.normalize().scale(vmax) : v);
        }

        @Override
        public void tick() {
            LivingEntity cible = boss.getTarget();
            if (cible == null) {
                return;
            }
            double vol = boss.getAttributeValue(Attributes.FLYING_SPEED);
            if (boss.aPortee()) {                                   // apres le tir : il descend a hauteur du joueur, a 3 blocs, pour qu'on le frappe a l'epee
                Vec3 loin = boss.position().subtract(cible.position());
                Vec3 dir = new Vec3(loin.x, 0, loin.z);
                dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
                allerVers(cible.position().add(dir.scale(3.2)).add(0, -0.2, 0), vol * 1.2);
                etape = 0;
            } else if (boss.oeilOuvert()) {                         // pendant la charge du rayon : il garde ses distances, au-dessus et en face
                Vec3 loin = boss.position().subtract(cible.position());
                Vec3 dir = new Vec3(loin.x, 0, loin.z);
                dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
                allerVers(cible.position().add(dir.scale(9)).add(0, 4.5, 0), vol * 0.6);
                etape = 0;
            } else {
                if (--duree <= 0) {
                    choisirMode();
                }
                switch (mode) {
                    case ORBITE -> {
                        angle += 0.02 * sens;
                        allerVers(cible.position().add(Math.cos(angle) * 12, 9, Math.sin(angle) * 12), vol);
                    }
                    case VAGUE -> {                                 // le rayon et l'altitude ondulent : il ne cesse de monter, descendre et se rapprocher
                        angle += 0.045 * sens;
                        double r = 14 + 5 * Math.sin(angle * 1.5);
                        double h = 9 + 6 * Math.sin(angle * 2.3);
                        allerVers(cible.position().add(Math.cos(angle) * r, h, Math.sin(angle) * r), vol * 1.3);
                    }
                    case MONTEE -> {                                // haut dans le ciel, petit cercle : de la, les volees tombent sur les joueurs
                        angle += 0.03 * sens;
                        allerVers(cible.position().add(Math.cos(angle) * 7, 20, Math.sin(angle) * 7), vol * 1.1);
                    }
                    case PLONGEON -> {
                        etape++;
                        if (etape < 30) {                           // il prend de la hauteur au-dessus du joueur
                            allerVers(cible.position().add(0, 18, 0), vol * 1.3);
                        } else if (etape < 55) {                    // puis fonce sur lui
                            Vec3 vers = cible.getBoundingBox().getCenter().subtract(boss.position());
                            boss.setDeltaMovement(vers.normalize().scale(1.25));
                            if (!contact && vers.length() < 3.5) {
                                contact = true;
                                boss.playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.6F);
                                if (cible instanceof Player j && !j.isCreative() && !j.isSpectator()) {
                                    Vec3 dir = new Vec3(vers.x, 0, vers.z);
                                    dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
                                    j.hurt(boss.sourceDegats(), Reglages.BALAYAGE_DEGATS.get().floatValue());
                                    j.setDeltaMovement(dir.x * 1.4, 0.6, dir.z * 1.4);
                                    j.hurtMarked = true;
                                }
                            }
                        } else {
                            mode = Mode.ORBITE;                     // il repart en orbite
                            duree = 60;
                        }
                    }
                }
            }
            Vec3 vers = cible.getBoundingBox().getCenter().subtract(boss.getEyePosition());
            float lacet = (float) (Math.toDegrees(Math.atan2(vers.z, vers.x)) - 90.0);
            float tangage = (float) -Math.toDegrees(Math.atan2(vers.y, Math.sqrt(vers.x * vers.x + vers.z * vers.z)));
            boss.setYRot(lacet);
            boss.yBodyRot = lacet;
            boss.yHeadRot = lacet;
            boss.setXRot(tangage);
        }
    }

    /** Enchaine les attaques : balayage si un joueur est tout pres, sinon rayon ou volee au hasard. */
    public static class Combat extends Goal {
        private enum Etat { ATTENTE, CHARGE, TIR }

        private final Seraphin boss;
        private Etat etat = Etat.ATTENTE;
        private int delai = 60;             // premier tir apres 3 s
        private int delaiBalayage = 0;
        private int minuteur = 0;
        private Vec3 visee = Vec3.ZERO;

        public Combat(Seraphin boss) {
            this.boss = boss;
        }

        @Override
        public boolean canUse() {
            LivingEntity c = boss.getTarget();
            return c != null && c.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            boss.setOeilOuvert(false);
            boss.setAPortee(false);
            etat = Etat.ATTENTE;
            delai = 40;
        }

        private ServerLevel niveau() {
            return (ServerLevel) boss.level();
        }

        @Override
        public void tick() {
            LivingEntity cible = boss.getTarget();
            if (cible == null) {
                return;
            }
            if (delaiBalayage > 0) {
                delaiBalayage--;
            }
            switch (etat) {
                case ATTENTE -> {
                    if (delaiBalayage == 0 && !joueursProches().isEmpty()) {
                        LOG.info("[Seraphin] balayage d'ailes");
                        balayage();
                        delaiBalayage = RECHARGE_BALAYAGE;
                    }
                    if (delai > 0) {
                        delai--;
                    } else if (boss.getRandom().nextFloat() < 0.6F) {
                        etat = Etat.CHARGE;
                        minuteur = Reglages.RAYON_CHARGE.get();
                        boss.setOeilOuvert(true);
                        LOG.info("[Seraphin] rayon : charge (phase {})", boss.phase());
                        boss.playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.5F);
                    } else {
                        LOG.info("[Seraphin] volee de plumes (phase {})", boss.phase());
                        volee(cible);
                        delai = SeraphinLogique.recharge(boss.phase(), RECHARGE_BASE);
                    }
                }
                case CHARGE -> {
                    visee = cible.getBoundingBox().getCenter().subtract(boss.getEyePosition()).normalize();
                    Vec3 o = boss.getEyePosition();
                    for (int i = 1; i <= 6; i++) {                // l'avertissement : des etincelles le long de la future ligne de tir
                        Vec3 p = o.add(visee.scale(i * 2.0));
                        niveau().sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    }
                    if (--minuteur <= 0) {
                        tirer();
                        boss.setAPortee(true);
                        etat = Etat.TIR;
                        minuteur = Reglages.OEIL_OUVERT.get();
                    }
                }
                case TIR -> {
                    if (--minuteur <= 0) {
                        boss.setOeilOuvert(false);
                        boss.setAPortee(false);
                        etat = Etat.ATTENTE;
                        delai = SeraphinLogique.recharge(boss.phase(), RECHARGE_BASE);
                    }
                }
            }
        }

        private List<Player> joueursProches() {
            return boss.level().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(PORTEE_BALAYAGE),
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceTo(boss) <= PORTEE_BALAYAGE + 1.5);
        }

        private void balayage() {
            boss.playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.7F);
            for (Player p : joueursProches()) {
                Vec3 loin = p.position().subtract(boss.position());
                Vec3 dir = new Vec3(loin.x, 0, loin.z);
                dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
                p.hurt(boss.sourceDegats(), Reglages.BALAYAGE_DEGATS.get().floatValue());
                p.setDeltaMovement(dir.x * 1.6, 0.5, dir.z * 1.6);
                p.hurtMarked = true;
            }
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                niveau().sendParticles(ParticleTypes.CLOUD, boss.getX() + Math.cos(a) * 3, boss.getY() + 1.5, boss.getZ() + Math.sin(a) * 3, 1, 0, 0, 0, 0.2);
            }
        }

        private void volee(LivingEntity cible) {
            boss.playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 1.4F);
            Vec3 vers = cible.getBoundingBox().getCenter().subtract(boss.getEyePosition()).normalize();
            for (double deg : SeraphinLogique.anglesEventail(5, 60)) {
                boss.level().addFreshEntity(new Plume(boss.level(), boss, vers.yRot((float) Math.toRadians(deg))));
            }
        }

        private void tirer() {
            LOG.info("[Seraphin] rayon : tir");
            boss.playSound(SoundEvents.ENDER_DRAGON_SHOOT, 4.0F, 0.8F);
            int n = SeraphinLogique.nombreRayons(boss.phase());
            Vec3 o = boss.getEyePosition();
            for (int i = 0; i < n; i++) {
                Vec3 dir = visee.yRot((float) Math.toRadians(i == 0 ? 0 : 8));
                Vec3 fin = boss.level().clip(new ClipContext(o, o.add(dir.scale(PORTEE_RAYON)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getLocation();
                double longueur = o.distanceTo(fin);
                for (double d = 1; d <= longueur; d += 0.5) {
                    Vec3 p = o.add(dir.scale(d));
                    niveau().sendParticles(OR, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
                    niveau().sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
                }
                AABB zone = new AABB(o, fin).inflate(1.0);
                for (Player p : boss.level().getEntitiesOfClass(Player.class, zone, j -> j.isAlive() && !j.isSpectator() && !j.isCreative())) {
                    if (distanceAuSegment(p.getBoundingBox().getCenter(), o, fin) <= 1.0) {
                        p.hurt(boss.sourceDegats(), Reglages.RAYON_DEGATS.get().floatValue());
                    }
                }
            }
        }
    }
}
