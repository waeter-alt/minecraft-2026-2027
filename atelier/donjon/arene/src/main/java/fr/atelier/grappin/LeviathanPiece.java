// [claude-build] Une piece de collision du Leviathan : un anneau du corps, la queue, ou la perle du front. Elle suit le serpent (voir Leviathan.positionSection) et ne sert qu'a recevoir des coups :
// aucun degat de contact. Les anneaux et la queue arretent les projectiles ; la perle n'en arrete que quand le serpent l'a exposee. Les degats recus sont transmis au serpent.
package fr.atelier.grappin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

public class LeviathanPiece extends PartEntity<Leviathan> {
    private final int indice;
    private final EntityDimensions taille;

    public LeviathanPiece(Leviathan serpent, int indice) {
        super(serpent);
        this.indice = indice;
        this.taille = EntityDimensions.scalable(Leviathan.TAILLES[indice], Leviathan.TAILLES[indice]);
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

    @Override
    public boolean isPickable() {
        return indice != Leviathan.PERLE || getParent().perleOuverte();
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
