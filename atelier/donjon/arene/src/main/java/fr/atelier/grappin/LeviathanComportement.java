// [claude-build] Le comportement du Leviathan : il nage vers le joueur le plus proche (Nager), en evitant piliers et murs, et se degage s'il est coince.
// Les attaques (morsure, rayon, queue, plongee) sont dans Combat. Rien de ce qui est ici n'est sauvegarde : apres un chargement le serpent repart a zero.
package fr.atelier.grappin;

import java.util.EnumSet;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.logging.LogUtils;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class LeviathanComportement {
    private LeviathanComportement() {
    }

    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static final double VUE = 8.0;                  // distance a laquelle on regarde devant pour eviter un obstacle
    private static final double ACCELERATION = 0.06;
    private static final double DISTANCE_ORBITE = 14.0;      // hors attaque le serpent tourne autour du joueur a cette distance

    /** Distance libre dans la direction `dir` depuis `de` : VUE si rien ne barre la route, sinon la distance au bloc solide (les fluides ne comptent pas). */
    static double distanceLibre(Leviathan boss, Vec3 de, Vec3 dir) {
        HitResult r = boss.level().clip(new ClipContext(de, de.add(dir.scale(VUE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        return r.getType() == HitResult.Type.MISS ? VUE : de.distanceTo(r.getLocation());
    }

    /** Fait tourner la direction `dir` d'un angle `a` (radians) autour de l'axe vertical, puis d'un angle `b` vers le haut ou le bas. */
    static Vec3 tourner(Vec3 dir, double a, double b) {
        Vec3 d = dir.yRot((float) a);
        double y = Math.max(-0.75, Math.min(0.75, d.y + Math.sin(b)));
        double h = Math.sqrt(Math.max(1.0E-6, 1.0 - y * y));
        double n = Math.sqrt(d.x * d.x + d.z * d.z);
        return n < 1.0E-6 ? new Vec3(0, y, 0) : new Vec3(d.x / n * h, y, d.z / n * h);
    }

    /** Parmi quelques directions autour de `voulu`, la plus proche de `voulu` qui est libre ; a defaut, celle qui est libre le plus loin. */
    static Vec3 choisirDirection(Leviathan boss, Vec3 de, Vec3 voulu) {
        double[][] essais = {{0, 0}, {0.5, 0}, {-0.5, 0}, {0, 0.5}, {0, -0.5}, {1.0, 0}, {-1.0, 0}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}, {-0.5, -0.5}, {1.6, 0}, {-1.6, 0}, {2.4, 0}, {-2.4, 0}};
        Vec3 meilleure = voulu;
        double distMax = -1;
        for (double[] e : essais) {
            Vec3 d = tourner(voulu, e[0], e[1]);
            double libre = distanceLibre(boss, de, d);
            if (libre >= VUE) {
                return d;
            }
            if (libre > distMax) {
                distMax = libre;
                meilleure = d;
            }
        }
        return meilleure;
    }

    /** Nage vers le joueur le plus proche (ou vers `boss.visee` quand une attaque en impose un), a la vitesse `boss.facteurVitesse` ; la tete reste dans l'eau et se degage quand elle est coincee. */
    public static class Nager extends Goal {
        private final Leviathan boss;
        private Vec3 direction = new Vec3(1, 0, 0);
        private double vitesse = 0;
        private Vec3 reference = Vec3.ZERO;
        private int ticksReference = 0;
        private int degagement = 0;
        private Vec3 directionDegagement = Vec3.ZERO;

        public Nager(Leviathan boss) {
            this.boss = boss;
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

        @Override
        public void start() {
            direction = boss.getLookAngle();
            reference = boss.position();
            ticksReference = 0;
        }

        @Override
        public void tick() {
            LivingEntity cible = boss.getTarget();
            if (cible == null) {
                return;
            }
            Vec3 tete = boss.position().add(0, boss.getBbHeight() / 2, 0);
            Vec3 centreCible = cible.getBoundingBox().getCenter();
            Vec3 point;
            if (boss.visee != null) {
                point = boss.visee;
            } else if (boss.fonce) {
                point = centreCible;
            } else {                                                                // il tourne autour du joueur, sans le coller : un point a 14 blocs, decale sur le cote pour avancer en cercle
                Vec3 radial = tete.subtract(centreCible);
                Vec3 horizontal = new Vec3(radial.x, 0, radial.z);
                horizontal = horizontal.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : horizontal.normalize();
                Vec3 tangente = new Vec3(-horizontal.z, 0, horizontal.x);
                point = centreCible.add(horizontal.scale(DISTANCE_ORBITE)).add(tangente.scale(6.0)).add(0, Math.max(-3.0, Math.min(3.0, radial.y)), 0);
            }
            if (!boss.level().getFluidState(boss.blockPosition()).is(FluidTags.WATER)) {
                point = tete.add(0, -4, 0);                                         // la tete est sortie de l'eau : on redescend
            }
            Vec3 voulu = point.subtract(tete);
            voulu = voulu.lengthSqr() < 1.0E-4 ? direction : voulu.normalize();

            ticksReference++;
            if (ticksReference >= 40) {                                             // coince ? la tete a-t-elle avance d'au moins un bloc en 40 ticks
                if (LeviathanLogique.estCoince(boss.position().distanceTo(reference), ticksReference) && boss.facteurVitesse > 0) {
                    degagement = 30;
                    directionDegagement = tourner(direction.scale(-1), (boss.getRandom().nextDouble() - 0.5) * 3.0, (boss.getRandom().nextDouble() - 0.5));
                }
                reference = boss.position();
                ticksReference = 0;
            }
            if (degagement > 0) {
                degagement--;
                voulu = directionDegagement;
            }

            Vec3 choisie = choisirDirection(boss, tete, voulu);
            direction = direction.add(choisie.subtract(direction).scale(0.15));
            direction = direction.lengthSqr() < 1.0E-6 ? choisie : direction.normalize();

            double max = boss.getAttributeValue(Attributes.MOVEMENT_SPEED) * boss.facteurVitesse * (boss.fonce || boss.visee != null || boss.facteurVitesse == 0 ? 1.0 : 0.6);       // plus lent en orbite
            vitesse = Math.min(max, vitesse + ACCELERATION);
            if (vitesse > max) {
                vitesse = max;
            }
            boss.setDeltaMovement(direction.scale(vitesse));

            float lacet = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float tangage = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, direction.y))));
            boss.setYRot(lacet);
            boss.yBodyRot = lacet;
            boss.yHeadRot = lacet;
            boss.setXRot(tangage);
        }

        @Override
        public void stop() {
            vitesse = 0;
            boss.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.3F, 0.95F, 1.0F), 2.5F);

    /** Les attaques : morsure, rayon de l'oeil (puis perle exposee), coup de queue et, en phase 2, plongee. Une a la fois, toutes les `cadence` ticks. */
    public static class Combat extends Goal {
        private enum Etat { ATTENTE, MORSURE, CHARGE, ETOURDI, DESCENTE, REMONTEE }

        private final Leviathan boss;
        private Etat etat = Etat.ATTENTE;
        private int delai = 80;
        private int minuteur = 0;
        private boolean aMordu = false;
        private Vec3 visee = Vec3.ZERO;

        public Combat(Leviathan boss) {
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
            reinitialiser();
            delai = 40;
        }

        private void reinitialiser() {
            boss.fermerPerle();
            boss.facteurVitesse = 1.0;
            boss.visee = null;
            boss.fonce = false;
            etat = Etat.ATTENTE;
        }

        private ServerLevel niveau() {
            return (ServerLevel) boss.level();
        }

        private List<Player> joueursA(Vec3 centre, double rayon) {
            return boss.level().getEntitiesOfClass(Player.class, new AABB(centre, centre).inflate(rayon),
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.position().add(0, 0.9, 0).distanceTo(centre) <= rayon);
        }

        @Override
        public void tick() {
            LivingEntity cible = boss.getTarget();
            if (cible == null) {
                return;
            }
            switch (etat) {
                case ATTENTE -> {
                    boss.facteurVitesse = 1.0;
                    if (--delai > 0) {
                        return;
                    }
                    Vec3 queue = boss.positionSection(Leviathan.QUEUE).add(0, boss.getBbHeight() / 2, 0);
                    boolean prochaQueue = !joueursA(queue, 6.0).isEmpty();
                    LeviathanLogique.Attaque a = LeviathanLogique.choisirAttaque(boss.phase(), prochaQueue, n -> boss.getRandom().nextInt(n));
                    LOG.debug("[Leviathan] attaque : {} (phase {})", a, boss.phase());
                    switch (a) {
                        case QUEUE -> {
                            coupDeQueue(queue);
                            delai = LeviathanLogique.cadence(boss.phase(), 90);
                        }
                        case MORSURE -> {
                            etat = Etat.MORSURE;
                            boss.fonce = true;
                            minuteur = 50;
                            aMordu = false;
                            boss.facteurVitesse = 1.8;
                        }
                        case RAYON -> {
                            etat = Etat.CHARGE;
                            minuteur = Reglages.RAYON_LEVIATHAN_CHARGE.get();
                            boss.facteurVitesse = 0;
                            boss.playSound(SoundEvents.BEACON_ACTIVATE, 4.0F, 0.5F);
                        }
                        case PLONGEE -> {
                            etat = Etat.DESCENTE;
                            minuteur = 40;
                            boss.facteurVitesse = 1.3;
                            boss.visee = new Vec3(cible.getX(), boss.getY() - 25, cible.getZ());           // vers le fond, sous le joueur : pas droit dans le sol
                        }
                    }
                }
                case MORSURE -> {
                    mordre();
                    if (--minuteur <= 0) {
                        reinitialiser();
                        delai = LeviathanLogique.cadence(boss.phase(), 90);
                    }
                }
                case CHARGE -> {
                    Vec3 o = boss.position().add(0, boss.getBbHeight() / 2, 0);
                    visee = cible.getBoundingBox().getCenter().subtract(o).normalize();
                    for (int i = 1; i <= 10; i++) {                                         // l'avertissement : une ligne d'etincelles vers la cible
                        particule(o.add(visee.scale(i * 3.0)), 1);
                    }
                    if (--minuteur <= 0) {
                        tirer();
                        boss.ouvrirPerle();
                        for (ServerPlayer j : niveau().getPlayers(p -> p.distanceToSqr(boss) < 90 * 90)) {
                            j.displayClientMessage(Component.translatable("entity.grappin.leviathan.perle"), true);
                        }
                        etat = Etat.ETOURDI;
                        minuteur = Reglages.LEVIATHAN_PERLE.get();
                    }
                }
                case ETOURDI -> {
                    boss.facteurVitesse = 0;
                    if (--minuteur <= 0) {
                        reinitialiser();
                        delai = LeviathanLogique.cadence(boss.phase(), 90);
                    }
                }
                case DESCENTE -> {
                    if (--minuteur <= 0) {
                        etat = Etat.REMONTEE;
                        boss.fonce = true;
                        minuteur = 45;
                        aMordu = false;
                        boss.visee = null;
                        boss.facteurVitesse = 2.0;
                    }
                }
                case REMONTEE -> {
                    mordre();
                    if (--minuteur <= 0) {
                        reinitialiser();
                        delai = LeviathanLogique.cadence(boss.phase(), 90);
                    }
                }
            }
        }

        private void particule(Vec3 p, int n) {
            for (ServerPlayer j : niveau().players()) {                                      // forcees : visibles de toute la salle
                niveau().sendParticles(j, CYAN, true, p.x, p.y, p.z, n, 0.1, 0.1, 0.1, 0);
            }
        }

        /** Un joueur a moins de 3 blocs de la tete est mordu (une fois par morsure) et repousse. */
        private void mordre() {
            if (aMordu) {
                return;
            }
            Vec3 tete = boss.position().add(0, boss.getBbHeight() / 2, 0);
            for (Player p : joueursA(tete, 3.0)) {
                p.hurt(boss.sourceDegats(), Reglages.MORSURE_DEGATS.get().floatValue());
                Vec3 dir = p.position().subtract(boss.position()).normalize();
                p.setDeltaMovement(dir.x * 1.2, 0.4, dir.z * 1.2);
                p.hurtMarked = true;
                aMordu = true;
                boss.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 4.0F, 0.5F);
            }
        }

        private void coupDeQueue(Vec3 queue) {
            boss.playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.6F);
            for (Player p : joueursA(queue, 6.0)) {
                Vec3 dir = p.position().subtract(queue).normalize();
                p.hurt(boss.sourceDegats(), Reglages.QUEUE_DEGATS.get().floatValue());
                p.setDeltaMovement(dir.x * 1.6, 0.4, dir.z * 1.6);
                p.hurtMarked = true;
            }
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20;
                niveau().sendParticles(ParticleTypes.BUBBLE_POP, queue.x + Math.cos(a) * 3, queue.y, queue.z + Math.sin(a) * 3, 1, 0, 0, 0, 0.1);
            }
        }

        /** Le rayon : une ligne droite de 60 blocs, arretee par les blocs solides (pas par l'eau), qui blesse tout joueur a moins de 1,2 bloc de la ligne. */
        private void tirer() {
            boss.playSound(SoundEvents.ENDER_DRAGON_SHOOT, 5.0F, 0.6F);
            Vec3 o = boss.position().add(0, boss.getBbHeight() / 2, 0);
            Vec3 fin = boss.level().clip(new ClipContext(o, o.add(visee.scale(60.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getLocation();
            double longueur = o.distanceTo(fin);
            for (double d = 1; d <= longueur; d += 0.7) {
                particule(o.add(visee.scale(d)), 2);
            }
            for (Player p : boss.level().getEntitiesOfClass(Player.class, new AABB(o, fin).inflate(2.0), j -> j.isAlive() && !j.isCreative() && !j.isSpectator())) {
                if (SeraphinAttaques.distanceAuSegment(p.getBoundingBox().getCenter(), o, fin) <= 1.2) {
                    p.hurt(boss.sourceDegats(), Reglages.RAYON_LEVIATHAN_DEGATS.get().floatValue());
                }
            }
        }
    }
}
