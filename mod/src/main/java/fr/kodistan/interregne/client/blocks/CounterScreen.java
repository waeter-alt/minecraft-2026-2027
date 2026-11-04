package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ui.PageScreen;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * What the windows of our money blocks share: amounts in a row of quick buttons, a typed amount, and the server's
 * answer refreshing the window in place.
 */
public abstract class CounterScreen extends PageScreen
{
    protected static final long[] AMOUNTS = {10, 100, 1_000, 10_000};

    protected CounterScreen(final String title, final int preferredWidth, final int preferredHeight)
    {
        super(Component.literal(title), null, preferredWidth);
        this.preferredHeight = preferredHeight;
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
     * A field for an amount, digits only, at this place.
     */
    protected void amountField(final int x, final int y, final int width)
    {
        addField(x, y, width, "Montant").setFilter(text -> text.matches("[0-9 ]*"));
    }

    /**
     * One button per amount after a label; why returns the reason an amount is not possible, or null when it is.
     * Returns the y below the row.
     */
    protected int amounts(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final String label, final long[] amounts,
      final LongFunction<String> why, final LongConsumer action)
    {
        graphics.drawString(font, label, x + 2, y + 3, Palette.INK, false);
        int bx = x + 70;
        for (final long amount : amounts)
        {
            final String text = Wallet.format(amount);
            final String reason = why.apply(amount);
            bx += button(graphics, mouseX, mouseY, bx, y, text, Ui.ButtonStyle.SECONDARY, reason == null, reason, () -> action.accept(amount)) + 3;
        }
        return y + 18;
    }

    /**
     * A thin gauge of how full something is.
     */
    protected void gauge(final GuiGraphics graphics, final int x, final int y, final int width, final long value, final long max)
    {
        graphics.fill(x, y, x + width, y + 4, Palette.PAPER_DARK);
        final int filled = max <= 0 ? 0 : (int) Math.min(width, width * value / max);
        graphics.fill(x, y, x + filled, y + 4, value >= max ? Palette.AMBER_TEXT : Palette.GREEN);
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
