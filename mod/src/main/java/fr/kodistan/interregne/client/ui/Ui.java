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
import net.minecraft.world.item.ItemStack;

import java.util.List;

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
    /**
     * The left column, with its labels or as a rail of icons.
     */
    public static final int NAV_FULL = 112;
    public static final int NAV_RAIL = 26;
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

    /**
     * An item drawn somewhere, for its tooltip on hover.
     */
    public record Slot(int x, int y, int size, ItemStack stack)
    {
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
     * A full-width rule between two sections, with air above and below; returns the y below it.
     */
    public static int separator(final GuiGraphics graphics, final int x, final int y, final int width)
    {
        graphics.fill(x, y + 5, x + width, y + 6, Palette.RULE);
        return y + 11;
    }

    /**
     * Stages of items joined by arrows (« pavé → gravier → sable »), each stage on a small plate. Items shrink when the
     * stages do not fit at full size. Returns the y below it; the drawn items are added to slots.
     */
    public static int flow(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final List<List<ItemStack>> stages,
      final List<Slot> slots)
    {
        final int arrow = font.width("→") + 6;
        int count = 0;
        for (final List<ItemStack> stage : stages)
        {
            count += stage.size();
        }
        final int fixed = (stages.size() - 1) * arrow + stages.size() * 4;
        final int size = count * 17 + fixed <= width ? 16 : Math.max(8, (width - fixed) / Math.max(1, count) - 1);
        int cx = x;
        for (int s = 0; s < stages.size(); s++)
        {
            final List<ItemStack> stage = stages.get(s);
            final int plateW = stage.size() * (size + 1) + 3;
            plate(graphics, cx, y, cx + plateW, y + size + 4, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
            for (int i = 0; i < stage.size(); i++)
            {
                final int ix = cx + 2 + i * (size + 1);
                Icon.of(stage.get(i)).draw(graphics, ix, y + 2, size);
                slots.add(new Slot(ix, y + 2, size, stage.get(i)));
            }
            cx += plateW;
            if (s < stages.size() - 1)
            {
                graphics.drawString(font, "→", cx + 3, y + (size + 4 - 8) / 2, Palette.LEATHER, false);
                cx += arrow;
            }
        }
        return y + size + 4;
    }

    /**
     * A left navigation row: icon and word, leather when it is the current one.
     */
    /**
     * The left column's width for a window this wide: labels when the page keeps enough room, icons only otherwise.
     */
    public static int navWidth(final int windowWidth)
    {
        return windowWidth >= 470 ? NAV_FULL : NAV_RAIL;
    }

    public static boolean rail(final int navWidth)
    {
        return navWidth < NAV_FULL;
    }

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
        if (width < 40)
        {
            // A rail: the icon alone, its name on hover
            icon.draw(graphics, x + (width - 16) / 2, y, 16);
            if (hover)
            {
                graphics.renderTooltip(font, Component.literal(label), x + width + 2, y + 13);
            }
            return;
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
            graphics.drawString(font, label, x + (width - font.width(label)) / 2, textY, Palette.MUTED, false);
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
     * A button in a building level's colour, for what buys that level.
     */
    public static void levelButton(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final int height, final String label,
      final int level, final boolean enabled, final boolean hover)
    {
        if (!enabled)
        {
            button(graphics, font, x, y, width, height, label, ButtonStyle.PRIMARY, false, hover);
            return;
        }
        plate(graphics, x, y, x + width, y + height, Palette.levelFill(level), Palette.levelEdge(level), Palette.levelFill(level));
        if (hover)
        {
            graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, Palette.levelText(level));
        }
        final Component bold = Component.literal(label).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, bold, x + (width - font.width(bold)) / 2, y + (height - 8) / 2, Palette.levelText(level), false);
    }

    /**
     * A small badge in a building level's colour (« Niv. 3 »); returns its width.
     */
    public static int levelBadge(final GuiGraphics graphics, final Font font, final int x, final int y, final String text, final int level)
    {
        final int width = font.width(text) + 8;
        plate(graphics, x, y, x + width, y + 12, Palette.levelFill(level), Palette.levelEdge(level), Palette.levelFill(level));
        graphics.drawString(font, text, x + 4, y + 2, Palette.levelText(level), false);
        return width;
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

    /**
     * A chip in its own colours; a clickable one has an edge, darker on hover.
     */
    public static void chip(final GuiGraphics graphics, final Font font, final int x, final int y, final String text, final int soft, final int ink,
      final boolean clickable, final boolean hover)
    {
        final int width = font.width(text) + 8;
        plate(graphics, x, y, x + width, y + 11, soft, clickable ? ink : soft, soft);
        if (hover)
        {
            graphics.renderOutline(x + 1, y + 1, width - 2, 9, ink);
        }
        graphics.drawString(font, text, x + 4, y + 2, ink, false);
    }

    /**
     * A small filled button in a state's strong colour, white text: the action that fixes that state.
     */
    public static void solidButton(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final int height,
      final String label, final State state, final boolean hover)
    {
        plate(graphics, x, y, x + width, y + height, state.strong, Palette.EDGE, state.strong);
        if (hover)
        {
            graphics.renderOutline(x + 1, y + 1, width - 2, height - 2, Palette.WHITE);
        }
        graphics.drawString(font, label, x + (width - font.width(label)) / 2, y + (height - 8) / 2, Palette.WHITE, false);
    }

    public static void progressBar(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final float fraction,
      final State state)
    {
        graphics.fill(x, y, x + width, y + height, Palette.PAPER_DARK);
        final int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        graphics.fill(x, y, x + filled, y + height, state.strong);
    }

    public static void progressBar(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final float fraction,
      final int color, final int edge)
    {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, edge);
        graphics.fill(x, y, x + width, y + height, Palette.PAPER_DARK);
        final int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        graphics.fill(x, y, x + filled, y + height, color);
    }

    /**
     * A Couronne coin of this size.
     */
    public static void coin(final GuiGraphics graphics, final int x, final int y, final int size)
    {
        graphics.blit(COIN, x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    /**
     * The coin cropped to its own pixels (the texture is mostly empty), so it reads as big as the Influence star
     * drawn at the same width.
     */
    public static void coinTight(final GuiGraphics graphics, final int x, final int y, final int width)
    {
        final int height = width * 7 / 9;
        graphics.blit(COIN, x, y + (width - height) / 2, width, height, 3, 4, 9, 7, 16, 16);
    }

    public static final ResourceLocation INFLUENCE = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_influence.png");

    /**
     * The purple star of Influence, as on the HUD.
     */
    public static void influence(final GuiGraphics graphics, final int x, final int y, final int size)
    {
        graphics.blit(INFLUENCE, x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    /**
     * « 🪙 240 Couronnes », red when the player cannot pay. Returns the y of the next line.
     */
    public static int priceLine(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final String text, final boolean affordable)
    {
        coin(graphics, x, y, 9);
        graphics.drawString(font, fit(font, text, width - 12), x + 12, y + 1, affordable ? Palette.INK : Palette.RED, false);
        return y + 11;
    }

    /**
     * One regional crate line (« 2 × Caisse de pierre »), red when missing. Returns the y of the next line.
     */
    public static int crateLine(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final CrateRows.Row row)
    {
        Icon.of(row.icon()).draw(graphics, x, y - 1, 10);
        // Narrow: the icon already says it is a crate, the name keeps only what is inside
        String text = row.count() + " × " + row.label().getString();
        if (font.width(text) > width - 12)
        {
            text = row.count() + " × " + row.label().getString().replaceFirst("^Caisse (de |d'|d’)", "");
        }
        graphics.drawString(font, fit(font, text, width - 12), x + 12, y + 1, row.available() ? Palette.INK : Palette.RED, false);
        return y + 11;
    }
}
