// [claude-build] Le Voile des Profondeurs : un casque qui permet de respirer sous l'eau. Sa reserve s'use quand la tete est sous l'eau et se recharge toute seule
// a la surface ; a sec il ne s'epuise pas, et vide il ne casse pas (on se remet a se noyer normalement). Les coups recus ne l'usent pas.
package fr.atelier.grappin;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class VoileItem extends ArmorItem {
    /** Unites de la barre de reserve ; la duree reelle (en secondes) est reglee dans la config. */
    public static final int CAPACITE = 2000;

    public VoileItem(Holder<ArmorMaterial> materiau, Properties proprietes) {
        super(materiau, Type.HELMET, proprietes.durability(CAPACITE));
    }

    /** Les degats recus par le porteur n'usent pas le voile : sa reserve ne varie que sous l'eau et en surface. */
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

    /** Secondes de respiration qui restent, d'apres la barre de reserve. */
    public static float secondesRestantes(ItemStack pile) {
        return (CAPACITE - pile.getDamageValue()) * Reglages.VOILE_RESERVE.get() / (float) CAPACITE;
    }

    @Override
    public void appendHoverText(ItemStack pile, TooltipContext contexte, List<Component> lignes, TooltipFlag drapeau) {
        lignes.add(Component.translatable("item.grappin.voile.aide1"));
        lignes.add(Component.translatable("item.grappin.voile.aide2", Reglages.VOILE_RESERVE.get(), Reglages.VOILE_RECHARGE.get()));
        if (Reglages.VOILE_VISION_NOCTURNE.get() || Reglages.VOILE_NAGE_RAPIDE.get()) {
            lignes.add(Component.translatable("item.grappin.voile.aide3"));
        }
        lignes.add(Component.translatable("item.grappin.voile.reserve", Math.round(secondesRestantes(pile)), Reglages.VOILE_RESERVE.get()));
    }
}
