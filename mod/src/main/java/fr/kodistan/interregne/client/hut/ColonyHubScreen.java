package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.core.client.gui.map.WindowColonyMap;
import com.minecolonies.core.client.gui.townhall.WindowAlliancePage;
import com.minecolonies.core.client.gui.townhall.WindowMainPage;
import com.minecolonies.core.client.gui.townhall.WindowPermissionsPage;
import com.minecolonies.core.client.gui.townhall.WindowSettings;
import com.minecolonies.core.client.gui.townhall.WindowStatsPage;
import com.minecolonies.core.client.gui.townhall.WindowTownHallNameEntry;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import fr.kodistan.interregne.client.catalogue.CatalogueIndex;
import fr.kodistan.interregne.client.hud.Grievances;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.colony.ColonyMaps;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The colony hub, opened from the town hall: how the colony is doing, every building, every citizen, every open
 * request, each one click away from its own window. MineColonies' rarer town hall pages stay reachable as tools.
 */
public final class ColonyHubScreen extends ParchmentScreen
{
    private static final int NAV_W = 112;

    /**
     * The seat's header: a leather band holding the colony's banner, its name and the town hall's level.
     */
    private static final int HDR = 38;

    /**
     * Set just before opening MineColonies' own town hall page on purpose (« Apparence »), so it is not replaced.
     */
    private static boolean openOriginal;

    private enum Page
    {
        OVERVIEW("Vue d'ensemble", Icon.module("main")),
        BUILDINGS("Bâtiments", Icon.of(Items.BRICKS)),
        CITIZENS("Citoyens", Icon.module("workers")),
        REQUESTS("Demandes", Icon.module("requests"));

        private final String label;
        private final Icon icon;

        Page(final String label, final Icon icon)
        {
            this.label = label;
            this.icon = icon;
        }
    }

    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    /**
     * A building's state in the list.
     */
    private record BuildingState(Ui.State state, String text)
    {
    }

    private record OpenRequest(IRequest<?> request, IBuildingView building)
    {
    }

    private ITownHallView townHall;
    private final IColonyView colony;
    private Page page = Page.OVERVIEW;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    private String hint;
    @Nullable
    private String notice;
    private long noticeUntil;
    private int scroll;
    private int contentHeight;

    private ColonyHubScreen(final ITownHallView townHall)
    {
        super(Component.literal(townHall.getColony().getName()), null);
        this.townHall = townHall;
        this.colony = townHall.getColony();
    }

    public static void open(final IBuildingView townHall)
    {
        if (townHall instanceof ITownHallView view)
        {
            Minecraft.getInstance().setScreen(new ColonyHubScreen(view));
        }
    }

    /**
     * True once when MineColonies' own town hall page was asked for on purpose: it then opens as usual.
     */
    public static boolean consumeOpenOriginal()
    {
        final boolean original = openOriginal;
        openOriginal = false;
        return original;
    }

