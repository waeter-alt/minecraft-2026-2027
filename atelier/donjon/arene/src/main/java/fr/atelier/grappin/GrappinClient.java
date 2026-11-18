// [claude-build] Cote client : la traction (grappin 3 : on est tire en ligne droite vers le point d'ancrage vise, sans balancier), ESPACE pour lacher
// en route, le dessin du crochet et de la corde.
package fr.atelier.grappin;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GrappinClient {
    private GrappinClient() {
    }

    /** Enregistrement du dessin du crochet (bus du mod). */
    @EventBusSubscriber(modid = Grappin.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Rendus {
        static final ModelResourceLocation MODELE_CROCHET = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "item/crochet"));

        @SubscribeEvent
        static void modeles(ModelEvent.RegisterAdditional event) {
            event.register(MODELE_CROCHET);       // le crochet en vol n'est pas un objet : son modele doit etre charge a part
        }

        @SubscribeEvent
        static void couches(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(SeraphinModele.COUCHE, SeraphinModele::creerCouche);
            event.registerLayerDefinition(GolemModele.COUCHE, GolemModele::creerCouche);
            event.registerLayerDefinition(RodeurModele.COUCHE, RodeurModele::creerCouche);
            event.registerLayerDefinition(LeviathanModele.COUCHE, LeviathanModele::creerCouche);
        }

        @SubscribeEvent
        static void rendus(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(Grappin.CROCHET.get(), RenduCrochet::new);
            event.registerEntityRenderer(Grappin.PLUME.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);   // la plume n'est que sa traînee de particules
            event.registerEntityRenderer(Grappin.SERAPHIN.get(), SeraphinRendu::new);
            event.registerEntityRenderer(Grappin.GRAINE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);   // la graine n'est que sa traînee de particules
            event.registerEntityRenderer(Grappin.GOLEM.get(), GolemRendu::new);
            event.registerEntityRenderer(Grappin.RODEUR.get(), RodeurRendu::new);
            event.registerEntityRenderer(Grappin.LEVIATHAN.get(), LeviathanRendu::new);
        }
    }

    /** La traction, calculee chez le joueur a chaque tick (bus du jeu) : accroche, on est tire en ligne droite vers le point d'ancrage vise, sans balancier ; on s'arrete a ARRIVEE blocs de lui. */
    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Traction {
        private static int crochetId = -1;              // crochet qui nous tire
        private static double vitesse = 0;              // vitesse de traction du tick precedent (montee progressive au depart)
        private static double plusProche = 1.0E9;       // plus petite distance au crochet depuis l'accroche
        private static int sansProgres = 0;             // ticks de suite sans se rapprocher : un bloc nous bloque, on lache
        private static boolean elan = false;            // on lache avec ESPACE : on garde sa vitesse et on saute

        static Crochet crochetAccroche(LocalPlayer joueur) {
            List<Crochet> l = joueur.level().getEntitiesOfClass(Crochet.class, joueur.getBoundingBox().inflate(Reglages.PORTEE.get() + 10),
                    c -> c.estAccroche() && c.getOwner() == joueur);
            return l.isEmpty() ? null : l.get(0);
        }

        private static void lacher() {
            PacketDistributor.sendToServer(new LacherPayload());     // le serveur supprime le crochet
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Pre event) {
            LocalPlayer joueur = Minecraft.getInstance().player;
            if (joueur == null || Minecraft.getInstance().isPaused()) {
                return;
            }
            Crochet c = crochetAccroche(joueur);
            boolean appuiEspace = joueur.input.jumping && !espaceAvant;
            espaceAvant = joueur.input.jumping;
            if (c == null) {
                if (crochetId != -1) {                      // le crochet vient de se detacher
                    if (elan) {                             // lache avec ESPACE : on garde sa vitesse (reglable) et on saute
                        Vec3 v = joueur.getDeltaMovement().scale(Reglages.CONSERVATION.get());
                        joueur.setDeltaMovement(v.x, v.y + Reglages.SAUT.get(), v.z);
                    }
                    crochetId = -1;
                    vitesse = 0;
                    elan = false;
                }
                return;
            }
            Vec3 vers = c.position().subtract(joueur.getBoundingBox().getCenter());
            double d = vers.length();
            if (c.getId() != crochetId) {                   // nouvelle accroche
                crochetId = c.getId();
                vitesse = 0;
                plusProche = d;
                sansProgres = 0;
                elan = false;
            }
            if (appuiEspace) {                              // ESPACE : lacher en route, avec son elan
                elan = true;
                lacher();
                return;
            }
            double arrivee = Reglages.ARRIVEE.get();
            if (d <= arrivee + 0.05) {                      // arrive : on s'arrete a cote de l'ancrage, le crochet se detache, la gravite reprend
                joueur.setDeltaMovement(Vec3.ZERO);
                joueur.fallDistance = 0;
                lacher();
                return;
            }
            if (d < plusProche - 0.02) {
                plusProche = d;
                sansProgres = 0;
            } else if (++sansProgres > 10) {                // un bloc nous retient : on lache plutot que de rester colle
                lacher();
                return;
            }
            vitesse = Math.min(Reglages.VITESSE_TRACTION.get(), vitesse + Reglages.ACCELERATION.get());
            double v = Math.min(vitesse, Math.max(0.12, (d - arrivee) * 0.4));      // on ralentit en arrivant
            joueur.setDeltaMovement(vers.scale(v / d));
            joueur.fallDistance = 0;
        }

        private static boolean espaceAvant = false;         // etat d'ESPACE au tick precedent : on ne lache qu'a l'appui, pas en le maintenant
    }

    /** Le crochet en vol : le modele 3D grappin:item/crochet (griffes vers +X), tourne comme une fleche dans le sens du vol. */
    static class RenduCrochet extends EntityRenderer<Crochet> {
        private static final float ECHELLE_CROCHET = 0.75F;

        RenduCrochet(EntityRendererProvider.Context contexte) {
            super(contexte);
        }

        @Override
        public ResourceLocation getTextureLocation(Crochet crochet) {
            return TextureAtlas.LOCATION_BLOCKS;
        }

        @Override
        public void render(Crochet crochet, float lacet, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partiel, crochet.yRotO, crochet.getYRot()) - 90.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partiel, crochet.xRotO, crochet.getXRot())));
            pose.scale(ECHELLE_CROCHET, ECHELLE_CROCHET, ECHELLE_CROCHET);
            BakedModel modele = Minecraft.getInstance().getModelManager().getModel(Rendus.MODELE_CROCHET);
            Minecraft.getInstance().getItemRenderer().render(crochet.getItem(), ItemDisplayContext.NONE, false, pose, tampons, lumiere,
                    OverlayTexture.NO_OVERLAY, modele);
            pose.popPose();
            super.render(crochet, lacet, partiel, pose, tampons, lumiere);
            Entity lanceur = crochet.getOwner();
            if (lanceur == null) {
                return;
            }
            Vec3 main = lanceur.getRopeHoldPosition(partiel);
            Vec3 ici = crochet.getPosition(partiel);
            Vec3 d = main.subtract(ici);
            double[][] pts = Corde.points(d.x, d.y, d.z, Corde.mou(crochet.estAccroche(), d.length()), Corde.SEGMENTS);
            // Lumiere prise aux deux bouts et melangee le long de la corde, comme la laisse : visible dans le noir sans briller.
            BlockPos bc = BlockPos.containing(ici), bm = BlockPos.containing(main);
            int blocC = crochet.level().getBrightness(LightLayer.BLOCK, bc), blocM = crochet.level().getBrightness(LightLayer.BLOCK, bm);
            int cielC = crochet.level().getBrightness(LightLayer.SKY, bc), cielM = crochet.level().getBrightness(LightLayer.SKY, bm);
            double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            float demi = Corde.LARGEUR / 2;
            float ox = horiz < 1.0E-4 ? demi : (float) (-d.z / horiz) * demi;
            float oz = horiz < 1.0E-4 ? 0 : (float) (d.x / horiz) * demi;
            VertexConsumer corde = tampons.getBuffer(RenderType.leash());
            Matrix4f m = pose.last().pose();
            bande(corde, m, pts, ox, 0, oz, blocC, blocM, cielC, cielM, false);      // bande a plat, du crochet a la main
            bande(corde, m, pts, 0, demi, 0, blocC, blocM, cielC, cielM, true);      // bande debout, de la main au crochet : le meme tampon (triangle strip) enchaine les deux bandes sans triangle parasite, comme la laisse
        }

        /** Une bande de la corde (triangle strip), deux tons de brun en alternance pour l'effet tresse. */
        private static void bande(VertexConsumer c, Matrix4f m, double[][] pts, float ox, float oy, float oz, int blocC, int blocM, int cielC, int cielM, boolean envers) {
            for (int k = 0; k < pts.length; k++) {
                int i = envers ? pts.length - 1 - k : k;
                float t = (float) i / (pts.length - 1);
                int lum = LightTexture.pack((int) Mth.lerp(t, blocC, blocM), (int) Mth.lerp(t, cielC, cielM));
                int r = i % 2 == 0 ? 139 : 105, g = i % 2 == 0 ? 105 : 78, b = i % 2 == 0 ? 70 : 50;
                float x = (float) pts[i][0], y = (float) pts[i][1], z = (float) pts[i][2];
                c.addVertex(m, x - ox, y - oy, z - oz).setColor(r, g, b, 255).setLight(lum);
                c.addVertex(m, x + ox, y + oy, z + oz).setColor(r, g, b, 255).setLight(lum);
            }
        }
    }
}
