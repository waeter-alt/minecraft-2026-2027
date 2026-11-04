package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.minecolonies.core.client.gui.WindowHutAllInventory;
import com.minecolonies.core.client.gui.WindowHutNameEntry;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.WindowInfo;
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
    private static final int NAV_W = 112;
    private static final int SIDE_W = 150;
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

    @Override
    public void tick()
    {
        // The colony view may swap the building view on an update: always show the newest one
        final IBuildingView fresh = building.getColony().getBuilding(building.getID());
        if (fresh != null)
        {
            building = fresh;
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
            graphics.renderTooltip(font, Component.literal(hint), mouseX, mouseY);
        }
    }

    // --- Header: level and rename, after the title the shell draws ---

    private void renderHeader(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        int x = x0 + 8 + font.width(title) + 8;
        final int level = building.getBuildingLevel();
        final int max = building.getBuildingMaxLevel();
        // « Niveau 2 » and one square per level in its own colour, filled up to the current one
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
        graphics.drawString(font, "✎", x, y0 + 7, over ? Palette.SELECTED : headerText(), false);
        hits.add(new Hit(x - 2, y0 + 4, 12, 12, () -> new WindowHutNameEntry(building).open()));
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
    private int levelHeading(final GuiGraphics graphics, final int x, final int y, final int width, final String text, final int level)
    {
        Ui.plate(graphics, x - 3, y - 2, x + width + 3, y + 12, Palette.levelFill(level), Palette.levelEdge(level), Palette.levelFill(level));
        final Component bold = Component.literal(fit(text, width - 2)).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, bold, x, y + 1, Palette.levelText(level), false);
        return y + 16;
    }

    // --- Left: the building's pages and its tools ---

    private List<IBuildingModuleView> pages()
    {
        final List<IBuildingModuleView> pages = new ArrayList<>();
        for (final IBuildingModuleView view : building.getAllModuleViews())
        {
            if (view.isPageVisible())
            {
                pages.add(view);
            }
        }
        return pages;
    }

    private void renderNav(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int x = x0 + 5;
        final int width = NAV_W - 8;
        verticalRule(graphics, x0 + NAV_W);
        Ui.label(graphics, font, "BÂTIMENT", x + 2, y0 + 25);
        int y = y0 + 36;
        Ui.navItem(graphics, font, x, y, width, Icon.module("main"), "Vue d'ensemble", true, false);
        y += Ui.NAV_ROW + 1;

        final List<Tool> tools = tools();
        final int toolsTop = y0 + h - 6 - tools.size() * (Ui.NAV_ROW + 1);
        for (final IBuildingModuleView view : pages())
        {
            if (y + Ui.NAV_ROW > toolsTop - 4)
            {
                break;
            }
            final String label = fit(Component.translatable(view.getDesc().toLowerCase(Locale.US)).getString(), width - 22);
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW);
            Ui.navItem(graphics, font, x, y, width, Icon.texture(view.getIconResourceLocation(), 20), label, false, over);
            hits.add(new Hit(x, y, width, Ui.NAV_ROW, () -> openModule(view)));
            y += Ui.NAV_ROW + 1;
        }

        graphics.fill(x + 2, toolsTop - 3, x + width - 2, toolsTop - 2, Palette.RULE);
        y = toolsTop;
        for (final Tool tool : tools)
        {
            Ui.navItem(graphics, font, x, y, width, tool.icon(), tool.label(), false, Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW));
            hits.add(new Hit(x, y, width, Ui.NAV_ROW, tool.action()));
            y += Ui.NAV_ROW + 1;
        }
    }

    private record Tool(String label, Icon icon, Runnable action)
    {
    }

    /**
     * Chest, whole stock, help when MineColonies has a text for this building, and the colony hub when this window was
     * not opened from it.
     */
    private List<Tool> tools()
    {
        final List<Tool> tools = new ArrayList<>();
        tools.add(new Tool("Coffre", Icon.of(Items.CHEST), () -> new OpenInventoryMessage(building).sendToServer()));
        // Its back button reopens the hut window, which opens this screen again
        tools.add(new Tool("Tout le stock", Icon.module("inventory"), () -> new WindowHutAllInventory(building, building.getWindow()).open()));
        final String key = TranslationConstants.PARTIAL_INFO_TEXT + building.getBuildingType().getTranslationKey().replace("com.minecolonies.building.", "") + ".0";
        if (I18n.exists(key))
        {
            tools.add(new Tool("Aide", Icon.of(Items.BOOK), () -> new WindowInfo(building).open()));
        }
        final ITownHallView townHall = building.getColony().getTownHall();
        if (!(parent instanceof ColonyHubScreen) && townHall != null && townHall != building)
        {
            tools.add(new Tool("Colonie", Icon.of(townHall.getBuildingType().getBuildingBlock()), () -> ColonyHubScreen.open(townHall)));
        }
        return tools;
    }

    @Override
    protected String crumb()
    {
        return parent instanceof ColonyHubScreen ? "Colonie" : null;
    }

    private void openModule(final IBuildingModuleView view)
    {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        view.getWindow().open();
    }

    // --- Middle: state, workers, production, requests ---

    private int mainLeft()
    {
        return x0 + NAV_W + 7;
    }

    private int mainWidth()
    {
        return w - NAV_W - SIDE_W - 14;
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

        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;
        final List<Worker> workers = workers();
        final List<IRequest<?>> requests = requests(workers);

        final Banner banner = banner(workers, requests);
        Ui.banner(graphics, font, x, y, width, banner.state(), banner.title(), banner.text());
        if (banner.action() != null)
        {
            final int aw = font.width(banner.action()) + 6;
            final int ax = x + width - aw - 2;
            final boolean over = Ui.inside(mouseX, my, ax, y + 1, aw, 12);
            Ui.solidButton(graphics, font, ax, y + 1, aw, 12, banner.action(), banner.state(), over);
            addHit(ax, y + 1, aw, 12, banner.onAction());
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
            y = renderProduction(graphics, x, y, width);
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
     * A click target inside the scrolling middle column: only its visible part counts.
     */
    private void addHit(final int x, final int y, final int width, final int height, @Nullable final Runnable action)
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
        graphics.drawString(font, workers.size() + " / " + max, x + font.width("OUVRIERS") + 6, y, Palette.INK, false);

        final int recallW = font.width("Rappeler") + 10;
        final int manageW = font.width("Gérer") + 10;
        final int recallX = x + width - recallW;
        final int manageX = recallX - manageW - 3;
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
            graphics.drawString(font, canHire ? "Personne ne travaille ici." : "Construisez le bâtiment pour y embaucher.", x + 2, y, Palette.MUTED, false);
            return y + 14;
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
        final Component name = Component.literal(citizen.getName()).withStyle(ChatFormatting.BOLD);
        final List<CitizenBits.Chip> chips = CitizenBits.chips(citizen);
        int chipsW = 0;
        for (final CitizenBits.Chip chip : chips)
        {
            chipsW += font.width(chip.text()) + 11;
        }
        // Chips beside the name when they fit, on their own line below otherwise
        final boolean inline = font.width(name) + chipsW + 30 <= width;
        final int cardH = inline || chips.isEmpty() ? WORKER_H - 2 : WORKER_H + 11;
        final boolean overCard = Ui.inside(mouseX, mouseY, x, y, width, cardH);
        Ui.card(graphics, x, y, width, cardH, false, overCard, false);
        CitizenBits.face(graphics, citizen, x + 4, y + 3, 16);
        graphics.drawString(font, name, x + 24, y + 3, Palette.INK, false);
        graphics.drawString(font, fit(line, width - 28), x + 24, y + 12, Palette.MUTED, false);

        int chipX = inline ? x + width - 4 - chipsW + 3 : x + 24;
        final int chipY = inline ? y + 2 : y + 22;
        for (final CitizenBits.Chip chip : chips)
        {
            final int chipW = font.width(chip.text()) + 8;
            final boolean over = chip.action() != null && Ui.inside(mouseX, mouseY, chipX, chipY, chipW, 11);
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
        graphics.drawString(font, home.getResidents().size() + " / " + home.getMax(), x + font.width("HABITANTS") + 6, y, Palette.INK, false);
        final int recallW = font.width("Rappeler") + 10;
        final int manageW = font.width("Gérer") + 10;
        final int recallX = x + width - recallW;
        final int manageX = recallX - manageW - 3;
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

    private int renderProduction(final GuiGraphics graphics, final int x, int y, final int width)
    {
        final String id = idOf(building);
        final List<List<ItemStack>> flow = CatalogueIndex.flow(id);
        final Component rate = CatalogueIndex.production(id);
        final int level = building.getBuildingLevel();
        y = Ui.separator(graphics, x, y - 4, width);
        Ui.label(graphics, font, "PRODUCTION", x, y);
        if (level > 0)
        {
            graphics.drawString(font, "au niveau " + level, x + font.width("PRODUCTION") + 6, y, Palette.MUTED, false);
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
            graphics.drawString(font, "Rien tant que le bâtiment n'est pas construit.", x, y, Palette.MUTED, false);
            return y + 12;
        }
        if (Yields.isRawProducer(building.getBuildingType()))
        {
            graphics.drawString(font, "Récoltes ×" + format(Yields.outputMultiplier(level)), x, y, Palette.INK, false);
            y += 10;
        }
        graphics.drawString(font, "Vitesse de travail ×" + format(Yields.speedMultiplier(level)), x, y, Palette.INK, false);
        return y + 12;
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
            graphics.drawString(font, "Ce bâtiment ne manque de rien.", x + 2, y, Palette.MUTED, false);
            return y + 12;
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
        for (final Worker worker : workers)
        {
            if (worker.citizen().isSick())
            {
                return new Banner(Ui.State.BAD, "Bloqué", worker.citizen().getName() + " est malade", null, null);
            }
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
        final int left = detailPanel(graphics, SIDE_W);
        final int x = left + 6;
        final int width = SIDE_W - 14;
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
        final String heading = level == 0 ? "Construire le bâtiment" : "Passer au niveau " + target;
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
                graphics.drawString(font, "+ Récoltes ×" + format(Yields.outputMultiplier(target)), x, y, Palette.GREEN, false);
                y += 10;
            }
            graphics.drawString(font, "+ Vitesse ×" + format(Yields.speedMultiplier(target)), x, y, Palette.GREEN, false);
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
        y = Ui.priceLine(graphics, font, x, y, offer.price(), offer.affordable());
        for (final CrateRows.Row row : offer.crates())
        {
            y = Ui.crateLine(graphics, font, x, y, row);
        }

        sideButton(graphics, mouseX, mouseY, x, secondaryY, width, secondary, Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
        // Green when it can be done now, grey with the reason otherwise
        sideButton(graphics, mouseX, mouseY, x, buttonY, width, offer.action(), Ui.ButtonStyle.PRIMARY, offer.canBuy(), () ->
        {
            offer.buy(building);
            onClose();
        });
    }

    private void sideButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String label,
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
