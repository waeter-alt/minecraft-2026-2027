package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ui.Icon;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The middle column of a building's hut window when the building has a body of its own (the warehouse's stock, the bank's
 * treasury…), in place of workers and production. The header, the left navigation and the right column (level, upgrade)
 * stay the hut window's. The overview shows the tab {@code ""}; {@link #tabs} adds more entries under it in the navigation.
 */
public interface HutBody
{
    /**
     * A page of the body, shown under "Vue d'ensemble" in the left navigation.
     */
    record Tab(String key, String label, Icon icon)
    {
    }

    /**
     * The pages after the overview.
     */
    default List<Tab> tabs(final IBuildingView building)
    {
        return List.of();
    }

    /**
     * Draws a page from {@code y}, clipped and scrolled by the window; returns the y below it.
     */
    int render(BodyKit kit, GuiGraphics graphics, int mouseX, int mouseY, String tab, IBuildingView building, int x, int y, int width);

    /**
     * Every tick while the window is open: ask the server again, count down.
     */
    default void tick(final IBuildingView building)
    {
    }

    /**
     * Whether a page draws the typed-amount field (so the window creates it).
     */
    default boolean wantsField()
    {
        return false;
    }
}
