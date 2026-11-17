// [claude-build] La Cape lestee cote client : nage rapide (calculee chez le joueur, comme la paravoile), touche de lest, saut depuis le fond,
// clic gauche transforme en attaque tourbillon pendant la nage rapide, et les effets visuels (roulis de camera, corps qui tourne, sillage de bulles).
// Le serveur verifie et applique tout ce qui compte (CapeEvenements).
package fr.atelier.grappin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CapeClient {
    /** Duree de l'animation du tourbillon, en ticks. */
    static final double DUREE_TOURBILLON = 10.0;

    // V par defaut : a verifier contre les touches de sorts des mods RPG Series du modpack (reaffectable dans Options > Commandes).
    static final KeyMapping TOUCHE_LEST = new KeyMapping("key.grappin.lest", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.grappin");

    /** Debut (temps de jeu, en ticks) du dernier tourbillon de chaque entite, d'apres le serveur. */
    private static final Map<Integer, Long> TOURBILLONS = new HashMap<>();

    private CapeClient() {
    }

    static void recevoirLest(LestePayload message) {
        LocalPlayer moi = Minecraft.getInstance().player;
        if (moi != null && moi.getId() == message.idEntite()) {
            Nage.leste = message.actif();
        }
    }

    static void recevoirTourbillon(TourbillonPayload message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            TOURBILLONS.put(message.idEntite(), mc.level.getGameTime());
        }
    }

    /** Avancement 0..1 du tourbillon de cette entite, ou -1 s'il n'y en a pas en cours. Sert aussi au dessin de la cape (spirale). */
    static double tourbillon(Entity entite, double partiel) {
        Long debut = TOURBILLONS.get(entite.getId());
        if (debut == null) {
            return -1;
        }
        double p = CapeCalcul.avancement(entite.level().getGameTime() + partiel, debut, DUREE_TOURBILLON);
        if (p < 0 && entite.level().getGameTime() - debut > DUREE_TOURBILLON + 2) {
            TOURBILLONS.remove(entite.getId());
        }
        return p;
    }

    /** Nage rapide, vue de n'importe quel client : position de nage et dans l'eau. Sert au dessin et au sillage. */
    static boolean nageRapide(LivingEntity entite) {
        return entite.isSwimming() && entite.isInWater();
    }

    /** Tour complet adouci : lent au debut et a la fin, rapide au milieu. */
    private static double angleTourbillon(double p) {
        return (p - Math.sin(p * Math.PI * 2) / (Math.PI * 2)) * Math.PI * 2;
    }

    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Nage {
        static boolean leste = false;
        private static boolean active = false;
        private static double vx, vy, vz;
        private static long dernierClic = -1;

        private static boolean possible(LocalPlayer j) {
            return CapeLesteeItem.porte(j) && j.isEyeInFluidType(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value())
                    && j.input.forwardImpulse > 0 && !leste && !j.isPassenger() && !j.getAbilities().flying && !j.isSpectator()
                    && GrappinClient.Balancier.crochetAccroche(j) == null;
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Pre evenement) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer j = mc.player;
            if (j == null || mc.level == null || mc.isPaused()) {
                return;
            }

            while (TOUCHE_LEST.consumeClick()) {
                if (!CapeLesteeItem.porte(j)) {
                    continue;
                }
                if (!leste && !j.isInWater()) {
                    j.displayClientMessage(Component.translatable("message.grappin.cape_lestee.hors_eau").withStyle(ChatFormatting.GRAY), true);
                    continue;
                }
                PacketDistributor.sendToServer(new LestePayload(j.getId(), !leste));
            }

            if (leste && j.isInWater()) {
                // Sprinter dans l'eau annule la gravite du jeu (on ne coulerait plus) et met en position de nage : interdit tant qu'on est leste.
                mc.options.keySprint.setDown(false);
                j.setSprinting(false);
                if (j.onGround() && mc.options.keyJump.isDown() && mc.screen == null) {
                    Vec3 v = j.getDeltaMovement();
                    j.setDeltaMovement(v.x, Reglages.CAPE_SAUT_LESTE.get(), v.z);
                }
            }

            boolean avant = active;
            active = possible(j);
            if (active) {
                if (!avant) {
                    Vec3 v = j.getDeltaMovement();
                    vx = v.x;
                    vy = v.y;
                    vz = v.z;
                }
                nager(j, mc.options.keySprint.isDown());
                if (mc.screen == null) {
                    while (mc.options.keyAttack.consumeClick()) {
                        long maintenant = mc.level.getGameTime();
                        if (CapeCalcul.rechargee(maintenant, dernierClic, Reglages.TOURBILLON_RECHARGE.get())) {
                            dernierClic = maintenant;
                            PacketDistributor.sendToServer(new TourbillonPayload(j.getId()));
                        }
                    }
                    // Le clic garde enfonce ne doit pas non plus miner ni declencher l'attaque de Better Combat.
                    mc.options.keyAttack.setDown(false);
                }
            }

            sillage(mc);
        }

        /** Un pas de nage rapide : la vitesse glisse vers le regard (inertie facon elytres). */
        private static void nager(LocalPlayer j, boolean sprint) {
            Vec3 regard = j.getLookAngle();
            double[] voulue = CapeCalcul.vitesseVoulue(regard.x, regard.y, regard.z, sprint, Reglages.CAPE_VITESSE.get(), Reglages.CAPE_VITESSE_SPRINT.get());
            if (j.horizontalCollision) {
                vx *= 0.3;
                vz *= 0.3;
            }
            double part = Reglages.CAPE_INERTIE.get();
            vx = CapeCalcul.approcher(vx, voulue[0], part);
            vy = CapeCalcul.approcher(vy, voulue[1], part);
            vz = CapeCalcul.approcher(vz, voulue[2], part);
            j.setDeltaMovement(vx, vy, vz);
            j.setSprinting(true);
        }

        /** Sillage de bulles derriere tous les nageurs rapides portant la cape (soi compris). */
        private static void sillage(Minecraft mc) {
            for (Player p : mc.level.players()) {
                if (!nageRapide(p) || !CapeLesteeItem.porte(p)) {
                    continue;
                }
                Vec3 arriere = p.getLookAngle().scale(-0.9);
                for (int i = 0; i < 2; i++) {
                    double x = p.getX() + arriere.x + (p.getRandom().nextDouble() - 0.5) * 0.4;
                    double y = p.getY() + 0.3 + arriere.y + (p.getRandom().nextDouble() - 0.5) * 0.4;
                    double z = p.getZ() + arriere.z + (p.getRandom().nextDouble() - 0.5) * 0.4;
                    mc.level.addParticle(ParticleTypes.BUBBLE, x, y, z, arriere.x * 0.1, 0.02, arriere.z * 0.1);
                }
            }
        }

        @SubscribeEvent
        static void deconnexion(ClientPlayerNetworkEvent.LoggingOut evenement) {
            leste = false;
            active = false;
            dernierClic = -1;
            TOURBILLONS.clear();
        }
    }

    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Effets {
        private static final Set<Integer> TOURNES = new HashSet<>();

        /** Vue a la premiere personne : la camera fait un tonneau. */
        @SubscribeEvent
        static void camera(ViewportEvent.ComputeCameraAngles evenement) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
                return;
            }
            double p = tourbillon(mc.player, evenement.getPartialTick());
            if (p >= 0) {
                evenement.setRoll(evenement.getRoll() + (float) Math.toDegrees(angleTourbillon(p)));
            }
        }

        /** Vu par les autres (ou en vue a la troisieme personne) : le corps tourne autour de l'axe de nage. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        static void avant(RenderPlayerEvent.Pre evenement) {
            if (evenement.isCanceled()) {
                return;
            }
            Player joueur = evenement.getEntity();
            double partiel = evenement.getPartialTick();
            double p = tourbillon(joueur, partiel);
            if (p < 0) {
                return;
            }
            Vec3 axe = joueur.getViewVector((float) partiel);
            float milieu = joueur.getBbHeight() / 2;
            PoseStack pose = evenement.getPoseStack();
            pose.pushPose();
            pose.translate(0, milieu, 0);
            pose.mulPose(new Quaternionf().rotationAxis((float) angleTourbillon(p), (float) axe.x, (float) axe.y, (float) axe.z));
            pose.translate(0, -milieu, 0);
            TOURNES.add(joueur.getId());
        }

        @SubscribeEvent
        static void apres(RenderPlayerEvent.Post evenement) {
            if (TOURNES.remove(evenement.getEntity().getId())) {
                evenement.getPoseStack().popPose();
            }
        }
    }

    @EventBusSubscriber(modid = Grappin.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Enregistrement {
        @SubscribeEvent
        static void touches(RegisterKeyMappingsEvent evenement) {
            evenement.register(TOUCHE_LEST);
        }

        @SubscribeEvent
        static void rendu(FMLClientSetupEvent evenement) {
            evenement.enqueueWork(() -> AccessoriesRendererRegistry.registerRenderer(Grappin.CAPE_LESTEE.get(), CapeLesteeRendu::new));
        }
    }
}
