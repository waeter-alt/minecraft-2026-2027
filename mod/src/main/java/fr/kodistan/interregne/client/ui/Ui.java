package fr.kodistan.interregne.client.ui;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.CrateRows;
import fr.kodistan.interregne.client.hud.InterregneHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The shared drawing pieces of Interrègne screens: plates, navigation rows, cards, buttons, state banners and chips,
 * progress bars and price lines. Everything is in GUI coordinates, so it follows Minecraft's GUI scale.
 */
public final class Ui
{
    public static final ResourceLocation COIN = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_coin.png");

    /**
     * Height of a left navigation row and of a main button.
     */
    public static final int NAV_ROW = 17;
    public static final int BUTTON_H = 18;

    public enum ButtonStyle
    {
        PRIMARY, SECONDARY, DANGER
    }

    /**
     * The three state colours, always with the same meaning.
     */
    public enum State
    {
        OK(Palette.GREEN, Palette.GREEN_SOFT, Palette.GREEN),
        WAIT(Palette.AMBER, Palette.AMBER_SOFT, Palette.AMBER_TEXT),
        BAD(Palette.RED, Palette.RED_SOFT, Palette.RED);

        private final int strong;
        private final int soft;
        private final int text;

        State(final int strong, final int soft, final int text)
        {
            this.strong = strong;
            this.soft = soft;
            this.text = text;
        }
    }

    private Ui()
    {
    }

    public static boolean inside(final double mouseX, final double mouseY, final int x, final int y, final int width, final int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * The text, cut with an ellipsis to fit the width.
     */
    public static String fit(final Font font, final String text, final int width)
    {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    public static void plate(final GuiGraphics graphics, final int x1, final int y1, final int x2, final int y2, final int fill, final int edge, final int light)
    {
        InterregneHud.roundedPlate(graphics, x1, y1, x2, y2, fill, edge, light);
    }

    /**
     * A small uppercase section title (« PRIX », « BESOINS »…).
     */
    public static void label(final GuiGraphics graphics, final Font font, final String text, final int x, final int y)
    {
        graphics.drawString(font, text, x, y, Palette.MUTED, false);
    }

    /**
     * Wrapped text; returns the y below it.
     */
    public static int wrapped(final GuiGraphics graphics, final Font font, final Component text, final int x, int y, final int width, final int color,
      final int lineHeight)
    {
        for (final FormattedCharSequence line : font.split(text, width))
        {
            graphics.drawString(font, line, x, y, color, false);
            y += lineHeight;
        }
        return y;
    }

    /**
     * A left navigation row: icon and word, leather when it is the current one.
     */
    public static void navItem(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final Icon icon, final String label,
      final boolean on, final boolean hover)
    {
        if (on)
        {
            plate(graphics, x, y, x + width, y + NAV_ROW, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
        }
        else if (hover)
        {
            graphics.fill(x, y, x + width, y + NAV_ROW, Palette.PAPER_DARK);
        }
        icon.draw(graphics, x + 1, y, 16);
        graphics.drawString(font, label, x + 20, y + 5, on ? Palette.GOLD_TEXT : Palette.INK, false);
    }

    /**
     * A card's background: gold edge when selected, darker when it cannot be used.
     */
    public static void card(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final boolean selected,
      final boolean hover, final boolean dimmed)
    {
        final int fill = dimmed ? Palette.PAPER_DARK : hover ? Palette.CARD_HOVER : Palette.PAPER_LIGHT;
        plate(graphics, x, y, x + width, y + height, fill, selected ? Palette.SELECTED : Palette.RULE, fill);
        if (selected)
        {
            graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, Palette.SELECTED);
        }
    }

    public static void button(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final int height, final String label,
      final ButtonStyle style, final boolean enabled, final boolean hover)
    {
        final int textY = y + (height - 8) / 2;
        if (!enabled)
        {
            plate(graphics, x, y, x + width, y + height, Palette.PAPER_DARK, Palette.EDGE, Palette.PAPER_DARK);
            graphics.drawCenteredString(font, label, x + width / 2, textY, Palette.MUTED);
            return;
        }
        switch (style)
        {
            case PRIMARY ->
            {
                plate(graphics, x, y, x + width, y + height, hover ? Palette.GREEN_HOVER : Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
                graphics.drawCenteredString(font, label, x + width / 2, textY, Palette.GREEN_TEXT);
            }
            case SECONDARY ->
            {
                final int fill = hover ? Palette.CARD_HOVER : Palette.PAPER_LIGHT;
                plate(graphics, x, y, x + width, y + height, fill, Palette.RULE, fill);
                graphics.drawString(font, label, x + (width - font.width(label)) / 2, textY, Palette.INK, false);
            }
            case DANGER ->
            {
                final int fill = hover ? Palette.RED_SOFT : Palette.PAPER_LIGHT;
                plate(graphics, x, y, x + width, y + height, fill, Palette.RED, fill);
                graphics.drawString(font, label, x + (width - font.width(label)) / 2, textY, Palette.RED, false);
            }
        }
    }

    /**
     * A one-line state banner: coloured dot, the state in bold, then what it means.
     */
    public static void banner(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final State state, final String title,
      final String text)
    {
        plate(graphics, x, y, x + width, y + 14, state.soft, state.strong, state.soft);
        graphics.fill(x + 5, y + 5, x + 9, y + 9, state.strong);
        final Component bold = Component.literal(title).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, bold, x + 13, y + 3, Palette.INK, false);
        final int after = x + 13 + font.width(bold);
        graphics.drawString(font, fit(font, " · " + text, x + width - 4 - after), after, y + 3, Palette.INK, false);
    }

    /**
     * A small rounded chip; returns its width.
     */
    public static int chip(final GuiGraphics graphics, final Font font, final int x, final int y, final String text, final State state)
    {
        final int width = font.width(text) + 8;
        plate(graphics, x, y, x + width, y + 11, state.soft, state.soft, state.soft);
        graphics.drawString(font, text, x + 4, y + 2, state.text, false);
        return width;
    }

    public static void progressBar(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final float fraction,
      final State state)
    {
        graphics.fill(x, y, x + width, y + height, Palette.PAPER_DARK);
        final int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        graphics.fill(x, y, x + filled, y + height, state.strong);
    }

    /**
     * A Couronne coin of this size.
     */
    public static void coin(final GuiGraphics graphics, final int x, final int y, final int size)
    {
        graphics.blit(COIN, x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    /**
     * « 🪙 240 Couronnes », red when the player cannot pay. Returns the y of the next line.
     */
    public static int priceLine(final GuiGraphics graphics, final Font font, final int x, final int y, final String text, final boolean affordable)
    {
        coin(graphics, x, y, 9);
        graphics.drawString(font, text, x + 12, y + 1, affordable ? Palette.INK : Palette.RED, false);
        return y + 11;
    }

    /**
     * One regional crate line (« 2 × Caisse de pierre »), red when missing. Returns the y of the next line.
     */
    public static int crateLine(final GuiGraphics graphics, final Font font, final int x, final int y, final CrateRows.Row row)
    {
        Icon.of(row.icon()).draw(graphics, x, y - 1, 10);
        graphics.drawString(font, row.count() + " × " + row.label().getString(), x + 12, y + 1, row.available() ? Palette.INK : Palette.RED, false);
        return y + 11;
    }
}
