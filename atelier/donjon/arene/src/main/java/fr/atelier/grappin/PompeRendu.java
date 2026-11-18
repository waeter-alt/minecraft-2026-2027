// [claude-build] Le volant de la pompe de la station (modele block/station/pompe_station_volant, les cubes volant_* du .bbmodel) : dessine
// a part du corps pour pouvoir tourner autour de son axe (parallele a x, en y = 14, z = 8 pixels dans le modele nord).
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

public class PompeRendu implements BlockEntityRenderer<PompeEntite> {
    public static final ModelResourceLocation VOLANT = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "block/station/pompe_station_volant"));
    private static final float AXE_Y = 14 / 16F;
    private static final float AXE_Z = 8 / 16F;

    public PompeRendu(BlockEntityRendererProvider.Context contexte) {
    }

    @Override
    public void render(PompeEntite pompe, float partiel, PoseStack pose, MultiBufferSource tampons, int lumiere, int superposition) {
        BlockState etat = pompe.getBlockState();
        if (!(etat.getBlock() instanceof PompeStation) || pompe.getLevel() == null) {
            return;
        }
        pompe.avancer(pompe.getLevel().getGameTime() + partiel, etat.getValue(PompeStation.EN_MARCHE));
        BakedModel volant = Minecraft.getInstance().getModelManager().getModel(VOLANT);
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        // Meme rotation que le blockstate (y = 0 au nord, 90 a l'est...) : toYRot donne 180 au nord, 270 a l'est.
        pose.mulPose(Axis.YP.rotationDegrees(-((etat.getValue(DecorStation.FACING).toYRot() + 180) % 360)));
        pose.translate(-0.5F, -0.5F, -0.5F);
        pose.translate(0, AXE_Y, AXE_Z);
        pose.mulPose(Axis.XP.rotationDegrees(pompe.angle));
        pose.translate(0, -AXE_Y, -AXE_Z);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), tampons.getBuffer(Sheets.cutoutBlockSheet()),
                etat, volant, 1.0F, 1.0F, 1.0F, lumiere, superposition, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(PompeEntite pompe) {
        return new AABB(pompe.getBlockPos()).inflate(1.5);                       // le volant est hors du bloc
    }
}
