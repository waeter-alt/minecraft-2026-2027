// [claude-build] Dessin de la valve de la piscine (modele ValveStationGeometrie, animation ValveStationAnimations, generes depuis valve_station.bbmodel) : le volant tourne
// de trois tours quand on la tourne, la peinture suit sa couleur, le voyant vert du socle brille une fois la valve tournee.
package fr.atelier.grappin;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ValveStationRendu implements BlockEntityRenderer<ValveStationEntite> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "valve_station"), "main");

    private static ResourceLocation texture(ValveStation.Couleur couleur) {
        return ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/valve_station_" + couleur.getSerializedName() + ".png");
    }

    /** Le modele anime, sans entite : on l'anime a la main depuis le rendu. */
    static class Modele extends HierarchicalModel<Entity> {
        private final ModelPart racine;
        final List<ModelPart> lueurs = new ArrayList<>();
        final List<ModelPart> toutes;

        Modele(ModelPart maillage) {
            this.racine = maillage.getChild("racine");
            for (String chemin : ValveStationGeometrie.LUEURS) {
                ModelPart p = racine;
                for (String nom : chemin.split("/")) {
                    p = p.getChild(nom);
                }
                lueurs.add(p);
            }
            this.toutes = racine.getAllParts().toList();
        }

        @Override
        public ModelPart root() {
            return racine;
        }

        @Override
        public void setupAnim(Entity entite, float a, float b, float c, float d, float e) {
        }

        void poser(ValveStationEntite valve, float age) {
            racine.getAllParts().forEach(ModelPart::resetPose);
            this.animate(valve.tourner, ValveStationAnimations.TOURNER, age);
        }
    }

    private final Modele modele;

    public ValveStationRendu(BlockEntityRendererProvider.Context contexte) {
        this.modele = new Modele(contexte.bakeLayer(COUCHE));
    }

    @Override
    public void render(ValveStationEntite valve, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere, int superposition) {
        BlockState etat = valve.getBlockState();
        if (!(etat.getBlock() instanceof ValveStation) || valve.getLevel() == null) {
            return;
        }
        boolean tournee = etat.getValue(ValveStation.TOURNEE);
        int tick = (int) valve.getLevel().getGameTime();
        valve.suivre(tournee, tick);
        modele.poser(valve, tick + partiel);
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));                 // comme le levier de la Station tourne vers le nord
        pose.translate(0F, -0.5F, 0F);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        var tampon = tampons.getBuffer(RenderType.entityCutoutNoCull(texture(etat.getValue(ValveStation.COULEUR))));
        modele.lueurs.forEach(p -> p.visible = false);
        modele.root().render(pose, tampon, lumiere, OverlayTexture.NO_OVERLAY);
        modele.lueurs.forEach(p -> p.visible = true);
        for (ModelPart p : modele.toutes) {
            p.skipDraw = !modele.lueurs.contains(p);
        }
        modele.root().render(pose, tampon, tournee ? LightTexture.FULL_BRIGHT : lumiere, OverlayTexture.NO_OVERLAY);      // le voyant s'allume valve tournee
        modele.toutes.forEach(p -> p.skipDraw = false);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ValveStationEntite valve) {
        return new AABB(valve.getBlockPos()).inflate(1.0);
    }
}
