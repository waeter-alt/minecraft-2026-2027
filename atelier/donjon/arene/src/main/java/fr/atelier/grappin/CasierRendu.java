// [claude-build] Dessin du casier de vestiaire (modele CasierGeometrie, animations CasierAnimations, generes depuis
// atelier/modeles/station/casier.bbmodel) : dessine depuis le bas, sur deux blocs de haut, tourne selon facing, texture selon la couleur,
// porte qui s'ouvre ou se referme quand l'etat open du bloc change.
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class CasierRendu implements BlockEntityRenderer<CasierEntite> {
    public static final ModelLayerLocation COUCHE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "casier"), "main");

    /** Le modele anime, sans entite : on l'anime a la main depuis le rendu. */
    static class Modele extends HierarchicalModel<Entity> {
        private final ModelPart racine;

        Modele(ModelPart maillage) {
            this.racine = maillage.getChild("racine");
        }

        @Override
        public ModelPart root() {
            return racine;
        }

        @Override
        public void setupAnim(Entity entite, float a, float b, float c, float d, float e) {
        }

        void poser(CasierEntite casier, float age) {
            racine.getAllParts().forEach(ModelPart::resetPose);
            this.animate(casier.ouvrir, CasierAnimations.OUVRIR, age);
            this.animate(casier.fermer, CasierAnimations.FERMER, age);
        }
    }

    private final Modele modele;

    public CasierRendu(BlockEntityRendererProvider.Context contexte) {
        this.modele = new Modele(contexte.bakeLayer(COUCHE));
    }

    @Override
    public void render(CasierEntite casier, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere, int superposition) {
        BlockState etat = casier.getBlockState();
        if (!(etat.getBlock() instanceof CasierBloc) || casier.getLevel() == null) {
            return;
        }
        int tick = (int) casier.getLevel().getGameTime();
        casier.suivre(etat.getValue(CasierBloc.OPEN), tick);
        modele.poser(casier, tick + partiel);
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-etat.getValue(CasierBloc.FACING).toYRot()));    // la porte (+z du modele) vers facing
        pose.translate(0F, -0.5F, 0F);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        var tampon = tampons.getBuffer(RenderType.entityCutoutNoCull(etat.getValue(CasierBloc.COULEUR).texture()));
        modele.root().render(pose, tampon, lumiere, superposition);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CasierEntite casier) {
        return new AABB(casier.getBlockPos()).expandTowards(0, 1, 0).inflate(1);   // deux blocs de haut, et la porte ouverte depasse
    }
}
