// [claude-build] La Paravoile : une voile de planeur, comme dans Breath of the Wild. Elle se tient en main (principale ou secondaire) ;
// en l'air, un appui sur ESPACE la deploie et on plane. Sa jauge d'endurance (la barre de durabilite) se vide en vol et se recharge au sol. Elle ne casse jamais.
package fr.atelier.grappin;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ParavoileItem extends Item {
    /** Unites de la jauge ; la duree reelle (en secondes) est reglee dans la config. */
    public static final int CAPACITE = 2000;

    public ParavoileItem(Properties proprietes) {
        super(proprietes.stacksTo(1).durability(CAPACITE));
    }

    /** La jauge ne varie qu'avec le vol et la recharge : rien d'autre n'use la paravoile. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack pile, int montant, @Nullable T porteur, Consumer<Item> surCasse) {
        return 0;
    }

    @Override
    public boolean isEnchantable(ItemStack pile) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack pile, ItemStack livre) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack pile, ItemStack reparation) {
        return false;
    }

    /** La paravoile que le joueur tient en main (principale d'abord, puis secondaire) ; vide s'il n'en tient pas : rangee dans l'inventaire, elle ne sert pas. */
    public static ItemStack trouver(Player joueur) {
        ItemStack principale = joueur.getItemBySlot(EquipmentSlot.MAINHAND);
        if (principale.getItem() instanceof ParavoileItem) {
            return principale;
        }
        ItemStack secondaire = joueur.getItemBySlot(EquipmentSlot.OFFHAND);
        return secondaire.getItem() instanceof ParavoileItem ? secondaire : ItemStack.EMPTY;
    }

    /** Secondes de vol qui restent, d'apres la jauge. */
    public static float secondesRestantes(ItemStack pile) {
        return (CAPACITE - pile.getDamageValue()) * Reglages.PARAVOILE_ENDURANCE.get() / (float) CAPACITE;
    }

    @Override
    public void appendHoverText(ItemStack pile, TooltipContext contexte, List<Component> lignes, TooltipFlag drapeau) {
        lignes.add(Component.translatable("item.grappin.paravoile.aide1"));
        lignes.add(Component.translatable("item.grappin.paravoile.aide2"));
        lignes.add(Component.translatable("item.grappin.paravoile.aide3", Reglages.PARAVOILE_ENDURANCE.get(), Reglages.PARAVOILE_RECHARGE.get()));
        lignes.add(Component.translatable("item.grappin.paravoile.reserve", Math.round(secondesRestantes(pile)), Reglages.PARAVOILE_ENDURANCE.get()));
    }
}
