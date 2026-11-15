// [claude-build] Les bottes lourdes du Voile (cote client) : une touche (V par defaut, modifiable dans les controles) les active ou les desactive. Actives et dans l'eau, le joueur coule et
// marche au fond ; elles ne marchent que si le Voile est sur la tete et qu'il lui reste de la reserve, et se coupent d'elles-memes des qu'il est vide ou retire. Le mouvement est calcule chez
// le joueur (comme le balancier du grappin), le serveur n'a rien a savoir.
package fr.atelier.grappin;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class BottesLourdesClient {
    private static final double POIDS = 0.12;                  // vitesse de chute minimale dans l'eau, en blocs par tick : plus forte que la poussee de la nage vers le haut

    public static final KeyMapping TOUCHE = new KeyMapping("key.grappin.bottes_lourdes", GLFW.GLFW_KEY_V, "key.categories.grappin");

    private static boolean actif = false;

    private BottesLourdesClient() {
    }

    /** Vrai si le joueur porte le voile et qu'il lui reste de la reserve. */
    private static boolean voileUtilisable(LocalPlayer joueur) {
        ItemStack tete = joueur.getItemBySlot(EquipmentSlot.HEAD);
        return tete.getItem() instanceof VoileItem && tete.getDamageValue() < VoileItem.CAPACITE;
    }

    private static void message(LocalPlayer joueur, String cle) {
        joueur.displayClientMessage(Component.translatable(cle), true);
    }

    private static void basculer(LocalPlayer joueur) {
        if (!Reglages.VOILE_BOTTES_LOURDES.get()) {
            message(joueur, "message.grappin.bottes_desactivees_config");
        } else if (actif) {
            actif = false;
            message(joueur, "message.grappin.bottes_off");
        } else if (voileUtilisable(joueur)) {
            actif = true;
            message(joueur, "message.grappin.bottes_on");
        } else {
            message(joueur, "message.grappin.bottes_impossible");              // pas de voile sur la tete, ou reserve vide
        }
    }

    @EventBusSubscriber(modid = Grappin.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Touches {
        @SubscribeEvent
        static void enregistrer(RegisterKeyMappingsEvent evenement) {
            evenement.register(TOUCHE);
        }
    }

    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Tick {
        @SubscribeEvent
        static void tick(ClientTickEvent.Pre evenement) {
            LocalPlayer joueur = Minecraft.getInstance().player;
            if (joueur == null) {
                actif = false;                                 // on a quitte le monde
                return;
            }
            if (Minecraft.getInstance().isPaused()) {
                return;
            }
            while (TOUCHE.consumeClick()) {
                basculer(joueur);
            }
            if (!actif) {
                return;
            }
            if (!voileUtilisable(joueur)) {                    // reserve vide ou voile retire : les bottes se coupent
                actif = false;
                message(joueur, "message.grappin.bottes_epuisees");
                return;
            }
            if (joueur.isInWater()) {
                Vec3 v = joueur.getDeltaMovement();
                joueur.setDeltaMovement(v.x, Math.min(v.y, -POIDS), v.z);       // on coule, et la nage vers le haut ne suffit pas a remonter
                joueur.setSwimming(false);
            }
        }
    }
}
