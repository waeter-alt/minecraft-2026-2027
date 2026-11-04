package fr.kodistan.interregne.client.hut;

import fr.kodistan.interregne.colony.Malus;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.minecolonies.core.client.gui.WindowHutNameEntry;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.huts.WindowHutLiving;
import com.minecolonies.core.client.gui.huts.WindowHutWorkerModulePlaceholder;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.minecolonies.core.network.messages.server.colony.building.RecallCitizenHutMessage;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.RecallCitizenMessage;
import fr.kodistan.interregne.client.BuildWindowPricing;
import fr.kodistan.interregne.client.CrateRows;
import fr.kodistan.interregne.client.catalogue.CatalogueIndex;
import fr.kodistan.interregne.client.hud.Grievances;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.colony.Yields;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import com.minecolonies.api.crafting.ItemStorage;
import fr.kodistan.interregne.network.TerminalDepositPayload;
import fr.kodistan.interregne.network.TerminalWithdrawPayload;
import fr.kodistan.interregne.warehouse.MagicNetwork;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The hut window: how the building is doing in one line, its workers, what it makes, what it waits for, and on the
 * right what the next level costs and brings. Its modules (recipes, settings…) are the left navigation.
 */
public final class HutScreen extends ParchmentScreen
{
    private int navW()
    {
        return HutNav.navWidth(w);
    }

    /**
     * The right column (level, upgrade): a little narrower when the window is.
     */
    private int sideW()
    {
        return w >= 470 ? 150 : 134;
    }
    private static final int WORKER_H = 24;
    private static final int REQUEST_H = 18;
    private static final int SMALL_H = 12;

    /**
     * Something clickable drawn this frame.
     */
    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private record Worker(ICitizenDataView citizen, String jobKey)
    {
    }

    private record Banner(Ui.State state, String title, String text, @Nullable String action, @Nullable Runnable onAction)
    {
    }

    private IBuildingView building;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    private int scroll;
    private int contentHeight;

    /**
     * A line of help for what the mouse is over, drawn as a tooltip.
     */
    @Nullable
    private String hint;

    /**
     * A warehouse's stock, its search field, and when it is next counted.
     */
    private final List<ItemStorage> stock = new ArrayList<>();
    @Nullable
    private EditBox stockSearch;
    private int stockRefresh;

    private HutScreen(final IBuildingView building, @Nullable final Screen parent)
    {
        super(Component.literal(nameOf(building)), parent);
        this.building = building;
    }

    public static boolean enabled()
    {
        return BuildWindowPricing.enabled();
    }

    public static void open(final IBuildingView building)
    {
        Minecraft.getInstance().setScreen(new HutScreen(building, null));
    }

    /**
     * Open a building from another of our screens: our hut window when it has one, Escape coming back here; the
     * building's own window otherwise.
     */
    /**
     * Our hut window for any building, Escape coming back to the parent (the colony hub…).
     */
    public static void openWith(final IBuildingView building, final Screen parent)
    {
        Minecraft.getInstance().setScreen(new HutScreen(building, parent));
    }

    public static void openFrom(final IBuildingView building, final Screen parent)
    {
        final BOWindow window = building.getWindow();
        if (window != null && (window.getClass() == WindowHutWorkerModulePlaceholder.class || window.getClass() == WindowHutLiving.class) && enabled())
        {
            Minecraft.getInstance().setScreen(new HutScreen(building, parent));
        }
        else if (window != null)
        {
            window.open();
        }
    }

    private static String nameOf(final IBuildingView building)
    {
        final String custom = building.getCustomName();
        if (custom != null && !custom.isEmpty())
        {
            return custom;
        }
        return CatalogueIndex.buildingName(idOf(building), Component.translatable(building.getBuildingDisplayName()).getString());
    }

    private static String idOf(final IBuildingView building)
    {
        return building.getBuildingType().getRegistryName().getPath();
    }

    public static boolean isWarehouse(final IBuildingView building)
    {
        return idOf(building).equals("warehouse");
    }

    @Override
    protected void init()
    {
        super.init();
        if (isWarehouse(building))
        {
            final String typed = stockSearch == null ? "" : stockSearch.getValue();
            stockSearch = new fr.kodistan.interregne.client.ui.ParchmentField(font, 0, 0, 110, 12, Component.literal("Rechercher…"));
            stockSearch.setValue(typed);
            stockSearch.setResponder(text -> scroll = 0);
            addWidget(stockSearch);
        }
    }

