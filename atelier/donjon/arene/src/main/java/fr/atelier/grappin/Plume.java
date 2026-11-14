// [claude-build] Une plume du Seraphin : projectile leger sans gravite, qui blesse les joueurs (jamais le Seraphin) et disparait au premier bloc ou apres 5 s.
// Pas de modele : une traînee de particules suffit (voir getTrailParticle).
package fr.atelier.grappin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class Plume extends AbstractHurtingProjectile {
    private static final net.minecraft.core.particles.DustParticleOptions OR = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.85F, 0.3F), 1.6F);
    private static final double VITESSE = 1.2;
    private static final int DUREE_MAX = 100;

    public Plume(EntityType<? extends Plume> type, Level level) {
        super(type, level);
    }

    public Plume(Level level, Seraphin tireur, Vec3 direction) {
        super(Grappin.PLUME.get(), tireur, direction.normalize().scale(VITESSE), level);
        this.setPos(tireur.getX(), tireur.getEyeY(), tireur.getZ());
        this.accelerationPower = 0;                                  // sans cela le constructeur vanilla ignore la vitesse et la plume accelere a chaque tick
        this.setDeltaMovement(direction.normalize().scale(VITESSE));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > DUREE_MAX) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity cible) {
        return !(cible instanceof Seraphin) && !(cible instanceof Plume) && super.canHitEntity(cible);
    }

    @Override
    protected void onHitEntity(EntityHitResult resultat) {
        super.onHitEntity(resultat);
        if (!this.level().isClientSide && resultat.getEntity() instanceof Player joueur) {
            joueur.hurt(this.getOwner() instanceof Seraphin s ? s.sourceDegats() : this.damageSources().magic(),
                    Reglages.PLUME_DEGATS.get().floatValue());
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult resultat) {
        super.onHitBlock(resultat);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return OR;
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
