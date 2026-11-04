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
    private static final ResourceLocation BANK_ICON      = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_bank.png");
    private static final ResourceLocation REGION_ICON    = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_region.png");
    private static final ResourceLocation PURSE_ICON     = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_purse.png");

    private static final int PAPER       = 0xFFE9D8A6;
    private static final int PAPER_EDGE  = 0xFF4A2E16;
    private static final int PAPER_LIGHT = 0xFFFFF4D2;
    private static final int INK         = 0xFF4A2A10;
    private static final int TAB         = 14;
    private static final int TAB_ICON    = 10;
    private static final int TAB_GAP     = 2;

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
    private static final double SCREEN_SIZE = 1.95;
    private static final int MARGIN      = 6;
    private static final int LINE        = 21;
    private static final long FLOAT_MS   = 1800;
    private static final String MIN_WIDTH_SAMPLE = "00 000";

    private static long influence;

    /**
     * The player's Influence as last heard from the server.
     */
    public static long influence()
    {
        if (fr.kodistan.interregne.client.dev.DevOverrides.influence != null)
        {
            return fr.kodistan.interregne.client.dev.DevOverrides.influence;
        }
        return influence;
    }
    private static long influenceDelta;
    private static long influenceChangedAt;

    private static long bank;
    private static long regional;
    private static boolean walletKnown;

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
     * Called when the server sends the player's Imperial Bank and regional treasury balances.
     */
    public static void setWallet(final long bankBalance, final long regionalBalance)
    {
        if (!walletKnown)
        {
            // The first balances are not a gain: no floating "+N"
            walletKnown = true;
            gold = -1;
        }
        bank = bankBalance;
        regional = regionalBalance;
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
        final long total = carried + bank + regional;
        if (gold >= 0 && total != gold)
        {
            goldDelta = total - gold;
            goldChangedAt = System.currentTimeMillis();
        }
        gold = total;

        final Font font = minecraft.font;
        // Same size on screen as at GUI scale 2: 65 % at scale 3, smaller still above, anchored top-right
        final double guiScale = minecraft.getWindow().getGuiScale();
        final float scale = guiScale > 2 ? (float) (SCREEN_SIZE / guiScale) : 1F;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        final int right = (int) (graphics.guiWidth() / scale) - MARGIN;
        final String influenceText = Wallet.format(influence);
        final String goldText = Wallet.format(gold);
        final String[] tabs = {compact(bank), compact(regional), compact(carried)};
        int tabWidth = 0;
        for (final String tab : tabs)
        {
            tabWidth = Math.max(tabWidth, 3 + TAB_ICON + 2 + font.width(tab) + 3);
        }
        final int plateWidth = 3 * tabWidth + 2 * TAB_GAP;
        final int textWidth = Math.max(Math.max(font.width(MIN_WIDTH_SAMPLE), plateWidth - 28), Math.max(font.width(influenceText), font.width(goldText)));
        drawLine(graphics, font, right, MARGIN, textWidth, INFLUENCE_ICON, STAR_ICON, VELVET, VELVET_EDGE, VELVET_LIGHT,
          influenceText, PURPLE, influenceDelta, influenceChangedAt);

        // The three papers slide out from under the leather wallet: Imperial Bank, regional treasury, carried
        final int goldY = MARGIN + LINE;
        final int left = right - (textWidth + 28);
        final int width = (right - left - 2 * TAB_GAP) / 3;
        final ResourceLocation[] icons = {BANK_ICON, REGION_ICON, PURSE_ICON};
        for (int i = 0; i < 3; i++)
        {
            final int x = left + i * (width + TAB_GAP);
            final int top = goldY + PILL - 3;
            roundedPlate(graphics, x, top, x + width, top + TAB, PAPER, PAPER_EDGE, PAPER_LIGHT);
            final int textW = font.width(tabs[i]);
            final int contentX = x + (width - (TAB_ICON + 2 + textW)) / 2;
            graphics.blit(icons[i], contentX, top + 3, TAB_ICON, TAB_ICON, 0, 0, 16, 16, 16, 16);
            graphics.drawString(font, tabs[i], contentX + TAB_ICON + 2, top + 4, INK, false);
        }
        drawLine(graphics, font, right, goldY, textWidth, COIN_ICON, COIN_ICON_SIZE, LEATHER, LEATHER_EDGE, LEATHER_LIGHT,
          goldText, GOLD, goldDelta, goldChangedAt);

        graphics.pose().popPose();
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
     * Short amounts for the small papers: 950, 5,7k, 12k, 1,2M.
     */
    /** Couronnes carried in the main inventory. */
    public static long carried()
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (fr.kodistan.interregne.client.dev.DevOverrides.purse != null)
        {
            return fr.kodistan.interregne.client.dev.DevOverrides.purse;
        }
        return minecraft.player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory()));
    }

    /** The player's Imperial Bank balance, as last heard from the server. */
    public static long bank()
    {
        return bank;
    }

    /** The player's colony's regional treasury, as last heard from the server. */
    public static long regional()
    {
        return regional;
    }

    public static String compact(final long value)
    {
        if (value < 1_000)
        {
            return Long.toString(value);
        }
        if (value < 1_000_000)
        {
            return value < 10_000 ? String.format(java.util.Locale.FRANCE, "%.1fk", value / 1_000.0) : (value / 1_000) + "k";
        }
        return value < 10_000_000 ? String.format(java.util.Locale.FRANCE, "%.1fM", value / 1_000_000.0) : (value / 1_000_000) + "M";
    }

    /**
     * A plate with pixel-rounded corners (radius 2), a dark outline and a light top edge.
     */
    public static void roundedPlate(final GuiGraphics graphics, final int left, final int top, final int right, final int bottom, final int fill, final int edge, final int light)
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
