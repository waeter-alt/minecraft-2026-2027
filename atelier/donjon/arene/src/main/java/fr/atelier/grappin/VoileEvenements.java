// [claude-build] Le voile en action, cote serveur : sous l'eau il garde l'air du porteur au maximum tant qu'il lui reste de la reserve, et l'use ; en surface il se recharge.
// Quand la reserve baisse, un message au dessus de la barre d'action et un son avertissent ; vide, il laisse le porteur se noyer normalement.
package fr.atelier.grappin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Grappin.MODID)
public final class VoileEvenements {
    private static final int PERIODE = 10;                         // la reserve est mise a jour tous les 10 ticks (une demi-seconde)

    private VoileEvenements() {
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post evenement) {
        Player joueur = evenement.getEntity();
        if (joueur.level().isClientSide) {
            return;
        }
        ItemStack tete = joueur.getItemBySlot(EquipmentSlot.HEAD);
        if (!(tete.getItem() instanceof VoileItem)) {
            return;
        }
        int usure = tete.getDamageValue();
        if (joueur.isEyeInFluid(FluidTags.WATER)) {
            if (usure < VoileItem.CAPACITE) {
                joueur.setAirSupply(joueur.getMaxAirSupply());     // on respire tant qu'il reste de la reserve
            }
            if (joueur.tickCount % PERIODE == 0) {
                int conso = Math.max(1, Math.round(VoileItem.CAPACITE / (Reglages.VOILE_RESERVE.get() * (20f / PERIODE))));
                tete.setDamageValue(Math.min(VoileItem.CAPACITE, usure + conso));
                alerter(joueur, tete, usure < VoileItem.CAPACITE);
            }
        } else if (usure > 0 && joueur.tickCount % PERIODE == 0) {
            int recharge = Math.max(1, Math.round(VoileItem.CAPACITE / (Reglages.VOILE_RECHARGE.get() * (20f / PERIODE))));
            tete.setDamageValue(Math.max(0, usure - recharge));
        }
    }

    private static void alerter(Player joueur, ItemStack voile, boolean etaitCharge) {
        float restant = VoileItem.secondesRestantes(voile);
        boolean chaqueSeconde = joueur.tickCount % 20 == 0;
        if (restant <= 0) {
            if (etaitCharge) {                                  // la reserve vient de s'epuiser
                joueur.displayClientMessage(Component.translatable("item.grappin.voile.epuise").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                joueur.level().playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.0F, 0.5F);
            }
        } else if (restant <= Reglages.VOILE_ALERTE.get()) {
            joueur.displayClientMessage(Component.translatable("item.grappin.voile.alerte", Math.round(restant))
                    .withStyle(restant <= 3 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
            if (chaqueSeconde) {
                joueur.level().playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F,
                        restant <= 3 ? 1.4F : 0.9F);
            }
        }
    }
}
