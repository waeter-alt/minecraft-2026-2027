package fr.kodistan.interregne.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.RegionalBankActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A Banque régionale's window: the colony treasury and its capacity, a deposit button for members,
 * and withdrawals and the upgrade for the owner and officers.
 */
public class WindowRegionalBank extends AbstractWindowSkeleton
{
    private final BlockPos bank;

    private WindowRegionalBank(final OpenRegionalBankPayload state)
    {
        super("interregne:gui/regional_bank.xml");
        this.bank = state.bank();
        registerButton("deposit", () -> send(RegionalBankActionPayload.DEPOSIT, 0));
        registerButton("take10", () -> send(RegionalBankActionPayload.WITHDRAW, 10));
        registerButton("take100", () -> send(RegionalBankActionPayload.WITHDRAW, 100));
        registerButton("take1000", () -> send(RegionalBankActionPayload.WITHDRAW, 1_000));
        registerButton("take10000", () -> send(RegionalBankActionPayload.WITHDRAW, 10_000));
        registerButton("takeAmount", this::withdrawTyped);
        registerButton("upgrade", () -> send(RegionalBankActionPayload.UPGRADE, 0));
        registerButton("close", this::close);
        show(state);
    }

    /**
     * Open the window, or refresh it when it is already showing this bank.
     */
    public static void open(final OpenRegionalBankPayload state)
    {
        ClientTreasury.set(state.colonyKey(), state.balance());
        if (Minecraft.getInstance().screen instanceof BOScreen screen && screen.getWindow() instanceof WindowRegionalBank window && window.bank.equals(state.bank()))
        {
            window.show(state);
            return;
        }
        new WindowRegionalBank(state).open();
    }

    private void show(final OpenRegionalBankPayload state)
    {
        findPaneOfTypeByID("colony", Text.class).setText(Component.literal(state.colonyName()));
        findPaneOfTypeByID("level", Text.class).setText(Component.literal("Niveau " + state.level() + " / 5"));
        findPaneOfTypeByID("balance", Text.class).setText(Component.literal("Trésor : " + Wallet.format(state.balance()) + " / " + Wallet.format(state.capacity())
                                                                             + " couronnes"));

        final Button deposit = findPaneOfTypeByID("deposit", Button.class);
        deposit.setEnabled(state.member() && state.balance() < state.capacity());
        deposit.setText(Component.literal(!state.member() ? "Réservé aux membres" : state.balance() >= state.capacity() ? "Coffre plein" : "Déposer toutes mes couronnes"));

        if (state.manager())
        {
            findPaneByID("withdrawPanel").show();
            findPaneByID("withdrawLabel").show();
            findPaneByID("managerOnly").hide();
        }
        else
        {
            findPaneByID("withdrawPanel").hide();
            findPaneByID("withdrawLabel").hide();
            findPaneByID("managerOnly").show();
        }

        final Button upgrade = findPaneOfTypeByID("upgrade", Button.class);
        if (state.upgradeCost() < 0)
        {
            findPaneOfTypeByID("upgradeInfo", Text.class).setText(Component.literal("Banque au niveau maximal"));
            upgrade.hide();
        }
        else
        {
            findPaneOfTypeByID("upgradeInfo", Text.class).setText(Component.literal("Niveau " + (state.level() + 1) + " : capacité " + Wallet.format(state.nextCapacity())));
            upgrade.setText(Component.literal("Améliorer la banque (" + Wallet.format(state.upgradeCost()) + ")"));
            upgrade.setEnabled(state.manager());
            upgrade.show();
        }
    }

    private void withdrawTyped()
    {
        final TextField field = findPaneOfTypeByID("amount", TextField.class);
        try
        {
            final long amount = Long.parseLong(field.getText().replace(" ", "").trim());
            if (amount > 0)
            {
                send(RegionalBankActionPayload.WITHDRAW, amount);
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
        PacketDistributor.sendToServer(new RegionalBankActionPayload(bank, action, amount));
    }
}
