package fr.kodistan.interregne.client;

import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.ThroneActionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The imperial treasury, reached from the throne: anyone may pay in; Intendants may also take out.
 * Preset amounts, everything, or a typed amount.
 */
public class WindowImperialTreasury extends AbstractWindowSkeleton
{
    private final BlockPos throne;
    private OpenThronePayload last;

    public WindowImperialTreasury(final OpenThronePayload payload)
    {
        super("interregne:gui/imperial_treasury.xml");
        this.throne = payload.throne();
        registerButton("fund100", () -> send(ThroneActionPayload.FUND, 100));
        registerButton("fund1000", () -> send(ThroneActionPayload.FUND, 1_000));
        registerButton("fund10000", () -> send(ThroneActionPayload.FUND, 10_000));
        registerButton("fundAll", () -> send(ThroneActionPayload.FUND, 0));
        registerButton("take100", () -> send(ThroneActionPayload.WITHDRAW, 100));
        registerButton("take1000", () -> send(ThroneActionPayload.WITHDRAW, 1_000));
        registerButton("take10000", () -> send(ThroneActionPayload.WITHDRAW, 10_000));
        registerButton("takeAll", () -> send(ThroneActionPayload.WITHDRAW, 0));
        registerButton("fundCustom", () -> sendTyped(ThroneActionPayload.FUND));
        registerButton("takeCustom", () -> sendTyped(ThroneActionPayload.WITHDRAW));
        registerButton("back", () -> WindowThrone.open(last, true));
        show(payload);
    }

    public BlockPos throne()
    {
        return throne;
    }

    public void show(final OpenThronePayload payload)
    {
        last = payload;
        findPaneOfTypeByID("balance", Text.class).setText(Component.literal("Solde : " + Wallet.couronnes(payload.treasury(), null)));
        if (payload.intendant())
        {
            findPaneByID("withdrawPanel").show();
            findPaneByID("takeCustom").show();
        }
        else
        {
            findPaneByID("withdrawPanel").hide();
            findPaneByID("takeCustom").hide();
        }
    }

    private void sendTyped(final int action)
    {
        final TextField field = findPaneOfTypeByID("amount", TextField.class);
        try
        {
            final int amount = Integer.parseInt(field.getText().replace(" ", "").trim());
            if (amount > 0)
            {
                send(action, amount);
            }
        }
        catch (final NumberFormatException e)
        {
            // Nothing sensible typed: ignore
        }
        field.setText("");
    }

    private void send(final int action, final int amount)
    {
        PacketDistributor.sendToServer(new ThroneActionPayload(throne, action, "", amount));
    }
}
