package fr.kodistan.interregne.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * An Interrègne window: a parchment sheet of at most 480 × 260 GUI pixels, centred, with its title and ✕ in the header.
 * The world stays visible behind a light veil and the game does not pause. Escape and ✕ go back to the screen it was
 * opened from (the colony hub, the catalogue…), or close it when there is none.
 */
public abstract class ParchmentScreen extends Screen
{
    public static final int MAX_W = 480;
    public static final int MAX_H = 260;

    /**
     * The header's height: content starts below this line.
     */
    public static final int HEADER = 20;

    @Nullable
    protected final Screen parent;

    protected int x0;
    protected int y0;
    protected int w;
    protected int h;

    protected ParchmentScreen(final Component title, @Nullable final Screen parent)
    {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        w = Math.min(width - 16, MAX_W);
        h = Math.min(height - 16, MAX_H);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        graphics.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        Ui.plate(graphics, x0, y0, x0 + w, y0 + h, Palette.PAPER, Palette.EDGE, Palette.PAPER_LIGHT);
        int titleX = x0 + 8;
        final String crumb = crumb();
        if (crumb != null)
        {
            graphics.drawString(font, crumb + " › ", titleX, y0 + 7, Palette.MUTED, false);
            titleX += font.width(crumb + " › ");
        }
        graphics.drawString(font, title, titleX, y0 + 7, Palette.INK, false);
        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, overClose(mouseX, mouseY) ? Palette.RED : Palette.MUTED, false);
        graphics.fill(x0 + 4, y0 + 19, x0 + w - 4, y0 + 20, Palette.RULE);
        renderContent(graphics, mouseX, mouseY);
    }

    /**
     * Everything below the header.
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY);

    /**
     * Where this window sits, shown before the title (« Colonie »…), or null.
     */
    @Nullable
    protected String crumb()
    {
        return null;
    }

    /**
     * A left click below the header; true when it hit something.
     */
    protected abstract boolean clicked(double mouseX, double mouseY);

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button == 0)
        {
            if (overClose(mouseX, mouseY))
            {
                onClose();
                return true;
            }
            if (clicked(mouseX, mouseY))
            {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean overClose(final double mouseX, final double mouseY)
    {
        return Ui.inside(mouseX, mouseY, x0 + w - 16, y0 + 4, 12, 12);
    }

    @Override
    public void onClose()
    {
        if (parent != null)
        {
            minecraft.setScreen(parent);
        }
        else
        {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /**
     * A vertical rule at this x, between header and bottom edge.
     */
    protected void verticalRule(final GuiGraphics graphics, final int x)
    {
        graphics.fill(x, y0 + 22, x + 1, y0 + h - 4, Palette.RULE);
    }

    /**
     * The lighter detail panel on the right, this wide; returns its left edge.
     */
    protected int detailPanel(final GuiGraphics graphics, final int panelWidth)
    {
        final int left = x0 + w - panelWidth;
        verticalRule(graphics, left - 1);
        graphics.fill(left, y0 + 20, x0 + w - 3, y0 + h - 3, Palette.PANEL);
        return left;
    }

    /**
     * The y of the main action button at the bottom right.
     */
    protected int actionY()
    {
        return y0 + h - 26;
    }

    protected String fit(final String text, final int width)
    {
        return Ui.fit(font, text, width);
    }
}
