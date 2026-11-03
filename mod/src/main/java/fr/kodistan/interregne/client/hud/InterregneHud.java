package fr.kodistan.interregne.client.hud;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.Wallet;
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
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * The always-visible score in the top-right corner: Influence (a purple star, Humankind style)
 * and the Couronnes carried, each with a short floating "+5" / "-5" when it changes.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InterregneHud
{
    private static final ResourceLocation INFLUENCE_ICON = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_influence.png");
    private static final ResourceLocation COIN_ICON      = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_coin.png");

    private static final int PURPLE = 0xFFB27CFF;
    private static final int GOLD   = 0xFFFFC940;
    private static final int GAIN   = 0xFF7CFF8A;
    private static final int LOSS   = 0xFFFF6B6B;

    private static final int ICON        = 10;
    private static final int MARGIN      = 6;
    private static final int LINE        = 13;
    private static final long FLOAT_MS   = 1800;

    private static long influence;
    private static long influenceDelta;
    private static long influenceChangedAt;

    private static long gold = -1;
    private static long goldDelta;
    private static long goldChangedAt;

    private InterregneHud()
    {
    }

    @SubscribeEvent
    public static void onRegisterLayers(final RegisterGuiLayersEvent event)
    {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "score"), InterregneHud::render);
    }

    /**
     * Called when the server sends the player's Influence.
     */
    public static void setInfluence(final long value, final long delta)
    {
        influence = value;
        if (delta != 0)
        {
            influenceDelta = delta;
            influenceChangedAt = System.currentTimeMillis();
        }
    }

    private static void render(final GuiGraphics graphics, final DeltaTracker delta)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen())
        {
            return;
        }

        final long carried = Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory()));
        if (gold >= 0 && carried != gold)
        {
            goldDelta = carried - gold;
            goldChangedAt = System.currentTimeMillis();
        }
        gold = carried;

        final Font font = minecraft.font;
        final int right = graphics.guiWidth() - MARGIN;
        drawLine(graphics, font, right, MARGIN, INFLUENCE_ICON, Wallet.format(influence), PURPLE, influenceDelta, influenceChangedAt);
        drawLine(graphics, font, right, MARGIN + LINE, COIN_ICON, Wallet.format(gold), GOLD, goldDelta, goldChangedAt);
    }

    /**
     * One right-aligned line: floating change, value, then the icon at the edge.
     */
    private static void drawLine(
      final GuiGraphics graphics,
      final Font font,
      final int right,
      final int y,
      final ResourceLocation icon,
      final String value,
      final int color,
      final long change,
      final long changedAt)
    {
        final int iconX = right - ICON;
        graphics.blit(icon, iconX, y, ICON, ICON, 0, 0, 16, 16, 16, 16);
        final int textX = iconX - 3 - font.width(value);
        graphics.drawString(font, value, textX, y + 1, color, true);

        final long age = System.currentTimeMillis() - changedAt;
        if (change != 0 && age < FLOAT_MS)
        {
            final float fade = 1.0F - (float) age / FLOAT_MS;
            final int alpha = Math.max(8, (int) (fade * 255)) << 24;
            final String text = (change > 0 ? "+" : "−") + Wallet.format(Math.abs(change));
            final int rise = (int) ((1.0F - fade) * 6);
            graphics.drawString(font, text, textX - 4 - font.width(text), y + 1 - rise, ((change > 0 ? GAIN : LOSS) & 0xFFFFFF) | alpha, true);
        }
    }
}
