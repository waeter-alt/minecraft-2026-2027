package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.RegionalBankActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A Banque régionale: the colony treasury and how full it is, a deposit for members, withdrawals and the upgrade for
 * the owner and officers.
 */
public final class RegionalBankScreen extends CounterScreen
{
    private static final String MANAGERS = "Réservé au propriétaire et aux officiers de la colonie.";

    private OpenRegionalBankPayload state;

    private RegionalBankScreen(final OpenRegionalBankPayload state)
    {
        super("Banque régionale", 320, 196);
        this.state = state;
    }

    /**
     * Opens the window, or refreshes it when it already shows this bank.
     */
    public static void open(final OpenRegionalBankPayload state)
    {
        ClientTreasury.set(state.colonyKey(), state.balance());
        if (Minecraft.getInstance().screen instanceof RegionalBankScreen screen && screen.state.bank().equals(state.bank()))
        {
            screen.state = state;
            return;
        }
        Minecraft.getInstance().setScreen(new RegionalBankScreen(state));
    }

    @Override
    protected String crumb()
    {
        return state.colonyName();
    }

    @Override
    protected void init()
    {
        super.init();
        amountField(left() + 70, y0 + HEADER + 74, 90);
    }

    private void send(final int action, final long amount)
    {
        PacketDistributor.sendToServer(new RegionalBankActionPayload(state.bank(), action, amount));
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final boolean full = state.balance() >= state.capacity();
        Ui.banner(graphics, font, x, y, width, full ? Ui.State.WAIT : Ui.State.OK, "Niveau " + state.level() + " / 5",
          Wallet.format(state.balance()) + " / " + couronnes(state.capacity()) + (full ? " · plein" : ""));
        y += 20;
        gauge(graphics, x, y, width, state.balance(), state.capacity());
        y += 9;

        graphics.drawString(font, "Déposer", x + 2, y + 3, Palette.INK, false);
        final String depositWhy = !state.member() ? "Réservé aux membres de la colonie." : full ? "Le trésor est plein : améliore la banque." : null;
        button(graphics, mouseX, mouseY, x + 70, y, "Toutes mes couronnes", Ui.ButtonStyle.PRIMARY, depositWhy == null, depositWhy,
          () -> send(RegionalBankActionPayload.DEPOSIT, 0));
        y += 20;

        y = amounts(graphics, mouseX, mouseY, x, y, "Retirer", AMOUNTS,
          amount -> !state.manager() ? MANAGERS : notMore(amount, state.balance(), "dans le trésor"), amount -> send(RegionalBankActionPayload.WITHDRAW, amount));
        graphics.drawString(font, "Autre", x + 2, y + 3, Palette.INK, false);
        final long typed = typed();
        final String why = !state.manager() ? MANAGERS : typed == 0 ? "Tape un montant." : notMore(typed, state.balance(), "dans le trésor");
        button(graphics, mouseX, mouseY, x + 166, y, "Retirer", Ui.ButtonStyle.PRIMARY, why == null, why, () -> {
            send(RegionalBankActionPayload.WITHDRAW, typed);
            search.setValue("");
        });
        y += 22;

        // The next level
        y = Ui.separator(graphics, x, y, width);
        if (state.upgradeCost() < 0)
        {
            graphics.drawString(font, Component.literal("Banque au niveau maximal").withStyle(ChatFormatting.BOLD), x + 2, y + 3, Palette.MUTED, false);
            return y + 16;
        }
        final int next = state.level() + 1;
        Ui.plate(graphics, x, y, x + width, y + 34, Palette.levelSoft(next), Palette.levelEdge(next), Palette.levelSoft(next));
        graphics.drawString(font, Component.literal("Niveau " + next).withStyle(ChatFormatting.BOLD), x + 6, y + 5, Palette.INK, false);
        graphics.drawString(font, "capacité " + couronnes(state.nextCapacity()), x + 6, y + 17, Palette.MUTED, false);
        final String label = "Améliorer · " + Wallet.format(state.upgradeCost());
        button(graphics, mouseX, mouseY, x + width - 6 - buttonWidth(label), y + 10, label, Ui.ButtonStyle.PRIMARY, state.manager(),
          state.manager() ? "Payé avec le trésor et les couronnes sur toi." : MANAGERS, () -> send(RegionalBankActionPayload.UPGRADE, 0));
        return y + 38;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }
}
