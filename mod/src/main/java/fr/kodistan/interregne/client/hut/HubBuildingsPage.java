package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.catalogue.CatalogueIndex;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bâtiments : la liste de tous les bâtiments de la colonie et leur état.
 * Une page du hub de colonie ({@link ColonyHubScreen}) : elle dessine dans le cadre du hub et lui emprunte son état.
 */
final class HubBuildingsPage
{
    private final ColonyHubScreen hub;

    HubBuildingsPage(final ColonyHubScreen hub)
    {
        this.hub = hub;
    }

    // --- Buildings ---

    static String nameOf(final IBuildingView building)
    {
        final String custom = building.getCustomName();
        if (custom != null && !custom.isEmpty())
        {
            return custom;
        }
        return CatalogueIndex.buildingName(building.getBuildingType().getRegistryName().getPath(),
          Component.translatable(building.getBuildingDisplayName()).getString());
    }

    int renderBuildings(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IBuildingView> buildings = new ArrayList<>(hub.colony.getBuildings());
        final Map<IBuildingView, ColonyHubScreen.BuildingState> states = new LinkedHashMap<>();
        buildings.forEach(b -> states.put(b, hub.stateOf(b)));
        // Problems first, then by name
        buildings.sort(Comparator.<IBuildingView>comparingInt(b -> -states.get(b).state().ordinal()).thenComparing(HubBuildingsPage::nameOf));
        Ui.label(graphics, hub.font(), buildings.size() + " BÂTIMENTS", x, y);
        y += 12;
        for (final IBuildingView building : buildings)
        {
            final ColonyHubScreen.BuildingState state = states.get(building);
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 20);
            Ui.card(graphics, x, y, width, 20, false, over, false);
            // Some hut models stand taller than a block: keep them inside their row
            graphics.enableScissor(x + 1, y + 1, x + 21, y + 19);
            Icon.of(building.getBuildingType().getBuildingBlock()).draw(graphics, x + 3, y + 2, 16);
            graphics.disableScissor();
            final int level = building.getBuildingLevel();
            final int badgeX = x + width - 150;
            graphics.drawString(hub.font(), hub.fitText(nameOf(building), badgeX - x - 28), x + 23, y + 6, Palette.INK, false);
            levelSquares(graphics, badgeX, y + 6, level, building.getBuildingMaxLevel());
            final int chipW = hub.font().width(state.text()) + 8;
            Ui.chip(graphics, hub.font(), x + width - chipW - 14, y + 5, state.text(), state.state());
            graphics.drawString(hub.font(), "›", x + width - 9, y + 6, Palette.MUTED, false);
            hub.addHit(x, y, width, 20, () -> HutScreen.openWith(building, hub));
            y += 22;
        }
        return y;
    }

    /**
     * One square per level in its colour, filled up to the building's level.
     */
    void levelSquares(final GuiGraphics graphics, int x, int y, final int level, final int max)
    {
        for (int l = 1; l <= max; l++)
        {
            graphics.fill(x, y, x + 6, y + 7, l <= level ? Palette.levelEdge(l) : Palette.LEVEL_EMPTY_EDGE);
            graphics.fill(x + 1, y + 1, x + 5, y + 6, l <= level ? Palette.levelFill(l) : Palette.LEVEL_EMPTY);
            x += 8;
        }
    }
}
