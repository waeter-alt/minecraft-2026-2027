package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.EnchanterStationsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Where the enchanter goes to draw experience from: every workplace, nearest first, as a card that turns green when
 * chosen. Replaces MineColonies' enchanter page.
 */
public final class EnchanterScreen extends ModuleScreen
{
    private static final int CARD_H = 20;

    private final int moduleId;
    private EnchanterStationsModuleView module;

    private EnchanterScreen(final IBuildingView building, final EnchanterStationsModuleView module, @Nullable final Screen parent)
    {
        super("Sources d'expérience", building, parent, 380);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    public static void open(final IBuildingView building, final EnchanterStationsModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new EnchanterScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        final EnchanterStationsModuleView fresh = building.getModuleViewMatching(EnchanterStationsModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
    }

    private int distance(final IBuildingView other)
    {
        return (int) Math.sqrt(other.getPosition().distSqr(building.getPosition()));
    }

    private List<IBuildingView> workplaces()
    {
        return building.getColony().getBuildings().stream()
          .filter(b -> b instanceof AbstractBuildingView && !b.getModuleViews(WorkerBuildingModuleView.class).isEmpty() && b.getBuildingType() != ModBuildings.enchanter.get())
          .sorted(Comparator.comparingInt(this::distance))
          .toList();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final int chosen = module.getBuildingsToGatherFrom().size();
        Ui.banner(graphics, font, x, y, width, chosen == 0 ? Ui.State.BAD : Ui.State.OK, chosen + (chosen > 1 ? " bâtiments choisis" : " bâtiment choisi"),
          chosen == 0 ? "l'enchanteur n'a nulle part où puiser" : "l'enchanteur y puise l'expérience des ouvriers");
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IBuildingView> workplaces = workplaces();
        if (workplaces.isEmpty())
        {
            graphics.drawString(font, "Aucun lieu de travail dans la colonie.", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        final int columnW = (width - 3) / 2;
        for (int i = 0; i < workplaces.size(); i++)
        {
            final IBuildingView other = workplaces.get(i);
            final int cx = x + (i % 2) * (columnW + 3);
            final int cy = y + (i / 2) * (CARD_H + 2);
            final boolean on = module.getBuildingsToGatherFrom().contains(other.getID());
            final boolean over = Ui.inside(mouseX, mouseY, cx, cy, columnW, CARD_H);
            final int fill = on ? (over ? Palette.GREEN_LIGHT : Palette.GREEN_SOFT) : (over ? Palette.CARD_HOVER : Palette.PAPER_DARK);
            Ui.plate(graphics, cx, cy, cx + columnW, cy + CARD_H, fill, on ? Palette.GREEN : Palette.RULE, fill);
            Icon.of(other.getBuildingType().getBuildingBlock()).draw(graphics, cx + 3, cy + 2, 16);
            final String distance = distance(other) + " m";
            graphics.drawString(font, distance, cx + columnW - 14 - font.width(distance), cy + 6, Palette.MUTED, false);
            graphics.drawString(font, on ? "✔" : "✕", cx + columnW - 10, cy + 6, on ? Palette.GREEN : Palette.MUTED, false);
            graphics.drawString(font, fit(RequestScreen.nameOf(other), columnW - 50 - font.width(distance)), cx + 22, cy + 6, on ? Palette.INK : Palette.MUTED, false);
            addHit(cx, cy, columnW, CARD_H, () -> {
                if (on)
                {
                    module.removeWorker(other.getID());
                }
                else
                {
                    module.addWorker(other.getID());
                }
            });
        }
        return y + ((workplaces.size() + 1) / 2) * (CARD_H + 2);
    }
}
