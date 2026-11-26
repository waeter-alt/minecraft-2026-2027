// [claude-build] La jauge d'endurance de la paravoile, a l'ecran (facon Breath of the Wild) : un anneau a droite du reticule qui se vide
// dans le sens des aiguilles d'une montre. Verte, orange sous 40 %, rouge (qui clignote en vol) sous 20 %. Elle apparait quand la voile
// s'ouvre, reste tant qu'elle se recharge, et disparait en fondu une fois pleine. Regles : EnduranceLogique.
package fr.atelier.grappin;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = Grappin.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ParavoileJauge {
    private static final int R_INTERIEUR = 5, R_EXTERIEUR = 8, ECART = 26;          // pixels d'interface ; ECART : a droite du reticule
    private static float opacite = 0F;
    private static float reste = 1F;                                                // la derniere valeur vue (garde la jauge pendant le fondu)
    private static long derniereImage = 0L;

    private ParavoileJauge() {
    }

    @SubscribeEvent
    static void calques(RegisterGuiLayersEvent evenement) {
        evenement.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "paravoile_endurance"), ParavoileJauge::dessiner);
    }

    static void dessiner(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        long maintenant = Util.getMillis();
        float secondes = derniereImage == 0L ? 0F : Math.min(0.25F, (maintenant - derniereImage) / 1000F);
        derniereImage = maintenant;
        if (joueur == null || mc.options.hideGui) {
            return;
        }
        ItemStack pile = ParavoileItem.trouver(joueur);
        boolean enVol = ParavoileClient.Vol.actif;
        if (!pile.isEmpty()) {
            reste = EnduranceLogique.reste(pile.getDamageValue(), ParavoileItem.CAPACITE);
        }
        opacite = EnduranceLogique.opacite(opacite, !pile.isEmpty() && EnduranceLogique.visible(enVol, reste), secondes);
        if (opacite <= 0F) {
            return;
        }
        float a = opacite;
        if (enVol && reste < EnduranceLogique.ROUGE) {
            a *= 0.55F + 0.45F * (float) Math.abs(Math.sin(maintenant / 140.0));     // presque vide : elle clignote
        }
        int plein = argb(a, EnduranceLogique.couleur(reste));
        int vide = argb(a * 0.55F, 0x1A1F24);
        int cx = g.guiWidth() / 2 + ECART, cy = g.guiHeight() / 2;
        for (int dy = -R_EXTERIEUR; dy < R_EXTERIEUR; dy++) {
            for (int dx = -R_EXTERIEUR; dx < R_EXTERIEUR; dx++) {
                float p = EnduranceLogique.position(dx + 0.5F, dy + 0.5F, R_INTERIEUR, R_EXTERIEUR);
                if (p >= 0F) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, p < reste ? plein : vide);
                }
            }
        }
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.max(0F, Math.min(1F, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
