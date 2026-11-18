// [claude-build] Une graine du Gardien des Racines : projectile a vitesse constante qui explose au premier contact ou apres 5 s, avec des degats de zone aux joueurs
// (aucun bloc detruit). Pas de modele : une traînee de particules rouges.
package fr.atelier.grappin;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class Graine extends AbstractHurtingProjectile {
    private static final DustParticleOptions ROUGE = new DustParticleOptions(new Vector3f(0.85F, 0.15F, 0.1F), 2.0F);
    private static final double VITESSE = 0.9;
    private static final int DUREE_MAX = 100;

    public Graine(EntityType<? extends Graine> type, Level level) {
        super(type, level);
    }

    public Graine(Level level, Golem tireur, Vec3 depart, Vec3 direction) {
        super(Grappin.GRAINE.get(), tireur, direction.normalize().scale(VITESSE), level);
        this.setPos(depart.x, depart.y, depart.z);
        this.accelerationPower = 0;                                  // sans cela le constructeur vanilla ignore la vitesse et la graine accelere a chaque tick
        this.setDeltaMovement(direction.normalize().scale(VITESSE));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > DUREE_MAX) {
            explose();
        }
    }

    @Override
    protected boolean canHitEntity(Entity cible) {
        return !(cible instanceof Golem) && !(cible instanceof GolemPiece) && !(cible instanceof Graine) && super.canHitEntity(cible);
    }

    @Override
    protected void onHitEntity(EntityHitResult resultat) {
        super.onHitEntity(resultat);
        explose();
    }

    @Override
    protected void onHitBlock(BlockHitResult resultat) {
        super.onHitBlock(resultat);
        explose();
    }

    private void explose() {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        double zone = Reglages.GRAINE_ZONE.get();
        if (this.level() instanceof ServerLevel niveau) {
            niveau.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 3, zone / 3, zone / 3, zone / 3, 0);
            niveau.sendParticles(ROUGE, getX(), getY(), getZ(), 30, zone / 2, zone / 2, zone / 2, 0);
        }
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 1.2F);
        for (Player joueur : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(zone),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(this) <= zone * zone)) {
            joueur.hurt(this.getOwner() instanceof Golem g ? g.sourceDegats() : this.damageSources().magic(), Reglages.GRAINE_DEGATS.get().floatValue());
        }
        this.discard();
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ROUGE;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }
}
