package fr.kodistan.interregne.client.ui;

import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.client.hud.InterregneHud;
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
    public static final int MAX_W = 560;
    public static final int MAX_H = 300;

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
        renderFrame(graphics, mouseX, mouseY);
        renderContent(graphics, mouseX, mouseY);
        if (walletW > 0 && Ui.inside(mouseX, mouseY, walletX, y0 + 3, walletW, 15))
        {
            graphics.renderTooltip(font, font.split(Component.literal(walletHint()), 220), mouseX, mouseY);
        }
    }

    /** Where the header's Influence and gold tag sits, for its tooltip; 0 wide when not shown. */
    private int walletX;
    private int walletW;

    /**
     * Whether the header shows the player's Influence and gold (most windows do; one with a header of its own does not).
     */
    protected boolean showWallet()
    {
        return true;
    }

    private static String walletHint()
    {
        return "Influence : " + Wallet.format(InterregneHud.influence()) + ".\nOr : " + Wallet.format(walletGold()) + " couronnes, dont "
          + Wallet.format(InterregneHud.carried()) + " sur toi, " + Wallet.format(InterregneHud.bank()) + " à la Banque impériale et "
          + Wallet.format(InterregneHud.regional()) + " au trésor régional.";
    }

    private static long walletGold()
    {
        return InterregneHud.carried() + InterregneHud.bank() + InterregneHud.regional();
    }

    /**
     * Draws the « ★ 12  ● 1 388 » tag right-aligned at this x, in full or compact figures to leave the title room.
     *
     * @return the tag's left edge.
     */
    private int drawWallet(final GuiGraphics graphics, final int right, final int titleRoom, final int ink)
    {
        String influence = Wallet.format(InterregneHud.influence());
        String gold = Wallet.format(walletGold());
        if (tagWidth(influence, gold) > Math.max(0, right - x0 - 8 - titleRoom))
        {
            influence = InterregneHud.compact(InterregneHud.influence());
            gold = InterregneHud.compact(walletGold());
        }
        final int width = tagWidth(influence, gold);
        int x = right - width;
        walletX = x;
        walletW = width;
        Ui.influence(graphics, x, y0 + 6, 9);
        x += 11;
        graphics.drawString(font, influence, x, y0 + 7, ink, false);
        x += font.width(influence) + 7;
        Ui.coinTight(graphics, x, y0 + 6, 9);
        x += 11;
        graphics.drawString(font, gold, x, y0 + 7, ink, false);
        return walletX;
    }

    private int tagWidth(final String influence, final String gold)
    {
        return 11 + font.width(influence) + 7 + 11 + font.width(gold);
    }

    /**
     * The sheet, its header and ✕: parchment unless a window has a frame of its own (the colony hub).
     */
    protected void renderFrame(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        Ui.plate(graphics, x0, y0, x0 + w, y0 + h, Palette.PAPER, Palette.EDGE, Palette.PAPER_LIGHT);
        final Integer band = headerFill();
        final int ink = band == null ? Palette.INK : headerText();
        final int muted = band == null ? Palette.MUTED : headerText();
        if (band != null)
        {
            Ui.plate(graphics, x0 + 2, y0 + 2, x0 + w - 2, y0 + 19, band, headerEdge(), band);
        }
        int titleX = x0 + 8;
        final String crumb = crumb();
        final int crumbW = crumb == null ? 0 : font.width(crumb + " › ");
        final Component title = shownTitle();
        // The wallet keeps clear of the ✕ and leaves the title at least its own width or 80 pixels
        final int titleEnd = showWallet()
          ? drawWallet(graphics, x0 + w - 19, crumbW + Math.min(font.width(title), 80), ink) - 8
          : x0 + w - 17;
        if (!showWallet())
        {
            walletW = 0;
        }
        if (crumb != null && titleEnd - titleX - crumbW >= 40)
        {
            graphics.drawString(font, crumb + " › ", titleX, y0 + 7, muted, false);
            titleX += crumbW;
        }
        if (font.width(title) <= titleEnd - titleX)
        {
            graphics.drawString(font, title, titleX, y0 + 7, ink, false);
        }
        else
        {
            graphics.drawString(font, Ui.fit(font, title.getString(), titleEnd - titleX), titleX, y0 + 7, ink, false);
        }
        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, overClose(mouseX, mouseY) ? Palette.RED : muted, false);
        graphics.fill(x0 + 4, y0 + 19, x0 + w - 4, y0 + 20, Palette.RULE);
    }

    /**
     * Everything below the header.
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY);

    /**
     * The title in the header; a page of a building shows the building's instead of its own.
     */
    protected Component shownTitle()
    {
        return title;
    }

    /**
     * Where this window sits, shown before the title (« Colonie »…), or null.
     */
    @Nullable
    protected String crumb()
    {
        return null;
    }

    /**
     * A coloured band behind the header (a hut's level colour…), or null for plain parchment.
     */
    @Nullable
    protected Integer headerFill()
    {
        return null;
    }

    protected int headerEdge()
    {
        return Palette.EDGE;
    }

    /**
     * The header's text colour on its band.
     */
    protected int headerText()
    {
        return Palette.INK;
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

    protected boolean overClose(final double mouseX, final double mouseY)
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
