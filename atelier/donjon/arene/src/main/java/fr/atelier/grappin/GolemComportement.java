// [claude-build] Le comportement du Gardien des Racines : il se tourne lentement vers le joueur le plus proche (Tourner), ouvre un point faible du cote oppose (Ouvrir)
// et lance des graines depuis son poing (Lancer). L'etat de combat n'est pas sauvegarde : apres un chargement, toutes les pieces sont fermees.
package fr.atelier.grappin;

import java.util.EnumSet;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GolemComportement {
    private GolemComportement() {
    }

    private static final DustParticleOptions ROUGE = new DustParticleOptions(new Vector3f(0.9F, 0.1F, 0.1F), 2.5F);
    private static final DustParticleOptions GROS_ROUGE = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 5.0F);
    private static final int PAUSE_FERMEE = 40;

    /** Fait pivoter le golem vers sa cible (le joueur le plus proche), d'un demi-tour par `golemDemiTour` ticks. */
    public static class Tourner extends Goal {
        private final Golem golem;

        public Tourner(Golem golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity c = golem.getTarget();
            return c != null && c.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity cible = golem.getTarget();
            if (cible == null || golem.emergence() > 0) {
                return;
            }
            double pas = GolemLogique.vitesseRotation(golem.pieceOuverte() >= 0, 180.0 / Reglages.GOLEM_DEMI_TOUR.get());
            float lacet = (float) GolemLogique.tournerVers(golem.getYRot(), golem.lacetVers(cible), pas);
            golem.setYRot(lacet);
            golem.yBodyRot = lacet;
            golem.yHeadRot = lacet;
        }
    }

    /** Ouvre un point faible a la fois, du cote oppose au joueur ; il ne change pas de cote avant de se refermer. */
    public static class Ouvrir extends Goal {
        private final Golem golem;
        private int derniere = -1;
        private int pause = PAUSE_FERMEE;
        private int ouvert = 0;

        public Ouvrir(Golem golem) {
            this.golem = golem;
        }

        @Override
        public boolean canUse() {
            LivingEntity c = golem.getTarget();
            return c != null && c.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        /** Un faisceau d'etincelles monte de la piece ouverte, visible de loin, et des particules rouges l'entourent. */
        private void signaler(int i) {
            Vec3 p = golem.piece(i).position().add(0, Golem.TAILLES[i] / 2, 0);
            ServerLevel niveau = (ServerLevel) golem.level();
            for (net.minecraft.server.level.ServerPlayer j : niveau.players()) {               // forcees : visibles de toute la salle, meme au-dela de la distance normale des particules
                niveau.sendParticles(j, GROS_ROUGE, true, p.x, p.y, p.z, 14, 1.6, 1.6, 1.6, 0);
                for (int k = 0; k < 24; k++) {                                                  // un grand pilier lumineux, rouge et blanc, qui monte de la piece
                    niveau.sendParticles(j, GROS_ROUGE, true, p.x, p.y + 1.5 + k * 1.2, p.z, 1, 0.1, 0.1, 0.1, 0);
                    niveau.sendParticles(j, ParticleTypes.END_ROD, true, p.x, p.y + 1.5 + k * 1.2, p.z, 2, 0.2, 0.2, 0.2, 0);
                }
            }
        }

        @Override
        public void stop() {
            golem.fermerPieces();
            pause = PAUSE_FERMEE;
        }

        @Override
        public void tick() {
            LivingEntity cible = golem.getTarget();
            if (cible == null || golem.emergence() > 0) {
                return;
            }
            if (golem.pieceOuverte() >= 0) {
                if (ouvert % 3 == 0) {
                    signaler(golem.pieceOuverte());
                }
                if (--ouvert <= 0) {
                    golem.fermerPieces();
                    pause = PAUSE_FERMEE;
                }
                return;
            }
            if (pause > 0) {
                pause--;
                return;
            }
            int i = GolemLogique.choisirPiece(golem.anglesPieces(), golem.lacetVers(cible), derniere, n -> golem.getRandom().nextInt(n));
            if (i < 0) {
                return;
            }
            golem.ouvrirPiece(i);
            derniere = i;
            ouvert = GolemLogique.dureeOuverture(golem.phase(), Reglages.GOLEM_OUVERTURE.get());
            golem.playSound(SoundEvents.BEACON_ACTIVATE, 4.0F, 0.6F);
            for (net.minecraft.server.level.ServerPlayer j : ((ServerLevel) golem.level()).getPlayers(p -> p.distanceToSqr(golem) < 90 * 90)) {
                j.displayClientMessage(net.minecraft.network.chat.Component.translatable("entity.grappin.golem.point_faible", net.minecraft.network.chat.Component.translatable("entity.grappin.golem.piece." + Golem.NOMS_PIECES[i])), true);
            }
            signaler(i);
        }
    }

    /** Lance une graine explosive depuis le poing, une en phase 1, deux en eventail en phase 2. */
    public static class Lancer extends Goal {
        private final Golem golem;
        private int delai = 80;

        public Lancer(Golem golem) {
            this.golem = golem;
        }

        @Override
        public boolean canUse() {
            LivingEntity c = golem.getTarget();
            return c != null && c.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            delai = 60;
        }

        @Override
        public void tick() {
            LivingEntity cible = golem.getTarget();
            if (cible == null || golem.emergence() > 0) {
                return;
            }
            if (--delai > 0) {
                return;
            }
            Vec3 poing = golem.piece(2).position().add(0, 1.5, 0);
            Vec3 vers = cible.getBoundingBox().getCenter().subtract(poing).normalize();
            double[] angles = golem.phase() == 2 ? new double[] {-12, 12} : new double[] {0};
            golem.playSound(SoundEvents.RAVAGER_ATTACK, 5.0F, 0.5F);
            for (double deg : angles) {
                golem.level().addFreshEntity(new Graine(golem.level(), golem, poing, vers.yRot((float) Math.toRadians(deg))));
            }
            delai = GolemLogique.cadenceGraines(golem.phase(), Reglages.GRAINE_CADENCE.get());
        }
    }
}
