package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.commerce.RelaisStatePayload;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * The buildings whose hut window has a body of its own, and the server's answers they show. The window of one of them
 * opens at once and fills in when the answer comes.
 */
public final class HutBodies
{
    private static final HutBody BANK = new BankBody();
    private static final HutBody DEPOT = new DepotBody();
    private static final HutBody RELAIS = new RelaisBody();

    private HutBodies()
    {
    }

    @Nullable
    public static HutBody of(final IBuildingView building)
    {
        return switch (building.getBuildingType().getRegistryName().getPath())
        {
            case "banque_regionale" -> BANK;
            case "depot_regional" -> DEPOT;
            case "relais_commercial" -> RELAIS;
            default -> null;
        };
    }

    public static void receive(final OpenRegionalBankPayload payload)
    {
        BankBody.receive(payload);
    }

    public static void receive(final OpenComptoirPayload payload)
    {
        DepotBody.receive(payload);
    }

    public static void receive(final RelaisStatePayload payload)
    {
        RelaisBody.receive(payload);
    }

    /**
     * Fake Relais data that is never refreshed (UiShot).
     */
    public static void preview(final RelaisStatePayload payload, final int escort)
    {
        RelaisBody.preview(payload, escort);
    }

    /**
     * What a page shows until the server's answer is here (or when the building is not built, so there is none).
     */
    static int loading(final BodyKit kit, final GuiGraphics graphics, final int x, final int y, final int width, final IBuildingView building)
    {
        if (building.getBuildingLevel() < 1)
        {
            return y + Ui.banner(graphics, kit.font(), x, y, width, Ui.State.WAIT, "En construction", "le bâtiment doit être terminé") + 21;
        }
        return y + Ui.banner(graphics, kit.font(), x, y, width, Ui.State.WAIT, "Chargement", "le serveur répond dans un instant") + 21;
    }
}
