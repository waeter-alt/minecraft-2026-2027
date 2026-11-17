package fr.kodistan.interregne.client.hut;

import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * The building blocks of a hut body: the hero card with the one figure that matters, the wide main button, a panel for one
 * way the money moves, small buttons and choosers. Same look as the windows of our blocks ({@code CounterScreen}), drawn
 * inside the hut window.
 */
public final class BodyKit
{
    /**
     * What the window gives its body: the font, a place to register clickable areas and the hover hint, the amount field.
     */
    public interface Host
    {
        Font font();

        void hit(int x, int y, int width, int height, Runnable action);

        void hint(String text);

        /** The window's amount field (digits), or null when the body did not ask for one. */
        @Nullable
        EditBox field();

        /** Tells the window the field is drawn this frame, at its position. */
        void fieldShown();

        /** An item drawn this frame, to show its tooltip on hover. */
        void slot(int x, int y, int size, ItemStack stack);
    }

    public static final long[] AMOUNTS = {10, 100, 1_000, 10_000};
    private static final int HERO_H = 46;
    private static final int HERO_MUTED = 0xFFD8BF8C;

    private final Host host;
    private int heroExtra;

    BodyKit(final Host host)
    {
        this.host = host;
    }

    public Font font()
    {
        return host.font();
    }

    public void slot(final int x, final int y, final int size, final ItemStack stack)
    {
        host.slot(x, y, size, stack);
    }

    /**
     * Small text in a colour, wrapped to the width rather than cut; returns the y below it.
     */
    public int note(final GuiGraphics graphics, final int x, final int y, final int width, final String text, final int color)
    {
        return Ui.wrapped(graphics, font(), Component.literal(text), x, y, width, color, 9);
    }

    public String fit(final String text, final int width)
    {
        return Ui.fit(font(), text, width);
    }

