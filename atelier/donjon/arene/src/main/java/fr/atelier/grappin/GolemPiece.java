// [claude-build] Une piece de collision du Gardien des Racines : une boite carree (5 a 7 blocs selon la piece) posee sur un point faible de son corps, qui tourne avec lui.
// Elle ne se laisse toucher (fleches, epees) que quand le golem l'a ouverte ; les degats recus sont transmis au golem, multiplies.
package fr.atelier.grappin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

public class GolemPiece extends PartEntity<Golem> {
    private final int indice;
    private final EntityDimensions taille;

    public GolemPiece(Golem golem, int indice) {
        super(golem);
        this.indice = indice;
        this.taille = EntityDimensions.scalable(Golem.TAILLES[indice], Golem.TAILLES[indice]);
        this.refreshDimensions();
    }

    public int indice() {
        return indice;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Seule la piece ouverte arrete les fleches ; les autres les laissent passer (elles heurtent alors le corps, qui les « rebondit »). */
    @Override
    public boolean isPickable() {
        return getParent().pieceOuverte() == indice;
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        return !this.isInvulnerableTo(source) && getParent().blesserParPiece(source, montant, indice);
    }

    @Override
    public boolean is(Entity autre) {
        return this == autre || getParent() == autre;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return taille;
    }
}
