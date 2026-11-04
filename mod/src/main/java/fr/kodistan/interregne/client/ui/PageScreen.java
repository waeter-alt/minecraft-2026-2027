package fr.kodistan.interregne.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A window with a fixed top (banner, filters, a field) and a scrolling body below: click targets clipped to what is
 * visible, item and text tooltips, a search or input field, and small controls (buttons that explain why they are
 * grey, choices side by side, check boxes). A page draws its top in {@link #renderTop} and its body in
 * {@link #renderBody}; it is as wide and tall as it asks, within the parchment's limits.
 */
public abstract class PageScreen extends ParchmentScreen
{
    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    protected String hint;
    private int scroll;
    private int contentHeight;
    private final int preferredWidth;
    /**
     * How tall the window may be; a short form asks for less than the parchment's full height.
     */
    protected int preferredHeight = MAX_H;
    /**
     * The visible part of the body this frame: hits outside it are dropped.
     */
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;
    /**
     * A search field, drawn above the parchment's content when the page has one.
     */
    @Nullable
    protected EditBox search;

    protected PageScreen(final Component title, @Nullable final Screen parent, final int preferredWidth)
    {
        super(title, parent);
        this.preferredWidth = preferredWidth;
    }

    /**
     * Whether the window takes its own size; false when it keeps a larger frame's.
     */
    protected boolean compact()
    {
        return true;
    }

    @Override
    protected void init()
    {
        super.init();
        if (compact())
        {
            w = Math.min(width - 16, preferredWidth);
            x0 = (width - w) / 2;
            h = Math.min(height - 16, preferredHeight);
            y0 = (height - h) / 2;
        }
    }

    /**
     * A search field at the right of the content, dy below the header; call from {@link #init()} of the page. What was typed survives a
     * resize.
     */
    protected void addSearch(final int dy)
    {
        addField(x0 + w - 128, contentTop() - 6 + dy, 116, "Rechercher…");
    }

    /**
     * The page's text field (a search, a name, an amount…) at this place; call from {@link #init()} of the page. What was
     * typed survives a resize.
     */
    protected EditBox addField(final int x, final int y, final int width, final String placeholder)
    {
        final String typed = search == null ? "" : search.getValue();
        search = new EditBox(font, x, y, width, 12, Component.literal(placeholder));
        search.setBordered(true);
        search.setHint(Component.literal(placeholder));
        search.setValue(typed);
        search.setResponder(text -> scroll = 0);
        addWidget(search);
        return search;
    }

    protected String query()
    {
        return search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
    }

    protected int left()
    {
        return x0 + 10;
    }

    /**
     * Where the page's top starts, below the window's header.
     */
    protected int contentTop()
    {
        return y0 + HEADER + 6;
    }

    protected int innerWidth()
    {
        return w - 24;
    }

    /**
     * What a window draws around its page before it (a building's left column…).
     */
    protected void renderChrome(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
    }

    /**
     * The fixed part under the header; returns the y where the scrolling body starts.
     */
    protected abstract int renderTop(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width);

    /**
     * The scrolling body from y; returns the y below its last line. mouseY is off-screen when the mouse is outside it.
     */
    protected abstract int renderBody(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width);

    @Override
    protected final void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        hits.clear();
        slots.clear();
        hint = null;
        final int x = left();
        final int width = innerWidth();
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        renderChrome(graphics, mouseX, mouseY);
        final int top = renderTop(graphics, mouseX, mouseY, x, contentTop(), width);
        if (search != null)
        {
            search.render(graphics, mouseX, mouseY, 0);
        }

        final int bottom = y0 + h - 5;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        clipTop = top;
        clipBottom = bottom;
        graphics.enableScissor(x, top, x + width, bottom);
        final int end = renderBody(graphics, mouseX, my, x, top - scroll, width);
        graphics.disableScissor();
        contentHeight = end + scroll - top;
        if (contentHeight > bottom - top)
        {
            final int track = bottom - top;
            final int thumb = Math.max(12, track * track / contentHeight);
            final int thumbY = top + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            graphics.fill(x + width + 2, top, x + width + 4, bottom, Palette.PAPER_DARK);
            graphics.fill(x + width + 2, thumbY, x + width + 4, thumbY + thumb, Palette.RULE);
        }

        for (final Ui.Slot slot : slots)
        {
            if (Ui.inside(mouseX, mouseY, slot.x(), slot.y(), slot.size(), slot.size()))
            {
                graphics.renderTooltip(font, slot.stack(), mouseX, mouseY);
                return;
            }
        }
        if (hint != null)
        {
            graphics.renderTooltip(font, font.split(Component.literal(hint), 220), mouseX, mouseY);
        }
    }

    /**
     * A click target; in the body only its visible part counts.
     */
    protected void addHit(final int x, final int y, final int width, final int height, final Runnable action)
    {
        final int hitTop = Math.max(y, clipTop);
        final int hitBottom = Math.min(y + height, clipBottom);
        if (hitBottom > hitTop)
        {
            hits.add(new Hit(x, hitTop, width, hitBottom - hitTop, action));
        }
    }

    /**
     * An item whose tooltip shows on hover, when the mouse is over its visible part.
     */
    protected void addSlot(final int x, final int y, final int size, final ItemStack stack, final int mouseY)
    {
        if (mouseY >= clipTop && mouseY < clipBottom)
        {
            slots.add(new Ui.Slot(x, y, size, stack));
        }
    }

    protected void resetScroll()
    {
        scroll = 0;
    }

    /**
     * A clickable « ☐/☑ label » line; returns its width.
     */
    protected int checkbox(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final String label, final boolean checked,
      final Runnable toggle)
    {
        final String text = (checked ? "☑ " : "☐ ") + label;
        final int width = font.width(text);
        final boolean over = Ui.inside(mouseX, mouseY, x - 2, y - 1, width + 4, 11);
        graphics.drawString(font, text, x, y, over ? Palette.SELECTED : Palette.INK, false);
        addHit(x - 2, y - 1, width + 4, 11, toggle);
        return width;
    }

    /**
     * Choices side by side, the current one in green; returns the total width.
     */
    protected int segmented(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final List<String> labels, final int current,
      final java.util.function.IntConsumer choose)
    {
        int bx = x;
        for (int i = 0; i < labels.size(); i++)
        {
            final String label = labels.get(i);
            final int bw = font.width(label) + 10;
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
            graphics.drawString(font, label, bx + 5, y + 3, on ? Palette.GREEN_TEXT : Palette.INK, false);
            if (!on)
            {
                final int index = i;
                addHit(bx, y, bw, 14, () -> choose.accept(index));
            }
            bx += bw - 1;
        }
        return bx - x;
    }

    /**
     * A small button that runs its action when enabled and explains itself on hover; returns its width.
     */
    protected int button(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final String label, final Ui.ButtonStyle style,
      final boolean enabled, @Nullable final String why, final Runnable action)
    {
        final int width = font.width(label) + 10;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 14);
        Ui.button(graphics, font, x, y, width, 14, label, style, enabled, over);
        if (enabled)
        {
            addHit(x, y, width, 14, action);
        }
        if (over && why != null)
        {
            hint = why;
        }
        return width;
    }

    protected int buttonWidth(final String label)
    {
        return font.width(label) + 10;
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        if (search != null && search.isMouseOver(mouseX, mouseY))
        {
            setFocused(search);
            search.setFocused(true);
            return search.mouseClicked(mouseX, mouseY, 0);
        }
        if (search != null)
        {
            search.setFocused(false);
        }
        for (final Hit hit : List.copyOf(hits))
        {
            if (Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height()))
            {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        // While typing a search, letters must not trigger key bindings
        if (search != null && search.isFocused() && keyCode != 256)
        {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers)
    {
        if (search != null && search.isFocused())
        {
            return search.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        scroll -= (int) (scrollY * 12);
        return true;
    }
}
