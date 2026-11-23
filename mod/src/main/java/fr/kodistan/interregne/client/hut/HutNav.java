package fr.kodistan.interregne.client.hut;

import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.modules.IMinimumStockModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.core.colony.buildings.moduleviews.CourierAssignmentModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseRequestTaskModuleView;
import com.minecolonies.core.client.gui.WindowHutNameEntry;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What stays put in a building's window whichever page is open: the header with the level and rename, and the left
 * column with the overview, the building's pages and its tools. The hut screen and every page of the building draw
 * it the same way, so only the right part changes when the player moves between pages.
 */
public final class HutNav
{
    public static int navWidth(final int windowWidth)
    {
        return Ui.navWidth(windowWidth);
    }
    /**
     * The overview's key; a page's key is {@link #key(IBuildingModuleView)}, a tool's its label.
     */
    public static final String OVERVIEW = "";

    @FunctionalInterface
    public interface Hits
    {
        void add(int x, int y, int width, int height, Runnable action);
    }

    private record Tool(String label, Icon icon, Runnable action)
    {
    }

    private HutNav()
    {
    }

    public static String key(final IBuildingModuleView view)
    {
        return "page:" + view.getDesc();
    }

    /**
     * The key of a page of the building's own body ({@link HutBody.Tab#key}).
     */
    public static String tabKey(final String tab)
    {
        return "tab:" + tab;
    }

    /**
     * The level, one square per level in its own colour filled up to the current one, and ✎ to rename; after the title
     * the shell draws.
     */
    public static void header(final GuiGraphics graphics, final Font font, final int titleEnd, final int y0, final IBuildingView building, final int textColor,
      final int mouseX, final int mouseY, final Hits hits)
    {
        int x = titleEnd + 8;
        final int level = building.getBuildingLevel();
        final int max = building.getBuildingMaxLevel();
        final String text = level == 0 ? "Pas construit" : "Niveau " + level;
        final int plateW = font.width(text) + 10 + max * 8;
        Ui.plate(graphics, x, y0 + 4, x + plateW, y0 + 17, Palette.PAPER_LIGHT, Palette.EDGE, Palette.PAPER_LIGHT);
        graphics.drawString(font, text, x + 4, y0 + 7, Palette.INK, false);
        int px = x + 6 + font.width(text);
        for (int l = 1; l <= max; l++)
        {
            graphics.fill(px, y0 + 7, px + 6, y0 + 14, Palette.levelEdge(l));
            graphics.fill(px + 1, y0 + 8, px + 5, y0 + 13, l <= level ? Palette.levelFill(l) : Palette.PAPER_LIGHT);
            px += 8;
        }
        x += plateW + 6;
        final boolean over = Ui.inside(mouseX, mouseY, x - 2, y0 + 4, 12, 12);
        graphics.drawString(font, "✎", x, y0 + 7, over ? Palette.SELECTED : textColor, false);
        hits.add(x - 2, y0 + 4, 12, 12, () -> new WindowHutNameEntry(building).open());
    }

    public static boolean isGuardTower(final IBuildingView building)
    {
        final String type = building.getBuildingType().getRegistryName().getPath();
        return "guardtower".equals(type) || "barrackstower".equals(type);
    }

    public static List<IBuildingModuleView> pages(final IBuildingView building)
    {
        final List<IBuildingModuleView> pages = new ArrayList<>();
        // Guards are bought and raids are off: the barracks and its towers consume nothing, a minimum stock there is dead weight
        final boolean barracks = "barracks".equals(building.getBuildingType().getRegistryName().getPath());
        // Towers only house the guards bought elsewhere: their hiring pages (knight, archer, druid) are gone
        final boolean tower = isGuardTower(building);
        // Troops fight gearless, so a guard building has no weapons to hand out; the academy and archery sell troops, no trainees
        final boolean guards = building instanceof AbstractBuildingGuards.View;
        final String path = building.getBuildingType().getRegistryName().getPath();
        final boolean school = "combatacademy".equals(path) || "archery".equals(path);
        for (final IBuildingModuleView view : building.getAllModuleViews())
        {
            if (view.isPageVisible() && !(view instanceof CourierAssignmentModuleView) && !(view instanceof WarehouseRequestTaskModuleView)
              && !((barracks || tower) && view instanceof IMinimumStockModuleView) && !((tower || school) && view instanceof WorkerBuildingModuleView)
              && !(guards && view instanceof ToolModuleView))
            {
                pages.add(view);
            }
        }
        return pages;
    }

