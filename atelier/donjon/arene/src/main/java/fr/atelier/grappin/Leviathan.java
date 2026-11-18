// [claude-build] Le Leviathan : serpent de mer d'une trentaine de blocs, boss de la salle noyee du Donjon 2. Sa tete nage ; huit anneaux, la queue et la perle de son front sont des pieces de collision
// (LeviathanPiece) qui suivent le chemin de la tete (historique des positions de la tete, tenu par chaque cote a partir de la position de l'entite : le client peut avoir un leger retard). Seule la perle, exposee apres chaque rayon, subit de gros degats.
// Le comportement est dans LeviathanComportement.
package fr.atelier.grappin;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

public class Leviathan extends Monster {
    public static final int ANNEAUX = 8;
    public static final int QUEUE = 8;                       // indice de la queue parmi les pieces
    public static final int PERLE = 9;                       // indice de la perle
    /** Cote de la boite de chaque piece : 8 anneaux, la queue, la perle. */
    public static final float[] TAILLES = {4, 4, 4, 4, 4, 4, 4, 4, 3, 2.5F};
    /** Le corps suit le chemin de la tete : chaque section est a ESPACEMENT blocs de la precedente le long de ce chemin (points espaces de 0,5 bloc), 36 blocs en tout. */
    public static final double ESPACEMENT = 4.0;
    private static final double PAS_CHEMIN = 0.5;
    private static final int HISTORIQUE_MAX = 120;

