// [claude-build] Le Seraphin : boss volant du donjon n°3. Un oeil entoure d'ailes qui flotte au-dessus de ses cibles (sans gravite), avec une barre de vie de boss.
// Les attaques sont dans SeraphinAttaques, les chiffres de combat dans SeraphinLogique ; ici : attributs, etat de l'oeil, barre de vie, degats.
package fr.atelier.grappin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public class Seraphin extends Monster {
    private static final EntityDataAccessor<Boolean> OEIL_OUVERT = SynchedEntityData.defineId(Seraphin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEUXIEME_PHASE = SynchedEntityData.defineId(Seraphin.class, EntityDataSerializers.BOOLEAN);

    private boolean aPortee = false;                  // vrai apres le tir : il descend a hauteur des joueurs pour qu'on le frappe a l'epee (pas sauvegarde)

    private final ServerBossEvent barre = new ServerBossEvent(Component.translatable("entity.grappin.seraphin"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

    public Seraphin(EntityType<? extends Seraphin> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();                       // un boss ne disparait pas tout seul
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Reglages.SERAPHIN_VIE.get());
        this.setHealth(this.getMaxHealth());                 // (la sauvegarde remet la vie reelle au chargement)
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder attributs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SeraphinAttaques.Orbite(this));
        this.goalSelector.addGoal(2, new SeraphinAttaques.Combat(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));   // joueur le plus proche, sans voir ni atteindre (il vole)
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OEIL_OUVERT, false);
        builder.define(DEUXIEME_PHASE, false);
    }

    public boolean oeilOuvert() {
        return this.entityData.get(OEIL_OUVERT);
    }

    public void setOeilOuvert(boolean ouvert) {
        this.entityData.set(OEIL_OUVERT, ouvert);
    }

    public boolean aPortee() {
        return aPortee;
    }

    public void setAPortee(boolean valeur) {
        this.aPortee = valeur;
    }

    /** Vrai des que la phase 2 a commence (donnee synchronisee : le rendu s'en sert pour changer d'aspect). */
    public boolean deuxiemePhase() {
        return this.entityData.get(DEUXIEME_PHASE);
    }

    /** 1 ou 2 : voir SeraphinLogique.phase. */
    public int phase() {
        return SeraphinLogique.phase(this.getHealth(), this.getMaxHealth());
    }

    /** Dans les mondes de donjon (Paisible) un monstre n'attaque normalement pas les joueurs : le boss, lui, combat a toutes les difficultes. */
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity cible) {
        return cible.canBeSeenAsEnemy();
    }

    /** Degats sans cause : Minecraft ne les reduit pas a zero en difficulte Paisible (ceux d'un monstre le sont). */
    public DamageSource sourceDegats() {
        return this.damageSources().magic();
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, (float) (amount * SeraphinLogique.facteurDegats(oeilOuvert(), Reglages.AILES_FERMEES.get())));
    }

    /** Fonctions executees a la mort du boss : un monde (datapack) les declare dans le tag grappin:seraphin_mort, comme le donjon n°3 pour ouvrir sa sortie. */
    private static final net.minecraft.resources.ResourceLocation TAG_MORT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "seraphin_mort");

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof net.minecraft.server.level.ServerLevel niveau) {
            var serveur = niveau.getServer();
            var origine = serveur.createCommandSourceStack().withLevel(niveau).withPosition(this.position()).withSuppressedOutput();
            for (var fonction : serveur.getFunctions().getTag(TAG_MORT)) {
                serveur.getFunctions().execute(fonction, origine);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.tickCount % 40 == 0 && this.getTarget() != null) {           // avec plusieurs joueurs : il passe sur un joueur nettement plus proche que sa cible
            Player proche = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 64.0, e -> e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator());
            if (proche != null && proche != this.getTarget() && proche.distanceToSqr(this) < this.getTarget().distanceToSqr(this) * 0.64) {
                this.setTarget(proche);
            }
        }
        this.barre.setProgress(this.getHealth() / this.getMaxHealth());
        if (phase() == 2 && !deuxiemePhase()) {
            this.entityData.set(DEUXIEME_PHASE, true);
            this.barre.setColor(BossEvent.BossBarColor.RED);
            if (this.tickCount > 20 && this.level() instanceof net.minecraft.server.level.ServerLevel niveau) {      // annonce, sauf au chargement d'un boss deja blesse
                this.playSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.8F);
                niveau.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
                for (ServerPlayer j : niveau.getPlayers(p -> p.distanceToSqr(this) < 80 * 80)) {
                    j.displayClientMessage(Component.translatable("entity.grappin.seraphin.phase2"), true);
                }
            }
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer joueur) {
        super.startSeenByPlayer(joueur);
        this.barre.addPlayer(joueur);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer joueur) {
        super.stopSeenByPlayer(joueur);
        this.barre.removePlayer(joueur);
    }

    @Override
    public void remove(Entity.RemovalReason raison) {
        this.barre.removeAllPlayers();                       // jamais de barre fantome quand le boss meurt ou est supprime
        super.remove(raison);
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(4.0);          // les ailes depassent largement la boite de 3 x 3 blocs
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplicateur, DamageSource source) {
        return false;
    }
}
