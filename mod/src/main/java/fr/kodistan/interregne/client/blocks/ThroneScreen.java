package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.ThroneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Trône impérial: the Empire's state and the tithe for everyone, the imperial treasury in its own tab (anyone
 * pays in, the Intendants also take out), and the edicts for the Intendants.
 */
public final class ThroneScreen extends CounterScreen
{
    private static final long[] TREASURY_AMOUNTS = {100, 1_000, 10_000};

    private OpenThronePayload state;
    private int tab;
    private int influence = 5;

    private ThroneScreen(final OpenThronePayload state)
    {
        super("Trône impérial", 400, 230);
        this.state = state;
    }

    /**
     * Opens the throne, or refreshes it when it is already showing.
     */
    public static void open(final OpenThronePayload state)
    {
        if (Minecraft.getInstance().screen instanceof ThroneScreen screen && screen.state.throne().equals(state.throne()))
        {
            screen.state = state;
            return;
        }
        Minecraft.getInstance().setScreen(new ThroneScreen(state));
    }

    @Override
    protected void init()
    {
        super.init();
        // The field is a player's name for the edicts, an amount for the treasury
        if (tab == 0)
        {
            addField(left() + 50, y0 + HEADER + 54, 100, "Nom du joueur").visible = state.intendant();
        }
        else
        {
            amountField(left() + 70, y0 + HEADER + 6 + (state.intendant() ? 76 : 58), 90);
        }
    }

    private void send(final int action, final String target, final int amount)
    {
        PacketDistributor.sendToServer(new ThroneActionPayload(state.throne(), action, target, amount));
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        segmented(graphics, mouseX, mouseY, x, y, List.of("Empire", "Trésor impérial"), tab, index -> {
            tab = index;
            search = null;
            rebuildWidgets();
            resetScroll();
        });
        final String deliver = "Livrer la dîme";
        button(graphics, mouseX, mouseY, x + width - buttonWidth(deliver), y, deliver, Ui.ButtonStyle.PRIMARY, true,
          "Prend dans ton inventaire ce que la dîme demande.", () -> send(ThroneActionPayload.DELIVER, "", 0));
        y += 18;
        return tab == 0 ? edicts(graphics, mouseX, mouseY, x, y, width) : treasury(graphics, mouseX, mouseY, x, y, width);
    }

    private int edicts(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (!state.intendant())
        {
            return y;
        }
        Ui.label(graphics, font, "ÉDITS DE L'INTENDANCE", x, y);
        y += 11;
        graphics.drawString(font, "Dîme", x + 2, y + 3, Palette.INK, false);
        // The current weight is in the state text; each choice stays clickable
        segmented(graphics, mouseX, mouseY, x + 50, y, List.of("Clémente", "Ordinaire", "Écrasante"), -1, index -> send(switch (index)
        {
            case 0 -> ThroneActionPayload.CLEMENTE;
            case 1 -> ThroneActionPayload.ORDINAIRE;
            default -> ThroneActionPayload.ECRASANTE;
        }, "", 0));
        y += 18;
        graphics.drawString(font, "Joueur", x + 2, y + 3, Palette.INK, false);
        int bx = x + 156;
        bx += button(graphics, mouseX, mouseY, bx, y, "−", Ui.ButtonStyle.SECONDARY, influence > 1, "Maj : par 10", () -> influence = Math.max(1, influence - step())) + 2;
        final String amount = String.valueOf(influence);
        Ui.plate(graphics, bx, y, bx + 26, y + 14, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        graphics.drawString(font, amount, bx + 13 - font.width(amount) / 2, y + 3, Palette.INK, false);
        bx += 28;
        bx += button(graphics, mouseX, mouseY, bx, y, "+", Ui.ButtonStyle.SECONDARY, true, "Maj : par 10", () -> influence += step()) + 6;
        final String name = search == null ? "" : search.getValue().trim();
        final String why = name.isEmpty() ? "Écris le nom d'un joueur connecté." : null;
        bx += button(graphics, mouseX, mouseY, bx, y, "Faveur", Ui.ButtonStyle.PRIMARY, why == null, why == null ? "Lui donne " + influence + " d'influence." : why,
          () -> send(ThroneActionPayload.FAVOR, name, influence)) + 3;
        button(graphics, mouseX, mouseY, bx, y, "Disgrâce", Ui.ButtonStyle.DANGER, why == null, why == null ? "Lui retire " + influence + " d'influence." : why,
          () -> send(ThroneActionPayload.DISGRACE, name, influence));
        return y + 20;
    }

    private static int step()
    {
        return Screen.hasShiftDown() ? 10 : 1;
    }

    private int treasury(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        Ui.banner(graphics, font, x, y, width, Ui.State.OK, couronnes(state.treasury()), "dans le trésor impérial");
        y += 21;
        y = amounts(graphics, mouseX, mouseY, x, y, "Verser", TREASURY_AMOUNTS, amount -> null, amount -> send(ThroneActionPayload.FUND, "", (int) amount));
        button(graphics, mouseX, mouseY, x + width - buttonWidth("Tout verser"), y - 18, "Tout verser", Ui.ButtonStyle.PRIMARY, true, "Verse toutes les couronnes sur toi.",
          () -> send(ThroneActionPayload.FUND, "", 0));
        if (state.intendant())
        {
            y = amounts(graphics, mouseX, mouseY, x, y, "Prélever", TREASURY_AMOUNTS, amount -> notMore(amount, state.treasury(), "dans le trésor"),
              amount -> send(ThroneActionPayload.WITHDRAW, "", (int) amount));
            button(graphics, mouseX, mouseY, x + width - buttonWidth("Tout prélever"), y - 18, "Tout prélever", Ui.ButtonStyle.SECONDARY, state.treasury() > 0,
              "Le trésor est vide.", () -> send(ThroneActionPayload.WITHDRAW, "", 0));
        }
        graphics.drawString(font, "Autre", x + 2, y + 3, Palette.INK, false);
        final long typed = Math.min(typed(), Integer.MAX_VALUE);
        int bx = x + 166;
        bx += button(graphics, mouseX, mouseY, bx, y, "Verser", Ui.ButtonStyle.PRIMARY, typed > 0, typed > 0 ? null : "Tape un montant.", () -> {
            send(ThroneActionPayload.FUND, "", (int) typed);
            search.setValue("");
        }) + 3;
        if (state.intendant())
        {
            final String why = typed == 0 ? "Tape un montant." : notMore(typed, state.treasury(), "dans le trésor");
            button(graphics, mouseX, mouseY, bx, y, "Prélever", Ui.ButtonStyle.SECONDARY, why == null, why, () -> {
                send(ThroneActionPayload.WITHDRAW, "", (int) typed);
                search.setValue("");
            });
        }
        return y + 20;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (tab != 0)
        {
            return y;
        }
        Ui.label(graphics, font, "L'EMPIRE", x, y);
        y = Ui.wrapped(graphics, font, Component.literal(state.state()), x + 2, y + 11, width - 4, Palette.INK, 10) + 6;
        Ui.label(graphics, font, "TA COLONIE", x, y);
        return Ui.wrapped(graphics, font, Component.literal(state.colonyLine()), x + 2, y + 11, width - 4, Palette.INK, 10);
    }
}
