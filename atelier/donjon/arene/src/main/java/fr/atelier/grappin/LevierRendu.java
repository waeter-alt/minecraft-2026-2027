// [claude-build] Dessin du levier de racine (modele LevierGeometrie, animations LevierAnimations, generes depuis levier_racine.bbmodel) :
// pose selon la face (sol, mur, plafond) et l'orientation du bloc, animation tirer/relacher au changement d'etat, cristal lumineux baisse.
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;

public class LevierRendu implements BlockEntityRenderer<LevierEntite> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "levier_racine"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/levier_racine.png");

    /** Le modele anime, sans entite : on l'anime a la main depuis le rendu. */
    static class Modele extends HierarchicalModel<Entity> {
        private final ModelPart racine;
        final List<ModelPart> lueurs = new ArrayList<>();
        final List<ModelPart> toutes;

        Modele(ModelPart maillage) {
            this.racine = maillage.getChild("racine");
            for (String chemin : LevierGeometrie.LUEURS) {
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

        void poser(LevierEntite levier, float age) {
            racine.getAllParts().forEach(ModelPart::resetPose);
            this.animate(levier.tirer, LevierAnimations.TIRER, age);
            this.animate(levier.relacher, LevierAnimations.RELACHER, age);
        }
    }

    private final Modele modele;

    public LevierRendu(BlockEntityRendererProvider.Context contexte) {
        this.modele = new Modele(contexte.bakeLayer(COUCHE));
    }

    @Override
    public void render(LevierEntite levier, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere, int superposition) {
        BlockState etat = levier.getBlockState();
        if (!(etat.getBlock() instanceof LevierRacine) || levier.getLevel() == null) {
            return;
        }
        boolean tire = etat.getValue(LeverBlock.POWERED);
        int tick = (int) levier.getLevel().getGameTime();
        levier.suivre(tire, tick);
        modele.poser(levier, tick + partiel);
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        Direction facing = etat.getValue(LeverBlock.FACING);
        AttachFace face = etat.getValue(LeverBlock.FACE);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        if (face == AttachFace.WALL) {
            pose.mulPose(Axis.XP.rotationDegrees(90));                // couche contre le mur, le socle vers le mur
        } else if (face == AttachFace.CEILING) {
            pose.mulPose(Axis.XP.rotationDegrees(180));
        }
        pose.translate(0F, -0.5F, 0F);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        var tampon = tampons.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        modele.lueurs.forEach(p -> p.visible = false);
        modele.root().render(pose, tampon, lumiere, OverlayTexture.NO_OVERLAY);
        modele.lueurs.forEach(p -> p.visible = true);
        for (ModelPart p : modele.toutes) {
            p.skipDraw = !modele.lueurs.contains(p);
        }
        modele.root().render(pose, tampon, tire ? LightTexture.FULL_BRIGHT : lumiere, OverlayTexture.NO_OVERLAY);   // le cristal s'allume baisse
        modele.toutes.forEach(p -> p.skipDraw = false);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(LevierEntite levier) {
        return new AABB(levier.getBlockPos()).inflate(1.5);                       // le levier depasse son bloc (2 blocs de haut)
    }
}
