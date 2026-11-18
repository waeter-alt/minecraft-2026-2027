// [claude-build] Le contenu du casier de vestiaire : 27 cases, sauvegardees comme celles d'un coffre (NBT Items, ou LootTable), pour que le
// datapack du donjon puisse le poser plein : setblock ~ ~ ~ donjon:casier[facing=south]{Items:[{Slot:13b,id:"donjon:cape_lestee",count:1}]}.
// L'ouverture suit les joueurs qui regardent dedans (ContainerOpenersCounter, comme le tonneau) et se lit dans l'etat du bloc (open) ;
// cote client, on garde quelle animation de la porte joue (ouvrir / fermer) et depuis quand, comme le levier de racine.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CasierEntite extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> objets = NonNullList.withSize(CasierLogique.CASES, ItemStack.EMPTY);
    final AnimationState ouvrir = new AnimationState();
    final AnimationState fermer = new AnimationState();
    private Boolean dernier;                   // dernier etat dessine (null : jamais dessine)

    private final ContainerOpenersCounter regards = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level niveau, BlockPos pos, BlockState etat) {
            son(SoundEvents.IRON_TRAPDOOR_OPEN);
            marquer(etat, true);
        }

        @Override
        protected void onClose(Level niveau, BlockPos pos, BlockState etat) {
            son(SoundEvents.IRON_TRAPDOOR_CLOSE);
            marquer(etat, false);
        }

        @Override
        protected void openerCountChanged(Level niveau, BlockPos pos, BlockState etat, int avant, int apres) {
        }

        @Override
        protected boolean isOwnContainer(Player joueur) {
            return joueur.containerMenu instanceof ChestMenu menu && menu.getContainer() == CasierEntite.this;
        }
    };

    public CasierEntite(BlockPos pos, BlockState etat) {
        super(DonjonBlocs.CASIER_ENTITE.get(), pos, etat);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registres) {
        super.saveAdditional(nbt, registres);
        if (!trySaveLootTable(nbt)) {
            ContainerHelper.saveAllItems(nbt, objets, registres);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registres) {
        super.loadAdditional(nbt, registres);
        objets = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(nbt)) {
            ContainerHelper.loadAllItems(nbt, objets, registres);
        }
    }

    @Override
    public int getContainerSize() {
        return CasierLogique.CASES;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return objets;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nouveaux) {
        objets = nouveaux;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.donjon.casier");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventaire) {
        return ChestMenu.threeRows(id, inventaire, this);
    }

    @Override
    public void startOpen(Player joueur) {
        if (!remove && !joueur.isSpectator() && level != null) {
            regards.incrementOpeners(joueur, level, getBlockPos(), getBlockState());
        }
    }

    @Override
    public void stopOpen(Player joueur) {
        if (!remove && !joueur.isSpectator() && level != null) {
            regards.decrementOpeners(joueur, level, getBlockPos(), getBlockState());
        }
    }

    void verifierOuverture() {
        if (!remove && level != null) {
            regards.recheckOpeners(level, getBlockPos(), getBlockState());
        }
    }

    private void marquer(BlockState etat, boolean ouvert) {
        if (level != null && etat.hasProperty(CasierBloc.OPEN)) {
            level.setBlock(getBlockPos(), etat.setValue(CasierBloc.OPEN, ouvert), Block.UPDATE_ALL);
        }
    }

    private void son(SoundEvent son) {
        if (level != null) {
            BlockPos p = getBlockPos();
            level.playSound(null, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, son, SoundSource.BLOCKS, 0.6F, level.random.nextFloat() * 0.1F + 1.15F);
        }
    }

    /** Appele a chaque image par le rendu : lance l'animation de la porte quand l'etat change (ou la pose finie au premier affichage). */
    void suivre(boolean ouvert, int tick) {
        if (dernier != null && dernier == ouvert) {
            return;
        }
        int depart = DonjonLogique.depart(dernier == null, tick);
        (ouvert ? fermer : ouvrir).stop();
        (ouvert ? ouvrir : fermer).start(depart);
        dernier = ouvert;
    }
}
