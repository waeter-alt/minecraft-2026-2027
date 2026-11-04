package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ui.PageScreen;
import fr.kodistan.interregne.client.ui.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What every page of a building shares on top of {@link PageScreen}: the building's name before the title, the
 * newest view of the building, and Escape back to the hut window. Opened from the hut window, a page takes the hut
 * window's place with the same header and left column ({@link HutNav}), only the right part being its own; a page of
 * the colony opened from the colony hub keeps the hub's frame and left column the same way.
 */
public abstract class ModuleScreen extends PageScreen
{
    protected IBuildingView building;
    /**
     * The hut window this page belongs to, whose header and left column it keeps; null when opened on its own.
     */
    @Nullable
    final HutScreen hut;
    /**
     * Which entry of the left column is lit: this page's key, or a tool's label.
     */
    String navCurrent;
    /**
     * The colony hub this page shows in, whose frame and left column it keeps; null otherwise.
     */
    @Nullable
    final ColonyHubScreen hub;

    protected ModuleScreen(final String title, final IBuildingView building, @Nullable final Screen parent, final int preferredWidth)
    {
        super(Component.literal(title), parent, preferredWidth);
        this.building = building;
        this.hut = parent instanceof HutScreen screen ? screen : parent instanceof ModuleScreen page ? page.hut : null;
        this.navCurrent = parent instanceof ModuleScreen page ? page.navCurrent : HutNav.OVERVIEW;
        this.hub = parent instanceof ColonyHubScreen screen ? screen : parent instanceof ModuleScreen page ? page.hub : null;
    }

    protected final boolean framed()
    {
        return hut != null;
    }

    @Override
    protected boolean compact()
    {
        return !framed() && hub == null;
    }

    @Override
    protected void init()
    {
        if (hub != null)
        {
            // Same place and size as the hub around it
            hub.sync(minecraft, width, height);
        }
        super.init();
        if (hub != null)
        {
            x0 = hub.frameX();
            y0 = hub.frameY();
            w = hub.frameW();
            h = hub.frameH();
        }
    }

    @Override
    protected void renderFrame(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        if (hub != null)
        {
            hub.chrome(graphics, mouseX, mouseY, title.getString());
            return;
        }
        super.renderFrame(graphics, mouseX, mouseY);
    }

    @Override
    protected int contentTop()
    {
        return hub != null ? y0 + ColonyHubScreen.headerHeight() + 7 : super.contentTop();
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        return super.clicked(mouseX, mouseY) || hub != null && hub.chromeClicked(mouseX, mouseY);
    }

    @Override
    public void tick()
    {
        final IBuildingView fresh = building.getColony().getBuilding(building.getID());
        if (fresh != null)
        {
            building = fresh;
        }
    }

    @Override
    @Nullable
    protected String crumb()
    {
        return framed() ? hut.crumb() : RequestScreen.nameOf(building);
    }

    @Override
    protected Component shownTitle()
    {
        return framed() ? hut.getTitle() : title;
    }

    @Override
    @Nullable
    protected Integer headerFill()
    {
        return framed() ? Palette.levelFill(building.getBuildingLevel()) : null;
    }

    @Override
    protected int headerEdge()
    {
        return framed() ? Palette.levelEdge(building.getBuildingLevel()) : super.headerEdge();
    }

    @Override
    protected int headerText()
    {
        return framed() ? Palette.levelText(building.getBuildingLevel()) : super.headerText();
    }

    @Override
    public void onClose()
    {
        if (parent == null)
        {
            // Opened from one of MineColonies' windows: back to the building, as their back button did
            building.openGui(false);
            return;
        }
        super.onClose();
    }

    @Override
    protected int left()
    {
        return framed() ? x0 + HutNav.navWidth(w) + 8 : hub != null ? x0 + hub.navW() + 7 : super.left();
    }

    @Override
    protected int innerWidth()
    {
        return framed() ? w - HutNav.navWidth(w) - 22 : hub != null ? w - hub.navW() - 16 : super.innerWidth();
    }

    @Override
    protected void renderChrome(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        if (framed())
        {
            HutNav.header(graphics, font, x0 + 8 + font.width(shownTitle()) + (crumb() == null ? 0 : font.width(crumb() + " › ")), y0, building, headerText(), mouseX, mouseY,
              this::addHit);
            HutNav.nav(graphics, font, x0, y0, w, h, building, hut, navCurrent, mouseX, mouseY, this::addHit);
        }
        if (hub != null)
        {
            // The town hall's frame around this page: its hover help (Influence and gold, the left column) shows here too
            hint = hub.chromeHint();
        }
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        // Over the hub's left column, the wheel scrolls the column rather than the page
        return hub != null && hub.navScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
