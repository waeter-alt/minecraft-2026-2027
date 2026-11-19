// [claude-build] La paravoile cote client : le deploiement (ESPACE en l'air, la vue passe de dos le temps du vol), le vol calcule chaque tick chez le joueur (cap = regard, nez bas = pique, nez haut = frein),
// la fin du vol, et le dessin de la voile (une aile de parapente qui se balance) au dessus de la tete des planeurs. Le serveur tient la jauge et annule la chute (ParavoileEvenements).
package fr.atelier.grappin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
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
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
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
    /** Quand chaque voile s'est ouverte, et quand elle s'est refermee (temps du monde, en ticks) : on y cale les animations. */
    private static final Map<Integer, Long> OUVERTES = new HashMap<>();
    private static final Map<Integer, Long> FERMEES = new HashMap<>();
    /** Ticks pendant lesquels une voile refermee reste dessinee, le temps de se replier (FERMETURE dure 0,5 s). */
    private static final int REPLI = 11;

    private ParavoileClient() {
    }

    /** Appele a la reception d'un message du serveur (voir PlaneurPayload). */
    static void recevoir(PlaneurPayload message) {
        long maintenant = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        if (message.actif()) {
            PLANEURS.add(message.idEntite());
            OUVERTES.put(message.idEntite(), maintenant);
            FERMEES.remove(message.idEntite());
        } else {
            PLANEURS.remove(message.idEntite());
            FERMEES.put(message.idEntite(), maintenant);
            LocalPlayer moi = Minecraft.getInstance().player;
            if (moi != null && moi.getId() == message.idEntite()) {
                Vol.fermer();                                   // le serveur a range notre voile (jauge vide, eau...) : on cesse de planer
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
        /** La vue d'avant le vol, quand on l'a passee de dos a l'ouverture (null : on n'y a pas touche, ou le joueur a choisi sa vue lui-meme). */
        private static CameraType vueAvant = null;

        /** A l'ouverture : en premiere personne, on passe en vue de dos (deja en troisieme personne : on ne touche a rien). */
        private static void ouvrirVue(Minecraft mc) {
            CameraType vue = mc.options.getCameraType();
            vueAvant = vue.isFirstPerson() ? vue : null;
            if (vueAvant != null) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }

        /** Fin du vol, quelle qu'en soit la cause : on rend la vue d'avant, sauf si le joueur en a change pendant le vol. */
        static void fermer() {
            actif = false;
            Minecraft mc = Minecraft.getInstance();
            if (vueAvant != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(vueAvant);
            }
            vueAvant = null;
        }

        private static boolean empeche(LocalPlayer j) {
            return j.isInWater() || j.isInLava() || j.isFallFlying() || j.isPassenger() || j.getAbilities().flying || j.onClimbable() || j.isSpectator()
                    || GrappinClient.Traction.crochetAccroche(j) != null;
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
                if (vueAvant != null && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                    vueAvant = null;                            // le joueur a change de vue lui-meme (F5) : on respecte son choix jusqu'a la fin
                }
                auSol = joueur.onGround() ? auSol + 1 : 0;
                boolean fin = empeche(joueur) || appui || joueur.input.shiftKeyDown || (auSol >= 3 && depuis > 3)
                        || ParavoileItem.trouver(joueur).isEmpty() || ParavoileItem.trouver(joueur).getDamageValue() >= ParavoileItem.CAPACITE;
                if (fin) {
                    fermer();
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
                    ouvrirVue(mc);
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
            evenement.registerLayerDefinition(CALQUE, ParavoileGeometrie::creerCouche);   // atelier/modeles/paravoile/paravoile_voile.bbmodel
        }

        @SubscribeEvent
        static void calques(EntityRenderersEvent.AddLayers evenement) {
            for (PlayerSkin.Model peau : evenement.getSkins()) {
                if (evenement.getSkin(peau) instanceof PlayerRenderer rendu) {
                    rendu.addLayer(new Voilure(rendu, evenement.getEntityModels()));
                }
            }
        }

    }

    /** La voile (modele ParavoileGeometrie) et son balancement en vol (ParavoileAnimations.VOL, en boucle sur l'age du joueur). */
    static class ModeleVoile extends HierarchicalModel<Entity> {
        private final ModelPart racine;
        private final ModelPart voile;
        private final AnimationState ouverture = new AnimationState();
        private final AnimationState vol = new AnimationState();
        private final AnimationState fermeture = new AnimationState();

        ModeleVoile(ModelPart maillage) {
            this.racine = maillage.getChild("racine");
            this.voile = racine.getChild("voile");
        }

        @Override
        public ModelPart root() {
            return racine;
        }

        @Override
        public void setupAnim(Entity entite, float a, float b, float c, float d, float e) {
        }

        /** maintenant, ouverte, fermee : temps du monde en ticks (fermee < 0 : toujours en vol) ; inclinaison en degres (virage). */
        void poser(float maintenant, long ouverte, long fermee, float inclinaison) {
            racine.getAllParts().forEach(ModelPart::resetPose);
            ouverture.start((int) ouverte);                             // recalees a chaque image : chaque planeur a ses propres instants
            this.animate(ouverture, ParavoileAnimations.OUVERTURE, maintenant);
            vol.start((int) ouverte);
            this.animate(vol, ParavoileAnimations.VOL, maintenant);
            if (fermee >= 0) {
                fermeture.start((int) fermee);
                this.animate(fermeture, ParavoileAnimations.FERMETURE, maintenant);
            }
            voile.zRot += inclinaison * Mth.DEG_TO_RAD;
        }
    }

    static class Voilure extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ModeleVoile modele;

        Voilure(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet modeles) {
            super(parent);
            this.modele = new ModeleVoile(modeles.bakeLayer(CALQUE));
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource tampons, int lumiere, AbstractClientPlayer joueur, float pas, float amplitude, float partiel,
                float age, float lacetTete, float tangage) {
            int id = joueur.getId();
            float maintenant = joueur.level().getGameTime() + partiel;
            boolean enVol = PLANEURS.contains(id);
            Long fermee = FERMEES.get(id);
            if (!enVol && (fermee == null || maintenant - fermee > REPLI)) {
                if (fermee != null) {
                    FERMEES.remove(id);
                    OUVERTES.remove(id);
                }
                return;
            }
            long ouverte = OUVERTES.getOrDefault(id, (long) maintenant - 100);   // deja en vol quand on l'a vu arriver : pas d'ouverture rejouee
            // En virage, la voile se couche du cote ou l'on tourne (droite du joueur = -x du modele, donc angle negatif vers la droite).
            float virage = Mth.wrapDegrees(joueur.getYRot() - joueur.yRotO);
            float inclinaison = enVol ? -Mth.clamp(virage * 2.5F, -18.0F, 18.0F) : 0.0F;
            VertexConsumer sommets = tampons.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            modele.poser(maintenant, ouverte, enVol ? -1 : fermee, inclinaison);
            modele.root().render(pose, sommets, lumiere, OverlayTexture.NO_OVERLAY);
        }
    }
}
