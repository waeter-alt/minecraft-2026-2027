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
    private static final HutBody GARRISON = new GarrisonBody();

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
            case "combatacademy", "archery" -> GARRISON;
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

    public static void receive(final fr.kodistan.interregne.garrison.GarrisonStatePayload payload)
    {
        GarrisonBody.receive(payload);
    }

    /**
     * Fake recruiting data that is never refreshed (UiShot).
     */
    public static void preview(final fr.kodistan.interregne.garrison.GarrisonStatePayload payload)
    {
        GarrisonBody.preview(payload);
    }

    /**
     * Asks the server for the recruiting page of this hut (combat academy or archery).
     */
    public static void openGarrison(final IBuildingView building)
    {
        GarrisonBody.open(building);
    }

    /**
     * Fake Relais data that is never refreshed (UiShot).
     */
    public static void preview(final RelaisStatePayload payload, final int escort)
    {
        RelaisBody.preview(payload, escort);
    }

    /**
     * The building of this type that counts in its colony: the highest level, the first one built when several are as high. A
     * colony has one relais, one bank, one depot; the others are duplicates.
     */
    public static IBuildingView activeOf(final IBuildingView building)
    {
        IBuildingView best = building;
        for (final IBuildingView other : building.getColony().getBuildings())
        {
            if (other.getBuildingType() != building.getBuildingType())
            {
                continue;
            }
            if (other.getBuildingLevel() > best.getBuildingLevel()
                  || (other.getBuildingLevel() == best.getBuildingLevel() && other.getID().compareTo(best.getID()) < 0))
            {
                best = other;
            }
        }
        return best;
    }

    /**
     * Says in the chat that this building is one too many and where the one that counts stands.
     */
    public static void tellDuplicate(final IBuildingView building, final IBuildingView active, final String what)
    {
        final var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null)
        {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Une colonie n'a qu'un " + what + " : celui de " + building.getColony().getName() + " est en "
                                                                                       + active.getPosition().toShortString() + ". Celui-ci est en trop, tu peux le démolir.")
                                          .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        }
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