    private static final EntityDataAccessor<Boolean> PERLE_OUVERTE = SynchedEntityData.defineId(Leviathan.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEUXIEME_PHASE = SynchedEntityData.defineId(Leviathan.class, EntityDataSerializers.BOOLEAN);

    private final LeviathanPiece[] pieces = new LeviathanPiece[ANNEAUX + 2];
    private final List<Vec3> historique = new ArrayList<>();       // positions de la tete, la plus recente en tete
    private final ServerBossEvent barre = new ServerBossEvent(Component.translatable("entity.grappin.leviathan"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean degatsDePiece = false;
    /** Reglages d'une attaque en cours : point que la tete doit viser (null = le joueur) et facteur de vitesse (0 = immobile). Pas sauvegardes. */
    public Vec3 visee = null;
    public double facteurVitesse = 1.0;
    public boolean fonce = false;                             // vrai pendant une morsure ou la remontee de la plongee : il vise alors le joueur, sinon il tourne autour de lui

    public Leviathan(EntityType<? extends Leviathan> type, Level level) {
        super(type, level);
        for (int i = 0; i < pieces.length; i++) {
            pieces[i] = new LeviathanPiece(this, i);
        }
        this.setId(ENTITY_COUNTER.getAndAdd(pieces.length + 1) + 1);                    // reserve les identifiants des pieces (comme le dragon de l'Ender)
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Reglages.LEVIATHAN_VIE.get());
        this.setHealth(this.getMaxHealth());
        this.xpReward = 0;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LeviathanComportement.Nager(this));
        this.goalSelector.addGoal(2, new LeviathanComportement.Combat(this));
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));   // la premiere cible sans ligne de vue est prise dans customServerAiStep
    }

    public static AttributeSupplier.Builder attributs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.9)
                .add(Attributes.FOLLOW_RANGE, 128.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PERLE_OUVERTE, false);
        builder.define(DEUXIEME_PHASE, false);
    }

    public boolean perleOuverte() {
        return this.entityData.get(PERLE_OUVERTE);
    }

    public void ouvrirPerle() {
        this.entityData.set(PERLE_OUVERTE, true);
    }

    public void fermerPerle() {
        this.entityData.set(PERLE_OUVERTE, false);
    }

    public boolean deuxiemePhase() {
        return this.entityData.get(DEUXIEME_PHASE);
    }

    /** 1 ou 2 : voir SeraphinLogique.phase. */
    public int phase() {
        return SeraphinLogique.phase(this.getHealth(), this.getMaxHealth());
    }

    /** Degats sans cause : Minecraft ne les reduit pas a zero en difficulte Paisible. */
    public DamageSource sourceDegats() {
        return this.damageSources().magic();
    }

    /** Position (monde, pieds) de la section `section` quand la tete est en `tete` : 0 = premier anneau, ANNEAUX = queue ; le long du chemin deja parcouru par la tete. */
    public Vec3 positionSection(int section, Vec3 tete) {
        double[][] chemin = new double[historique.size()][];
        for (int i = 0; i < chemin.length; i++) {
            Vec3 p = historique.get(i);
            chemin[i] = new double[] {p.x, p.y, p.z};
        }
        double[] r = LeviathanLogique.pointSurChemin(new double[] {tete.x, tete.y, tete.z}, chemin, ESPACEMENT * (section + 1));
        return new Vec3(r[0], r[1], r[2]);
    }

    public Vec3 positionSection(int section) {
        return positionSection(section, this.position());
    }

    /** Position de la perle, la meme que celle du modele (a 1,5 bloc devant et 3 blocs au-dessus du centre de la tete) : elle depasse du dessus de la boite de la tete, donc un coup qui la vise la touche. */
    public Vec3 positionPerle() {
        return this.position().add(0, this.getBbHeight() / 2, 0).add(this.getLookAngle().scale(1.5)).add(0, 3.0, 0);
    }

    /** Positions des sections au tick precedent et au tick courant : le rendu les interpole, sinon les anneaux sautent a chaque tick. */
    private final Vec3[] sectionsPrecedentes = new Vec3[QUEUE + 1];
    private final Vec3[] sectionsCourantes = new Vec3[QUEUE + 1];

    /** Position (monde, pieds) de la section `section` interpolee entre deux ticks. */
    public Vec3 positionSectionRendu(int section, float partialTick) {
        Vec3 a = sectionsPrecedentes[section];
        Vec3 b = sectionsCourantes[section];
        if (a == null || b == null) {
            return positionSection(section);
        }
        return a.lerp(b, partialTick);
    }

    private void mettreAJourPieces() {
        for (int i = 0; i <= QUEUE; i++) {
            Vec3 p = positionSection(i);
            sectionsPrecedentes[i] = sectionsCourantes[i] == null ? p : sectionsCourantes[i];
            sectionsCourantes[i] = p;
            pieces[i].setPos(p.x, p.y + this.getBbHeight() / 2 - TAILLES[i] / 2, p.z);
        }
        Vec3 perle = positionPerle();
        pieces[PERLE].setPos(perle.x, perle.y - TAILLES[PERLE] / 2, perle.z);
    }

    // ------------------------------------------------------------------ pieces (multipart)

    public LeviathanPiece piece(int indice) {
        return pieces[indice];
    }

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

    /** Appele par une piece touchee : la perle ouverte fait de gros degats, le reste du corps tres peu (facteur dans hurt). */
    public boolean blesserParPiece(DamageSource source, float montant, int indice) {
        if (indice == PERLE) {
            if (!perleOuverte()) {
                return false;
            }
            degatsDePiece = true;
            try {
                return this.hurt(source, montant * Reglages.LEVIATHAN_FACTEUR_PERLE.get().floatValue());
            } finally {
                degatsDePiece = false;
            }
        }
        return this.hurt(source, montant);
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        if (degatsDePiece || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, montant);                 // perle ouverte, ou /kill : pas de reduction
        }
        return super.hurt(source, (float) (montant * GolemLogique.facteurDegats(false, 1.0, Reglages.LEVIATHAN_FACTEUR_CORPS.get())));
    }

    // ------------------------------------------------------------------ monstre aquatique, persistant, qui combat a toutes les difficultes

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)) {
            return true;                                         // il vit sous l'eau : ni noyade (canBreatheUnderwater est final), ni feu, ni etouffement
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canAttack(LivingEntity cible) {
        return cible.canBeSeenAsEnemy();                       // il combat a toutes les difficultes, Paisible comprise
    }

    /** Un monstre invoque par /summon perd son drapeau « persistant » (Mob le relit depuis ses donnees) : sans cela il disparait tout seul. */
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
        return super.getBoundingBoxForCulling().inflate(24.0);
    }

    // ------------------------------------------------------------------ mort et phase 2 : le monde (datapack) est prevenu par des tags de fonctions

    private static final net.minecraft.resources.ResourceLocation TAG_MORT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "leviathan_mort");
    private static final net.minecraft.resources.ResourceLocation TAG_PHASE2 = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "leviathan_phase2");

    /** Execute les fonctions que le monde declare dans ce tag (comme le donjon pour ouvrir sa sortie ou inonder ses cloches). */
    private void appelerTag(net.minecraft.resources.ResourceLocation tag) {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel niveau) {
            var serveur = niveau.getServer();
            var origine = serveur.createCommandSourceStack().withLevel(niveau).withPosition(this.position()).withSuppressedOutput();
            for (var fonction : serveur.getFunctions().getTag(tag)) {
                serveur.getFunctions().execute(fonction, origine);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        boolean dejaMort = this.dead;
        super.die(source);
        this.barre.setProgress(0.0F);
        if (!dejaMort && this.dead) {                            // une seule fois ; une mort annulee par un autre mod n'ouvre pas la sortie
            appelerTag(TAG_MORT);
        }
    }

    // ------------------------------------------------------------------ vie, historique, barre de boss

    /** Ajoute au chemin des points espaces de PAS_CHEMIN entre la derniere position retenue et la position actuelle de la tete. */
    private void enregistrerChemin() {
        Vec3 ici = this.position();
        if (historique.isEmpty()) {
            historique.add(ici);
            return;
        }
        Vec3 dernier = historique.get(0);
        double d = ici.distanceTo(dernier);
        if (d > ESPACEMENT * (ANNEAUX + 1) + 1) {                // teleportation : on repart de la position actuelle, le corps se redeploie
            historique.clear();
            historique.add(ici);
            return;
        }
        while (d >= PAS_CHEMIN) {
            dernier = dernier.add(ici.subtract(dernier).normalize().scale(PAS_CHEMIN));
            historique.add(0, dernier);
            d = ici.distanceTo(dernier);
        }
        while (historique.size() > HISTORIQUE_MAX) {
            historique.remove(historique.size() - 1);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        enregistrerChemin();                                     // le chemin de la tete : meme calcul des deux cotes, les pieces le suivent
        mettreAJourPieces();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.barre.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 40 == 0) {                          // premiere cible, puis passage sur un joueur nettement plus proche
            Player proche = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 128.0, e -> e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator());
            if (proche != null && (this.getTarget() == null || (proche != this.getTarget() && proche.distanceToSqr(this) < this.getTarget().distanceToSqr(this) * 0.64))) {
                this.setTarget(proche);
            }
        }
        if (phase() == 2 && !deuxiemePhase()) {
            this.entityData.set(DEUXIEME_PHASE, true);
            this.barre.setColor(BossEvent.BossBarColor.RED);
            if (this.tickCount > 20 && this.level() instanceof net.minecraft.server.level.ServerLevel niveau) {     // annonce, sauf au chargement d'un boss deja blesse
                this.playSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.5F);
                for (ServerPlayer j : niveau.getPlayers(p -> p.distanceToSqr(this) < 90 * 90)) {
                    j.displayClientMessage(Component.translatable("entity.grappin.leviathan.phase2"), true);
                }
                appelerTag(TAG_PHASE2);
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
        this.barre.removeAllPlayers();                           // jamais de barre fantome quand le boss meurt ou est supprime
        super.remove(raison);
    }
}
