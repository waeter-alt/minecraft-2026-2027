package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.network.BankActionPayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * A bank counter: the player's own balance, everything carried deposited in one click, and withdrawals by preset or
 * typed amount, with the fee shown.
 */
public final class BankScreen extends CounterScreen
{
    private OpenBankPayload account;

    private BankScreen(final OpenBankPayload account)
    {
        super("Banque", 300, 140);
        this.account = account;
    }

    /**
     * Opens the window, or refreshes it when it already shows this counter.
     */
    public static void open(final OpenBankPayload account)
    {
        if (Minecraft.getInstance().screen instanceof BankScreen screen && screen.account.counter().equals(account.counter()))
        {
            screen.account = account;
            return;
        }
        Minecraft.getInstance().setScreen(new BankScreen(account));
    }

    @Override
    protected void init()
    {
        super.init();
        amountField(left() + 70, y0 + HEADER + 69, 90);
    }

    private void send(final int action, final long amount)
    {
        PacketDistributor.sendToServer(new BankActionPayload(account.counter(), action, amount));
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final String fee = String.format(Locale.FRANCE, "%.1f", account.feeRate() * 100).replace(",0", "");
        Ui.banner(graphics, font, x, y, width, Ui.State.OK, couronnes(account.balance()), "sur ton compte · retrait : " + fee + " % de frais");
        y += 24;
        graphics.drawString(font, "Déposer", x + 2, y + 3, fr.kodistan.interregne.client.ui.Palette.INK, false);
        button(graphics, mouseX, mouseY, x + 70, y, "Toutes mes couronnes", Ui.ButtonStyle.PRIMARY, true, "Le dépôt est gratuit.", () -> send(BankActionPayload.DEPOSIT, 0));
        y += 20;
        y = amounts(graphics, mouseX, mouseY, x, y, "Retirer", AMOUNTS, amount -> notMore(amount, account.balance(), "sur ton compte"),
          amount -> send(BankActionPayload.WITHDRAW, amount));
        graphics.drawString(font, "Autre", x + 2, y + 3, fr.kodistan.interregne.client.ui.Palette.INK, false);
        final long typed = typed();
        final String why = typed == 0 ? "Tape un montant." : notMore(typed, account.balance(), "sur ton compte");
        button(graphics, mouseX, mouseY, x + 166, y, "Retirer", Ui.ButtonStyle.PRIMARY, why == null, why, () -> {
            send(BankActionPayload.WITHDRAW, typed);
            search.setValue("");
        });
        return y + 18;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }
}