    @Override
    public void tick()
    {
        // The colony view may swap the building view on an update: always show the newest one
        final IBuildingView fresh = building.getColony().getBuilding(building.getID());
        if (fresh != null)
        {
            building = fresh;
        }
        if (isWarehouse(building) && stockRefresh-- <= 0)
        {
            stockRefresh = 20;
            stock.clear();
            stock.addAll(TerminalScreen.stockOf(building));
        }
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        hits.clear();
        slots.clear();
        hint = null;
        renderHeader(graphics, mouseX, mouseY);
        renderNav(graphics, mouseX, mouseY);
        renderMain(graphics, mouseX, mouseY);
        renderSide(graphics, mouseX, mouseY);
        for (final Ui.Slot slot : slots)
        {
            if (Ui.inside(mouseX, mouseY, slot.x(), slot.y(), slot.size(), slot.size()))
            {
                graphics.renderTooltip(font, slot.stack(), mouseX, mouseY);
            }
        }
        if (hint != null)
        {
            graphics.renderTooltip(font, font.split(Component.literal(hint), 220), mouseX, mouseY);
        }
    }

    // --- Header: level and rename, after the title the shell draws ---

    private void renderHeader(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        HutNav.header(graphics, font, x0 + 8 + font.width(title), y0, building, headerText(), mouseX, mouseY, (x, y, w, h, a) -> hits.add(new Hit(x, y, w, h, a)));
    }

    @Override
    protected Integer headerFill()
    {
        return Palette.levelFill(building.getBuildingLevel());
    }

    @Override
    protected int headerEdge()
    {
        return Palette.levelEdge(building.getBuildingLevel());
    }

    @Override
    protected int headerText()
    {
        return Palette.levelText(building.getBuildingLevel());
    }