    /**
     * The Couronnes the player carries.
     */
    public long purse()
    {
        final var player = Minecraft.getInstance().player;
        return player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(player.getInventory()));
    }

    /**
     * The typed amount, or 0 when nothing sensible is typed.
     */
    public long typed()
    {
        final EditBox field = host.field();
        if (field == null)
        {
            return 0;
        }
        try
        {
            return Math.max(0, Long.parseLong(field.getValue().replace(" ", "").trim()));
        }
        catch (final NumberFormatException e)
        {
            return 0;
        }
    }

    public static String couronnes(final long amount)
    {
        return Wallet.format(amount) + (amount > 1 ? " couronnes" : " couronne");
    }

    @Nullable
    public static String notMore(final long amount, final long available, final String what)
    {
        return amount > available ? "Pas assez " + what + "." : null;
    }

    /**
     * The hero card: an icon (a coin when none), what the figure is in small capitals, the figure in bold gold and one line
     * saying what it means. A long line wraps and the card grows. Returns the y below it.
     */
    public int hero(final GuiGraphics graphics, final int x, final int y, final int width, @Nullable final ItemStack icon, final String label, final String value,
      final String line)
    {
        final List<FormattedCharSequence> lines = font().split(Component.literal(line), width - 50);
        heroExtra = Math.max(0, lines.size() - 1) * 10;
        Ui.plate(graphics, x, y, x + width, y + HERO_H + heroExtra, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
        if (icon == null)
        {
            Ui.coin(graphics, x + 9, y + 9, 24);
        }
        else
        {
            Icon.of(icon).draw(graphics, x + 9, y + 9, 24);
        }
        graphics.drawString(font(), fit(label, width - 50), x + 42, y + 7, HERO_MUTED, false);
        graphics.drawString(font(), Component.literal(fit(value, width - 50)).withStyle(ChatFormatting.BOLD), x + 42, y + 18, Palette.GOLD_TEXT, true);
        int ly = y + 30;
        for (final FormattedCharSequence part : lines)
        {
            graphics.drawString(font(), part, x + 42, ly, HERO_MUTED, false);
            ly += 10;
        }
        return y + HERO_H + heroExtra + 6;
    }

    /**
     * A thin gauge along the bottom of the hero card drawn last.
     */
    public void heroGauge(final GuiGraphics graphics, final int x, final int y, final int width, final long value, final long max)
    {
        final int gx = x + 42;
        final int gw = width - 50;
        final int gy = y + HERO_H + heroExtra - 4;
        graphics.fill(gx, gy, gx + gw, gy + 2, Palette.LEATHER_LIGHT);
        final int filled = max <= 0 ? 0 : (int) Math.min(gw, gw * value / max);
        graphics.fill(gx, gy, gx + filled, gy + 2, value >= max ? Palette.AMBER : Palette.GREEN_LIGHT);
    }

    /**
     * The page's main action, wide and green; grey with the reason on hover when it is not possible. The label is cut to fit.
     */
    public int bigButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String label,
      final boolean enabled, @Nullable final String why, final Runnable action)
    {
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 20);
        Ui.button(graphics, font(), x, y, width, 20, fit(label, width - 8), Ui.ButtonStyle.PRIMARY, enabled, over);
        if (enabled)
        {
            host.hit(x, y, width, 20, action);
        }
        if (over && why != null)
        {
            host.hint(why);
        }
        return y + 25;
    }

    /**
     * A small button that runs its action when enabled and explains itself on hover; returns its width.
     */
    public int button(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final String label, final Ui.ButtonStyle style,
      final boolean enabled, @Nullable final String why, final Runnable action)
    {
        final int width = font().width(label) + 10;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 14);
        Ui.button(graphics, font(), x, y, width, 14, label, style, enabled, over);
        if (enabled)
        {
            host.hit(x, y, width, 14, action);
        }
        if (over && why != null)
        {
            host.hint(why);
        }
        return width;
    }

    public int buttonWidth(final String label)
    {
        return font().width(label) + 10;
    }

    /**
     * Exclusive choices; the chosen one is green. They go on to a new row when they would pass {@code right}. Returns the y below them.
     */
    public int segmented(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int right, int y, final List<String> labels, final int current,
      final IntConsumer choose)
    {
        int bx = x;
        for (int i = 0; i < labels.size(); i++)
        {
            final String label = labels.get(i);
            final int bw = font().width(label) + 10;
            if (bx > x && bx + bw > right)
            {
                bx = x;
                y += 17;
            }
            final boolean on = i == current;
            final boolean over = Ui.inside(mouseX, mouseY, bx, y, bw, 14);
            if (on)
            {
                Ui.plate(graphics, bx, y, bx + bw, y + 14, Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
            }
            else
            {
                Ui.plate(graphics, bx, y, bx + bw, y + 14, over ? Palette.CARD_HOVER : Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
            }
            graphics.drawString(font(), label, bx + 5, y + 3, on ? Palette.PAPER_LIGHT : Palette.INK, false);
            final int index = i;
            host.hit(bx, y, bw, 14, () -> choose.accept(index));
            bx += bw + 3;
        }
        return y + 17;
    }

    /**
     * A card with an icon, a bold title, a description and one button on the right; the description wraps in the room the
     * button leaves and the card grows to hold it. Returns the next y.
     */
    public int actionCard(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final Icon icon, final String title,
      final String text, final String label, final boolean enabled, @Nullable final String why, final Runnable action)
    {
        final int bw = buttonWidth(label);
        final int textW = Math.max(40, width - 26 - bw - 12);
        final Component bold = Component.literal(title).withStyle(ChatFormatting.BOLD);
        final List<FormattedCharSequence> lines = font().split(Component.literal(text), textW);
        // The title wraps too: a long colony name must not be cut
        final int titleLines = font().split(bold, textW).size();
        final int height = Math.max(34, 12 + titleLines * 10 + lines.size() * 10 + 5);
        Ui.card(graphics, x, y, width, height, false, false, false);
        icon.draw(graphics, x + 5, y + (height - 16) / 2, 16);
        int ty = Ui.wrapped(graphics, font(), bold, x + 26, y + 6, textW, Palette.INK, 10) + 2;
        for (final FormattedCharSequence line : lines)
        {
            graphics.drawString(font(), line, x + 26, ty, Palette.MUTED, false);
            ty += 10;
        }
        button(graphics, mouseX, mouseY, x + width - 6 - bw, y + (height - 14) / 2, label, Ui.ButtonStyle.PRIMARY, enabled, why, action);
        return y + height + 4;
    }

    /**
     * A panel for one way the money moves: the verb in bold with a note on the right, one button per quick amount and, with
     * the field, a typed amount whose result shows as it is typed. {@code why} gives the reason an amount is not possible
     * (null when it is); {@code preview} what a possible amount does. Returns the y below the panel.
     */
    public int panel(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String verb, @Nullable final String note,
      final long[] amounts, final LongFunction<String> why, final LongConsumer action, final boolean field, @Nullable final LongFunction<String> preview)
    {
        final int height = field ? 56 : 38;
        Ui.plate(graphics, x, y, x + width, y + height, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        graphics.drawString(font(), Component.literal(verb).withStyle(ChatFormatting.BOLD), x + 6, y + 6, Palette.INK, false);
        if (note != null)
        {
            final String shown = fit(note, width - 20 - font().width(verb));
            graphics.drawString(font(), shown, x + width - 6 - font().width(shown), y + 6, Palette.MUTED, false);
        }
        // Quick amounts: as many as fit on the row, the others dropped rather than cut
        int bx = x + 6;
        for (final long amount : amounts)
        {
            final String label = Wallet.format(amount);
            if (bx + buttonWidth(label) > x + width - 6)
            {
                break;
            }
            final String reason = why.apply(amount);
            bx += button(graphics, mouseX, mouseY, bx, y + 18, label, Ui.ButtonStyle.SECONDARY, reason == null, reason, () -> action.accept(amount)) + 3;
        }
        final EditBox box = host.field();
        if (field && box != null)
        {
            final int fy = y + 37;
            box.setX(x + 6);
            box.setY(fy + 1);
            host.fieldShown();
            final long typed = typed();
            final String reason = typed == 0 ? "Tape un montant." : why.apply(typed);
            final int bw = button(graphics, mouseX, mouseY, x + 86, fy, verb, Ui.ButtonStyle.PRIMARY, reason == null, reason, () -> {
                action.accept(typed);
                box.setValue("");
            });
            if (typed > 0)
            {
                final String result = reason != null ? reason : preview == null ? null : preview.apply(typed);
                if (result != null)
                {
                    graphics.drawString(font(), fit(result, width - bw - 98), x + 92 + bw, fy + 3, reason != null ? Palette.RED : Palette.MUTED, false);
                }
            }
        }
        return y + height + 5;
    }
}
