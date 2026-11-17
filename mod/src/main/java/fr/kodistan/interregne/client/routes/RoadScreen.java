package fr.kodistan.interregne.client.routes;

import fr.kodistan.interregne.client.blocks.CounterScreen;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.routes.RoadActionPayload;
import fr.kodistan.interregne.routes.RoadStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The road screen of a Poste commercial: the road's level and its ladder of five levels, what each one looks like and gives
 * to anyone who walks it, then the price of the next level and the button that orders it.
 */
public final class RoadScreen extends CounterScreen
{
    /** What each level looks like, in the order of {@code RoadLevels}. */
    private static final String[] NAMES = {"Chemin de terre", "Route tassée", "Route empierrée", "Route dallée", "Voie romaine"};
    private static final String[] LOOKS = {
      "Terre battue, ponts de bois, tunnels bruts.",
      "Boue tassée, bordures de chêne noir, premiers lampadaires.",
      "Andésite et tuf, ponts de pierre, lampadaires plus serrés.",
      "Pierre polie et basalte, tunnels soignés, lampadaires réguliers.",
      "Pierre lisse, bordures de deepslate, ponts et tunnels ouvragés, lampadaires partout."};

    private RoadStatePayload state;
    private int ticks;
    /** False for the UiShot preview: its fake data must not be replaced by the real server's answer. */
    private boolean live = true;

    private RoadScreen(final RoadStatePayload state)
    {
        super("Route", 380, 330);
        this.state = state;
    }

    /**
     * Opens the screen, or refreshes it when it is already showing this post.
     */
    public static void open(final RoadStatePayload state)
    {
        if (Minecraft.getInstance().screen instanceof RoadScreen screen && screen.state.post().equals(state.post()))
        {
            screen.state = state;
            return;
        }
        Minecraft.getInstance().setScreen(new RoadScreen(state));
    }

    /**
     * Shows fake data that is never refreshed (UiShot).
     */
    public static void preview(final RoadStatePayload state)
    {
        final RoadScreen screen = new RoadScreen(state);
        screen.live = false;
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected String crumb()
    {
        return state.from() + " ↔ " + state.to();
    }

    @Override
    public void tick()
    {
        // The works end by themselves: ask again once a second
        if (live && ++ticks % 20 == 0)
        {
            PacketDistributor.sendToServer(new RoadActionPayload(state.post(), RoadActionPayload.OPEN));
        }
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int top = y;
        final int level = Math.clamp(state.level(), 1, NAMES.length);
        y = hero(graphics, x, y, width, null, "ROUTE · " + state.length() + " BLOCS", NAMES[level - 1],
          state.underWay() ? "Travaux en cours : niveau " + (level + 1) + " dans " + state.seconds() + " s au plus." : LOOKS[level - 1]);
        final String badge = "Niv. " + level + " / " + NAMES.length;
        Ui.levelBadge(graphics, font, x + width - 8 - font.width(badge) - 8, top + 6, badge, level);
        if (!state.notice().isEmpty())
        {
            y += Ui.banner(graphics, font, x, y, width, Ui.State.BAD, "Refusé", state.notice());
            y += 25;
        }
        return upgrade(graphics, mouseX, mouseY, x, y, width, level);
    }

    /**
     * The main action: the next level and its price, or why there is none.
     */
    private int upgrade(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final int level)
    {
        if (state.cost() < 0)
        {
            graphics.drawString(font, Component.literal("Niveau maximal : rien au-dessus de la voie romaine.").withStyle(ChatFormatting.BOLD), x + 2, y + 3, Palette.MUTED, false);
            return y + 16;
        }
        final int next = level + 1;
        final boolean afford = state.available() >= state.cost();
        final boolean enabled = afford && !state.underWay();
        final String price = state.cost() == 0 ? "gratuit" : Wallet.format(state.cost());
        final String label = state.underWay() ? "Travaux en cours" : "Améliorer au niveau " + next + " · " + price;
        final String why = state.underWay() ? "Attends la fin des travaux." : afford ? null : "Il te faut " + couronnes(state.cost()) + " (trésor régional et couronnes sur toi).";
        final int bw = width;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, bw, 20);
        Ui.levelButton(graphics, font, x, y, bw, 20, label, next, enabled, over);
        if (enabled)
        {
            addHit(x, y, bw, 20, () -> PacketDistributor.sendToServer(new RoadActionPayload(state.post(), RoadActionPayload.UPGRADE)));
        }
        if (over)
        {
            hint = why == null ? "Payé avec le trésor régional d'abord, puis les couronnes sur toi. Durée : " + state.seconds() + " s." : why;
        }
        if (!afford && !state.underWay())
        {
            final String need = "il manque " + (state.cost() - state.available());
            graphics.drawString(font, need, x + width - 2 - font.width(need), y + 24, Palette.RED, false);
            return y + 36;
        }
        return y + 25;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        Ui.label(graphics, font, "LES CINQ NIVEAUX", x, y);
        y += 12;
        final List<String> perks = state.perks();
        for (int l = 1; l <= NAMES.length; l++)
        {
            y = row(graphics, x, y, width, l, perks.size() >= l ? perks.get(l - 1) : "");
        }
        return y;
    }

    private int row(final GuiGraphics graphics, final int x, final int y, final int width, final int level, final String perk)
    {
        final boolean done = level < state.level();
        final boolean current = level == state.level();
        final int textW = width - 64;
        final List<net.minecraft.util.FormattedCharSequence> perkLines = font.split(Component.literal(perk), textW);
        final List<net.minecraft.util.FormattedCharSequence> lookLines = font.split(Component.literal(LOOKS[level - 1]), textW);
        final int height = 18 + (lookLines.size() + perkLines.size()) * 10 + 5;
        Ui.card(graphics, x, y, width, height, current, false, false);
        final String badge = done ? "✔" : String.valueOf(level);
        Ui.levelBadge(graphics, font, x + 6, y + 6, badge, level);
        graphics.drawString(font, Component.literal(NAMES[level - 1]).withStyle(ChatFormatting.BOLD), x + 36, y + 6, current ? Palette.INK : done ? Palette.MUTED : Palette.INK, false);
        if (current)
        {
            final String here = "niveau actuel";
            graphics.drawString(font, here, x + width - 6 - font.width(here), y + 6, Palette.GREEN, false);
        }
        int ty = y + 18;
        for (final net.minecraft.util.FormattedCharSequence line : lookLines)
        {
            graphics.drawString(font, line, x + 36, ty, Palette.MUTED, false);
            ty += 10;
        }
        for (final net.minecraft.util.FormattedCharSequence line : perkLines)
        {
            graphics.drawString(font, line, x + 36, ty, Palette.INK, false);
            ty += 10;
        }
        return y + height + 4;
    }
}
