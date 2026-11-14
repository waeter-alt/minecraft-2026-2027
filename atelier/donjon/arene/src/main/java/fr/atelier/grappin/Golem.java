// [claude-build] Le Gardien des Racines : golem d'arbre colossal enfonce dans la lave, au centre de la salle du boss du Temple. Il est fait de plusieurs pieces de collision
// (GolemPiece), une par point faible. Une seule est ouverte a la fois ; le reste du corps ne prend presque pas de degats. Le comportement est dans GolemComportement.
package fr.atelier.grappin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

public class Golem extends Monster {
    /** Points faibles : nom, direction par rapport a l'avant du golem (degres), distance a son axe, hauteur au-dessus de la surface de la lave (blocs). */
    public static final String[] NOMS_PIECES = {"masque", "plastron", "poing", "dos_g", "dos_d"};
    public static final double[] AZIMUTS = {0, 0, -50, 150, -150};
    public static final double[] RAYONS = {6.5, 7.5, 12, 7.2, 7.2};
    public static final double[] HAUTEURS = {34, 26, 40, 30, 22};
    /** Cote de la boite de collision de chaque piece : assez grande pour qu'une fleche vise sans effort, et qui depasse du corps. */
    public static final float[] TAILLES = {5, 6, 7, 5, 5};

    private static final int DUREE_EMERGENCE = 100;
    private static final double PROFONDEUR_EMERGENCE = 40;
    private static final EntityDataAccessor<Integer> PIECE_OUVERTE = SynchedEntityData.defineId(Golem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEUXIEME_PHASE = SynchedEntityData.defineId(Golem.class, EntityDataSerializers.BOOLEAN);

    private final GolemPiece[] pieces = new GolemPiece[NOMS_PIECES.length];
    private final ServerBossEvent barre = new ServerBossEvent(Component.translatable("entity.grappin.golem"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    private double surfaceY = Double.NaN;       // hauteur de la surface de la lave, fixee a la premiere seconde ; sauvegardee
    private int emergence = 0;                  // ticks restants avant d'etre sorti de la lave (pas sauvegarde : apres un chargement il est considere sorti)
    private boolean degatsDePiece = false;

    public Golem(EntityType<? extends Golem> type, Level level) {
        super(type, level);
        for (int i = 0; i < pieces.length; i++) {
            pieces[i] = new GolemPiece(this, i);
        }
        this.setId(ENTITY_COUNTER.getAndAdd(pieces.length + 1) + 1);                    // reserve les identifiants des pieces (comme le dragon de l'Ender) : jamais partages avec une autre entite
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Reglages.GOLEM_VIE.get());
        this.setHealth(this.getMaxHealth());
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder attributs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 128.0);                    // il est au fond de la salle, 60 blocs sous la sortie : 64 ne suffisait pas
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PIECE_OUVERTE, -1);
        builder.define(DEUXIEME_PHASE, false);
    }

    /** Indice de la piece ouverte, -1 si toutes sont fermees. */
    public int pieceOuverte() {
        return this.entityData.get(PIECE_OUVERTE);
    }

    public void ouvrirPiece(int indice) {
        this.entityData.set(PIECE_OUVERTE, indice);
    }

    public void fermerPieces() {
        this.entityData.set(PIECE_OUVERTE, -1);
    }

    /** Ticks restants avant qu'il soit sorti de la lave ; 0 quand il est sorti. */
    public int emergence() {
        return emergence;
    }

    public boolean deuxiemePhase() {
        return this.entityData.get(DEUXIEME_PHASE);
    }

    /** 1 ou 2 : voir SeraphinLogique.phase. */
    public int phase() {
        return SeraphinLogique.phase(this.getHealth(), this.getMaxHealth());
    }

    /** Degats sans cause : Minecraft ne les reduit pas a zero en difficulte Paisible (ceux d'un monstre le sont). */
    public DamageSource sourceDegats() {
        return this.damageSources().magic();
    }

    /** Angle (convention du lacet de Minecraft) de la direction horizontale vers `cible`. */
    public double lacetVers(Entity cible) {
        return Math.toDegrees(Math.atan2(-(cible.getX() - this.getX()), cible.getZ() - this.getZ()));
    }

    /** Angles monde des pieces, dans la convention du lacet : lacet du golem + direction de la piece. */
    public double[] anglesPieces() {
        double[] angles = new double[NOMS_PIECES.length];
        for (int i = 0; i < angles.length; i++) {
            angles[i] = this.getYRot() + AZIMUTS[i];
        }
        return angles;
    }

