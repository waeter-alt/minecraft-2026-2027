// [claude-build] La paravoile cote client : le deploiement (ESPACE en l'air), le vol calcule chaque tick chez le joueur (cap = regard, nez bas = pique, nez haut = frein),
// la fin du vol, et le dessin de la voile au dessus de la tete des planeurs. Le serveur tient la jauge et annule la chute (ParavoileEvenements).
package fr.atelier.grappin;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ParavoileClient {
    private static final ModelLayerLocation CALQUE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "paravoile"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/paravoile.png");

    /** Les entites (ce joueur compris) dont la voile est deployee, d'apres le serveur. */
    private static final Set<Integer> PLANEURS = new HashSet<>();

    private ParavoileClient() {
    }

    /** Appele a la reception d'un message du serveur (voir PlaneurPayload). */
    static void recevoir(PlaneurPayload message) {
        if (message.actif()) {
            PLANEURS.add(message.idEntite());
        } else {
            PLANEURS.remove(message.idEntite());
            LocalPlayer moi = Minecraft.getInstance().player;
            if (moi != null && moi.getId() == message.idEntite()) {
                Vol.actif = false;                              // le serveur a range notre voile (jauge vide, eau...) : on cesse de planer
            }
        }
    }

    /** Le vol, calcule chez le joueur a chaque tick (bus du jeu). */
    @EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
    public static final class Vol {
        static boolean actif = false;
        private static boolean espaceAvant = false;             // ESPACE au tick precedent : on ouvre et on ferme a l'appui, pas en le maintenant
        private static int depuis = 0;                          // ticks depuis l'ouverture
        private static int auSol = 0;                           // ticks consecutifs ou le jeu dit « au sol » : une simple effleure d'un bloc ne doit pas ranger la voile
        private static double vx, vy, vz;                       // vitesse de vol : la gravite et les frottements du jeu ne comptent pas en vol

        private static boolean empeche(LocalPlayer j) {
            return j.isInWater() || j.isInLava() || j.isFallFlying() || j.isPassenger() || j.getAbilities().flying || j.onClimbable() || j.isSpectator()
                    || GrappinClient.Balancier.crochetAccroche(j) != null;
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Pre evenement) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer joueur = mc.player;
            if (joueur == null || mc.isPaused()) {
                return;
            }
            boolean espace = joueur.input.jumping && mc.screen == null;
            boolean appui = espace && !espaceAvant;
            espaceAvant = espace;
            if (actif) {
                auSol = joueur.onGround() ? auSol + 1 : 0;
                boolean fin = empeche(joueur) || appui || joueur.input.shiftKeyDown || (auSol >= 3 && depuis > 3)
                        || ParavoileItem.trouver(joueur).isEmpty() || ParavoileItem.trouver(joueur).getDamageValue() >= ParavoileItem.CAPACITE;
                if (fin) {
                    actif = false;
                    PacketDistributor.sendToServer(new PlaneurPayload(joueur.getId(), false));
                    return;
                }
                depuis++;
                piloter(joueur);
            } else if (appui && !joueur.onGround() && joueur.getDeltaMovement().y < 0.1 && !empeche(joueur)) {
                var pile = ParavoileItem.trouver(joueur);
                if (!pile.isEmpty() && pile.getDamageValue() < ParavoileItem.CAPACITE) {
                    actif = true;
                    depuis = 0;
                    auSol = 0;
                    Vec3 v = joueur.getDeltaMovement();
                    vx = v.x;
                    vy = v.y;
                    vz = v.z;
                    PacketDistributor.sendToServer(new PlaneurPayload(joueur.getId(), true));
                    piloter(joueur);
                }
            }
        }

        /** Un pas de vol : la vitesse glisse vers la cible (cap du regard, pique ou frein selon l'inclinaison de la tete). */
        private static void piloter(LocalPlayer joueur) {
            double plongeon = Mth.clamp(joueur.getXRot() / 60.0, -1.0, 1.0);      // > 0 : nez vers le bas
            double allure = plongeon > 0 ? 1.0 + 0.5 * plongeon : 1.0 + 0.4 * plongeon;
            double chute = plongeon > 0 ? Reglages.PARAVOILE_DESCENTE.get() + 0.22 * plongeon : Reglages.PARAVOILE_DESCENTE.get() * (1.0 + 0.3 * plongeon);
            float avant = joueur.input.forwardImpulse, cote = joueur.input.leftImpulse;
            double poussee = avant > 0 ? 1.0 : (avant < 0 ? 0.35 : 0.75);          // sans touche on file quand meme, plus doucement ; arriere = frein
            double lacet = Math.toRadians(joueur.getYRot());
            double vit = Reglages.PARAVOILE_VITESSE.get();
            double cx = -Math.sin(lacet) * vit * allure * poussee + Math.cos(lacet) * cote * vit * 0.4;
            double cz = Math.cos(lacet) * vit * allure * poussee + Math.sin(lacet) * cote * vit * 0.4;
            if (joueur.horizontalCollision) {                   // un mur arrete la glisse
                vx *= 0.3;
                vz *= 0.3;
            }
            vx += (cx - vx) * 0.1;
            vz += (cz - vz) * 0.1;
            vy += (-chute - vy) * 0.25;
            if (joueur.verticalCollision && vy > 0) {            // un plafond coupe la montee
                vy = -chute;
            }
            joueur.setDeltaMovement(vx, vy, vz);
            joueur.resetFallDistance();
        }
    }

    /** Dessin de la voile : un calque ajoute aux deux modeles de joueur (bus du mod). */
    @EventBusSubscriber(modid = Grappin.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Rendus {
        @SubscribeEvent
        static void definition(EntityRenderersEvent.RegisterLayerDefinitions evenement) {
            evenement.registerLayerDefinition(CALQUE, Rendus::modele);
        }

        @SubscribeEvent
        static void calques(EntityRenderersEvent.AddLayers evenement) {
            for (PlayerSkin.Model peau : evenement.getSkins()) {
                if (evenement.getSkin(peau) instanceof PlayerRenderer rendu) {
                    rendu.addLayer(new Voilure(rendu, evenement.getEntityModels()));
                }
            }
        }

        /** Une toile tendue 14 pixels au dessus de la tete, et deux montants jusqu'aux epaules. */
        private static LayerDefinition modele() {
            MeshDefinition maille = new MeshDefinition();
            maille.getRoot().addOrReplaceChild("toile", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -23.0F, -8.0F, 24.0F, 1.0F, 16.0F), PartPose.ZERO);
            maille.getRoot().addOrReplaceChild("montant_gauche", CubeListBuilder.create().texOffs(0, 20).addBox(5.0F, -22.0F, -0.5F, 1.0F, 21.0F, 1.0F), PartPose.ZERO);
            maille.getRoot().addOrReplaceChild("montant_droit", CubeListBuilder.create().texOffs(0, 20).addBox(-6.0F, -22.0F, -0.5F, 1.0F, 21.0F, 1.0F), PartPose.ZERO);
            return LayerDefinition.create(maille, 128, 64);
        }
    }

    static class Voilure extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ModelPart racine;

        Voilure(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet modeles) {
            super(parent);
            this.racine = modeles.bakeLayer(CALQUE);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource tampons, int lumiere, AbstractClientPlayer joueur, float pas, float amplitude, float partiel,
                float age, float lacetTete, float tangage) {
            if (!PLANEURS.contains(joueur.getId())) {
                return;
            }
            VertexConsumer sommets = tampons.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            racine.render(pose, sommets, lumiere, OverlayTexture.NO_OVERLAY);
        }
    }
}