    /**
     * A heading on a band of this level's colour, across the side panel; returns the y below it.
     */
    private int levelHeading(final GuiGraphics graphics, final int x, int y, final int width, final String text, final int level)
    {
        Ui.plate(graphics, x - 3, y - 2, x + width + 3, y + 12, Palette.levelFill(level), Palette.levelEdge(level), Palette.levelFill(level));
        final Component bold = Component.literal(Ui.fitBold(font, text, width - 2)).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, bold, x, y + 1, Palette.levelText(level), false);
        return y + 16;
    }

    // --- Left: the building's pages and its tools ---

    private void renderNav(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        HutNav.nav(graphics, font, x0, y0, w, h, building, this, HutNav.OVERVIEW, mouseX, mouseY, (x, y, w, h, a) -> hits.add(new Hit(x, y, w, h, a)));
    }

    boolean fromHub()
    {
        return parent instanceof ColonyHubScreen;
    }

    @Override
    protected String crumb()
    {
        return parent instanceof ColonyHubScreen ? "Colonie" : null;
    }

    // --- Middle: state, workers, production, requests ---

    private int mainLeft()
    {
        return x0 + navW() + 7;
    }

    private int mainWidth()
    {
        return w - navW() - sideW() - 14;
    }

    private int mainTop()
    {
        return y0 + HEADER + 4;
    }

    private int mainBottom()
    {
        return y0 + h - 5;
    }

    private void renderMain(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int x = mainLeft();
        final int width = mainWidth();
        final int top = mainTop();
        final int bottom = mainBottom();
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final boolean overMain = Ui.inside(mouseX, mouseY, x, top, width, bottom - top);
        final int my = overMain ? mouseY : Integer.MIN_VALUE;

        if (isWarehouse(building))
        {
            renderStock(graphics, mouseX, mouseY, x, top, width, bottom);
            return;
        }
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;
        final List<Worker> workers = workers();
        final List<IRequest<?>> requests = requests(workers);

        final Banner banner = banner(workers, requests);
        final int aw = banner.action() == null ? 0 : font.width(banner.action()) + 6;
        final int bannerY = y;
        y += Ui.banner(graphics, font, x, y, width, banner.state(), banner.title(), banner.text(), aw == 0 ? 0 : aw + 4);
        if (banner.action() != null)
        {
            final int ax = x + width - aw - 2;
            final boolean over = Ui.inside(mouseX, my, ax, bannerY + 1, aw, 12);
            Ui.solidButton(graphics, font, ax, bannerY + 1, aw, 12, banner.action(), banner.state(), over);
            addHit(ax, bannerY + 1, aw, 12, banner.onAction());
        }
        y += 20;

        if (hasJobs())
        {
            y = renderWorkers(graphics, mouseX, my, x, y, width, workers);
        }
        if (building instanceof LivingBuildingView home)
        {
            y = renderResidents(graphics, mouseX, my, x, y, width, home);
        }
        else
        {
            y = renderProduction(graphics, mouseX, my, x, y, width, workers);
        }
        y = renderRequests(graphics, mouseX, my, x, y, width, requests);
        graphics.disableScissor();

        contentHeight = y + scroll - top;
        if (contentHeight > bottom - top)
        {
            final int track = bottom - top;
            final int thumb = Math.max(12, track * track / contentHeight);
            final int thumbY = top + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            graphics.fill(x + width + 2, top, x + width + 4, bottom, Palette.PAPER_DARK);
            graphics.fill(x + width + 2, thumbY, x + width + 4, thumbY + thumb, Palette.RULE);
        }
    }

    /**
     * A warehouse's middle column: everything in its racks, searchable; with the magic network, storing all that is
     * carried in one click and taking one item or a stack from each line.
     */
    private void renderStock(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int top, final int width, final int bottom)
    {
        final boolean magic = MagicNetwork.enabled();
        int total = 0;
        for (final ItemStorage storage : stock)
        {
            total += storage.getAmount();
        }
        int tx = x;
        if (magic)
        {
            final String deposit = "Tout ranger";
            final int bw = font.width(deposit) + 10;
            final boolean over = Ui.inside(mouseX, mouseY, x, top, bw, 14);
            Ui.button(graphics, font, x, top, bw, 14, deposit, Ui.ButtonStyle.PRIMARY, true, over);
            hits.add(new Hit(x, top, bw, 14, () -> {
                PacketDistributor.sendToServer(new TerminalDepositPayload(building.getID()));
                stockRefresh = 5;
            }));
            if (over)
            {
                hint = "Range tout ce que tu portes, sauf ta barre d'action.";
            }
            tx += bw + 6;
        }
        // The totals only when they fit whole beside the search box
        final Component totals = Component.literal(total + " objets · " + stock.size() + " sortes").withStyle(ChatFormatting.BOLD);
        if (font.width(totals) <= width - (tx - x) - 118)
        {
            graphics.drawString(font, totals, tx, top + 3, Palette.INK, false);
        }
        if (stockSearch != null)
        {
            stockSearch.setX(x + width - 110);
            stockSearch.setY(top + 1);
            stockSearch.render(graphics, mouseX, mouseY, 0);
        }

        final int listTop = top + 19;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - listTop)));
        final int my = Ui.inside(mouseX, mouseY, x, listTop, width, bottom - listTop) ? mouseY : Integer.MIN_VALUE;
        final String query = stockSearch == null ? "" : stockSearch.getValue().trim().toLowerCase(Locale.ROOT);
        graphics.enableScissor(x, listTop, x + width, bottom);
        int y = listTop - scroll;
        int shown = 0;
        for (final ItemStorage storage : stock)
        {
            final ItemStack icon = storage.getItemStack().copyWithCount(1);
            final String name = icon.getHoverName().getString();
            if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query))
            {
                continue;
            }
            shown++;
            Ui.card(graphics, x, y, width, 20, false, false, false);
            Icon.of(icon).draw(graphics, x + 2, y + 2, 16);
            if (Ui.inside(mouseX, my, x + 2, y + 2, 16, 16))
            {
                slots.add(new Ui.Slot(x + 2, y + 2, 16, icon));
            }
            final String count = storage.getAmount() >= 10_000 ? storage.getAmount() / 1000 + "k" : String.valueOf(storage.getAmount());
            graphics.drawString(font, Component.literal(count).withStyle(ChatFormatting.BOLD), x + 22, y + 6, Palette.INK, false);
            // Narrow window: « 1 » and « 64 » instead of « Un » and « Un stack » when names would lack room
            final int fullButtonsW = font.width("Un stack") + 10 + 3 + font.width("Un") + 10 + 4;
            final boolean shortLabels = width - 56 - fullButtonsW - 6 < 110;
            final String pile = shortLabels ? "64" : "Un stack";
            final String one = shortLabels ? "1" : "Un";
            final int buttonsW = magic ? font.width(pile) + 10 + 3 + font.width(one) + 10 + 4 : 0;
            graphics.drawString(font, Ui.fit(font, name, width - 56 - buttonsW - 6), x + 56, y + 6, Palette.INK, false);
            if (magic)
            {
                int bx = x + width - 4 - (font.width(pile) + 10);
                stockButton(graphics, mouseX, my, bx, y + 3, pile, () -> take(storage, 64));
                bx -= font.width(one) + 10 + 3;
                stockButton(graphics, mouseX, my, bx, y + 3, one, () -> take(storage, 1));
            }
            y += 22;
        }
        if (shown == 0)
        {
            graphics.drawString(font, stock.isEmpty() ? "L'entrepôt est vide." : "Rien ne correspond.", x + 2, y + 2, Palette.MUTED, false);
            y += 14;
        }
        graphics.disableScissor();
        contentHeight = y + scroll - listTop;
        if (contentHeight > bottom - listTop)
        {
            final int track = bottom - listTop;
            final int thumb = Math.max(12, track * track / contentHeight);
            final int thumbY = listTop + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            graphics.fill(x + width + 2, listTop, x + width + 4, bottom, Palette.PAPER_DARK);
            graphics.fill(x + width + 2, thumbY, x + width + 4, thumbY + thumb, Palette.RULE);
        }
    }

    private void stockButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final String label, final Runnable action)
    {
        final int bw = font.width(label) + 10;
        Ui.button(graphics, font, x, y, bw, 14, label, Ui.ButtonStyle.SECONDARY, true, Ui.inside(mouseX, mouseY, x, y, bw, 14));
        addHit(x, y, bw, 14, action);
    }

    private void take(final ItemStorage storage, final int amount)
    {
        PacketDistributor.sendToServer(new TerminalWithdrawPayload(building.getID(), storage.getItemStack().copyWithCount(1), amount));
        stockRefresh = 5;
    }

    /**
     * A click target inside the scrolling middle column: only its visible part counts.
     */
    private void addHit(final int x, int y, final int width, final int height, @Nullable final Runnable action)
    {
        if (action == null)
        {
            return;
        }
        final int top = Math.max(y, mainTop());
        final int bottom = Math.min(y + height, mainBottom());
        if (bottom > top)
        {
            hits.add(new Hit(x, top, width, bottom - top, action));
        }
    }

    private int renderWorkers(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width,
      final List<Worker> workers)
    {
        Ui.label(graphics, font, "OUVRIERS", x, y);
        int max = 0;
        for (final WorkerBuildingModuleView module : building.getModuleViews(WorkerBuildingModuleView.class))
        {
            max += module.getMaxInhabitants();
        }
        final String filled = workers.size() + " / " + max;
        graphics.drawString(font, filled, x + font.width("OUVRIERS") + 6, y, Palette.INK, false);

        final int recallW = font.width("Rappeler") + 10;
        final int manageW = font.width("Gérer") + 10;
        final int recallX = x + width - recallW;
        final int manageX = recallX - manageW - 3;
        if (x + font.width("OUVRIERS") + 6 + font.width(filled) + 6 > manageX)
        {
            y += 12;
        }
        final boolean canHire = building.allowsAssignment();
        Ui.button(graphics, font, manageX, y - 2, manageW, SMALL_H, "Gérer", Ui.ButtonStyle.SECONDARY, canHire,
          Ui.inside(mouseX, mouseY, manageX, y - 2, manageW, SMALL_H));
        if (canHire)
        {
            addHit(manageX, y - 2, manageW, SMALL_H, this::hire);
        }
        final boolean canRecall = !workers.isEmpty();
        Ui.button(graphics, font, recallX, y - 2, recallW, SMALL_H, "Rappeler", Ui.ButtonStyle.SECONDARY, canRecall,
          Ui.inside(mouseX, mouseY, recallX, y - 2, recallW, SMALL_H));
        if (canRecall)
        {
            addHit(recallX, y - 2, recallW, SMALL_H, () -> new RecallCitizenMessage(building).sendToServer());
        }
        y += 14;

        if (workers.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal(canHire ? "Personne ne travaille ici." : "Construisez le bâtiment pour y embaucher."), x + 2, y,
              width - 4, Palette.MUTED, 10) + 4;
        }
        for (final Worker worker : workers)
        {
            final ICitizenDataView citizen = worker.citizen();
            // Sickness already has its chip: the line does not repeat it
            final String job = CitizenBits.jobName(worker.jobKey(), citizen.isFemale());
            y = citizenCard(graphics, mouseX, mouseY, x, y, width, citizen, citizen.isSick() ? job : job + " · " + CitizenBits.activity(citizen));
        }
        return y + 2;
    }

    /**
     * A citizen as a card: face, name, a line under it, their problems as chips (a click settles one), the rest of the
     * card opening their sheet; returns the y below it.
     */
    private int citizenCard(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final ICitizenDataView citizen,
      final String line)
    {
        final Component fullName = Component.literal(citizen.getName()).withStyle(ChatFormatting.BOLD);
        final List<CitizenBits.Chip> chips = CitizenBits.chips(citizen);
        int chipsW = 0;
        for (final CitizenBits.Chip chip : chips)
        {
            chipsW += font.width(chip.text()) + 11;
        }
        // Chips beside the name when they fit, otherwise under the line, wrapping as often as needed
        final boolean inline = font.width(fullName) + chipsW + 30 <= width;
        int chipLines = 0;
        if (!inline && !chips.isEmpty())
        {
            chipLines = 1;
            int cx = x + 24;
            for (final CitizenBits.Chip chip : chips)
            {
                final int chipW = font.width(chip.text()) + 8;
                if (cx + chipW > x + width - 4 && cx > x + 24)
                {
                    chipLines++;
                    cx = x + 24;
                }
                cx += chipW + 3;
            }
        }
        final int cardH = WORKER_H - 2 + chipLines * 13;
        final boolean overCard = Ui.inside(mouseX, mouseY, x, y, width, cardH);
        Ui.card(graphics, x, y, width, cardH, false, overCard, false);
        CitizenBits.face(graphics, citizen, x + 4, y + 3, 16);
        final String nameText = fit(citizen.getName(), inline ? width - 28 - chipsW : width - 32);
        graphics.drawString(font, Component.literal(nameText).withStyle(ChatFormatting.BOLD), x + 24, y + 3, Palette.INK, false);
        graphics.drawString(font, fit(line, width - 28), x + 24, y + 12, Palette.MUTED, false);

        int chipX = inline ? x + width - 4 - chipsW + 3 : x + 24;
        int chipY = inline ? y + 2 : y + 22;
        for (final CitizenBits.Chip chip : chips)
        {
            final int chipW = font.width(chip.text()) + 8;
            if (!inline && chipX + chipW > x + width - 4 && chipX > x + 24)
            {
                chipX = x + 24;
                chipY += 13;
            }
            final boolean over = chip.action() != null && Ui.inside(mouseX, mouseY, chipX, chipY, chipW, 11);
            if (Ui.inside(mouseX, mouseY, chipX, chipY, chipW, 11) && chip.hover() != null)
            {
                hint = chip.hover();
            }
            Ui.chip(graphics, font, chipX, chipY, chip.text(), chip.soft(), chip.ink(), chip.action() != null, over);
            if (chip.action() != null)
            {
                addHit(chipX, chipY, chipW, 11, chip.action());
                if (over)
                {
                    hint = "Cliquez pour régler le problème de " + citizen.getName();
                }
            }
            chipX += chipW + 3;
        }
        // After the chips, so a click on a chip settles the problem instead
        addHit(x, y, width, cardH, () -> CitizenScreen.open(citizen, this));
        if (overCard && hint == null)
        {
            hint = "Voir la fiche de " + citizen.getName();
        }
        return y + cardH + 2;
    }

    private int renderResidents(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final LivingBuildingView home)
    {
        Ui.label(graphics, font, "HABITANTS", x, y);
        final String filled = home.getResidents().size() + " / " + home.getMax();
        graphics.drawString(font, filled, x + font.width("HABITANTS") + 6, y, Palette.INK, false);
        final int recallW = font.width("Rappeler") + 10;
        final int manageW = font.width("Gérer") + 10;
        final int recallX = x + width - recallW;
        final int manageX = recallX - manageW - 3;
        if (x + font.width("HABITANTS") + 6 + font.width(filled) + 6 > manageX)
        {
            y += 12;
        }
        final boolean built = home.getBuildingLevel() > 0;
        final boolean overManage = Ui.inside(mouseX, mouseY, manageX, y - 2, manageW, SMALL_H);
        Ui.button(graphics, font, manageX, y - 2, manageW, SMALL_H, "Gérer", Ui.ButtonStyle.SECONDARY, built, overManage);
        if (built)
        {
            addHit(manageX, y - 2, manageW, SMALL_H, this::house);
        }
        else if (overManage)
        {
            hint = "Construisez la maison pour y loger quelqu'un.";
        }
        final boolean canRecall = !home.getResidents().isEmpty();
        final boolean overRecall = Ui.inside(mouseX, mouseY, recallX, y - 2, recallW, SMALL_H);
        Ui.button(graphics, font, recallX, y - 2, recallW, SMALL_H, "Rappeler", Ui.ButtonStyle.SECONDARY, canRecall, overRecall);
        if (canRecall)
        {
            addHit(recallX, y - 2, recallW, SMALL_H, () -> new RecallCitizenHutMessage(home).sendToServer());
            if (overRecall)
            {
                hint = "Fait revenir les habitants à la maison.";
            }
        }
        y += 14;
        if (home.getResidents().isEmpty())
        {
            graphics.drawString(font, built ? "Personne n'habite ici." : "Construisez la maison pour y loger quelqu'un.", x + 2, y, Palette.MUTED, false);
            return y + 14;
        }
        for (final int id : home.getResidents())
        {
            final ICitizenDataView citizen = home.getColony().getCitizen(id);
            if (citizen != null)
            {
                final String job = citizen.getJob() == null || citizen.getJob().isEmpty() ? "Sans emploi" : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());
                y = citizenCard(graphics, mouseX, mouseY, x, y, width, citizen, citizen.isSick() ? job : job + " · " + CitizenBits.activity(citizen));
            }
        }
        return y + 2;
    }

    private void house()
    {
        if (building instanceof LivingBuildingView home)
        {
            HousingScreen.open(home, this);
        }
    }

    /**
     * Whether people work here (the town hall, a house… have no jobs).
     */
    private boolean hasJobs()
    {
        return !building.getModuleViews(WorkerBuildingModuleView.class).isEmpty();
    }

    private void hire()
    {
        HireScreen.open(building, this);
    }

    private int renderProduction(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final List<Worker> workers)
    {
        final String id = idOf(building);
        final List<List<ItemStack>> flow = CatalogueIndex.flow(id);
        final Component rate = CatalogueIndex.production(id);
        final int level = building.getBuildingLevel();
        y = Ui.separator(graphics, x, y - 4, width);
        Ui.label(graphics, font, "PRODUCTION", x, y);
        if (level > 0)
        {
            graphics.drawString(font, fit("au niveau " + level, width - (font.width("PRODUCTION") + 6)), x + font.width("PRODUCTION") + 6, y, Palette.MUTED, false);
        }
        y += 12;
        if (!flow.isEmpty())
        {
            y = Ui.flow(graphics, font, x, y, width, flow, slots) + 4;
        }
        if (rate != null)
        {
            y = Ui.wrapped(graphics, font, rate, x, y, width, Palette.INK, 10);
        }
        if (level == 0)
        {
            return Ui.wrapped(graphics, font, Component.literal("Rien tant que le bâtiment n'est pas construit."), x, y, width, Palette.MUTED, 10) + 2;
        }
        if (Yields.isRawProducer(building.getBuildingType()))
        {
            graphics.drawString(font, fit("Récoltes ×" + format(Yields.outputMultiplier(level)), width), x, y, Palette.INK, false);
            y += 10;
        }
        // Hungry or sick workers: the same average slowdown the server applies to the hut
        double slowdown = 0;
        for (final Worker worker : workers)
        {
            slowdown += Malus.factor(worker.citizen().getSaturation(), worker.citizen().isSick());
        }
        slowdown = workers.isEmpty() ? 1.0 : slowdown / workers.size();
        final double speed = Yields.speedMultiplier(level);
        if (slowdown <= 1.0)
        {
            return Ui.wrapped(graphics, font, Component.literal("Vitesse de travail ×" + format(speed)), x, y, width, Palette.INK, 10) + 2;
        }
        final int top = y;
        y = Ui.wrapped(graphics, font, Component.literal("Vitesse de travail ×" + format(speed / slowdown) + " (×" + format(speed) + " " + Malus.percent(slowdown) + ")"),
          x, y, width, Palette.RED, 10) + 2;
        if (Ui.inside(mouseX, mouseY, x, top, width, y - top))
        {
            hint = "Niveau " + level + " : ×" + format(speed) + ".\nOuvriers affamés ou malades : " + Malus.times(slowdown) + " moins vite en moyenne ("
              + Malus.percent(slowdown) + "), sur leur travail comme sur la production de la hutte.\nNourrissez-les et soignez-les pour retrouver ×" + format(speed) + ".";
        }
        return y;
    }

    private int renderRequests(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width,
      final List<IRequest<?>> requests)
    {
        y = Ui.separator(graphics, x, y - 4, width);
        Icon.module("requests").draw(graphics, x, y - 3, 12);
        Ui.label(graphics, font, "DEMANDES EN COURS", x + 15, y);
        graphics.drawString(font, Integer.toString(requests.size()), x + 15 + font.width("DEMANDES EN COURS") + 6, y, Palette.INK, false);
        y += 12;
        if (requests.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal("Ce bâtiment ne manque de rien."), x + 2, y, width - (2), Palette.MUTED, 10) + 2;
        }
        for (final IRequest<?> request : requests)
        {
            if (Ui.inside(mouseX, mouseY, x - 2, y - 1, width + 4, REQUEST_H - 1))
            {
                graphics.fill(x - 2, y - 1, x + width + 2, y + REQUEST_H - 2, Palette.CARD_HOVER);
            }
            addHit(x - 2, y - 1, width + 4, REQUEST_H - 1, () -> RequestScreen.open(request, building.getColony(), null, this));
            final List<ItemStack> stacks = request.getDisplayStacks();
            final ItemStack stack = stacks.isEmpty() ? new ItemStack(Items.PAPER) : stacks.get(0);
            Icon.of(stack).draw(graphics, x + 1, y, 16);
            slots.add(new Ui.Slot(x + 1, y, 16, stack));
            final String state = CitizenBits.requestStateText(request);
            final int chipW = font.width(state) + 8;
            Ui.chip(graphics, font, x + width - chipW, y + 3, state, CitizenBits.requestState(request));
            final String text = request.getShortDisplayString().getString();
            graphics.drawString(font, fit(text, width - chipW - 26), x + 20, y + 5, Palette.INK, false);
            y += REQUEST_H;
        }
        return y + 2;
    }

    private List<Worker> workers()
    {
        final List<Worker> workers = new ArrayList<>();
        final IColonyView colony = building.getColony();
        for (final WorkerBuildingModuleView module : building.getModuleViews(WorkerBuildingModuleView.class))
        {
            final String jobKey = module.getJobEntry().getTranslationKey();
            for (final int id : module.getAssignedCitizens())
            {
                final ICitizenDataView citizen = colony.getCitizen(id);
                if (citizen != null)
                {
                    workers.add(new Worker(citizen, jobKey));
                }
            }
        }
        return workers;
    }

    /**
     * The building's own open requests and its workers', once each.
     */
    private List<IRequest<?>> requests(final List<Worker> workers)
    {
        final Map<Object, IRequest<?>> found = new LinkedHashMap<>();
        try
        {
            building.getOpenRequestsOfBuilding().forEach(r -> found.put(r.getId(), r));
            for (final Worker worker : workers)
            {
                building.getOpenRequests(worker.citizen()).forEach(r -> found.put(r.getId(), r));
            }
        }
        catch (final RuntimeException e)
        {
            // The colony's request system is not synced yet: nothing to show
        }
        return new ArrayList<>(found.values());
    }

    /**
     * The building's state in one line, worst first.
     */
    private Banner banner(final List<Worker> workers, final List<IRequest<?>> requests)
    {
        final String name = title.getString();
        if (building.isBuilding() || building.isRepairing() || building.isDeconstructing())
        {
            return new Banner(Ui.State.WAIT, "Chantier", "les travaux sont en cours", null, null);
        }
        if (building.getBuildingLevel() == 0)
        {
            return new Banner(Ui.State.BAD, "Pas construit", "lancez la construction à droite", null, null);
        }
        if (building instanceof LivingBuildingView home)
        {
            final int residents = home.getResidents().size();
            if (residents == 0)
            {
                return new Banner(Ui.State.WAIT, "Vide", "personne n'habite ici", "Loger ›", this::house);
            }
            return new Banner(Ui.State.OK, residents >= home.getMax() ? "Pleine" : "Habitée", residents + " sur " + home.getMax() + (home.getMax() > 1 ? " lits occupés" : " lit occupé"), null, null);
        }
        if (workers.isEmpty() && hasJobs())
        {
            return new Banner(Ui.State.BAD, "Bloqué", "aucun ouvrier : rien n'est produit", "Embaucher ›", this::hire);
        }
        // The slowest worker: sick or hungry, they still work, slowed down
        Worker slowest = null;
        double worst = 1.0;
        for (final Worker worker : workers)
        {
            final double factor = Malus.factor(worker.citizen().getSaturation(), worker.citizen().isSick());
            if (factor > worst)
            {
                worst = factor;
                slowest = worker;
            }
        }
        if (slowest != null)
        {
            final ICitizenDataView citizen = slowest.citizen();
            final String why = citizen.isSick() ? (Malus.hungry(citizen.getSaturation()) ? "malade et affamé" : "malade")
              : Malus.starving(citizen.getSaturation()) ? "mourant de faim" : "affamé";
            return new Banner(Ui.State.BAD, "Ralenti", citizen.getName() + " est " + why + " : " + Malus.times(worst) + " moins vite ("
              + Malus.percent(worst) + "). L'Intendance de l'hôtel de ville " + (citizen.isSick() ? "le soigne" : "le nourrit") + " contre de l'or.", null, null);
        }
        if (!requests.isEmpty())
        {
            final String missing = requests.get(0).getShortDisplayString().getString();
            return new Banner(Ui.State.WAIT, "En attente", "il manque " + missing, null, null);
        }
        if (workers.isEmpty())
        {
            return new Banner(Ui.State.OK, "Tout va bien", "le bâtiment est en état", null, null);
        }
        final Worker first = workers.get(0);
        return new Banner(Ui.State.OK, "Tout va bien", first.citizen().getName() + " " + CitizenBits.activity(first.citizen()), null, null);
    }

    // --- Right: the next level, or the site in progress ---

    private void renderSide(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int left = detailPanel(graphics, sideW());
        final int x = left + 6;
        final int width = sideW() - 14;
        int y = y0 + 26;
        final int level = building.getBuildingLevel();
        // The whole panel is tinted with the level it is about: the next one, or the current one when there is none
        final boolean site = building.isRepairing() || building.isDeconstructing();
        final int tint = site || level >= building.getBuildingMaxLevel() ? level : level + 1;
        graphics.fill(left, y0 + 20, x0 + w - 3, y0 + h - 3, Palette.levelSoft(tint));
        graphics.fill(left, y0 + 20, left + 2, y0 + h - 3, Palette.levelEdge(tint));
        final int buttonY = actionY();

        if (building.isBuilding() || building.isRepairing() || building.isDeconstructing())
        {
            renderSite(graphics, x, y, width);
            return;
        }

        final boolean canUpgrade = level < building.getBuildingMaxLevel();
        final String secondary = "Réparer, démolir…";
        final int secondaryY = canUpgrade ? buttonY - Ui.BUTTON_H - 3 : buttonY;
        if (!canUpgrade)
        {
            y = levelHeading(graphics, x, y, width, "Niveau maximum", level);
            final String desc = CatalogueIndex.buildingDesc(idOf(building));
            if (desc != null)
            {
                Ui.wrapped(graphics, font, Component.literal(desc), x, y, width, Palette.MUTED, 9);
            }
            sideButton(graphics, mouseX, mouseY, x, secondaryY, width, secondary, Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
            return;
        }

        final int target = level + 1;
        final String full = level == 0 ? "Construire le bâtiment" : "Passer au niveau " + target;
        // Narrow side column: the short form rather than a cut one
        final String heading = font.width(Component.literal(full).withStyle(ChatFormatting.BOLD)) <= width - 2 ? full : level == 0 ? "Construire" : "Niveau " + target;
        y = levelHeading(graphics, x, y, width, heading, target);

        final UpgradeOffer offer = UpgradeOffer.of(building);
        if (offer.refusal() != null)
        {
            y = Ui.wrapped(graphics, font, Component.literal(offer.refusal()), x, y, width, Palette.RED, 9) + 3;
        }
        else if (level > 0)
        {
            Ui.label(graphics, font, "DÉBLOQUE", x, y);
            y += 11;
            if (Yields.isRawProducer(building.getBuildingType()))
            {
                graphics.drawString(font, fit("+ Récoltes ×" + format(Yields.outputMultiplier(target)), width), x, y, Palette.GREEN, false);
                y += 10;
            }
            graphics.drawString(font, fit("+ Vitesse ×" + format(Yields.speedMultiplier(target)), width), x, y, Palette.GREEN, false);
            y += 12;
        }
        else
        {
            final String desc = CatalogueIndex.buildingDesc(idOf(building));
            if (desc != null)
            {
                y = Ui.wrapped(graphics, font, Component.literal(desc), x, y, width, Palette.MUTED, 9) + 3;
            }
        }

        Ui.label(graphics, font, "PRIX", x, y);
        y += 11;
        y = Ui.priceLine(graphics, font, x, y, width, offer.price(), offer.affordable());
        for (final CrateRows.Row row : offer.crates())
        {
            y = Ui.crateLine(graphics, font, x, y, width, row);
        }

        sideButton(graphics, mouseX, mouseY, x, secondaryY, width, secondary, Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
        // Green when it can be done now, grey with the reason otherwise
        sideButton(graphics, mouseX, mouseY, x, buttonY, width, offer.action(font, width - 6), Ui.ButtonStyle.PRIMARY, offer.canBuy(), () ->
        {
            offer.buy(building);
            onClose();
        });
    }

    private void sideButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final String label,
      final Ui.ButtonStyle style, final boolean enabled, final Runnable action)
    {
        Ui.button(graphics, font, x, y, width, Ui.BUTTON_H, fit(label, width - 6), style, enabled, Ui.inside(mouseX, mouseY, x, y, width, Ui.BUTTON_H));
        if (enabled)
        {
            hits.add(new Hit(x, y, width, Ui.BUTTON_H, action));
        }
    }

    private void openBuildWindow()
    {
        new WindowBuildBuilding(building.getColony(), building).open();
    }

    private void renderSite(final GuiGraphics graphics, final int x, int y, final int width)
    {
        final int level = building.getBuildingLevel();
        final String heading;
        final String sub;
        final int seconds;
        if (building.isDeconstructing())
        {
            heading = "Démolition en cours";
            sub = null;
            seconds = BuildCosts.secondsForFlat();
        }
        else if (building.isRepairing())
        {
            heading = "Réparation en cours";
            sub = null;
            seconds = BuildCosts.secondsForFlat();
        }
        else
        {
            heading = level == 0 ? "Construction en cours" : "Amélioration en cours";
            sub = level == 0 ? "Niveau 1" : "Niveau " + level + " → " + (level + 1);
            seconds = BuildCosts.secondsForLevel(level + 1);
        }
        // Demolition is red like its site bar; repair keeps the current level's colour
        final int colourLevel = building.isRepairing() || building.isDeconstructing() ? level : level + 1;
        y = building.isDeconstructing() ? Ui.wrapped(graphics, font, Component.literal(heading).withStyle(ChatFormatting.BOLD), x, y, width, Palette.RED, 10)
                                        : levelHeading(graphics, x, y, width, heading, colourLevel);
        if (sub != null)
        {
            graphics.drawString(font, sub, x, y, Palette.MUTED, false);
            y += 10;
        }
        y += 4;
        Ui.label(graphics, font, "AVANCEMENT", x, y);
        y += 11;
        final Float progress = UpgradeOffer.siteProgress(building, title.getString());
        if (progress == null)
        {
            Ui.wrapped(graphics, font, Component.literal("Le chantier avance. Approchez-vous pour suivre sa barre."), x, y, width, Palette.MUTED, 9);
            return;
        }
        if (building.isDeconstructing())
        {
            Ui.progressBar(graphics, x, y, width, 6, progress, Ui.State.BAD);
        }
        else
        {
            Ui.progressBar(graphics, x, y, width, 6, progress, Palette.levelFill(colourLevel), Palette.levelEdge(colourLevel));
        }
        y += 10;
        final int left = Math.max(1, Math.round((1 - progress) * seconds));
        graphics.drawString(font, Math.round(progress * 100) + " % · encore " + BuildCosts.formatSeconds(left) + " environ", x, y, Palette.INK, false);
    }

    private static String format(final double value)
    {
        return String.format(Locale.FRANCE, value == Math.floor(value) ? "%.0f" : "%.2f", value).replaceAll("0$", "");
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        for (final Hit hit : List.copyOf(hits))
        {
            if (Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height()))
            {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        scroll -= (int) (scrollY * 12);
        return true;
    }
}
