package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.BankActionPayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A bank counter. First the player's balance; then depositing everything carried, the one thing most visits are for;
 * then withdrawing, with the fee added on top shown before paying.
 */
public final class BankScreen extends CounterScreen
{
    private OpenBankPayload account;

    private BankScreen(final OpenBankPayload account)
    {
        super("Banque impériale", 320, 178);
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
        amountField();
    }

    private void send(final int action, final long amount)
    {
        PacketDistributor.sendToServer(new BankActionPayload(account.counter(), action, amount));
    }

    /**
     * The fee on top of a withdrawal, rounded as the bank rounds it.
     */
    private long fee(final long amount)
    {
        return Math.round(amount * account.feeRate());
    }

    @Nullable
    private String cannot(final long amount)
    {
        return amount + fee(amount) > account.balance() ? "Il faut " + couronnes(amount + fee(amount)) + " sur ton compte, frais compris." : null;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final long purse = purse();
        y = hero(graphics, x, y, width, null, "TON COMPTE", couronnes(account.balance()),
          "Sur toi : " + couronnes(purse) + " · le dépôt est gratuit");

        y = bigButton(graphics, mouseX, mouseY, x, y, width, purse > 0 ? "Déposer mes " + couronnes(purse) : "Rien à déposer", purse > 0,
          purse > 0 ? "Gratuit : tout l'or que tu portes passe sur ton compte." : "Tu ne portes aucune couronne.", () -> send(BankActionPayload.DEPOSIT, 0));

        final String rate = String.format(Locale.FRANCE, "%.1f", account.feeRate() * 100).replace(",0", "");
        return panel(graphics, mouseX, mouseY, x, y, width, "Retirer", "+ " + rate + " % de frais", AMOUNTS, this::cannot,
          amount -> send(BankActionPayload.WITHDRAW, amount), true,
          amount -> fee(amount) > 0 ? "coûte " + Wallet.format(amount + fee(amount)) + " (" + Wallet.format(fee(amount)) + " de frais)" : "sans frais");
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }
}
