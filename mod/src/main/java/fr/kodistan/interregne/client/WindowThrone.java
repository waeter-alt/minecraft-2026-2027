package fr.kodistan.interregne.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.ThroneActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Trône impérial window: the Empire's state and the tithe for everyone, edict controls for the Intendants.
 */
public class WindowThrone extends AbstractWindowSkeleton
{
    private final BlockPos throne;

    private WindowThrone(final OpenThronePayload payload)
    {
        super("interregne:gui/throne.xml");
        this.throne = payload.throne();
        registerButton("deliver", () -> send(ThroneActionPayload.DELIVER));
        registerButton("clemente", () -> send(ThroneActionPayload.CLEMENTE));
        registerButton("ordinaire", () -> send(ThroneActionPayload.ORDINAIRE));
        registerButton("ecrasante", () -> send(ThroneActionPayload.ECRASANTE));
        registerButton("fund", () -> send(ThroneActionPayload.FUND));
        registerButton("favor", () -> send(ThroneActionPayload.FAVOR));
        registerButton("disgrace", () -> send(ThroneActionPayload.DISGRACE));
        show(payload);
    }

    public static void open(final OpenThronePayload payload)
    {
        if (Minecraft.getInstance().screen instanceof BOScreen screen && screen.getWindow() instanceof WindowThrone window && window.throne.equals(payload.throne()))
        {
            window.show(payload);
            return;
        }
        new WindowThrone(payload).open();
    }

    private void show(final OpenThronePayload payload)
    {
        findPaneOfTypeByID("state", Text.class).setText(Component.literal(payload.state()));
        findPaneOfTypeByID("colony", Text.class).setText(Component.literal(payload.colonyLine()));
        if (payload.intendant())
        {
            findPaneByID("rule").show();
        }
        else
        {
            findPaneByID("rule").hide();
        }
    }

    private void send(final int action)
    {
        final String target = findPaneOfTypeByID("target", TextField.class).getText();
        int amount = 5;
        try
        {
            amount = Integer.parseInt(findPaneOfTypeByID("amount", TextField.class).getText().trim());
        }
        catch (final NumberFormatException e)
        {
            // Keep the default amount
        }
        PacketDistributor.sendToServer(new ThroneActionPayload(throne, action, target, amount));
    }
}
