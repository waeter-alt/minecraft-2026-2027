package fr.kodistan.interregne.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A grid of cards that scrolls a row at a time with the mouse wheel. Lay it out each frame, then draw and hit-test
 * the cards it says are visible.
 */
public final class ScrollGrid
{
    private int columns;
    private final int gap;

    private int left;
    private int top;
    private int width;
    private int bottom;
    private int cardHeight;
    private int scroll;

    public ScrollGrid(final int columns, final int gap)
    {
        this.columns = columns;
        this.gap = gap;
    }

    /**
     * Where the grid sits: its top-left corner, its width and the lowest y a card may reach.
     */
    public void layout(final int left, final int top, final int width, final int bottom, final int cardHeight)
    {
        this.left = left;
        this.top = top;
        this.width = width;
        this.bottom = bottom;
        this.cardHeight = cardHeight;
    }

    /**
     * Fewer columns when the grid is narrow, so each card keeps room for its text.
     */
    public void setColumns(final int columns)
    {
        if (columns != this.columns)
        {
            // Keep the first visible card in view
            scroll = scroll * this.columns / columns;
            this.columns = columns;
        }
    }

    public int cardWidth()
    {
        return (width - (columns - 1) * gap) / columns;
    }

    public int visibleRows()
    {
        return Math.max(1, (bottom - top + gap) / (cardHeight + gap));
    }

    public int rows(final int count)
    {
        return (count + columns - 1) / columns;
    }

    /**
     * Keep the scroll inside the list; call before drawing.
     */
    public void clamp(final int count)
    {
        scroll = Math.max(0, Math.min(scroll, rows(count) - visibleRows()));
    }

    public void scroll(final double wheel)
    {
        scroll -= (int) Math.signum(wheel);
    }

    public void reset()
    {
        scroll = 0;
    }

    /**
     * The first visible card's index.
     */
    public int first()
    {
        return scroll * columns;
    }

    /**
     * One past the last visible card's index.
     */
    public int end(final int count)
    {
        return Math.min(count, (scroll + visibleRows()) * columns);
    }

    public int x(final int index)
    {
        return left + (index % columns) * (cardWidth() + gap);
    }

    public int y(final int index)
    {
        return top + (index / columns - scroll) * (cardHeight + gap);
    }

    /**
     * The visible card under the mouse, or -1.
     */
    public int hit(final double mouseX, final double mouseY, final int count)
    {
        for (int i = first(); i < end(count); i++)
        {
            if (Ui.inside(mouseX, mouseY, x(i), y(i), cardWidth(), cardHeight))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * A thin scrollbar this many pixels right of the grid, only when the list overflows.
     */
    public void renderScrollbar(final GuiGraphics graphics, final int count, final int offset)
    {
        final int rows = rows(count);
        if (rows <= visibleRows())
        {
            return;
        }
        final int trackH = visibleRows() * (cardHeight + gap) - gap;
        final int thumbH = Math.max(10, trackH * visibleRows() / rows);
        final int thumbY = top + (trackH - thumbH) * scroll / Math.max(1, rows - visibleRows());
        final int sx = left + width + offset;
        graphics.fill(sx, top, sx + 2, top + trackH, Palette.PAPER_DARK);
        graphics.fill(sx, thumbY, sx + 2, thumbY + thumbH, Palette.LEATHER);
    }
}