    @Override
    public void tick()
    {
        final ITownHallView fresh = colony.getTownHall();
        if (fresh != null)
        {
            townHall = fresh;
        }
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        hits.clear();
        slots.clear();
        hint = null;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The same parchment as every hut, but the seat of the colony gets a leather header with gold rules: the colony's
     * banner, its name, and the town hall's level in its colour.
     */
    @Override
    protected void renderFrame(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        Ui.plate(graphics, x0, y0, x0 + w, y0 + h, Palette.PAPER, Palette.EDGE, Palette.PAPER_LIGHT);
        Ui.plate(graphics, x0 + 2, y0 + 2, x0 + w - 2, y0 + HDR, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
        graphics.fill(x0 + 2, y0 + HDR, x0 + w - 2, y0 + HDR + 1, Palette.SELECTED);
        graphics.fill(x0 + 2, y0 + HDR + 2, x0 + w - 2, y0 + HDR + 3, Palette.RULE);

        final ItemStack banner = new ItemStack(Items.WHITE_BANNER);
        if (colony.getColonyFlag() != null)
        {
            banner.set(DataComponents.BANNER_PATTERNS, colony.getColonyFlag());
        }
        Icon.of(banner).draw(graphics, x0 + 8, y0 + 3, 32);

        final Component name = Component.literal(colony.getName()).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, name, x0 + 44, y0 + 10, Palette.GOLD_TEXT, true);
        final int penX = x0 + 44 + font.width(name) + 5;
        final boolean overPen = Ui.inside(mouseX, mouseY, penX - 2, y0 + 8, 12, 12);
        graphics.drawString(font, "✎", penX, y0 + 10, overPen ? Palette.WHITE : 0xFFC9A877, false);
        hits.add(new Hit(penX - 2, y0 + 8, 12, 12, () -> new WindowTownHallNameEntry(colony).open()));
        if (overPen)
        {
            hint = "Renommer la colonie";
        }
        graphics.drawString(font, "Hôtel de ville · siège de la colonie", x0 + 44, y0 + 22, 0xFFC9A877, false);

        // The town hall's level, a medallion in its colour; a click opens the town hall to upgrade it
        final int level = townHall.getBuildingLevel();
        final String label = "Niveau " + level;
        final int medalW = font.width(label) + 14;
        final int medalX = x0 + w - 22 - medalW;
        final int medalY = y0 + 11;
        final boolean overMedal = Ui.inside(mouseX, mouseY, medalX, medalY, medalW, 16);
        graphics.fill(medalX - 1, medalY - 1, medalX + medalW + 1, medalY + 17, overMedal ? Palette.WHITE : Palette.SELECTED);
        Ui.plate(graphics, medalX, medalY, medalX + medalW, medalY + 16, Palette.levelFill(level), Palette.levelEdge(level), Palette.levelFill(level));
        graphics.drawString(font, Component.literal(label).withStyle(ChatFormatting.BOLD), medalX + 7, medalY + 4, Palette.levelText(level), false);
        hits.add(new Hit(medalX, medalY, medalW, 16, () -> HutScreen.openWith(townHall, this)));
        if (overMedal)
        {
            hint = "Ouvrir l'hôtel de ville pour l'améliorer";
        }

        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, overClose(mouseX, mouseY) ? Palette.RED_SOFT : Palette.GOLD_TEXT, false);
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        renderNav(graphics, mouseX, mouseY);

        final int x = x0 + NAV_W + 7;
        final int width = w - NAV_W - 16;
        final int top = y0 + HDR + 7;
        final int bottom = y0 + h - 5;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;
        if (notice != null && Util.getMillis() < noticeUntil)
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "Pas encore", notice);
            y += 20;
        }
        y = switch (page)
        {
            case OVERVIEW -> renderOverview(graphics, mouseX, my, x, y, width);
            case BUILDINGS -> renderBuildings(graphics, mouseX, my, x, y, width);
            case CITIZENS -> renderCitizens(graphics, mouseX, my, x, y, width);
            case REQUESTS -> renderRequests(graphics, mouseX, my, x, y, width);
        };
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

        for (final Ui.Slot slot : slots)
        {
            if (Ui.inside(mouseX, mouseY, slot.x(), slot.y(), slot.size(), slot.size()))
            {
                graphics.renderTooltip(font, slot.stack(), mouseX, mouseY);
            }
        }
        if (hint != null)
        {
            graphics.renderTooltip(font, font.split(Component.literal(hint), 200), mouseX, mouseY);
        }
    }

    private void addHit(final int x, final int y, final int width, final int height, final Runnable action)
    {
        final int top = Math.max(y, y0 + HDR + 7);
        final int bottom = Math.min(y + height, y0 + h - 5);
        if (bottom > top)
        {
            hits.add(new Hit(x, top, width, bottom - top, action));
        }
    }

    // --- Navigation ---

    private record Tool(String label, Icon icon, boolean locked, Runnable action)
    {
    }

    private List<Tool> tools()
    {
        return List.of(
          new Tool("Carte", Icon.of(Items.FILLED_MAP), townHall.getBuildingLevel() < ColonyMaps.UNLOCK_LEVEL, this::openMap),
          new Tool("Statistiques", Icon.module("stats"), false, () -> new WindowStatsPage((BuildingTownHall.View) townHall).open()),
          new Tool("Réglages", Icon.module("settings"), false, () -> new WindowSettings((BuildingTownHall.View) townHall).open()),
          new Tool("Permissions", Icon.module("scepter"), false, () -> new WindowPermissionsPage((BuildingTownHall.View) townHall).open()),
          new Tool("Alliances", Icon.of(Items.WHITE_BANNER), false, () -> new WindowAlliancePage((BuildingTownHall.View) townHall).open()),
          new Tool("Apparence", Icon.module("domum"), false, () ->
          {
              openOriginal = true;
              new WindowMainPage((BuildingTownHall.View) townHall).open();
          }));
    }

    /**
     * The colony map, drawn by the server from the terrain: no filled map to put in the chest any more.
     */
    private void openMap()
    {
        if (townHall.getBuildingLevel() < ColonyMaps.UNLOCK_LEVEL)
        {
            notice("la carte se débloque au niveau " + ColonyMaps.UNLOCK_LEVEL + " de l'hôtel de ville. Améliorez-le pour la voir.");
            return;
        }
        if (townHall.getMapDataList().isEmpty())
        {
            notice("la carte se dessine encore, revenez dans un instant.");
            return;
        }
        final WindowColonyMap map = new WindowColonyMap(true, townHall);
        map.findPaneByID("inventory").hide();
        map.findPaneByID("warning").hide();
        map.open();
    }

    private void notice(final String text)
    {
        notice = text;
        noticeUntil = Util.getMillis() + 6000;
        scroll = 0;
    }

    private void renderNav(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int x = x0 + 5;
        final int width = NAV_W - 8;
        graphics.fill(x0 + NAV_W, y0 + HDR + 5, x0 + NAV_W + 1, y0 + h - 4, Palette.RULE);
        int y = y0 + HDR + 7;
        for (final Page p : Page.values())
        {
            Ui.navItem(graphics, font, x, y, width, p.icon, p.label, p == page, Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW));
            hits.add(new Hit(x, y, width, Ui.NAV_ROW, () -> show(p)));
            y += Ui.NAV_ROW + 1;
        }

        final List<Tool> tools = tools();
        y = y0 + h - 6 - tools.size() * (Ui.NAV_ROW + 1);
        graphics.fill(x + 2, y - 3, x + width - 2, y - 2, Palette.RULE);
        for (final Tool tool : tools)
        {
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW);
            Ui.navItem(graphics, font, x, y, width, tool.icon(), tool.label(), false, over);
            if (tool.locked())
            {
                final String lock = "niv. " + ColonyMaps.UNLOCK_LEVEL;
                graphics.drawString(font, lock, x + width - 3 - font.width(lock), y + 5, Palette.MUTED, false);
                if (over)
                {
                    hint = "La carte se débloque au niveau " + ColonyMaps.UNLOCK_LEVEL + " de l'hôtel de ville";
                }
            }
            hits.add(new Hit(x, y, width, Ui.NAV_ROW, tool.action()));
            y += Ui.NAV_ROW + 1;
        }
    }

    private void show(final Page target)
    {
        page = target;
        scroll = 0;
    }

    // --- Overview ---

    private int renderOverview(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IBuildingView> buildings = colony.getBuildings();
        final List<ICitizenDataView> citizens = new ArrayList<>(colony.getCitizens().values());
        int blocked = 0;
        int sites = 0;
        for (final IBuildingView building : buildings)
        {
            final BuildingState state = stateOf(building);
            if (state.state() == Ui.State.BAD)
            {
                blocked++;
            }
            if (building.isBuilding() || building.isRepairing() || building.isDeconstructing())
            {
                sites++;
            }
        }
        int grievances = 0;
        int troubled = 0;
        for (final ICitizenDataView citizen : citizens)
        {
            final int count = Grievances.count(citizen);
            grievances += count;
            if (count > 0)
            {
                troubled++;
            }
        }
        final int requests = requests().size();

        if (blocked > 0)
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.BAD, "À régler", blocked + (blocked == 1 ? " bâtiment bloqué" : " bâtiments bloqués"));
            addHit(x, y, width, 14, () -> show(Page.BUILDINGS));
        }
        else if (troubled > 0)
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "À surveiller", troubled + (troubled == 1 ? " citoyen a" : " citoyens ont") + " des doléances");
            addHit(x, y, width, 14, () -> show(Page.CITIZENS));
        }
        else
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.OK, "Tout va bien", "aucun bâtiment bloqué, aucune doléance");
        }
        y += 20;

        // Tiles: the colony's vital signs, each opening the page that explains it
        final int tileW = (width - 3 * 4) / 4;
        final double happiness = colony.getOverallHappiness();
        y = tileRow(graphics, mouseX, mouseY, x, y, tileW, List.of(
          new Tile("Citoyens", citizens.size() + " / " + colony.getCitizenCountLimit(), Icon.module("workers"), Ui.State.OK, () -> show(Page.CITIZENS)),
          new Tile("Bonheur", format(happiness) + " / 10", Icon.module("happiness"), happiness < 4 ? Ui.State.BAD : happiness < 7 ? Ui.State.WAIT : Ui.State.OK,
            () -> show(Page.CITIZENS)),
          new Tile("Doléances", Integer.toString(grievances), Icon.of(Items.WRITABLE_BOOK), grievances > 0 ? Ui.State.WAIT : Ui.State.OK, () -> show(Page.CITIZENS)),
          new Tile("Demandes", Integer.toString(requests), Icon.module("requests"), requests > 0 ? Ui.State.WAIT : Ui.State.OK, () -> show(Page.REQUESTS))));
        y = tileRow(graphics, mouseX, mouseY, x, y + 4, tileW, List.of(
          new Tile("Bâtiments", Integer.toString(buildings.size()), Icon.of(Items.BRICKS), blocked > 0 ? Ui.State.BAD : Ui.State.OK, () -> show(Page.BUILDINGS)),
          new Tile("Chantiers", Integer.toString(sites), Icon.of(Items.IRON_PICKAXE), Ui.State.OK, () -> show(Page.BUILDINGS))));

        y = Ui.separator(graphics, x, y, width);
        final int level = townHall.getBuildingLevel();
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 24);
        Ui.card(graphics, x, y, width, 24, false, over, false);
        Icon.of(townHall.getBuildingType().getBuildingBlock()).draw(graphics, x + 4, y + 4, 16);
        graphics.drawString(font, Component.literal("Hôtel de ville").withStyle(ChatFormatting.BOLD), x + 24, y + 4, Palette.INK, false);
        graphics.drawString(font, level < ColonyMaps.UNLOCK_LEVEL ? "Le niveau 2 débloque la carte de la colonie" : "Améliorer, réparer, voir le stock",
          x + 24, y + 13, Palette.MUTED, false);
        final int badgeW = Ui.levelBadge(graphics, font, x + width - 70, y + 6, "Niveau " + level, level);
        graphics.drawString(font, "›", x + width - 70 + badgeW + 6, y + 8, Palette.MUTED, false);
        addHit(x, y, width, 24, () -> HutScreen.openWith(townHall, this));
        return y + 28;
    }

    private record Tile(String label, String value, Icon icon, Ui.State state, Runnable action)
    {
    }

    private int tileRow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int tileW, final List<Tile> tiles)
    {
        for (int i = 0; i < tiles.size(); i++)
        {
            final Tile tile = tiles.get(i);
            final int tx = x + i * (tileW + 4);
            final boolean over = Ui.inside(mouseX, mouseY, tx, y, tileW, 34);
            Ui.card(graphics, tx, y, tileW, 34, false, over, false);
            graphics.fill(tx + 1, y + 1, tx + 3, y + 33, switch (tile.state())
            {
                case OK -> Palette.GREEN;
                case WAIT -> Palette.AMBER;
                case BAD -> Palette.RED;
            });
            tile.icon().draw(graphics, tx + 6, y + 4, 12);
            graphics.drawString(font, fit(tile.label(), tileW - 24), tx + 21, y + 6, Palette.MUTED, false);
            graphics.drawString(font, Component.literal(tile.value()).withStyle(ChatFormatting.BOLD), tx + 7, y + 21, Palette.INK, false);
            addHit(tx, y, tileW, 34, tile.action());
        }
        return y + 34;
    }

    // --- Buildings ---

    private static String nameOf(final IBuildingView building)
    {
        final String custom = building.getCustomName();
        if (custom != null && !custom.isEmpty())
        {
            return custom;
        }
        return CatalogueIndex.buildingName(building.getBuildingType().getRegistryName().getPath(),
          Component.translatable(building.getBuildingDisplayName()).getString());
    }

    private BuildingState stateOf(final IBuildingView building)
    {
        if (building.isBuilding() || building.isRepairing() || building.isDeconstructing())
        {
            return new BuildingState(Ui.State.WAIT, "Chantier");
        }
        if (building.getBuildingLevel() == 0)
        {
            return new BuildingState(Ui.State.BAD, "Pas construit");
        }
        final List<WorkerBuildingModuleView> jobs = building.getModuleViews(WorkerBuildingModuleView.class);
        if (!jobs.isEmpty() && jobs.stream().allMatch(m -> m.getAssignedCitizens().isEmpty()))
        {
            return new BuildingState(Ui.State.BAD, "Sans ouvrier");
        }
        final int requests = requestsOf(building).size();
        if (requests > 0)
        {
            return new BuildingState(Ui.State.WAIT, requests == 1 ? "1 demande" : requests + " demandes");
        }
        return new BuildingState(Ui.State.OK, "En marche");
    }

    private int renderBuildings(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IBuildingView> buildings = new ArrayList<>(colony.getBuildings());
        final Map<IBuildingView, BuildingState> states = new LinkedHashMap<>();
        buildings.forEach(b -> states.put(b, stateOf(b)));
        // Problems first, then by name
        buildings.sort(Comparator.<IBuildingView>comparingInt(b -> -states.get(b).state().ordinal()).thenComparing(ColonyHubScreen::nameOf));
        Ui.label(graphics, font, buildings.size() + " BÂTIMENTS", x, y);
        y += 12;
        for (final IBuildingView building : buildings)
        {
            final BuildingState state = states.get(building);
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 20);
            Ui.card(graphics, x, y, width, 20, false, over, false);
            Icon.of(building.getBuildingType().getBuildingBlock()).draw(graphics, x + 3, y + 2, 16);
            final int level = building.getBuildingLevel();
            final int badgeX = x + width - 150;
            graphics.drawString(font, fit(nameOf(building), badgeX - x - 28), x + 23, y + 6, Palette.INK, false);
            levelSquares(graphics, badgeX, y + 6, level, building.getBuildingMaxLevel());
            final int chipW = font.width(state.text()) + 8;
            Ui.chip(graphics, font, x + width - chipW - 14, y + 5, state.text(), state.state());
            graphics.drawString(font, "›", x + width - 9, y + 6, Palette.MUTED, false);
            addHit(x, y, width, 20, () -> HutScreen.openWith(building, this));
            y += 22;
        }
        return y;
    }

    /**
     * One square per level in its colour, filled up to the building's level.
     */
    private void levelSquares(final GuiGraphics graphics, int x, final int y, final int level, final int max)
    {
        for (int l = 1; l <= max; l++)
        {
            graphics.fill(x, y, x + 6, y + 7, Palette.levelEdge(l));
            graphics.fill(x + 1, y + 1, x + 5, y + 6, l <= level ? Palette.levelFill(l) : Palette.PAPER_LIGHT);
            x += 8;
        }
    }

    // --- Citizens ---

    private int renderCitizens(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<ICitizenDataView> citizens = new ArrayList<>(colony.getCitizens().values());
        final Map<ICitizenDataView, List<CitizenBits.Chip>> chips = new LinkedHashMap<>();
        citizens.forEach(c -> chips.put(c, CitizenBits.chips(c)));
        // Those with something to settle first
        citizens.sort(Comparator.<ICitizenDataView>comparingInt(c -> -(int) chips.get(c).stream().filter(ch -> ch.action() != null).count())
          .thenComparing(ICitizenDataView::getName));
        Ui.label(graphics, font, citizens.size() + " / " + colony.getCitizenCountLimit() + " CITOYENS", x, y);
        y += 12;
        for (final ICitizenDataView citizen : citizens)
        {
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 22);
            Ui.card(graphics, x, y, width, 22, false, over, false);
            CitizenBits.face(graphics, citizen, x + 3, y + 3, 16);
            final String job = citizen.getJob() == null || citizen.getJob().isEmpty() ? (citizen.isChild() ? "Enfant" : "Sans emploi")
                                 : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());
            graphics.drawString(font, Component.literal(citizen.getName()).withStyle(ChatFormatting.BOLD), x + 23, y + 3, Palette.INK, false);
            graphics.drawString(font, fit(job, width / 2 - 26), x + 23, y + 12, Palette.MUTED, false);

            // Chips right-aligned; a click on one settles the problem, elsewhere opens the sheet
            int chipX = x + width - 12;
            final List<CitizenBits.Chip> list = chips.get(citizen);
            for (int i = list.size() - 1; i >= 0; i--)
            {
                final CitizenBits.Chip chip = list.get(i);
                final int chipW = font.width(chip.text()) + 8;
                chipX -= chipW;
                final boolean overChip = chip.action() != null && Ui.inside(mouseX, mouseY, chipX, y + 6, chipW, 11);
                Ui.chip(graphics, font, chipX, y + 6, chip.text(), chip.soft(), chip.ink(), chip.action() != null, overChip);
                if (chip.action() != null)
                {
                    addHit(chipX, y + 6, chipW, 11, chip.action());
                }
                chipX -= 3;
            }
            graphics.drawString(font, "›", x + width - 9, y + 7, Palette.MUTED, false);
            addHit(x, y, width, 22, () -> CitizenScreen.open(citizen, this));
            y += 24;
        }
        return y;
    }

    // --- Requests ---

    private List<IRequest<?>> requestsOf(final IBuildingView building)
    {
        final Map<Object, IRequest<?>> found = new LinkedHashMap<>();
        try
        {
            building.getOpenRequestsOfBuilding().forEach(r -> found.put(r.getId(), r));
            for (final WorkerBuildingModuleView module : building.getModuleViews(WorkerBuildingModuleView.class))
            {
                for (final int id : module.getAssignedCitizens())
                {
                    final ICitizenDataView citizen = colony.getCitizen(id);
                    if (citizen != null)
                    {
                        building.getOpenRequests(citizen).forEach(r -> found.put(r.getId(), r));
                    }
                }
            }
        }
        catch (final RuntimeException e)
        {
            // The colony's request system is not synced yet: nothing to show
        }
        return new ArrayList<>(found.values());
    }

    private List<OpenRequest> requests()
    {
        final List<OpenRequest> all = new ArrayList<>();
        for (final IBuildingView building : colony.getBuildings())
        {
            requestsOf(building).forEach(r -> all.add(new OpenRequest(r, building)));
        }
        return all;
    }

    private int renderRequests(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<OpenRequest> requests = requests();
        if (requests.isEmpty())
        {
            graphics.drawString(font, "Personne n'attend rien : la colonie ne manque de rien.", x, y, Palette.MUTED, false);
            return y + 12;
        }
        Ui.label(graphics, font, requests.size() + " DEMANDES OUVERTES", x, y);
        y += 12;
        for (final OpenRequest open : requests)
        {
            final IRequest<?> request = open.request();
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 22);
            Ui.card(graphics, x, y, width, 22, false, over, false);
            final List<ItemStack> stacks = request.getDisplayStacks();
            final ItemStack stack = stacks.isEmpty() ? new ItemStack(Items.PAPER) : stacks.get(0);
            Icon.of(stack).draw(graphics, x + 3, y + 3, 16);
            slots.add(new Ui.Slot(x + 3, y + 3, 16, stack));
            final String state = CitizenBits.requestStateText(request);
            final int chipW = font.width(state) + 8;
            Ui.chip(graphics, font, x + width - chipW - 12, y + 6, state, CitizenBits.requestState(request));
            final int textW = width - chipW - 40;
            graphics.drawString(font, fit(request.getShortDisplayString().getString(), textW), x + 23, y + 3, Palette.INK, false);
            graphics.drawString(font, fit("pour " + nameOf(open.building()), textW), x + 23, y + 12, Palette.MUTED, false);
            graphics.drawString(font, "›", x + width - 9, y + 7, Palette.MUTED, false);
            addHit(x, y, width, 22, () -> HutScreen.openWith(open.building(), this));
            y += 24;
        }
        return y;
    }

    // --- Shared ---

    private static String format(final double value)
    {
        return String.format(Locale.FRANCE, "%.1f", value).replace(",0", "");
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
