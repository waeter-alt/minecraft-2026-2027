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

    private static final int LEATHER        = 0xE05C3A21;
    private static final int LEATHER_EDGE   = 0xFF2E1B0E;
    private static final int LEATHER_LIGHT  = 0xFF7A5232;
    private static final int VELVET         = 0xE02E1A52;
    private static final int VELVET_EDGE    = 0xFF140A28;
    private static final int VELVET_LIGHT   = 0xFF4E3282;

    private static final int STAR_ICON   = 11;
    private static final int COIN_ICON_SIZE = 14;
    private static final int PILL        = 18;
    private static final int MARGIN      = 6;
    private static final int LINE        = 21;
    private static final long FLOAT_MS   = 1800;
    private static final String MIN_WIDTH_SAMPLE = "00 000";

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
        final String influenceText = Wallet.format(influence);
        final String goldText = Wallet.format(gold);
        final int textWidth = Math.max(font.width(MIN_WIDTH_SAMPLE), Math.max(font.width(influenceText), font.width(goldText)));
        drawLine(graphics, font, right, MARGIN, textWidth, INFLUENCE_ICON, STAR_ICON, VELVET, VELVET_EDGE, VELVET_LIGHT,
          influenceText, PURPLE, influenceDelta, influenceChangedAt);
        drawLine(graphics, font, right, MARGIN + LINE, textWidth, COIN_ICON, COIN_ICON_SIZE, LEATHER, LEATHER_EDGE, LEATHER_LIGHT,
          goldText, GOLD, goldDelta, goldChangedAt);
    }

    /**
     * One right-aligned plate (velvet for Influence, leather for gold): floating change, value, then the icon at the edge.
     */
    private static void drawLine(
      final GuiGraphics graphics,
      final Font font,
      final int right,
      final int y,
      final int textWidth,
      final ResourceLocation icon,
      final int size,
      final int fill,
      final int edge,
      final int light,
      final String value,
      final int color,
      final long change,
      final long changedAt)
    {
        final int slot = right - 4 - COIN_ICON_SIZE;
        final int textRight = slot - 4;
        final int textX = textRight - font.width(value);
        final int left = textRight - textWidth - 6;
        roundedPlate(graphics, left, y, right, y + PILL, fill, edge, light);

        graphics.blit(icon, slot + (COIN_ICON_SIZE - size) / 2, y + (PILL - size) / 2, size, size, 0, 0, 16, 16, 16, 16);
        final int textY = y + (PILL - font.lineHeight) / 2 + 1;
        graphics.drawString(font, value, textX, textY, color, true);

        final long age = System.currentTimeMillis() - changedAt;
        if (change != 0 && age < FLOAT_MS)
        {
            final float fade = 1.0F - (float) age / FLOAT_MS;
            final int alpha = Math.max(8, (int) (fade * 255)) << 24;
            final String text = (change > 0 ? "+" : "−") + Wallet.format(Math.abs(change));
            final int rise = (int) ((1.0F - fade) * 6);
            graphics.drawString(font, text, left - 4 - font.width(text), textY - rise, ((change > 0 ? GAIN : LOSS) & 0xFFFFFF) | alpha, true);
        }
    }

    /**
     * A plate with pixel-rounded corners (radius 2), a dark outline and a light top edge.
     */
    private static void roundedPlate(final GuiGraphics graphics, final int left, final int top, final int right, final int bottom, final int fill, final int edge, final int light)
    {
        graphics.fill(left + 2, top, right - 2, top + 1, edge);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, edge);
        graphics.fill(left, top + 2, right, bottom - 2, edge);
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, edge);
        graphics.fill(left + 2, bottom - 1, right - 2, bottom, edge);

        graphics.fill(left + 2, top + 1, right - 2, top + 2, light);
        graphics.fill(left + 1, top + 2, right - 1, bottom - 2, fill);
        graphics.fill(left + 2, bottom - 2, right - 2, bottom - 1, fill);
    }
}
