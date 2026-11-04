package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.PageScreen;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * What the windows of our blocks share, so each reads the same way: first the hero card with the one figure that
 * matters (a balance, a treasury, a reserve) in gold on leather, then the main action as a wide green button, then
 * the other moves, one panel each. The server's answer refreshes the window in place.
 */
public abstract class CounterScreen extends PageScreen
{
    protected static final long[] AMOUNTS = {10, 100, 1_000, 10_000};
    protected static final int HERO_H = 42;
    private static final int HERO_MUTED = 0xFFD8BF8C;

    protected CounterScreen(final String title, final int preferredWidth, final int preferredHeight)
    {
        super(Component.literal(title), null, preferredWidth);
        this.preferredHeight = preferredHeight;
    }

    /**
     * The Couronnes the player carries, counted on this side.
     */
    protected long purse()
    {
        return minecraft.player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory()));
    }

    /**
     * The typed amount, or 0 when nothing sensible is typed.
     */
    protected long typed()
    {
        if (search == null)
        {
            return 0;
        }
        try
        {
            return Math.max(0, Long.parseLong(search.getValue().replace(" ", "").trim()));
        }
        catch (final NumberFormatException e)
        {
            return 0;
        }
    }

    /**
     * The page's amount field, digits only; the panel that uses it places it. Call from {@link #init()}.
     */
    protected void amountField()
    {
        addField(0, 0, 76, "Montant").setFilter(text -> text.matches("[0-9 ]*"));
    }

    /**
     * The hero card: an icon (a coin when none), what the figure is in small capitals, the figure in bold gold, and
     * one line saying what it means. Returns the y below it.
     */
    protected int hero(final GuiGraphics graphics, final int x, final int y, final int width, @Nullable final ItemStack icon, final String label,
      final String value, final String line)
    {
        Ui.plate(graphics, x, y, x + width, y + HERO_H, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
        if (icon == null)
        {
            Ui.coin(graphics, x + 9, y + 9, 24);
        }
        else
        {
            Icon.of(icon).draw(graphics, x + 9, y + 9, 24);
        }
        graphics.drawString(font, label, x + 42, y + 7, HERO_MUTED, false);
        graphics.drawString(font, Component.literal(value).withStyle(ChatFormatting.BOLD), x + 42, y + 18, Palette.GOLD_TEXT, true);
        graphics.drawString(font, fit(line, width - 50), x + 42, y + 30, HERO_MUTED, false);
        return y + HERO_H + 6;
    }

    /**
     * A thin gauge along the hero card's bottom.
     */
    protected void heroGauge(final GuiGraphics graphics, final int x, final int y, final int width, final long value, final long max)
    {
        final int gx = x + 42;
        final int gw = width - 50;
        final int gy = y + HERO_H - 4;
        graphics.fill(gx, gy, gx + gw, gy + 2, Palette.LEATHER_LIGHT);
        final int filled = max <= 0 ? 0 : (int) Math.min(gw, gw * value / max);
        graphics.fill(gx, gy, gx + filled, gy + 2, value >= max ? Palette.AMBER : Palette.GREEN_LIGHT);
    }

    /**
     * The window's main action, wide and green; grey with the reason on hover when it is not possible.
     */
    protected int bigButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String label,
      final boolean enabled, @Nullable final String why, final Runnable action)
    {
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 20);
        Ui.button(graphics, font, x, y, width, 20, label, Ui.ButtonStyle.PRIMARY, enabled, over);
        if (enabled)
        {
            addHit(x, y, width, 20, action);
        }
        if (over && why != null)
        {
            hint = why;
        }
        return y + 25;
    }

    /**
     * A panel for one way the money moves: the verb in bold with a note on the right, one button per quick amount,
     * and, with the field, a typed amount whose result shows as it is typed. why gives the reason an amount is not
     * possible (null when it is); preview what a possible amount does. Returns the y below the panel.
     */
    protected int panel(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String verb,
      @Nullable final String note, final long[] amounts, final LongFunction<String> why, final LongConsumer action, final boolean field,
      @Nullable final LongFunction<String> preview)
    {
        final int height = field ? 56 : 38;
        Ui.plate(graphics, x, y, x + width, y + height, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        graphics.drawString(font, Component.literal(verb).withStyle(ChatFormatting.BOLD), x + 6, y + 6, Palette.INK, false);
        if (note != null)
        {
            graphics.drawString(font, note, x + width - 6 - font.width(note), y + 6, Palette.MUTED, false);
        }
        int bx = x + 6;
        for (final long amount : amounts)
        {
            final String reason = why.apply(amount);
            bx += button(graphics, mouseX, mouseY, bx, y + 18, Wallet.format(amount), Ui.ButtonStyle.SECONDARY, reason == null, reason, () -> action.accept(amount)) + 3;
        }
        if (field && search != null)
        {
            final int fy = y + 37;
            search.setX(x + 6);
            search.setY(fy + 1);
            final long typed = typed();
            final String reason = typed == 0 ? "Tape un montant." : why.apply(typed);
            final int bw = button(graphics, mouseX, mouseY, x + 86, fy, verb, Ui.ButtonStyle.PRIMARY, reason == null, reason, () -> {
                action.accept(typed);
                search.setValue("");
            });
            if (typed > 0)
            {
                final String result = reason != null ? reason : preview == null ? null : preview.apply(typed);
                if (result != null)
                {
                    graphics.drawString(font, fit(result, width - bw - 98), x + 92 + bw, fy + 3, reason != null ? Palette.RED : Palette.MUTED, false);
                }
            }
        }
        return y + height + 5;
    }

    protected static String couronnes(final long amount)
    {
        return Wallet.format(amount) + (amount > 1 ? " couronnes" : " couronne");
    }

    @Nullable
    protected static String notMore(final long amount, final long available, final String what)
    {
        return amount > available ? "Pas assez " + what + "." : null;
    }
}
