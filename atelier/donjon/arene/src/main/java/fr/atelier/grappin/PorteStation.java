// [claude-build] La porte de la Station (Vestiaire, Sas) : une cloison etanche de cuivre et de fonte, qui MONTE dans le linteau
// quand on l'ouvre (modele et animation : atelier/modeles/station/porte_station.bbmodel). Meme principe que la porte du Suspendu (PorteDonjon). C'est une entite immobile, sans collision :
// ce sont des blocs invisibles (barrieres) que les fonctions du donjon posent et retirent dans l'arche qui empechent de passer.
// Les fonctions la reglent par ses donnees : Largeur et Hauteur du passage (en blocs), Ouverte.
package fr.atelier.grappin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class PorteStation extends Entity {
    private static final EntityDataAccessor<Float> LARGEUR = SynchedEntityData.defineId(PorteStation.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HAUTEUR = SynchedEntityData.defineId(PorteStation.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OUVERTE = SynchedEntityData.defineId(PorteStation.class, EntityDataSerializers.BOOLEAN);

    final AnimationState ouverture = new AnimationState();         // cote client
    private boolean vue = false;                                    // cote client : la porte a deja ete dessinee une fois

    public PorteStation(EntityType<? extends PorteStation> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LARGEUR, 8.0F);
        builder.define(HAUTEUR, 8.0F);
        builder.define(OUVERTE, false);
    }

    float largeur() {
        return this.entityData.get(LARGEUR);
    }

    float hauteur() {
        return this.entityData.get(HAUTEUR);
    }

    boolean ouverte() {
        return this.entityData.get(OUVERTE);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Largeur")) {
            this.entityData.set(LARGEUR, tag.getFloat("Largeur"));
        }
        if (tag.contains("Hauteur")) {
            this.entityData.set(HAUTEUR, tag.getFloat("Hauteur"));
        }
        this.entityData.set(OUVERTE, tag.getBoolean("Ouverte"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Largeur", largeur());
        tag.putFloat("Hauteur", hauteur());
        tag.putBoolean("Ouverte", ouverte());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> donnee) {
        super.onSyncedDataUpdated(donnee);
        if (OUVERTE.equals(donnee) && this.level().isClientSide) {
            if (ouverte()) {
                ouverture.start(DonjonLogique.depart(!vue && this.tickCount < 5, this.tickCount));   // deja ouverte au chargement : dessinee ouverte
            } else {
                ouverture.stop();                                                                     // refermee (remise a zero) : d'un coup
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            vue = true;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** La porte est bien plus grande que sa boite : on la dessine tant qu'une partie de l'arche est a l'ecran. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double r = Math.max(largeur(), hauteur()) / 2 + 1;
        return new AABB(getX() - r, getY(), getZ() - r, getX() + r, getY() + hauteur() + 9, getZ() + r);
    }
}
