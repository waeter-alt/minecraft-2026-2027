// [claude-build] Le Rodeur des Racines, mini-boss du sous-sol 1 du Donjon Suspendu : bete de racines a quatre pattes (modele et animations :
// atelier/modeles/rodeur/rodeur.bbmodel). Il chasse et mord ; pret et a portee, il gratte le sol puis charge en ligne droite ; s'il percute
// un mur ou un pilier, il reste sonne et prend des degats doubles (regles : RodeurLogique). Valeurs reglables dans Reglages (rodeur*).
// Dans le donjon il apparait avec l'etiquette suspendu_ss1m : sa disparition declenche le coffre du grappin (fonctions du datapack).
package fr.atelier.grappin;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Rodeur extends Monster {
    private static final EntityDataAccessor<Integer> ETAT = SynchedEntityData.defineId(Rodeur.class, EntityDataSerializers.INT);
    static final byte EV_MORSURE = 90;
    static final byte EV_REVEIL = 91;

    // Animations, cote client (jouees par RodeurModele)
    final AnimationState repos = new AnimationState();
    final AnimationState morsure = new AnimationState();
    final AnimationState grattage = new AnimationState();
    final AnimationState charge = new AnimationState();
    final AnimationState sonne = new AnimationState();
    final AnimationState reveil = new AnimationState();
    final AnimationState mort = new AnimationState();

    // Combat, cote serveur (jamais sauvegarde : au chargement il repart en chasse)
    private int ticksDansEtat = 0;
    private int ticksDepuisCharge = 0;
    private double distanceParcourue = 0;
    private Vec3 directionCharge = Vec3.ZERO;
    private boolean eveille = false;            // sauvegarde : le reveil ne se joue qu'une fois

    public Rodeur(EntityType<? extends Rodeur> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Reglages.RODEUR_VIE.get());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Reglages.RODEUR_MORSURE.get());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Reglages.RODEUR_VITESSE.get());
        this.setHealth(this.getMaxHealth());
    }

    public static AttributeSupplier.Builder attributs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ETAT, RodeurLogique.Etat.CHASSE.ordinal());
    }

    RodeurLogique.Etat etat() {
        return RodeurLogique.Etat.values()[this.entityData.get(ETAT)];
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return etat() == RodeurLogique.Etat.CHASSE && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return etat() == RodeurLogique.Etat.CHASSE && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ combat (serveur)

    private RodeurLogique.Parametres parametres() {
        return new RodeurLogique.Parametres(Reglages.RODEUR_GRATTAGE.get(), Reglages.RODEUR_SONNE.get(), Reglages.RODEUR_DELAI_CHARGE.get(),
                Reglages.RODEUR_PORTEE_CHARGE.get());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!eveille) {
            eveille = true;
            this.level().broadcastEntityEvent(this, EV_REVEIL);
            this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 1.4F);
        }
        ticksDansEtat++;
        ticksDepuisCharge++;
        LivingEntity cible = this.getTarget();
        boolean cibleValide = cible != null && cible.isAlive() && !(cible instanceof Player p && (p.isCreative() || p.isSpectator()));
        RodeurLogique.Parametres p = parametres();
        double d = cibleValide ? this.distanceTo(cible) : 1e9;
        boolean aPortee = cibleValide && d >= 5 && d <= p.portee() * 0.8 && this.hasLineOfSight(cible);
        boolean bloque = false;
        boolean touche = false;
        RodeurLogique.Etat etat = etat();
        switch (etat) {
            case GRATTE, SONNE -> {
                this.getNavigation().stop();
                this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
                if (etat == RodeurLogique.Etat.GRATTE && cibleValide) {
                    this.getLookControl().setLookAt(cible, 30, 30);
                    this.setYRot((float) Math.toDegrees(Math.atan2(-(cible.getX() - getX()), cible.getZ() - getZ())));
                    this.yBodyRot = this.getYRot();
                }
            }
            case CHARGE -> {
                double v = Reglages.RODEUR_VITESSE_CHARGE.get();
                this.setDeltaMovement(directionCharge.x * v, this.getDeltaMovement().y, directionCharge.z * v);
                distanceParcourue += v;
                bloque = ticksDansEtat > 2 && this.horizontalCollision;
                List<Player> touches = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.5),
                        j -> j.isAlive() && !j.isSpectator() && !j.isCreative());
                for (Player j : touches) {
                    touche = true;
                    j.hurt(this.damageSources().mobAttack(this), Reglages.RODEUR_MORSURE.get().floatValue());
                    j.knockback(1.5, -directionCharge.x, -directionCharge.z);
                }
            }
            default -> {
            }
        }
        RodeurLogique.Etat suivant = RodeurLogique.suivant(etat, ticksDansEtat, cibleValide, aPortee, bloque, touche, distanceParcourue,
                ticksDepuisCharge, p);
        if (suivant != etat) {
            changer(suivant, cible);
        }
    }

    private void changer(RodeurLogique.Etat nouvel, LivingEntity cible) {
        this.entityData.set(ETAT, nouvel.ordinal());
        ticksDansEtat = 0;
        switch (nouvel) {
            case GRATTE -> {
                this.getNavigation().stop();
                this.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 1.6F);
            }
            case CHARGE -> {
                Vec3 vers = cible == null ? Vec3.directionFromRotation(0, this.getYRot()) : cible.position().subtract(this.position());
                directionCharge = new Vec3(vers.x, 0, vers.z).normalize();       // ligne droite figee au depart : on l'esquive en s'ecartant
                distanceParcourue = 0;
                ticksDepuisCharge = 0;
                this.playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.6F);
            }
            case SONNE -> {
                this.setDeltaMovement(Vec3.ZERO);
                this.playSound(SoundEvents.RAVAGER_STUNNED, 2.0F, 1.0F);
            }
            case CHASSE -> this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        return super.hurt(source, RodeurLogique.degats(etat(), montant, Reglages.RODEUR_FACTEUR_SONNE.get()));
    }

    @Override
    public boolean doHurtTarget(Entity cible) {
        boolean touche = super.doHurtTarget(cible);
        if (touche) {
            this.level().broadcastEntityEvent(this, EV_MORSURE);
            this.playSound(SoundEvents.ZOGLIN_ATTACK, 1.5F, 0.8F);
        }
        return touche;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Eveille", eveille);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        eveille = tag.getBoolean("Eveille");
    }

    // ------------------------------------------------------------------ animations (client)

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            RodeurLogique.Etat e = etat();
            boolean vivant = this.deathTime == 0;
            repos.animateWhen(vivant && e == RodeurLogique.Etat.CHASSE, this.tickCount);
            grattage.animateWhen(vivant && e == RodeurLogique.Etat.GRATTE, this.tickCount);
            charge.animateWhen(vivant && e == RodeurLogique.Etat.CHARGE, this.tickCount);
            sonne.animateWhen(vivant && e == RodeurLogique.Etat.SONNE, this.tickCount);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EV_MORSURE -> morsure.start(this.tickCount);
            case EV_REVEIL -> reveil.start(this.tickCount);
            case 3 -> {
                mort.start(this.tickCount);                     // mort (envoye par LivingEntity.die)
                super.handleEntityEvent(id);
            }
            default -> super.handleEntityEvent(id);
        }
    }
}
