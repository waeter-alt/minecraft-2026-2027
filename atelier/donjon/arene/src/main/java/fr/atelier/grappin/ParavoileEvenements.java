// [claude-build] La paravoile cote serveur : qui plane, la jauge d'endurance (elle se vide en vol, se recharge au sol ou dans l'eau), la chute qui ne fait pas de degats
// tant qu'on plane, et l'arret force (jauge vide, eau, lave, elytres, monture, plus de paravoile sur soi). Le mouvement lui-meme est calcule chez le joueur (ParavoileClient).
package fr.atelier.grappin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Grappin.MODID)
public final class ParavoileEvenements {
    private static final int PERIODE = 10;                         // la jauge est mise a jour tous les 10 ticks (une demi-seconde)
    private static final int GRACE = 6;                            // ticks pendant lesquels le serveur n'exige pas encore d'etre en l'air (son info de sol retarde sur celle du joueur)

    /** Les joueurs qui planent, avec leur tick de depart. */
    private static final Map<UUID, Integer> PLANEURS = new HashMap<>();
    /** Ticks consecutifs ou le joueur est « au sol » : on ne range la voile qu'apres quelques ticks, pas a la moindre effleure d'un bloc. */
    private static final Map<UUID, Integer> AU_SOL = new HashMap<>();
    private static final int TICKS_AU_SOL = 3;

    private ParavoileEvenements() {
    }

    static boolean plane(Player joueur) {
        return PLANEURS.containsKey(joueur.getUUID());
    }

    private static boolean empeche(ServerPlayer j) {
        return !j.isAlive() || j.isInWater() || j.isInLava() || j.isFallFlying() || j.isPassenger() || j.getAbilities().flying || j.isSpectator();
    }

    private static void diffuser(ServerPlayer j, boolean actif) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(j, new PlaneurPayload(j.getId(), actif));
    }

    /** Le joueur demande a deployer sa voile : accepte s'il en a une, avec de la jauge, et s'il est en l'air hors de l'eau. Sinon, refus renvoye au joueur seul. */
    static void demarrer(ServerPlayer j) {
        if (plane(j)) {
            return;
        }
        ItemStack pile = ParavoileItem.trouver(j);
        if (pile.isEmpty() || pile.getDamageValue() >= ParavoileItem.CAPACITE || empeche(j)) {
            PacketDistributor.sendToPlayer(j, new PlaneurPayload(j.getId(), false));
            return;
        }
        PLANEURS.put(j.getUUID(), j.tickCount);
        AU_SOL.remove(j.getUUID());
        j.resetFallDistance();
        diffuser(j, true);
        j.level().playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
    }

    /** Range la voile (si elle etait deployee) et previent tout le monde. */
    static void arreter(ServerPlayer j) {
        AU_SOL.remove(j.getUUID());
        if (PLANEURS.remove(j.getUUID()) == null) {
            return;
        }
        diffuser(j, false);
        j.level().playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post evenement) {
        if (!(evenement.getEntity() instanceof ServerPlayer j)) {
            return;
        }
        ItemStack pile = ParavoileItem.trouver(j);
        Integer depuis = PLANEURS.get(j.getUUID());
        if (depuis != null) {
            int auSol = j.onGround() ? AU_SOL.merge(j.getUUID(), 1, Integer::sum) : 0;
            if (auSol == 0) {
                AU_SOL.remove(j.getUUID());
            }
            boolean stop = pile.isEmpty() || empeche(j) || (auSol >= TICKS_AU_SOL && j.tickCount - depuis > GRACE) || j.tickCount < depuis;
            if (!stop) {
                j.resetFallDistance();                              // en vol, la chute ne compte pas : pas de degats a l'atterrissage
                if (j.tickCount % PERIODE == 0) {
                    int conso = Math.max(1, Math.round(ParavoileItem.CAPACITE / (Reglages.PARAVOILE_ENDURANCE.get() * (20f / PERIODE))));
                    int usure = Math.min(ParavoileItem.CAPACITE, pile.getDamageValue() + conso);
                    pile.setDamageValue(usure);
                    if (usure >= ParavoileItem.CAPACITE) {
                        j.displayClientMessage(Component.translatable("item.grappin.paravoile.epuisee").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                        stop = true;
                    } else if (ParavoileItem.secondesRestantes(pile) <= 4) {
                        j.displayClientMessage(Component.translatable("item.grappin.paravoile.alerte", Math.round(ParavoileItem.secondesRestantes(pile)))
                                .withStyle(ChatFormatting.GOLD), true);
                        j.level().playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
                    }
                }
            }
            if (stop) {
                arreter(j);
            }
        } else if (j.tickCount % PERIODE == 0 && (j.onGround() || j.isInWater())) {
            int recharge = Math.max(1, Math.round(ParavoileItem.CAPACITE / (Reglages.PARAVOILE_RECHARGE.get() * (20f / PERIODE))));
            for (ItemStack p : j.getInventory().items) {                // la jauge se recharge meme si la voile est rangee dans l'inventaire
                if (p.getItem() instanceof ParavoileItem && p.getDamageValue() > 0) {
                    p.setDamageValue(Math.max(0, p.getDamageValue() - recharge));
                }
            }
            ItemStack secondaire = j.getOffhandItem();
            if (secondaire.getItem() instanceof ParavoileItem && secondaire.getDamageValue() > 0) {
                secondaire.setDamageValue(Math.max(0, secondaire.getDamageValue() - recharge));
            }
        }
    }

    @SubscribeEvent
    static void deconnexion(PlayerEvent.PlayerLoggedOutEvent evenement) {
        PLANEURS.remove(evenement.getEntity().getUUID());
    }

    @SubscribeEvent
    static void changementDeMonde(PlayerEvent.PlayerChangedDimensionEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer j) {
            arreter(j);
        }
    }

    @SubscribeEvent
    static void reapparition(PlayerEvent.PlayerRespawnEvent evenement) {
        PLANEURS.remove(evenement.getEntity().getUUID());
    }

    @SubscribeEvent
    static void mort(LivingDeathEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer j) {
            PLANEURS.remove(j.getUUID());
        }
    }

    /** Un joueur qui entre dans le champ de vision d'un planeur le voit deja deploye. */
    @SubscribeEvent
    static void suivi(PlayerEvent.StartTracking evenement) {
        if (evenement.getTarget() instanceof ServerPlayer cible && plane(cible) && evenement.getEntity() instanceof ServerPlayer observateur) {
            PacketDistributor.sendToPlayer(observateur, new PlaneurPayload(cible.getId(), true));
        }
    }
}