    public GolemPiece piece(int indice) {
        return pieces[indice];
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new GolemComportement.Tourner(this));
        this.goalSelector.addGoal(2, new GolemComportement.Ouvrir(this));
        this.goalSelector.addGoal(3, new GolemComportement.Lancer(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));   // joueur le plus proche (la premiere cible sans ligne de vue est prise dans customServerAiStep)
    }

    // ------------------------------------------------------------------ pieces (multipart)

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return pieces;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < pieces.length; i++) {
            pieces[i].setId(id + i + 1);
        }
    }

    private void placerPieces() {
        double lacet = Math.toRadians(this.getYRot());
        for (int i = 0; i < pieces.length; i++) {
            double a = lacet + Math.toRadians(AZIMUTS[i]);
            double x = this.getX() - Math.sin(a) * RAYONS[i];
            double z = this.getZ() + Math.cos(a) * RAYONS[i];
            double y = this.getY() + HAUTEURS[i] - TAILLES[i] / 2;      // setPos pose les pieds de la boite : on retire la moitie de sa hauteur pour la centrer sur le point faible
            pieces[i].setPos(x, y, z);
            pieces[i].xo = pieces[i].xOld = x;
            pieces[i].zo = pieces[i].zOld = z;
        }
    }

    /** Appele par une piece touchee : les degats comptent pleins (facteur du point faible) si c'est la piece ouverte. */
    public boolean blesserParPiece(DamageSource source, float montant, int indice) {
        if (indice != pieceOuverte() || emergence > 0) {
            return false;
        }
        degatsDePiece = true;
        try {
            return this.hurt(source, montant * Reglages.GOLEM_FACTEUR_POINT.get().floatValue());
        } finally {
            degatsDePiece = false;
        }
    }

    // ------------------------------------------------------------------ degats

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            return true;                                        // il vit dans la lave : ni brulure ni etouffement (pendant l'emergence, sa tete est dans la roche)
        }
        return (emergence > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;                                           // la lave ne le pousse pas
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        if (degatsDePiece || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, montant);                 // piece ouverte, ou /kill : pas de reduction
        }
        Entity projectile = source.getDirectEntity();
        if (projectile != null && this.level() instanceof net.minecraft.server.level.ServerLevel niveau) {     // on entend et on voit que le coup a touche le bois, pas un point faible
            niveau.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
            this.level().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), net.minecraft.sounds.SoundEvents.WOOD_HIT, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        return super.hurt(source, (float) (montant * GolemLogique.facteurDegats(false, 1.0, Reglages.GOLEM_FACTEUR_CORPS.get())));
    }

    @Override
    public boolean canAttack(LivingEntity cible) {
        return cible.canBeSeenAsEnemy();                       // il combat a toutes les difficultes, Paisible comprise
    }

    /** Un monstre invoque par /summon perd son drapeau « persistant » (Mob le relit depuis ses donnees) : sans cela il disparait tout seul, inactif ou loin des joueurs. */
    @Override
    public boolean removeWhenFarAway(double distanceCarree) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplicateur, DamageSource source) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(16.0);
    }

    // ------------------------------------------------------------------ vie, emergence, barre de boss

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            if (Double.isNaN(surfaceY)) {                       // premiere seconde : il part sous la lave et en emerge
                surfaceY = this.getY();
                emergence = DUREE_EMERGENCE;
            }
            if (emergence > 0) {
                emergence--;
                this.setPos(this.getX(), surfaceY - PROFONDEUR_EMERGENCE * emergence / DUREE_EMERGENCE, this.getZ());
            } else if (Math.abs(this.getY() - surfaceY) > 1.0E-3) {
                this.setPos(this.getX(), surfaceY, this.getZ());   // apres un chargement : il est directement sorti
            }
        }
        placerPieces();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.barre.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 40 == 0) {                                       // sans ligne de vue : premiere cible, puis, avec plusieurs joueurs, passage sur un joueur nettement plus proche
            Player proche = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 128.0, e -> e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator());
            if (proche != null && (this.getTarget() == null || (proche != this.getTarget() && proche.distanceToSqr(this) < this.getTarget().distanceToSqr(this) * 0.64))) {
                this.setTarget(proche);
            }
        }
        if (phase() == 2 && !deuxiemePhase()) {
            this.entityData.set(DEUXIEME_PHASE, true);
            this.barre.setColor(BossEvent.BossBarColor.RED);
            if (this.tickCount > 20 && this.level() instanceof net.minecraft.server.level.ServerLevel niveau) {     // annonce, sauf au chargement d'un boss deja blesse
                this.playSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.6F);
                for (ServerPlayer j : niveau.getPlayers(p -> p.distanceToSqr(this) < 80 * 80)) {
                    j.displayClientMessage(Component.translatable("entity.grappin.golem.phase2"), true);
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

    /** Fonctions executees a la mort du boss : un monde (datapack) les declare dans le tag grappin:golem_mort, comme le temple pour ouvrir sa sortie. */
    private static final net.minecraft.resources.ResourceLocation TAG_MORT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "golem_mort");

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.barre.setProgress(0.0F);
        if (this.dead && this.level() instanceof net.minecraft.server.level.ServerLevel niveau) {      // une mort annulee par un autre mod n'ouvre pas la sortie
            var serveur = niveau.getServer();
            var origine = serveur.createCommandSourceStack().withLevel(niveau).withPosition(this.position()).withSuppressedOutput();
            for (var fonction : serveur.getFunctions().getTag(TAG_MORT)) {
                serveur.getFunctions().execute(fonction, origine);
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason raison) {
        this.barre.removeAllPlayers();                         // jamais de barre fantome quand le boss meurt ou est supprime
        super.remove(raison);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!Double.isNaN(surfaceY)) {
            tag.putDouble("SurfaceY", surfaceY);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SurfaceY")) {
            surfaceY = tag.getDouble("SurfaceY");
        }
    }
}
