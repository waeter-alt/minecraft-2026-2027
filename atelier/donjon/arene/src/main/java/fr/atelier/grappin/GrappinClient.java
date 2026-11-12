// [claude-build] Cote client : le balancier (physique du pendule chaque tick), la traction (clic droit maintenu), ESPACE pour lacher,
// la molette pour la corde, le dessin du crochet et de la corde.
package fr.atelier.grappin;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GrappinClient {
    private GrappinClient() {
    }

    /** Enregistrement du dessin du crochet (bus du mod). */
    @EventBusSubscriber(modid = Grappin.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Rendus {
        @SubscribeEvent
        static void couches(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(SeraphinModele.COUCHE, SeraphinModele::creerCouche);
        }

        @SubscribeEvent
        static void rendus(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(Grappin.CROCHET.get(), RenduCrochet::new);
            event.registerEntityRenderer(Grappin.PLUME.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);   // la plume n'est que sa traînee de particules
            event.registerEntityRenderer(Grappin.SERAPHIN.get(), SeraphinRendu::new);
        }
    }

    /** Le balancier, calcule chez le joueur a chaque tick (bus du jeu). */
    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Balancier {
        private static int crochetId = -1;              // crochet auquel on est pendu
        private static double corde = 0;                // longueur de corde fixee a l'accroche
        private static boolean espaceAvant = false;     // etat d'ESPACE au tick precedent : on ne lache qu'a l'appui, pas en le maintenant

        static Crochet crochetAccroche(LocalPlayer joueur) {
            List<Crochet> l = joueur.level().getEntitiesOfClass(Crochet.class, joueur.getBoundingBox().inflate(Reglages.PORTEE.get() + 10),
                    c -> c.estAccroche() && c.getOwner() == joueur);
            return l.isEmpty() ? null : l.get(0);
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Pre event) {
            LocalPlayer joueur = Minecraft.getInstance().player;
            if (joueur == null || Minecraft.getInstance().isPaused()) {
                return;
            }
            Crochet c = crochetAccroche(joueur);
            boolean espace = joueur.input.jumping;
            boolean appuiEspace = espace && !espaceAvant;
            espaceAvant = espace;
            if (c == null) {
                if (crochetId != -1) {                  // on vient de lacher : on garde sa vitesse (reglable) et on saute
                    Vec3 v = joueur.getDeltaMovement().scale(Reglages.CONSERVATION.get());
                    joueur.setDeltaMovement(v.x, v.y + Reglages.SAUT.get(), v.z);
                    crochetId = -1;
                }
                return;
            }
            Vec3 ancre = c.position();
            Vec3 centre = joueur.getBoundingBox().getCenter();
            Vec3 r = centre.subtract(ancre);
            double d = r.length();
            if (c.getId() != crochetId) {               // nouvelle accroche : la corde prend la distance du moment
                crochetId = c.getId();
                corde = Math.min(Math.max(d, Reglages.CORDE_MIN.get()), Reglages.PORTEE.get());
            }
            if (appuiEspace) {                          // ESPACE : lacher (le serveur supprime le crochet, le saut se fait au tick suivant)
                PacketDistributor.sendToServer(new LacherPayload());
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.keyUse.isDown() && mc.screen == null && joueur.isHolding(Grappin.GRAPPIN.get())) {   // traction : clic droit maintenu rembobine la corde
                corde = Math.max(Reglages.CORDE_MIN.get(), Math.min(corde, d) - Reglages.TREUIL.get());
            }
            if (d < 1.0E-3) {
                return;
            }
            Vec3 u = r.scale(1.0 / d);                  // de l'ancre vers le joueur
            Vec3 v = joueur.getDeltaMovement();
            v = v.add(0, -0.08 * (Reglages.GRAVITE.get() - 1.0), 0);       // gravite propre au balancier (en plus de celle du jeu)
            float avant = joueur.input.forwardImpulse, cote = joueur.input.leftImpulse;
            if (avant != 0 || cote != 0) {              // les touches poussent un peu le long de l'arc, jamais le long de la corde
                double lacet = Math.toRadians(joueur.getYRot());
                Vec3 f = new Vec3(-Math.sin(lacet), 0, Math.cos(lacet));
                Vec3 g = new Vec3(Math.cos(lacet), 0, Math.sin(lacet));
                Vec3 a = f.scale(avant).add(g.scale(cote)).normalize().scale(Reglages.INFLUENCE.get());
                a = a.subtract(u.scale(a.dot(u)));
                v = v.add(a);
            }
            if (d >= corde) {                           // corde tendue : plus de vitesse vers l'exterieur, rappel doux si on depasse
                double sortie = v.dot(u);
                if (sortie > 0) {
                    v = v.subtract(u.scale(sortie));
                }
                v = v.subtract(u.scale((d - corde) * Reglages.RAIDEUR.get()));
            }
            joueur.setDeltaMovement(v);
        }

        @SubscribeEvent
        static void molette(InputEvent.MouseScrollingEvent event) {
            LocalPlayer joueur = Minecraft.getInstance().player;
            if (joueur == null || crochetId == -1 || !joueur.isHolding(Grappin.GRAPPIN.get())) {
                return;
            }
            corde = Math.min(Math.max(corde - event.getScrollDeltaY() * Reglages.PAS_MOLETTE.get(), Reglages.CORDE_MIN.get()), Reglages.PORTEE.get());
            event.setCanceled(true);                    // la molette regle la corde au lieu de changer d'objet
        }
    }

    static class RenduCrochet extends ThrownItemRenderer<Crochet> {
        RenduCrochet(EntityRendererProvider.Context contexte) {
            super(contexte, 1.0F, false);
        }

        @Override
        public void render(Crochet crochet, float lacet, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere) {
            super.render(crochet, lacet, partiel, pose, tampons, lumiere);
            Entity lanceur = crochet.getOwner();
            if (lanceur == null) {
                return;
            }
            Vec3 main = lanceur.getRopeHoldPosition(partiel);
            Vec3 ici = crochet.getPosition(partiel);
            Vec3 d = main.subtract(ici);
            float lx = (float) d.x, ly = (float) d.y, lz = (float) d.z;
            float n = (float) Math.max(1.0E-4, d.length());
            VertexConsumer corde = tampons.getBuffer(RenderType.lines());
            PoseStack.Pose p = pose.last();
            corde.addVertex(p, 0.0F, 0.0F, 0.0F).setColor(70, 60, 50, 255).setNormal(p, lx / n, ly / n, lz / n);
            corde.addVertex(p, lx, ly, lz).setColor(70, 60, 50, 255).setNormal(p, lx / n, ly / n, lz / n);
        }
    }
}
