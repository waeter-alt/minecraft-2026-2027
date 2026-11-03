package fr.kodistan.interregne.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.BankActionPayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * A bank counter's window: the player's personal balance, one button to deposit every Couronne carried,
 * quick and custom withdrawals.
 */
public class WindowBank extends AbstractWindowSkeleton
{
    private final BlockPos counter;

    private WindowBank(final OpenBankPayload account)
    {
        super("interregne:gui/bank.xml");
        this.counter = account.counter();
        registerButton("deposit", () -> send(BankActionPayload.DEPOSIT, 0));
        registerButton("take10", () -> send(BankActionPayload.WITHDRAW, 10));
        registerButton("take100", () -> send(BankActionPayload.WITHDRAW, 100));
        registerButton("take1000", () -> send(BankActionPayload.WITHDRAW, 1_000));
        registerButton("take10000", () -> send(BankActionPayload.WITHDRAW, 10_000));
        registerButton("takeAmount", this::withdrawTyped);
        registerButton("close", this::close);
        show(account);
    }

    /**
     * Open the window, or refresh it when it is already showing this counter.
     */
    public static void open(final OpenBankPayload account)
    {
        if (Minecraft.getInstance().screen instanceof BOScreen screen && screen.getWindow() instanceof WindowBank bank && bank.counter.equals(account.counter()))
        {
            bank.show(account);
            return;
        }
        new WindowBank(account).open();
    }

    private void show(final OpenBankPayload account)
    {
        findPaneOfTypeByID("balance", Text.class).setText(Component.literal("Solde : " + Wallet.format(account.balance()) + " couronnes"));
        final String fee = String.format(Locale.FRANCE, "%.1f", account.feeRate() * 100).replace(",0", "");
        findPaneOfTypeByID("fee", Text.class).setText(Component.literal("Dépôt gratuit · retrait : " + fee + " % de frais"));
    }

    private void withdrawTyped()
    {
        final TextField field = findPaneOfTypeByID("amount", TextField.class);
        try
        {
            final long amount = Long.parseLong(field.getText().replace(" ", "").trim());
            if (amount > 0)
            {
                send(BankActionPayload.WITHDRAW, amount);
                field.setText("");
            }
        }
        catch (final NumberFormatException e)
        {
            field.setText("");
        }
    }

    private void send(final int action, final long amount)
    {
        PacketDistributor.sendToServer(new BankActionPayload(counter, action, amount));
    }
}
