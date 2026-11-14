// [claude-build] Le rendu du Leviathan (client) : la tete, puis huit anneaux et la queue dessines a la position que leur donne le chemin de la tete (Leviathan.positionSection), chacun tourne vers la section
// qui le precede. Un modele dessine a 4 unites par bloc est agrandi x4. Les yeux et la perle sont lumineux ; la perle gonfle et bat quand elle est exposee ; en phase 2 le serpent rougeoie.
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class LeviathanRendu extends EntityRenderer<Leviathan> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/leviathan.png");

    private final ModelPart tete;
    private final ModelPart lueurs;
    private final ModelPart perle;
    private final ModelPart anneau;
    private final ModelPart queue;

    public LeviathanRendu(EntityRendererProvider.Context contexte) {
        super(contexte);
        ModelPart racine = contexte.bakeLayer(LeviathanModele.COUCHE);
        this.tete = racine.getChild("tete");
        this.lueurs = tete.getChild("lueurs");
        this.perle = tete.getChild("perle");
        this.anneau = racine.getChild("anneau");
        this.queue = racine.getChild("queue");
    }

    @Override
    public ResourceLocation getTextureLocation(Leviathan serpent) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(Leviathan serpent, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;                                         // un corps de 36 blocs : sa boite de culling ne suffit pas toujours
    }

    @Override
    public void render(Leviathan serpent, float lacet, float partialTick, PoseStack pose, MultiBufferSource tampons, int lumiere) {
        VertexConsumer tampon = tampons.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int couleur = serpent.deuxiemePhase() ? FastColor.ARGB32.multiply(0xFFFFFFFF, 0xFFFFB8A0) : 0xFFFFFFFF;
        Vec3 teteMonde = serpent.getPosition(partialTick);
        float hauteur = serpent.getBbHeight() / 2;

        float lacetTete = Mth.rotLerp(partialTick, serpent.yRotO, serpent.getYRot());
        float tangageTete = Mth.lerp(partialTick, serpent.xRotO, serpent.getXRot());
        dessiner(pose, tampon, lumiere, couleur, tete, serpent, Vec3.ZERO.add(0, hauteur, 0), lacetTete, tangageTete, true);

        Vec3 precedente = teteMonde;
        for (int i = 0; i <= Leviathan.QUEUE; i++) {
            Vec3 p = serpent.positionSectionRendu(i, partialTick);
            Vec3 avant = precedente.subtract(p);
            float l = avant.lengthSqr() < 1.0E-6 ? lacetTete : (float) Math.toDegrees(Math.atan2(-avant.x, avant.z));
            float t = avant.lengthSqr() < 1.0E-6 ? 0 : (float) -Math.toDegrees(Math.asin(Mth.clamp(avant.normalize().y, -1, 1)));
            dessiner(pose, tampon, lumiere, couleur, i == Leviathan.QUEUE ? queue : anneau, serpent, p.subtract(teteMonde).add(0, hauteur, 0), l, t, false);
            precedente = p;
        }
    }

    /** Dessine un modele centre en `centre` (relatif a la tete), tourne de `lacet` et `tangage`, a l'echelle x4 ; la tete a en plus ses parties lumineuses. */
    private void dessiner(PoseStack pose, VertexConsumer tampon, int lumiere, int couleur, ModelPart modele, Leviathan serpent, Vec3 centre, float lacet, float tangage, boolean estTete) {
        pose.pushPose();
        pose.translate(centre.x, centre.y, centre.z);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - lacet));
        pose.mulPose(Axis.XP.rotationDegrees(-tangage));
        pose.scale(-4.0F, -4.0F, 4.0F);
        if (estTete) {
            lueurs.visible = false;
            perle.visible = false;
            modele.render(pose, tampon, lumiere, OverlayTexture.NO_OVERLAY, couleur);
            lueurs.visible = true;
            float pulse = serpent.perleOuverte() ? 1.25F + 0.2F * (float) Math.sin(serpent.tickCount * 0.5F) : 1.0F;
            perle.xScale = pulse;
            perle.yScale = pulse;
            perle.zScale = pulse;
            perle.visible = true;
            lueurs.render(pose, tampon, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            perle.render(pose, tampon, serpent.perleOuverte() ? LightTexture.FULL_BRIGHT : lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        } else {
            modele.render(pose, tampon, lumiere, OverlayTexture.NO_OVERLAY, couleur);
        }
        pose.popPose();
    }
}
