// [claude-build] Dessin de la Cape lestee dans l'emplacement "cape" d'Accessories : des bandes translucides (degrade turquoise -> bleu profond)
// avec un liseré lumineux, un ourlet qui ondule et flotte sous l'eau, s'aplatit avec la vitesse, scintille en nage rapide et s'enroule en spirale
// pendant le tourbillon.
// PROVISOIRE : geometrie procedurale en attendant un modele fait dans Blockbench (source .bbmodel dans art/, export dans assets/).
package fr.atelier.grappin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class CapeLesteeRendu implements AccessoryRenderer {
    private static final ResourceLocation TISSU = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/cape_lestee.png");
    private static final ResourceLocation LISERE = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/cape_lestee_lisere.png");

    private static final int BANDES = 5;
    private static final int SEGMENTS = 8;
    private static final float LARGEUR = 0.6F;
    private static final float ECART = 0.012F;
    private static final float LONGUEUR = 1.05F;

    @Override
    public <M extends LivingEntity> void render(ItemStack stack, SlotReference reference, PoseStack pose, EntityModel<M> modele, MultiBufferSource tampons,
            int lumiere, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity porteur = reference.entity();
        if (porteur.isInvisible()) {
            return;
        }
        pose.pushPose();
        if (modele instanceof HumanoidModel<?> humain) {
            humain.body.translateAndRotate(pose);
        }
        // Repere du torse : y vers le bas depuis les epaules, z positif = dans le dos.
        pose.translate(0, 0, 0.13);

        boolean dansLEau = porteur.isInWater();
        double dx = porteur.getX() - porteur.xo;
        double dy = porteur.getY() - porteur.yo;
        double dz = porteur.getZ() - porteur.zo;
        float vitesse = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float vitesseHoriz = (float) Math.sqrt(dx * dx + dz * dz);

        float ouverture;
        float amplitude;
        if (dansLEau) {
            float f = Mth.clamp(vitesse / 0.4F, 0, 1);
            amplitude = 0.09F * (1 - 0.8F * f);
            ouverture = Mth.lerp(f, 0.35F, 0.05F);
        } else {
            amplitude = 0.03F;
            ouverture = 0.08F + 0.6F * Mth.clamp(vitesseHoriz / 0.3F, 0, 1);
        }
        float scintillement = dansLEau && CapeClient.nageRapide(porteur) ? 1 : 0.15F;
        double p = CapeClient.tourbillon(porteur, partialTicks);
        float spirale = p < 0 ? 0 : (float) Math.sin(p * Math.PI);

        VertexConsumer tissu = tampons.getBuffer(RenderType.entityTranslucent(TISSU));
        bandes(pose, tissu, lumiere, ageInTicks, ouverture, amplitude, scintillement, spirale, 0, 0.85F);
        VertexConsumer lisere = tampons.getBuffer(RenderType.entityTranslucentEmissive(LISERE));
        bandes(pose, lisere, LightTexture.FULL_BRIGHT, ageInTicks, ouverture, amplitude, scintillement, spirale, 0.002F, 1);
        pose.popPose();
    }

    private static void bandes(PoseStack pose, VertexConsumer vc, int lumiere, float age, float ouverture, float amplitude, float scintillement,
            float spirale, float decalage, float opacite) {
        float largeurBande = (LARGEUR - ECART * (BANDES - 1)) / BANDES;
        for (int i = 0; i < BANDES; i++) {
            float x0 = -LARGEUR / 2 + i * (largeurBande + ECART);
            float x1 = x0 + largeurBande;
            float longueur = LONGUEUR * (0.88F + 0.12F * Mth.sin(i * 2.1F + age * 0.07F));
            float u0 = (float) i / BANDES;
            float u1 = (float) (i + 1) / BANDES;
            for (int s = 0; s < SEGMENTS; s++) {
                float ta = (float) s / SEGMENTS;
                float tb = (float) (s + 1) / SEGMENTS;
                float[] a0 = point(x0, ta, i, longueur, age, ouverture, amplitude, spirale, decalage);
                float[] a1 = point(x1, ta, i, longueur, age, ouverture, amplitude, spirale, decalage);
                float[] b0 = point(x0, tb, i, longueur, age, ouverture, amplitude, spirale, decalage);
                float[] b1 = point(x1, tb, i, longueur, age, ouverture, amplitude, spirale, decalage);
                // Normale approchee : perpendiculaire au segment dans le plan (y, z).
                float ny = -(b0[2] - a0[2]);
                float nz = b0[1] - a0[1];
                float n = Mth.sqrt(ny * ny + nz * nz);
                if (n > 1.0E-6F) {
                    ny /= n;
                    nz /= n;
                } else {
                    nz = 1;
                }
                float la = opacite * eclat(age, ta, i, scintillement);
                float lb = opacite * eclat(age, tb, i, scintillement);
                sommet(pose, vc, a0, u0, ta, la, lumiere, ny, nz);
                sommet(pose, vc, b0, u0, tb, lb, lumiere, ny, nz);
                sommet(pose, vc, b1, u1, tb, lb, lumiere, ny, nz);
                sommet(pose, vc, a1, u1, ta, la, lumiere, ny, nz);
            }
        }
    }

    /** Un point de bande : t = 0 aux epaules, 1 a l'ourlet. */
    private static float[] point(float x, float t, int i, float longueur, float age, float ouverture, float amplitude, float spirale, float decalage) {
        float y = Mth.cos(ouverture) * t * longueur;
        float z = Mth.sin(ouverture) * t * longueur + amplitude * (float) Math.pow(t, 1.3) * Mth.sin(age * 0.18F + t * 5 - i * 0.9F) + decalage;
        if (spirale > 0) {
            // Pendant le tourbillon, la cape s'enroule autour de l'axe du corps, d'autant plus vers l'ourlet.
            float theta = spirale * t * 2.5F * Mth.PI;
            float r = z + 0.2F * spirale * t;
            float cos = Mth.cos(theta);
            float sin = Mth.sin(theta);
            float nx = x * cos + r * sin;
            float nz = -x * sin + r * cos;
            return new float[] {nx, y, nz};
        }
        return new float[] {x, y, z};
    }

    private static float eclat(float age, float t, int i, float scintillement) {
        return Mth.clamp(1 + 0.25F * Mth.sin(age * 0.35F + t * 6 + i) * scintillement, 0, 1.25F) / 1.25F;
    }

    private static void sommet(PoseStack pose, VertexConsumer vc, float[] p, float u, float v, float alpha, int lumiere, float ny, float nz) {
        vc.addVertex(pose.last(), p[0], p[1], p[2])
                .setColor(1F, 1F, 1F, Mth.clamp(alpha, 0, 1))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lumiere)
                .setNormal(pose.last(), 0, ny, nz);
    }
}
