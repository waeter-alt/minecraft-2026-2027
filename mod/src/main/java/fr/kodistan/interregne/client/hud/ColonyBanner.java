package fr.kodistan.interregne.client.hud;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.dev.DevOverrides;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * The colony's sign that slides down from the top of the screen when the player walks into a colony, then goes back up.
 * One drawing per kind of crate the colony produces ({@code textures/gui/colonie_<crate>.png}); the name is written over it.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ColonyBanner
{
    /**
     * One sign: its drawing's height and the box (in drawing pixels) and colours the name is written in.
     */
    private record Style(ResourceLocation texture, int height, int textX, int textY, int textW, int textH, int color, int shadow)
    {
        static Style of(final String id, final int height, final int x, final int y, final int w, final int h, final int color, final int shadow)
        {
            return new Style(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/colonie_" + id + ".png"),
              height, x, y, w, h, 0xFF000000 | color, 0xFF000000 | shadow);
        }
    }

    private static final Map<String, Style> STYLES = Map.of(
      "bois", Style.of("bois", 66, 38, 27, 114, 18, 0x3A2412, 0xF6E3A0),
      "pierre", Style.of("pierre", 62, 26, 25, 138, 22, 0xEEF2F8, 0x171D27),
      "ble", Style.of("ble", 66, 54, 27, 82, 18, 0x4A2C10, 0xFFF7DC),
      "brique", Style.of("brique", 66, 26, 25, 138, 22, 0xF6E6D0, 0x1D120C),
      "minerai", Style.of("minerai", 66, 40, 31, 110, 16, 0xFFD966, 0x07090C));

    /**
     * Width of every sign's drawing.
     */
    private static final int WIDTH = 190;
    /**
     * Share of the screen's width the sign takes, whatever the window size or GUI scale.
     */
    private static final double SCREEN_SHARE = 0.30;
    private static final long SLIDE_IN_MS  = 700;
    private static final long HOLD_MS      = 4500;
    private static final long SLIDE_OUT_MS = 800;
    /**
     * Padding kept on each side of the name inside its box, in drawing pixels.
     */
    private static final int TEXT_PAD = 2;

    @Nullable
    private static Style style;
    private static String name = "";
    private static long startedAt;

    private ColonyBanner()
    {
    }

    @SubscribeEvent
    public static void onRegisterLayers(final RegisterGuiLayersEvent event)
    {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "colony_banner"), ColonyBanner::render);
    }

    /**
     * The server says the player just entered this colony.
     */
    public static void show(final String colonyName, final String crate)
    {
        final Style next = STYLES.get(crate);
        if (next == null)
        {
            return;
        }
        style = next;
        name = colonyName;
        startedAt = System.currentTimeMillis();
    }

    private static void render(final GuiGraphics graphics, final DeltaTracker delta)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen())
        {
            return;
        }

        final Style shown;
        final String text;
        final double hidden;
        if (DevOverrides.bannerCrate != null && STYLES.containsKey(DevOverrides.bannerCrate))
        {
            // A capture: the sign sits in its resting place
            shown = STYLES.get(DevOverrides.bannerCrate);
            text = DevOverrides.bannerName;
            hidden = 0;
        }
        else
        {
            if (style == null)
            {
                return;
            }
            final long elapsed = System.currentTimeMillis() - startedAt;
            if (elapsed > SLIDE_IN_MS + HOLD_MS + SLIDE_OUT_MS)
            {
                style = null;
                return;
            }
            shown = style;
            text = name;
            hidden = hiddenShare(elapsed);
        }

        final Window window = minecraft.getWindow();
        // Whole screen pixels per drawing pixel, so the pixel art stays sharp; converted to GUI units for drawing
        final int pixel = Math.max(1, (int) Math.round(window.getWidth() * SCREEN_SHARE / WIDTH));
        final float scale = (float) (pixel / window.getGuiScale());

        final PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(window.getGuiScaledWidth() / 2.0F, 0, 0);
        pose.scale(scale, scale, 1);
        final int top = (int) Math.round(-shown.height() * hidden);
        graphics.blit(shown.texture(), -WIDTH / 2, top, 0F, 0F, WIDTH, shown.height(), WIDTH, shown.height());
        drawName(graphics, minecraft.font, shown, text.toUpperCase(Locale.ROOT), top);
        pose.popPose();
    }

    /**
     * How much of the sign is still above the screen: drops in with a small bounce, holds, then lifts away.
     */
    private static double hiddenShare(final long elapsed)
    {
        if (elapsed < SLIDE_IN_MS)
        {
            final double u = (double) elapsed / SLIDE_IN_MS - 1;
            final double overshoot = 1.70158;
            return -(1 + (overshoot + 1) * u * u * u + overshoot * u * u) + 1;
        }
        if (elapsed < SLIDE_IN_MS + HOLD_MS)
        {
            return 0;
        }
        final double u = (double) (elapsed - SLIDE_IN_MS - HOLD_MS) / SLIDE_OUT_MS;
        return u * u;
    }

    private static void drawName(final GuiGraphics graphics, final Font font, final Style shown, final String text, final int top)
    {
        final int width = font.width(text);
        final float fit = Math.min(1F, (float) (shown.textW() - 2 * TEXT_PAD) / Math.max(1, width));
        final int centreX = -WIDTH / 2 + shown.textX() + shown.textW() / 2;
        final int centreY = top + shown.textY() + shown.textH() / 2;
        final PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centreX, centreY, 0);
        pose.scale(fit, fit, 1);
        graphics.drawString(font, text, -width / 2, -4 + 1, shown.shadow(), false);
        graphics.drawString(font, text, -width / 2, -4, shown.color(), false);
        pose.popPose();
    }
}
