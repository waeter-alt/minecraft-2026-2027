// [claude-build] La Cape lestee : accessoire porte dans l'emplacement « cape » d'Accessories. L'objet lui-meme ne fait rien ;
// les effets (respiration, nage, lest, tourbillon) sont dans CapeEvenements (serveur) et CapeClient (joueur).
package fr.atelier.grappin;

import java.util.List;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoryItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;

public class CapeLesteeItem extends AccessoryItem {
    public CapeLesteeItem(Properties proprietes) {
        super(proprietes);
    }

    /** Vrai si l'entite porte la cape dans un emplacement Accessories (fonctionne des deux cotes). */
    public static boolean porte(LivingEntity entite) {
        return AccessoriesCapability.getOptionally(entite)
                .map(c -> c.isEquipped(Grappin.CAPE_LESTEE.get()))
                .orElse(false);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantement) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack livre) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext contexte, List<Component> lignes, TooltipFlag drapeau) {
        lignes.add(Component.translatable("item.donjon.cape_lestee.aide1").withStyle(ChatFormatting.AQUA));
        lignes.add(Component.translatable("item.donjon.cape_lestee.aide2").withStyle(ChatFormatting.GRAY));
        lignes.add(Component.translatable("item.donjon.cape_lestee.aide3").withStyle(ChatFormatting.GRAY));
        lignes.add(Component.translatable("item.donjon.cape_lestee.aide4").withStyle(ChatFormatting.GRAY));
    }
}
