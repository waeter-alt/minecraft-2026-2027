// [claude-build] Le Scaphandrier rouille, mini-boss du Donjon de la Station : ancien ouvrier devenu une armure de plongee vivante (modele et
// animations : atelier/modeles/station/scaphandrier.bbmodel). Leste, il marche au fond de l'eau, frappe a la cle a molette et tire un
// harpon a chaine (HarponScaphandrier) : touche, le joueur est tire vers lui ; rate, le harpon se plante dans un bloc et il reste coince a
// l'arracher, avec des degats doubles (regles : ScaphandrierLogique). Valeurs reglables dans Reglages (scaphandrier*).
// Dans la Station il apparait avec les etiquettes du datapack (station_rotor) : sa disparition ouvre la suite.
package fr.atelier.grappin;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Scaphandrier extends Monster {
    private static final EntityDataAccessor<Integer> ETAT = SynchedEntityData.defineId(Scaphandrier.class, EntityDataSerializers.INT);
    static final byte EV_COUP = 92;

    // Animations, cote client (jouees par ScaphandrierModele)
    final AnimationState repos = new AnimationState();
    final AnimationState coup = new AnimationState();
    final AnimationState visee = new AnimationState();
    final AnimationState tir = new AnimationState();
    final AnimationState coince = new AnimationState();
    final AnimationState mort = new AnimationState();

    // Combat, cote serveur (jamais sauvegarde : au chargement il repart en chasse)
    private int ticksDansEtat = 0;
    private int ticksDepuisHarpon = 0;
    private HarponScaphandrier harpon;

    public Scaphandrier(EntityType<? extends Scaphandrier> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Reglages.SCAPH_VIE.get());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Reglages.SCAPH_CLE.get());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Reglages.SCAPH_VITESSE.get());
        this.setHealth(this.getMaxHealth());
    }

    public static AttributeSupplier.Builder attributs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ETAT, ScaphandrierLogique.Etat.CHASSE.ordinal());
    }

    ScaphandrierLogique.Etat etat() {
        return ScaphandrierLogique.Etat.values()[this.entityData.get(ETAT)];
    }

    @Override
    protected void registerGoals() {
        // Pas de FloatGoal : leste, il ne nage pas, il marche au fond.
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return etat() == ScaphandrierLogique.Etat.CHASSE && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return etat() == ScaphandrierLogique.Etat.CHASSE && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ leste : il coule et marche au fond
    // (il respire sous l'eau : etiquette minecraft:can_breathe_under_water, data/minecraft/tags/entity_type)

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void travel(Vec3 envie) {
        if (!this.isInWater() || !this.isEffectiveAi()) {
            super.travel(envie);
            return;
        }
        // Sous l'eau, Minecraft le ferait flotter et ramper a 0,02 : on le fait tomber comme a l'air libre et marcher a sa vitesse.
        Vec3 v = this.getDeltaMovement();
        double vy = this.onGround() ? -0.04 : Math.max(v.y - 0.06, -0.6);
        double vitesse = etat() == ScaphandrierLogique.Etat.CHASSE ? this.getSpeed() * 0.55 : 0;
        Vec3 marche = Vec3.ZERO;
        if (vitesse > 0 && envie.lengthSqr() > 1.0E-6) {
            Vec3 e = envie.normalize();
            float s = Mth.sin(this.getYRot() * Mth.DEG_TO_RAD), c = Mth.cos(this.getYRot() * Mth.DEG_TO_RAD);
            marche = new Vec3(e.x * c - e.z * s, 0, e.z * c + e.x * s).scale(vitesse);
        }
        if (this.horizontalCollision && this.onGround() && marche.lengthSqr() > 0) {
            vy = 0.42;                                                     // une marche d'un bloc : il l'enjambe
        }
        this.setDeltaMovement(marche.x, vy, marche.z);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.calculateEntityAnimation(false);
    }

    // ------------------------------------------------------------------ combat (serveur)

    private ScaphandrierLogique.Parametres parametres() {
        return new ScaphandrierLogique.Parametres(Reglages.SCAPH_DELAI_HARPON.get(), Reglages.SCAPH_VISEE.get(), Reglages.SCAPH_TRACTION.get(),
                Reglages.SCAPH_COINCE.get(), 60);
    }

    /** Bout du lance-harpon (avant-bras gauche, bras tendu), en coordonnees du monde. */
    Vec3 boutDuHarpon(float partiel) {
        float lacet = Mth.lerp(partiel, this.yBodyRotO, this.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 avant = new Vec3(-Mth.sin(lacet), 0, Mth.cos(lacet));
        Vec3 gauche = new Vec3(Mth.cos(lacet), 0, Mth.sin(lacet));
        return this.getPosition(partiel).add(0, 1.65, 0).add(avant.scale(1.05)).add(gauche.scale(0.73));
    }

    ScaphandrierLogique.Harpon etatHarpon() {
        if (harpon == null || harpon.isRemoved()) {
            return ScaphandrierLogique.Harpon.AUCUN;
        }
        return harpon.plante();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ticksDansEtat++;
        ticksDepuisHarpon++;
        LivingEntity cible = this.getTarget();
        boolean cibleValide = cible != null && cible.isAlive() && !(cible instanceof Player p && (p.isCreative() || p.isSpectator()));
        double d = cibleValide ? this.distanceTo(cible) : 1e9;
        boolean aPortee = cibleValide && d >= 4 && d <= Reglages.SCAPH_PORTEE.get() * 0.9 && this.hasLineOfSight(cible);
        ScaphandrierLogique.Etat etat = etat();
        boolean arrive = false;
        switch (etat) {
            case VISEE -> {
                this.getNavigation().stop();
                if (cibleValide) {
                    tournerVers(cible.position());
                    this.getLookControl().setLookAt(cible, 30, 30);
                }
                if (ticksDansEtat % 5 == 0) {
                    this.playSound(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.5F, 0.6F);   // cliquet : on l'entend armer
                }
            }
            case TIR -> this.getNavigation().stop();
            case TRACTION -> {
                this.getNavigation().stop();
                Entity tenu = harpon == null ? null : harpon.tenu();
                if (tenu instanceof Player j) {
                    tournerVers(j.position());
                    Vec3 vers = this.position().subtract(j.position());
                    arrive = vers.horizontalDistance() < 2.2;
                    if (!arrive) {
                        j.setDeltaMovement(vers.normalize().scale(Reglages.SCAPH_FORCE_TRACTION.get()));
                        j.hurtMarked = true;                             // la vitesse d'un joueur se decide chez lui : on la lui envoie
                    }
                }
            }
            case COINCE -> {
                this.getNavigation().stop();
                if (ticksDansEtat % 12 == 0) {
                    this.playSound(SoundEvents.CHAIN_HIT, 1.5F, 0.7F);
                }
            }
            default -> {
            }
        }
        ScaphandrierLogique.Etat suivant = ScaphandrierLogique.suivant(etat, ticksDansEtat, cibleValide, aPortee, ticksDepuisHarpon,
                etatHarpon(), arrive, parametres());
        if (suivant != etat) {
            changer(suivant, cible);
        }
    }

    private void tournerVers(Vec3 cible) {
        this.setYRot((float) Math.toDegrees(Math.atan2(-(cible.x - getX()), cible.z - getZ())));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    private void changer(ScaphandrierLogique.Etat nouvel, LivingEntity cible) {
        this.entityData.set(ETAT, nouvel.ordinal());
        ticksDansEtat = 0;
        switch (nouvel) {
            case VISEE -> this.getNavigation().stop();
            case TIR -> {
                ticksDepuisHarpon = 0;
                Vec3 depart = boutDuHarpon(1.0F);
                Vec3 vise = cible == null ? depart.add(Vec3.directionFromRotation(0, this.getYRot())) : cible.getEyePosition().add(0, -0.4, 0);
                harpon = new HarponScaphandrier(this.level(), this, depart, vise.subtract(depart).normalize());
                this.level().addFreshEntity(harpon);
                this.playSound(SoundEvents.CROSSBOW_SHOOT, 2.0F, 0.6F);
            }
            case COINCE -> this.playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
            case CHASSE -> {
                if (harpon != null) {
                    harpon.discard();                                    // il ramene sa chaine
                    harpon = null;
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        boolean coince = etat() == ScaphandrierLogique.Etat.COINCE;
        boolean blesse = super.hurt(source, ScaphandrierLogique.degats(etat(), montant, Reglages.SCAPH_FACTEUR_COINCE.get()));
        if (blesse && coince) {
            this.playSound(SoundEvents.ANVIL_PLACE, 0.8F, 1.6F);              // le laiton sonne : on sait que ca porte
        }
        return blesse;
    }

    @Override
    public boolean doHurtTarget(Entity cible) {
        boolean touche = super.doHurtTarget(cible);
        if (touche) {
            this.level().broadcastEntityEvent(this, EV_COUP);
            this.playSound(SoundEvents.ANVIL_LAND, 0.7F, 1.2F);
        }
        return touche;
    }

    @Override
    public void remove(RemovalReason raison) {
        if (harpon != null) {
            harpon.discard();
        }
        super.remove(raison);
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;                                       // 40 ticks au lieu de 20 : le temps de l'animation de mort (2 s)
        if (this.deathTime >= 40 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
            return;
        }
        if (this.level() instanceof ServerLevel monde && this.deathTime > 10 && this.deathTime % 2 == 0) {
            monde.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1.6, getZ(), 6, 0.25, 0.2, 0.25, 0.05);   // l'air du casque s'echappe
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState etat) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.8F, 0.6F);
    }

    // ------------------------------------------------------------------ animations (client)

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            ScaphandrierLogique.Etat e = etat();
            boolean vivant = this.deathTime == 0;
            repos.animateWhen(vivant && e == ScaphandrierLogique.Etat.CHASSE, this.tickCount);
            visee.animateWhen(vivant && e == ScaphandrierLogique.Etat.VISEE, this.tickCount);
            tir.animateWhen(vivant && (e == ScaphandrierLogique.Etat.TIR || e == ScaphandrierLogique.Etat.TRACTION), this.tickCount);
            coince.animateWhen(vivant && e == ScaphandrierLogique.Etat.COINCE, this.tickCount);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EV_COUP -> coup.start(this.tickCount);
            case 3 -> {
                mort.start(this.tickCount);                     // mort (envoye par LivingEntity.die)
                super.handleEntityEvent(id);
            }
            default -> super.handleEntityEvent(id);
        }
    }
}
