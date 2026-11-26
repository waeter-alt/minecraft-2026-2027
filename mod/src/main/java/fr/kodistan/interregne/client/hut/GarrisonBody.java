package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.garrison.GarrisonActionPayload;
import fr.kodistan.interregne.garrison.GarrisonStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The recruiting page of the combat academy (légionnaires) and the archery (archères): guards are bought here, at the
 * level of the hut, and go to a free place in the towers. The hut's own level and upgrade are the hut window's.
 */
final class GarrisonBody implements HutBody
{
    static final int LEGIONNAIRE = 0;
    static final int ARCHERE = 1;

    @Nullable
    private static GarrisonStatePayload state;
    /** False for the UiShot preview: its fake data must not be replaced by the real server's answer. */
    private static boolean live = true;
    private static int ticks;

    static void receive(final GarrisonStatePayload payload)
    {
        state = payload;
    }

    static void preview(final GarrisonStatePayload payload)
    {
        state = payload;
        live = false;
    }

    static int kind(final IBuildingView building)
    {
        return "archery".equals(building.getBuildingType().getRegistryName().getPath()) ? ARCHERE : LEGIONNAIRE;
    }

    static void open(final IBuildingView building)
    {
        live = true;
        state = null;
        PacketDistributor.sendToServer(new GarrisonActionPayload(GarrisonActionPayload.OPEN, building.getColony().getID(), kind(building)));
    }

    @Override
    public List<Tab> tabs(final IBuildingView building)
    {
        return List.of();
    }

    @Override
    public void tick(final IBuildingView building)
    {
        if (live && ++ticks % 20 == 0)
        {
            PacketDistributor.sendToServer(new GarrisonActionPayload(GarrisonActionPayload.OPEN, building.getColony().getID(), kind(building)));
        }
    }

    @Override
    public int render(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final String tab, final IBuildingView building, final int x,
      int y, final int width)
    {
        final GarrisonStatePayload garrison = state;
        final int kind = kind(building);
        if (garrison == null || garrison.colony() != building.getColony().getID() || garrison.kind() != kind)
        {
            return HutBodies.loading(kit, graphics, x, y, width, building);
        }
        final boolean archers = kind == ARCHERE;
        final String hut = archers ? "le champ de tir" : "l'académie de combat";
        if (garrison.level() < 1)
        {
            return y + Ui.banner(graphics, kit.font(), x, y, width, Ui.State.WAIT, "En construction", hut + " doit être terminé" + (archers ? "" : "e")) + 21;
        }
        if (!garrison.notice().isEmpty())
        {
            y += Ui.banner(graphics, kit.font(), x, y, width, Ui.State.BAD, "Refusé", garrison.notice());
            y += 25;
        }
        final ItemStack icon = new ItemStack(archers ? Items.BOW : Items.IRON_SWORD);
        y = kit.hero(graphics, x, y, width, icon, archers ? "ARCHÈRES" : "LÉGIONNAIRES", String.valueOf(garrison.count()),
          "Places dans les tours : " + garrison.total() + " / " + garrison.cap());
        y = kit.note(graphics, x, y, width, "Niveau des recrues : " + garrison.level() + ". Montez " + hut + " pour des recrues plus fortes, et plus chères.",
          Palette.MUTED);
        y += 4;
        final String why = garrison.total() >= garrison.cap()
          ? "Plus de place : construisez une tour de garde ou agrandissez la caserne."
          : null;
        return kit.priceButton(graphics, mouseX, mouseY, x, y, width, archers ? "Recruter une archère" : "Recruter un légionnaire", garrison.price(),
          garrison.available(), why, () -> PacketDistributor.sendToServer(new GarrisonActionPayload(GarrisonActionPayload.BUY, garrison.colony(), kind)));
    }
}
