// [claude-build] Le harpon a chaine du Scaphandrier rouille : file tout droit (ni gravite ni freinage de l'eau) jusqu'a la portee.
// Il ne s'arrete que sur un joueur (il s'y plante et le suit : le Scaphandrier le tire a lui) ou sur un bloc (il s'y plante : le
// Scaphandrier reste coince). Il traverse les autres monstres. C'est le Scaphandrier qui le fait disparaitre quand il ramene sa chaine.
package fr.atelier.grappin;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class HarponScaphandrier extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> PLANTE = SynchedEntityData.defineId(HarponScaphandrier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TENU = SynchedEntityData.defineId(HarponScaphandrier.class, EntityDataSerializers.INT);

    private Vec3 vitesse = Vec3.ZERO;

    public HarponScaphandrier(EntityType<? extends HarponScaphandrier> type, Level level) {
        super(type, level);
    }

    public HarponScaphandrier(Level level, Scaphandrier lanceur, Vec3 depart, Vec3 direction) {
        super(Grappin.HARPON.get(), level);
        this.setOwner(lanceur);
        this.setPos(depart.x, depart.y, depart.z);
        this.vitesse = direction.scale(Reglages.SCAPH_VITESSE_HARPON.get());
        this.setDeltaMovement(vitesse);
        this.setYRot((float) Math.toDegrees(Math.atan2(vitesse.x, vitesse.z)));
        this.setXRot((float) Math.toDegrees(Math.atan2(vitesse.y, vitesse.horizontalDistance())));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PLANTE, ScaphandrierLogique.Harpon.EN_VOL.ordinal());
        builder.define(TENU, -1);
    }

    ScaphandrierLogique.Harpon plante() {
        return ScaphandrierLogique.Harpon.values()[this.entityData.get(PLANTE)];
    }

    /** Le joueur dans lequel il est plante, ou null. */
    Entity tenu() {
        int id = this.entityData.get(TENU);
        return id < 0 ? null : this.level().getEntity(id);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_INGOT;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        switch (plante()) {
            case EN_VOL -> {
                if (vitesse.lengthSqr() < 1.0E-6) {
                    vitesse = this.getDeltaMovement();              // cote client : la vitesse arrive avec l'entite
                }
                this.setDeltaMovement(vitesse);                      // l'eau ne le freine pas : il va au bout de sa portee
                super.tick();
                if (this.level().isClientSide) {
                    if (this.isInWater()) {
                        this.level().addParticle(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 0, 0.02, 0);
                    }
                    return;
                }
                Entity lanceur = getOwner();
                if (!isRemoved() && plante() == ScaphandrierLogique.Harpon.EN_VOL
                        && (lanceur == null || !lanceur.isAlive() || lanceur.distanceTo(this) > Reglages.SCAPH_PORTEE.get())) {
                    discard();
                }
            }
            case JOUEUR -> {
                this.setDeltaMovement(Vec3.ZERO);
                if (this.level().isClientSide) {
                    return;
                }
                Entity j = tenu();
                Entity lanceur = getOwner();
                if (j == null || !j.isAlive() || lanceur == null || !lanceur.isAlive()) {
                    discard();
                    return;
                }
                this.setPos(j.getX(), j.getY() + j.getBbHeight() * 0.55, j.getZ());
            }
            case BLOC -> {
                this.setDeltaMovement(Vec3.ZERO);
                Entity lanceur = getOwner();
                if (!this.level().isClientSide && (lanceur == null || !lanceur.isAlive())) {
                    discard();
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity cible) {
        return cible instanceof Player j && !j.isSpectator() && super.canHitEntity(cible);
    }

    @Override
    protected void onHitEntity(EntityHitResult touche) {
        if (this.level().isClientSide || !(touche.getEntity() instanceof Player j)) {
            return;
        }
        Entity lanceur = getOwner();
        j.hurt(this.damageSources().mobProjectile(this, lanceur instanceof Scaphandrier s ? s : null), Reglages.SCAPH_DEGATS_HARPON.get().floatValue());
        this.entityData.set(TENU, j.getId());
        this.entityData.set(PLANTE, ScaphandrierLogique.Harpon.JOUEUR.ordinal());
        this.setDeltaMovement(Vec3.ZERO);
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.2F, 0.8F);
    }

    @Override
    protected void onHitBlock(BlockHitResult touche) {
        super.onHitBlock(touche);
        if (this.level().isClientSide) {
            return;
        }
        Vec3 point = touche.getLocation();
        this.setPos(point.x, point.y, point.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(PLANTE, ScaphandrierLogique.Harpon.BLOC.ordinal());
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 1.5F, 0.6F);
    }
}
