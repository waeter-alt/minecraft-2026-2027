// [claude-build] La Cape lestee cote client : nage rapide (calculee chez le joueur, comme la paravoile), touche de lest, saut depuis le fond,
// flottaison neutre a l'arret, bond hors de l'eau, clic gauche transforme en attaque tourbillon pendant la nage rapide
// (projection hors de l'eau pres de la surface), et tous les effets (champ de vision, lames et bulles du tourbillon,
// bords d'ecran et sons de vitesse).
// Le serveur verifie et applique tout ce qui compte (CapeEvenements).
package fr.atelier.grappin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CapeClient {
    /** Duree de l'animation du tourbillon, en ticks. */
    static final double DUREE_TOURBILLON = 10.0;
    /** Elargissement du champ de vision au tourbillon : montee, puis retour en douceur (ticks) et pic (part du champ ajoutee). */
    private static final double FOV_MONTEE = 2.0;
    private static final double FOV_RETOUR_ELAN = 14.0;
    private static final double FOV_PIC_ELAN = 0.25;
    private static final double FOV_RETOUR_PROJECTION = 24.0;
    private static final double FOV_PIC_PROJECTION = 0.35;

    // V par defaut : a verifier contre les touches de sorts des mods RPG Series du modpack (reaffectable dans Options > Commandes).
    static final KeyMapping TOUCHE_LEST = new KeyMapping("key.grappin.lest", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.grappin");

    /** Debut (temps de jeu, en ticks) du dernier tourbillon de chaque entite, d'apres le serveur. */
    private static final Map<Integer, Long> TOURBILLONS = new HashMap<>();

    /** Dernier elargissement du champ de vision chez soi : debut (temps de jeu), pic et duree du retour. */
    private static double debutFov = -1000;
    private static double picFov = 0;
    private static double retourFov = FOV_RETOUR_ELAN;

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
        if (mc.level == null) {
            return;
        }
        TOURBILLONS.put(message.idEntite(), mc.level.getGameTime());
    }

    private static void elargir(Minecraft mc, double pic, double retour) {
        debutFov = mc.level.getGameTime();
        picFov = pic;
        retourFov = retour;
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

    private static void son(Minecraft mc, Entity e, SoundEvent son, float volume, float hauteur) {
        mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), son, SoundSource.PLAYERS, volume, hauteur, false);
    }

    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Nage {
        static boolean leste = false;
        private static boolean active = false;
        private static double vx, vy, vz;
        private static long dernierClic = -1;
        private static long derniereEntree = -100;
        /** Intensite lissee 0..1 des effets de vitesse (bords d'ecran, son de ruee). */
        static double intensite = 0;
        private static Ruee ruee;
        /** Projection hors de l'eau en cours : ticks restants pour atteindre la surface, et vitesse visee. */
        private static int projection = 0;
        private static double px, py, pz;
        /** Ticks restants de la trainee d'eau sous le joueur apres une projection. */
        private static int trainee = 0;

        /** Conditions communes au demarrage et a la poursuite de la nage rapide. */
        private static boolean permis(LocalPlayer j) {
            return CapeLesteeItem.porte(j) && j.input.forwardImpulse > 0 && !leste && !j.isPassenger() && !j.getAbilities().flying && !j.isSpectator()
                    && GrappinClient.Balancier.crochetAccroche(j) == null;
        }

        /** Demarrer : les yeux sous l'eau. Continuer : il suffit que le corps y soit encore, pour garder l'elan jusqu'a la sortie (bond). */
        private static boolean possible(LocalPlayer j, boolean dejaActive) {
            boolean eau = dejaActive ? j.isInWater() : j.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
            return eau && permis(j);
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
            active = possible(j, avant);
            boolean sprint = mc.options.keySprint.isDown();
            if (active) {
                if (!avant) {
                    Vec3 v = j.getDeltaMovement();
                    vx = v.x;
                    vy = v.y;
                    vz = v.z;
                    entree(mc, j);
                }
                if (projection > 0) {
                    projection--;
                    vx = CapeCalcul.approcher(vx, px, 0.6);
                    vy = CapeCalcul.approcher(vy, py, 0.6);
                    vz = CapeCalcul.approcher(vz, pz, 0.6);
                    j.setDeltaMovement(vx, vy, vz);
                } else {
                    nager(j, sprint);
                }
                if (mc.screen == null) {
                    while (mc.options.keyAttack.consumeClick()) {
                        long maintenant = mc.level.getGameTime();
                        if (CapeCalcul.rechargee(maintenant, dernierClic, Reglages.TOURBILLON_RECHARGE.get())) {
                            dernierClic = maintenant;
                            // Meme decision que le serveur (CapeEvenements.projection), qui montre la gerbe aux autres joueurs.
                            boolean projeter = CapeEvenements.projection(j);
                            PacketDistributor.sendToServer(new TourbillonPayload(j.getId()));
                            if (projeter) {
                                projeter(mc, j);
                            } else {
                                elan(mc, j);
                            }
                        }
                    }
                    // Le clic garde enfonce ne doit pas non plus miner ni declencher l'attaque de Better Combat.
                    mc.options.keyAttack.setDown(false);
                }
            } else if (avant) {
                if (!j.isInWater() && (projection > 0 || vy > 0.1)) {
                    bond(mc, j, projection > 0);
                } else {
                    son(mc, j, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.7F, 0.8F);
                }
                projection = 0;
            } else {
                flotter(mc, j);
            }

            double voulue = active ? CapeCalcul.intensite(Math.sqrt(vx * vx + vy * vy + vz * vz), Reglages.CAPE_VITESSE_SPRINT.get()) * (sprint ? 1.0 : 0.7) : 0;
            intensite += (voulue - intensite) * 0.25;
            if (intensite < 0.005) {
                intensite = 0;
            }

            sillage(mc);
            lamesDesTourbillons(mc);
            if (trainee > 0) {
                trainee--;
                for (int k = 0; k < 3; k++) {
                    double x = j.getX() + (j.getRandom().nextDouble() - 0.5) * 0.6;
                    double z = j.getZ() + (j.getRandom().nextDouble() - 0.5) * 0.6;
                    mc.level.addParticle(k == 0 ? ParticleTypes.SPLASH : ParticleTypes.FALLING_WATER, x, j.getY() - 0.2, z, 0, 0, 0);
                }
            }
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

        /** Le tourbillon pousse vers l'avant ; l'inertie de la nage ramene ensuite doucement a la vitesse normale. */
        private static void elan(Minecraft mc, LocalPlayer j) {
            elargir(mc, FOV_PIC_ELAN, FOV_RETOUR_ELAN);
            double e = Reglages.TOURBILLON_ELAN.get();
            if (e <= 0) {
                return;
            }
            Vec3 regard = j.getLookAngle();
            vx += regard.x * e;
            vy += regard.y * e;
            vz += regard.z * e;
            j.setDeltaMovement(vx, vy, vz);
        }

        /**
         * Tourbillon pres de la surface en visant vers le haut : remplace l'elan (sinon un elan tire a la verticale depasserait la projection).
         * Quelques ticks pour atteindre la surface, puis bond() rend la vitesse visee entiere : en l'air, gravite et freinage normaux du jeu.
         */
        private static void projeter(Minecraft mc, LocalPlayer j) {
            elargir(mc, FOV_PIC_PROJECTION, FOV_RETOUR_PROJECTION);
            Vec3 regard = j.getLookAngle();
            double[] p = CapeCalcul.projection(regard.x, regard.y, regard.z, Reglages.TOURBILLON_PROJECTION.get(), Reglages.TOURBILLON_PROJECTION_PORTEE.get());
            px = p[0];
            py = p[1];
            pz = p[2];
            projection = 8;
            vx = CapeCalcul.approcher(vx, px, 0.6);
            vy = CapeCalcul.approcher(vy, py, 0.6);
            vz = CapeCalcul.approcher(vz, pz, 0.6);
            j.setDeltaMovement(vx, vy, vz);
        }

        /**
         * Sortie de l'eau vers le haut : on rend au joueur la vitesse entiere (le dernier tick dans l'eau l'a freinee),
         * puis le jeu fait le reste (gravite et air normaux, objets et grappin utilisables).
         */
        private static void bond(Minecraft mc, LocalPlayer j, boolean projete) {
            if (projete) {
                j.setDeltaMovement(px, py, pz);
            } else {
                j.setDeltaMovement(vx, vy, vz);
            }
            int gerbe = projete ? 72 : 24;
            for (int i = 0; i < gerbe; i++) {
                double a = i * Math.PI * 2 / 24 + i / 24 * 0.13;
                double r = projete ? 0.5 + (i / 24) * 0.4 : 0.6;
                double haut = projete ? 0.35 + (i / 24) * 0.25 : 0.3;
                mc.level.addParticle(ParticleTypes.SPLASH, j.getX() + Math.cos(a) * r, j.getY(), j.getZ() + Math.sin(a) * r, Math.cos(a) * 0.25, haut, Math.sin(a) * 0.25);
            }
            for (int i = 0; i < (projete ? 30 : 8); i++) {
                mc.level.addParticle(ParticleTypes.BUBBLE_POP, j.getX() + (j.getRandom().nextDouble() - 0.5) * 1.5, j.getY(), j.getZ() + (j.getRandom().nextDouble() - 0.5) * 1.5, 0, 0.08, 0);
            }
            if (projete) {
                for (int i = 0; i < 10; i++) {
                    mc.level.addParticle(ParticleTypes.CLOUD, j.getX() + (j.getRandom().nextDouble() - 0.5), j.getY() + 0.2, j.getZ() + (j.getRandom().nextDouble() - 0.5), 0, 0.05, 0);
                }
                son(mc, j, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 1.0F);
                son(mc, j, SoundEvents.DOLPHIN_JUMP, 1.0F, 0.8F);
                son(mc, j, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.8F, 1.2F);
                son(mc, j, SoundEvents.GENERIC_SPLASH, 0.8F, 0.7F);
                trainee = 12;
            } else {
                son(mc, j, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.5F, 1.3F);
                son(mc, j, SoundEvents.DOLPHIN_JUMP, 0.9F, 1.0F);
            }
        }

        private static void entree(Minecraft mc, LocalPlayer j) {
            long maintenant = mc.level.getGameTime();
            if (maintenant - derniereEntree > 10) {
                son(mc, j, SoundEvents.DOLPHIN_SWIM, 0.8F, 1.1F);
                son(mc, j, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.4F, 1.4F);
            }
            derniereEntree = maintenant;
            if (ruee == null || ruee.isStopped()) {
                ruee = new Ruee();
                mc.getSoundManager().play(ruee);
            }
        }

        /**
         * Flottaison neutre : cape portee, sous l'eau, aucune touche, pas leste : on garde son niveau au lieu de couler.
         * Le jeu applique ensuite son petit reste de gravite sur la vitesse, mais le deplacement de ce tick est deja fait avec 0.
         */
        private static void flotter(Minecraft mc, LocalPlayer j) {
            if (!CapeLesteeItem.porte(j) || leste || !j.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()) || j.onGround() || j.isPassenger()
                    || j.getAbilities().flying || j.isSpectator() || GrappinClient.Balancier.crochetAccroche(j) != null) {
                return;
            }
            if (j.input.forwardImpulse != 0 || j.input.leftImpulse != 0 || j.input.jumping || j.input.shiftKeyDown) {
                return;
            }
            Vec3 v = j.getDeltaMovement();
            double y = v.y * 0.6;
            j.setDeltaMovement(v.x, Math.abs(y) < 0.02 ? 0 : y, v.z);
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

        /**
         * Lames du tourbillon : trois croissants qui tournent autour de l'axe de nage en avancant le long du corps (spirale),
         * avec bulles et eclaboussures. Les croissants de soi ne sont pas dessines en vue a la premiere personne (ils boucheraient la vue).
         */
        private static void lamesDesTourbillons(Minecraft mc) {
            boolean premiere = mc.options.getCameraType().isFirstPerson();
            for (Integer id : new ArrayList<>(TOURBILLONS.keySet())) {
                Entity e = mc.level.getEntity(id);
                if (e == null) {
                    continue;
                }
                double p = tourbillon(e, 0);
                if (p < 0) {
                    continue;
                }
                boolean croissants = !(premiere && e == mc.player);
                Vec3 axe = e.getLookAngle();
                Vec3 centre = e.position().add(0, e.getBbHeight() / 2, 0);
                Vec3 a = Math.abs(axe.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 u = axe.cross(a).normalize();
                Vec3 v = axe.cross(u).normalize();
                double base = CapeCalcul.tourComplet(p);
                Vec3 decalage = axe.scale((p - 0.5) * 1.4);
                for (int k = 0; k < 3; k++) {
                    for (int s = 0; s < 3; s++) {
                        double phi = base + k * Math.PI * 2 / 3 - s * 0.3;
                        Vec3 dir = u.scale(Math.cos(phi)).add(v.scale(Math.sin(phi)));
                        Vec3 tangente = u.scale(-Math.sin(phi)).add(v.scale(Math.cos(phi)));
                        Vec3 pt = centre.add(decalage).add(dir.scale(1.5));
                        if (s == 0 && croissants) {
                            mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 0.4, 0, 0);
                        }
                        mc.level.addParticle(ParticleTypes.BUBBLE, pt.x, pt.y, pt.z, tangente.x * 0.25 + dir.x * 0.1, tangente.y * 0.25 + dir.y * 0.1, tangente.z * 0.25 + dir.z * 0.1);
                    }
                    if (e.getRandom().nextInt(2) == 0) {
                        double phi = base + k * Math.PI * 2 / 3;
                        Vec3 pt = centre.add(u.scale(Math.cos(phi) * 1.8)).add(v.scale(Math.sin(phi) * 1.8));
                        mc.level.addParticle(ParticleTypes.SPLASH, pt.x, pt.y, pt.z, 0, 0.1, 0);
                    }
                }
            }
        }

        @SubscribeEvent
        static void deconnexion(ClientPlayerNetworkEvent.LoggingOut evenement) {
            leste = false;
            active = false;
            dernierClic = -1;
            intensite = 0;
            projection = 0;
            trainee = 0;
            debutFov = -1000;
            TOURBILLONS.clear();
        }
    }

    /** Ruee de l'eau pendant la nage rapide : son en boucle dont le volume et la hauteur suivent la vitesse, puis s'eteint en fondu. */
    private static final class Ruee extends AbstractTickableSoundInstance {
        Ruee() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = 0.0F;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            LocalPlayer j = Minecraft.getInstance().player;
            if (j == null || j.isRemoved()) {
                stop();
                return;
            }
            double i = Nage.intensite;
            float voulu = Nage.active ? (float) (0.1 + 0.5 * i) : 0.0F;
            volume += (voulu - volume) * 0.2F;
            pitch = (float) (0.55 + 0.35 * i);
            if (!Nage.active && volume < 0.01F) {
                stop();
            }
        }
    }

    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Effets {
        private static final Set<Integer> TOURNES = new HashSet<>();

        /** Le champ de vision s'elargit nettement au tourbillon (plus encore a la projection), puis revient en douceur. */
        @SubscribeEvent
        static void champDeVision(ViewportEvent.ComputeFov evenement) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || !evenement.usedConfiguredFov()) {
                return;
            }
            double t = mc.level.getGameTime() + evenement.getPartialTick() - debutFov;
            double e = CapeCalcul.aCoup(t, FOV_MONTEE, retourFov);
            if (e > 0) {
                evenement.setFOV(evenement.getFOV() * (1 + picFov * e * mc.options.fovEffectScale().get()));
            }
        }

        /** Bords d'ecran : eclair du tourbillon, et pendant la nage rapide teinte, traits de vitesse et bulles dont l'intensite suit la vitesse. */
        static void ecran(GuiGraphics g, DeltaTracker delta) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) {
                return;
            }
            int w = g.guiWidth();
            int h = g.guiHeight();
            float partiel = delta.getGameTimeDeltaPartialTick(false);
            double force = mc.options.screenEffectScale().get();
            double p = tourbillon(mc.player, partiel);
            if (p >= 0) {
                double eclair = CapeCalcul.aCoup(p * DUREE_TOURBILLON, 1.2, 6.0);
                bords(g, w, h, 0xD8F6FF, eclair * 0.65 * force, Math.max(10, h / 4));
            }
            double i = Nage.intensite * force;
            if (i > 0.01) {
                double temps = mc.level.getGameTime() + partiel;
                bords(g, w, h, 0x4FB8DC, i * 0.45, (int) Math.max(10, h / 3.5));
                traits(g, w, h, i, temps);
                bulles(g, w, h, i, temps);
            }
        }

        private static int couleur(double alpha, int rgb) {
            return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
        }

        private static void bords(GuiGraphics g, int w, int h, int rgb, double alpha, int epaisseur) {
            g.fillGradient(0, 0, w, epaisseur, couleur(alpha, rgb), couleur(0, rgb));
            g.fillGradient(0, h - epaisseur, w, h, couleur(0, rgb), couleur(alpha, rgb));
            for (int x = 0; x < epaisseur; x += 2) {
                double f = 1 - (double) x / epaisseur;
                int c = couleur(alpha * f * f, rgb);
                g.fill(x, 0, x + 2, h, c);
                g.fill(w - x - 2, 0, w - x, h, c);
            }
        }

        /** Traits de vitesse rayonnants, seulement dans la couronne exterieure de l'ecran (le centre et le viseur restent libres). */
        private static void traits(GuiGraphics g, int w, int h, double i, double temps) {
            double rayon = Math.sqrt(w * w + h * h) / 2;
            int n = 44;
            PoseStack pose = g.pose();
            for (int k = 0; k < n; k++) {
                double graine = (k * 0.618034) % 1;
                double phase = (temps * 0.06 * (0.6 + i) + graine) % 1;
                double angle = k * Math.PI * 2 / n + graine * 0.2;
                // Distance du centre au bord de l'ecran dans cette direction : sur un ecran large, un rayon fixe mordrait le centre en haut et en bas.
                double c = Math.abs(Math.cos(angle));
                double s = Math.abs(Math.sin(angle));
                double bord = Math.min(c < 1e-6 ? Double.MAX_VALUE : w / 2.0 / c, s < 1e-6 ? Double.MAX_VALUE : h / 2.0 / s);
                int r0 = (int) (bord * (0.55 + 0.45 * phase));
                int longueur = (int) (rayon * 0.22 * (0.4 + i));
                pose.pushPose();
                pose.translate(w / 2.0F, h / 2.0F, 0);
                pose.mulPose(Axis.ZP.rotation((float) angle));
                g.fill(r0, 0, r0 + longueur, 2, couleur(i * 0.8 * (1 - phase), 0xE8FBFF));
                pose.popPose();
            }
        }

        /** Bulles qui remontent le long des bords gauche et droit. */
        private static void bulles(GuiGraphics g, int w, int h, double i, double temps) {
            int bande = Math.max(8, w / 6);
            for (int k = 0; k < 40; k++) {
                double graine = (k * 0.381966) % 1;
                double phase = (temps * 0.02 * (1 + graine) + graine) % 1;
                int x = (int) (graine * bande);
                int taille = 2 + k % 3;
                if (k % 2 == 1) {
                    x = w - taille - x;
                }
                int y = (int) (h * (1.05 - phase * 1.1));
                int c = couleur(i * 0.85 * Math.sin(phase * Math.PI), 0xE0F8FF);
                g.fill(x, y, x + taille, y + taille, c);
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
            pose.mulPose(new Quaternionf().rotationAxis((float) CapeCalcul.tourComplet(p), (float) axe.x, (float) axe.y, (float) axe.z));
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
        static void calques(RegisterGuiLayersEvent evenement) {
            evenement.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "cape_vitesse"), Effets::ecran);
        }

        @SubscribeEvent
        static void rendu(FMLClientSetupEvent evenement) {
            evenement.enqueueWork(() -> AccessoriesRendererRegistry.registerRenderer(Grappin.CAPE_LESTEE.get(), CapeLesteeRendu::new));
        }
    }
}