    /**
     * Chest, whole stock, help when MineColonies has a text for this building, and the colony hub when the window was
     * not opened from it.
     */
    private static List<Tool> tools(final IBuildingView building, final HutScreen hut)
    {
        final List<Tool> tools = new ArrayList<>();
        // The bank, the depot and the relais keep nothing in a chest and no stock of their own: no such tools
        final boolean ownBody = HutBodies.of(building) != null;
        if (!ownBody)
        {
            tools.add(new Tool("Coffre", Icon.of(Items.CHEST), () -> new OpenInventoryMessage(building).sendToServer()));
        }
        // In a warehouse with the magic network, the register takes and stores as well as it lists
        if (!HutScreen.isWarehouse(building) && !ownBody)
        {
            tools.add(new Tool("Tout le stock", Icon.module("inventory"), () -> StockScreen.open(building, hut)));
        }
        if (I18n.exists(HelpScreen.prefix(building) + "0"))
        {
            tools.add(new Tool("Aide", Icon.of(Items.BOOK), () -> HelpScreen.open(building, hut)));
        }
        final ITownHallView townHall = building.getColony().getTownHall();
        if (!hut.fromHub() && townHall != null && townHall != building)
        {
            tools.add(new Tool("Colonie", Icon.of(townHall.getBuildingType().getBuildingBlock()), () -> ColonyHubScreen.open(townHall)));
        }
        if (townHall != null)
        {
            tools.add(new Tool("Carte", Icon.of(Items.FILLED_MAP), () -> {
                final String why = ColonyHubScreen.openMap(townHall);
                if (why != null && Minecraft.getInstance().player != null)
                {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("Carte : " + why), true);
                }
            }));
        }
        return tools;
    }

    /**
     * The left column, the current entry lit; a click on another one opens it in place of the right part.
     */
    public static void nav(final GuiGraphics graphics, final Font font, final int x0, final int y0, final int w, final int h, final IBuildingView building,
      final HutScreen hut, final String current, final int mouseX, final int mouseY, final Hits hits)
    {
        final int navW = navWidth(w);
        final int x = x0 + 5;
        final int width = navW - 8;
        graphics.fill(x0 + navW, y0 + 22, x0 + navW + 1, y0 + h - 4, Palette.RULE);
        int y = y0 + 26;
        if (!Ui.rail(navW))
        {
            Ui.label(graphics, font, "BÂTIMENT", x + 2, y0 + 25);
            y = y0 + 36;
        }
        item(graphics, font, x, y, width, Icon.module("main"), "Vue d'ensemble", OVERVIEW.equals(current), mouseX, mouseY, hits, () -> hut.showTab(""));
        y += Ui.NAV_ROW + 1;
        // The building's own pages (the Relais's new offer, the Depot's crates), right under the overview
        for (final HutBody.Tab bodyTab : hut.bodyTabs())
        {
            item(graphics, font, x, y, width, bodyTab.icon(), Ui.rail(navW) ? bodyTab.label() : Ui.fit(font, bodyTab.label(), width - 22), tabKey(bodyTab.key()).equals(current),
              mouseX, mouseY, hits, () -> hut.showTab(bodyTab.key()));
            y += Ui.NAV_ROW + 1;
        }

        final List<Tool> tools = tools(building, hut);
        final int toolsTop = y0 + h - 6 - tools.size() * (Ui.NAV_ROW + 1);
        for (final IBuildingModuleView view : pages(building))
        {
            if (y + Ui.NAV_ROW > toolsTop - 4)
            {
                break;
            }
            final String full = Component.translatable(view.getDesc().toLowerCase(Locale.US)).getString();
            final String label = Ui.rail(navW) ? full : Ui.fit(font, full, width - 22);
            item(graphics, font, x, y, width, Icon.texture(view.getIconResourceLocation(), 20), label, key(view).equals(current), mouseX, mouseY, hits, () -> {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                view.getWindow().open();
            });
            y += Ui.NAV_ROW + 1;
        }

        graphics.fill(x + 2, toolsTop - 3, x + width - 2, toolsTop - 2, Palette.RULE);
        y = toolsTop;
        for (final Tool tool : tools)
        {
            item(graphics, font, x, y, width, tool.icon(), tool.label(), tool.label().equals(current), mouseX, mouseY, hits, tool.action());
            y += Ui.NAV_ROW + 1;
        }
    }

    private static void item(final GuiGraphics graphics, final Font font, final int x, final int y, final int width, final Icon icon, final String label,
      final boolean lit, final int mouseX, final int mouseY, final Hits hits, final Runnable action)
    {
        final boolean over = !lit && Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW);
        Ui.navItem(graphics, font, x, y, width, icon, label, lit, over);
        if (!lit)
        {
            hits.add(x, y, width, Ui.NAV_ROW, action);
        }
    }

    /**
     * Where a page of a building opened from this screen should go back to: the hut window itself, never the page it
     * was opened from, so that moving between pages does not pile them up.
     */
    @Nullable
    public static net.minecraft.client.gui.screens.Screen parentFor(@Nullable final net.minecraft.client.gui.screens.Screen current)
    {
        if (current instanceof ModuleScreen page && page.hut != null)
        {
            return page.hut;
        }
        return current instanceof fr.kodistan.interregne.client.ui.ParchmentScreen ? current : null;
    }
}
