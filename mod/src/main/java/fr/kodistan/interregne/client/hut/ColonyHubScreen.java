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
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * The colony hub, opened from the town hall: the colony's register, an open book with a bookmark per chapter. The
 * overview faces the colony's figures; buildings, citizens and requests run over both pages, problems first, each
 * row one click away from its own window. MineColonies' rarer town hall pages stay reachable from the overview.
 */
public final class ColonyHubScreen extends ParchmentScreen
{
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/townhall_book.png");
    private static final int BOOK_W = 374;
    private static final int BOOK_H = 243;

    /**
     * Inside the book's ink frame: two pages either side of the spine.
     */
    private static final int LEFT = 28;
    private static final int RIGHT = 196;
    private static final int PAGE_W = 150;
    private static final int TOP = 28;
    private static final int BOTTOM = 214;
    private static final int ROW_H = 22;
    private static final int ROWS_PER_PAGE = (BOTTOM - 12 - (TOP + 16)) / ROW_H;

    /**
     * The register's inks, on the book's cream pages.
     */
    private static final int INK = 0xFF2E2118;
    private static final int MUTED = 0xFF8A7A66;
    private static final int ACCENT = 0xFF8C2A2A;
    private static final int RULE = 0xFFD9CBB0;
    private static final int HOVER = 0x1C8C2A2A;
    private static final int RIBBON = 0xFF7A1F24;
    private static final int RIBBON_LIGHT = 0xFF9E3A3A;
    private static final int RIBBON_TEXT = 0xFFF6E7C8;

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

    /**
     * A row of a list running over both pages: drawn at its place, then clickable as a whole.
     */
    private interface RowPainter
    {
        void paint(GuiGraphics graphics, int x, int y, int mouseX, int mouseY);
    }

    private record Row(RowPainter painter, Runnable action)
    {
    }

    private ITownHallView townHall;
    private final IColonyView colony;
    private Page page = Page.OVERVIEW;
    private int spread;
    private int spreads = 1;
    @Nullable
    private Page hoveredTab;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    private String hint;
    @Nullable
    private String notice;
    private long noticeUntil;

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
    protected void init()
    {
        super.init();
        // The book at its own size, pixel for pixel, nudged right so the bookmarks fit on its left
        w = BOOK_W;
        h = BOOK_H;
        x0 = (width - w) / 2 + 20;
        y0 = (height - h) / 2;
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

    // --- Frame: bookmarks and the book ---

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        hits.clear();
        slots.clear();
        hint = null;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderFrame(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        // Bookmarks first, so they come out from under the cover; the open one and the hovered one show their name
        Page hovered = null;
        int y = y0 + 40;
        for (final Page p : Page.values())
        {
            final boolean on = p == page;
            // A hovered bookmark slides out, and stays out while the mouse is on its longer self
            final boolean over = Ui.inside(mouseX, mouseY, x0 - (p == hoveredTab ? 92 : 20), y, p == hoveredTab ? 92 : 20, 14);
            if (over)
            {
                hovered = p;
            }
            final int out = on || over ? 92 : 20;
            final int left = x0 - out;
            graphics.fill(left + 3, y, x0 + 4, y + 14, over && !on ? RIBBON_LIGHT : RIBBON);
            graphics.fill(left + 3, y, x0 + 4, y + 1, RIBBON_LIGHT);
            graphics.fill(left + 3, y + 13, x0 + 4, y + 14, 0xFF4E1215);
            // Swallowtail end
            graphics.fill(left, y, left + 3, y + 5, RIBBON);
            graphics.fill(left, y + 9, left + 3, y + 14, RIBBON);
            if (out > 20)
            {
                p.icon.draw(graphics, left + 6, y + 1, 12);
                graphics.drawString(font, p.label, left + 21, y + 3, RIBBON_TEXT, false);
            }
            else
            {
                p.icon.draw(graphics, left + 5, y + 1, 12);
            }
            hits.add(new Hit(left, y, out, 14, () -> show(p)));
            y += 22;
        }
        hoveredTab = hovered;

        graphics.blit(BOOK, x0, y0, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, overClose(mouseX, mouseY) ? Palette.RED : MUTED, false);
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        switch (page)
        {
            case OVERVIEW -> renderOverview(graphics, mouseX, mouseY);
            case BUILDINGS -> renderBuildings(graphics, mouseX, mouseY);
            case CITIZENS -> renderCitizens(graphics, mouseX, mouseY);
            case REQUESTS -> renderRequests(graphics, mouseX, mouseY);
        }

        if (notice != null && Util.getMillis() < noticeUntil)
        {
            final List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(notice), 220);
            final int boxW = 232;
            final int boxH = lines.size() * 10 + 10;
            final int bx = x0 + (w - boxW) / 2;
            final int by = y0 + h - boxH - 26;
            Ui.plate(graphics, bx, by, bx + boxW, by + boxH, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
            int ly = by + 6;
            for (final net.minecraft.util.FormattedCharSequence line : lines)
            {
                graphics.drawString(font, line, bx + (boxW - font.width(line)) / 2, ly, Palette.GOLD_TEXT, false);
                ly += 10;
            }
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

    private void show(final Page target)
    {
        page = target;
        spread = 0;
        spreads = 1;
    }

    private void notice(final String text)
    {
        notice = text;
        noticeUntil = Util.getMillis() + 6000;
    }

    // --- Register widgets ---

    /**
     * A chapter title centred on its page, in the cover's red, between two thin rules.
     */
    private void pageTitle(final GuiGraphics graphics, final String text, final int x, final int y)
    {
        final int textW = font.width(text);
        final int cx = x + PAGE_W / 2;
        graphics.drawString(font, text, cx - textW / 2, y, ACCENT, false);
        graphics.fill(x, y + 4, cx - textW / 2 - 5, y + 5, RULE);
        graphics.fill(cx + textW / 2 + 5, y + 4, x + PAGE_W, y + 5, RULE);
    }

    /**
     * A thin rule with a small diamond in its middle.
     */
    private static void ornament(final GuiGraphics graphics, final int x, final int y, final int width)
    {
        final int cx = x + width / 2;
        graphics.fill(x + 10, y + 2, cx - 6, y + 3, RULE);
        graphics.fill(cx + 6, y + 2, x + width - 10, y + 3, RULE);
        graphics.fill(cx - 1, y, cx + 1, y + 5, ACCENT);
        graphics.fill(cx - 3, y + 2, cx + 3, y + 3, ACCENT);
    }

    private static void dottedRule(final GuiGraphics graphics, final int x, final int y, final int width)
    {
        for (int i = x; i < x + width; i += 2)
        {
            graphics.fill(i, y, i + 1, y + 1, RULE);
        }
    }

    private static boolean hover(final int mouseX, final int mouseY, final int x, final int y, final int width, final int height)
    {
        return Ui.inside(mouseX, mouseY, x - 3, y, width + 6, height);
    }

    private static int ink(final Ui.State state)
    {
        return switch (state)
        {
            case OK -> Palette.GREEN;
            case WAIT -> Palette.AMBER_TEXT;
            case BAD -> Palette.RED;
        };
    }

    /**
     * Rows running over both pages, a spread at a time; the arrows at the bottom corners turn the pages.
     */
    private void renderList(final GuiGraphics graphics, final int mouseX, final int mouseY, final String title, final String subtitle, final String empty,
      final List<Row> rows)
    {
        final int perSpread = ROWS_PER_PAGE * 2;
        spreads = Math.max(1, (rows.size() + perSpread - 1) / perSpread);
        spread = Math.max(0, Math.min(spread, spreads - 1));
        pageTitle(graphics, title, x0 + LEFT, y0 + TOP);
        pageTitle(graphics, subtitle, x0 + RIGHT, y0 + TOP);
        if (rows.isEmpty())
        {
            Ui.wrapped(graphics, font, Component.literal(empty), x0 + LEFT, y0 + TOP + 18, PAGE_W, MUTED, 10);
        }
        for (int side = 0; side < 2; side++)
        {
            final int px = x0 + (side == 0 ? LEFT : RIGHT);
            int y = y0 + TOP + 16;
            for (int i = 0; i < ROWS_PER_PAGE; i++)
            {
                final int index = spread * perSpread + side * ROWS_PER_PAGE + i;
                if (index >= rows.size())
                {
                    break;
                }
                final Row row = rows.get(index);
                if (hover(mouseX, mouseY, px, y, PAGE_W, ROW_H - 1))
                {
                    graphics.fill(px - 3, y, px + PAGE_W + 3, y + ROW_H - 1, HOVER);
                }
                hits.add(new Hit(px - 3, y, PAGE_W + 6, ROW_H - 1, row.action()));
                row.painter().paint(graphics, px, y, mouseX, mouseY);
                dottedRule(graphics, px, y + ROW_H - 1, PAGE_W);
                y += ROW_H;
            }
            final String number = Integer.toString(spread * 2 + side + 1);
            graphics.drawString(font, number, px + (PAGE_W - font.width(number)) / 2, y0 + BOTTOM - 6, MUTED, false);
        }
        if (spread > 0)
        {
            arrow(graphics, mouseX, mouseY, x0 + LEFT, "‹ Précédent", () -> spread--);
        }
        if (spread < spreads - 1)
        {
            arrow(graphics, mouseX, mouseY, x0 + RIGHT + PAGE_W - font.width("Suivant ›"), "Suivant ›", () -> spread++);
        }
    }

    private void arrow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final String text, final Runnable action)
    {
        final int y = y0 + BOTTOM - 6;
        final boolean over = Ui.inside(mouseX, mouseY, x - 2, y - 2, font.width(text) + 4, 12);
        graphics.drawString(font, text, x, y, over ? ACCENT : INK, false);
        hits.add(new Hit(x - 2, y - 2, font.width(text) + 4, 12, action));
    }

    // --- Overview ---

    private record Figure(String label, String value, Icon icon, Ui.State state, Runnable action)
    {
    }

    private record Tool(String label, Icon icon, boolean locked, Runnable action)
    {
    }

    private List<Tool> tools()
    {
        final boolean mapLocked = townHall.getBuildingLevel() < ColonyMaps.UNLOCK_LEVEL;
        return List.of(
          new Tool("Carte", Icon.of(Items.FILLED_MAP), mapLocked, this::openMap),
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
            notice("La carte se débloque au niveau " + ColonyMaps.UNLOCK_LEVEL + " de l'hôtel de ville. Améliorez-le pour la voir.");
            return;
        }
        if (townHall.getMapDataList().isEmpty())
        {
            notice("La carte se dessine encore : revenez dans un instant.");
            return;
        }
        final WindowColonyMap map = new WindowColonyMap(true, townHall);
        map.findPaneByID("inventory").hide();
        map.findPaneByID("warning").hide();
        map.open();
    }

    private void renderOverview(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final List<IBuildingView> buildings = colony.getBuildings();
        final List<ICitizenDataView> citizens = new ArrayList<>(colony.getCitizens().values());
        int blocked = 0;
        int sites = 0;
        for (final IBuildingView building : buildings)
        {
            if (stateOf(building).state() == Ui.State.BAD)
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

        // Left page: the colony's arms, its name, its state, its seat
        final int lx = x0 + LEFT;
        final int cx = lx + PAGE_W / 2;
        int y = y0 + TOP;
        final ItemStack banner = new ItemStack(Items.WHITE_BANNER);
        if (colony.getColonyFlag() != null)
        {
            banner.set(DataComponents.BANNER_PATTERNS, colony.getColonyFlag());
        }
        Icon.of(banner).draw(graphics, cx - 16, y, 32);
        y += 36;

        final String name = fit(colony.getName(), PAGE_W - 16);
        final int nameW = font.width(name);
        graphics.drawString(font, name, cx - nameW / 2, y, INK, false);
        final int penX = cx + nameW / 2 + 4;
        final boolean overPen = Ui.inside(mouseX, mouseY, penX - 2, y - 2, 11, 12);
        graphics.drawString(font, "✎", penX, y, overPen ? ACCENT : MUTED, false);
        hits.add(new Hit(penX - 2, y - 2, 11, 12, () -> new WindowTownHallNameEntry(colony).open()));
        if (overPen)
        {
            hint = "Renommer la colonie";
        }
        y += 12;
        final String seat = "Colonie de " + citizens.size() + (citizens.size() > 1 ? " âmes" : " âme");
        graphics.drawString(font, seat, cx - font.width(seat) / 2, y, MUTED, false);
        y += 14;
        ornament(graphics, lx, y, PAGE_W);
        y += 12;

        final Ui.State state;
        final String stateTitle;
        final String stateText;
        final Runnable stateAction;
        if (blocked > 0)
        {
            state = Ui.State.BAD;
            stateTitle = "À régler";
            stateText = blocked + (blocked == 1 ? " bâtiment est bloqué." : " bâtiments sont bloqués.");
            stateAction = () -> show(Page.BUILDINGS);
        }
        else if (troubled > 0)
        {
            state = Ui.State.WAIT;
            stateTitle = "À surveiller";
            stateText = troubled + (troubled == 1 ? " citoyen a des doléances." : " citoyens ont des doléances.");
            stateAction = () -> show(Page.CITIZENS);
        }
        else
        {
            state = Ui.State.OK;
            stateTitle = "Tout va bien";
            stateText = "Aucun bâtiment bloqué, aucune doléance.";
            stateAction = null;
        }
        final int noteTop = y;
        final boolean overNote = stateAction != null && hover(mouseX, mouseY, lx, noteTop - 3, PAGE_W, 26);
        if (overNote)
        {
            graphics.fill(lx - 3, noteTop - 3, lx + PAGE_W + 3, noteTop + 23, HOVER);
        }
        graphics.fill(lx, y + 2, lx + 4, y + 6, ink(state));
        graphics.drawString(font, Component.literal(stateTitle).withStyle(ChatFormatting.BOLD), lx + 8, y, ink(state), false);
        if (stateAction != null)
        {
            graphics.drawString(font, "›", lx + PAGE_W - 5, y, ACCENT, false);
            hits.add(new Hit(lx - 3, noteTop - 3, PAGE_W + 6, 26, stateAction));
        }
        Ui.wrapped(graphics, font, Component.literal(stateText), lx + 8, y + 11, PAGE_W - 8, INK, 10);
        y += 32;

        pageTitle(graphics, "Le siège", lx, y);
        y += 14;
        final int level = townHall.getBuildingLevel();
        final boolean overHall = hover(mouseX, mouseY, lx, y, PAGE_W, 24);
        if (overHall)
        {
            graphics.fill(lx - 3, y, lx + PAGE_W + 3, y + 24, HOVER);
        }
        Icon.of(townHall.getBuildingType().getBuildingBlock()).draw(graphics, lx, y + 4, 16);
        graphics.drawString(font, "Hôtel de ville", lx + 21, y + 3, INK, false);
        final String sub = level < ColonyMaps.UNLOCK_LEVEL ? "Niveau 2 : débloque la carte" : "Améliorer, réparer, stock";
        graphics.drawString(font, fit(sub, PAGE_W - 30), lx + 21, y + 13, MUTED, false);
        final String badge = "Niv. " + level;
        Ui.levelBadge(graphics, font, lx + PAGE_W - font.width(badge) - 16, y + 2, badge, level);
        graphics.drawString(font, "›", lx + PAGE_W - 5, y + 8, ACCENT, false);
        hits.add(new Hit(lx - 3, y, PAGE_W + 6, 24, () -> HutScreen.openWith(townHall, this)));

        // Right page: the figures, as a ledger, then the registers
        final int rx = x0 + RIGHT;
        y = y0 + TOP;
        pageTitle(graphics, "La colonie en chiffres", rx, y);
        y += 15;
        final double happiness = colony.getOverallHappiness();
        final List<Figure> figures = List.of(
          new Figure("Citoyens", citizens.size() + " / " + colony.getCitizenCountLimit(), Icon.module("workers"), Ui.State.OK, () -> show(Page.CITIZENS)),
          new Figure("Bonheur", format(happiness) + " / 10", Icon.module("happiness"), happiness < 4 ? Ui.State.BAD : happiness < 7 ? Ui.State.WAIT : Ui.State.OK,
            () -> show(Page.CITIZENS)),
          new Figure("Doléances", Integer.toString(grievances), Icon.of(Items.WRITABLE_BOOK), grievances > 0 ? Ui.State.WAIT : Ui.State.OK,
            () -> show(Page.CITIZENS)),
          new Figure("Demandes", Integer.toString(requests), Icon.module("requests"), requests > 0 ? Ui.State.WAIT : Ui.State.OK, () -> show(Page.REQUESTS)),
          new Figure("Bâtiments", Integer.toString(buildings.size()), Icon.of(Items.BRICKS), blocked > 0 ? Ui.State.BAD : Ui.State.OK,
            () -> show(Page.BUILDINGS)),
          new Figure("Chantiers", Integer.toString(sites), Icon.of(Items.IRON_PICKAXE), Ui.State.OK, () -> show(Page.BUILDINGS)));
        for (final Figure figure : figures)
        {
            if (hover(mouseX, mouseY, rx, y, PAGE_W, 15))
            {
                graphics.fill(rx - 3, y, rx + PAGE_W + 3, y + 15, HOVER);
            }
            figure.icon().draw(graphics, rx, y + 1, 12);
            graphics.drawString(font, figure.label(), rx + 16, y + 4, INK, false);
            final int valueW = font.width(figure.value());
            final int valueX = rx + PAGE_W - valueW;
            dottedRule(graphics, rx + 19 + font.width(figure.label()), y + 10, valueX - rx - 23 - font.width(figure.label()));
            graphics.drawString(font, figure.value(), valueX, y + 4, figure.state() == Ui.State.OK ? INK : ink(figure.state()), false);
            hits.add(new Hit(rx - 3, y, PAGE_W + 6, 15, figure.action()));
            y += 16;
        }

        y += 8;
        pageTitle(graphics, "Registres", rx, y);
        y += 15;
        final List<Tool> tools = tools();
        final int colW = PAGE_W / 2;
        for (int i = 0; i < tools.size(); i++)
        {
            final Tool tool = tools.get(i);
            final int tx = rx + (i % 2) * colW;
            final int ty = y + (i / 2) * 16;
            final boolean over = Ui.inside(mouseX, mouseY, tx - 2, ty, colW - 2, 15);
            if (over)
            {
                graphics.fill(tx - 2, ty, tx + colW - 4, ty + 15, HOVER);
            }
            tool.icon().draw(graphics, tx, ty + 1, 12);
            graphics.drawString(font, tool.label(), tx + 16, ty + 4, tool.locked() ? MUTED : INK, false);
            if (tool.locked())
            {
                final String lock = "niv. " + ColonyMaps.UNLOCK_LEVEL;
                graphics.drawString(font, lock, tx + colW - 6 - font.width(lock), ty + 4, ACCENT, false);
                if (over)
                {
                    hint = "La carte se débloque au niveau " + ColonyMaps.UNLOCK_LEVEL + " de l'hôtel de ville";
                }
            }
            hits.add(new Hit(tx - 2, ty, colW - 2, 15, tool.action()));
        }
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

    private void renderBuildings(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final List<IBuildingView> buildings = new ArrayList<>(colony.getBuildings());
        final Map<IBuildingView, BuildingState> states = new LinkedHashMap<>();
        buildings.forEach(b -> states.put(b, stateOf(b)));
        // Problems first, then by name
        buildings.sort(Comparator.<IBuildingView>comparingInt(b -> -states.get(b).state().ordinal()).thenComparing(ColonyHubScreen::nameOf));
        final List<Row> rows = new ArrayList<>();
        for (final IBuildingView building : buildings)
        {
            final BuildingState state = states.get(building);
            rows.add(new Row((graphics1, x, y, mx, my) ->
            {
                Icon.of(building.getBuildingType().getBuildingBlock()).draw(graphics1, x, y + 3, 16);
                final int chipW = font.width(state.text()) + 8;
                Ui.chip(graphics1, font, x + PAGE_W - chipW, y + 5, state.text(), state.state());
                graphics1.drawString(font, fit(nameOf(building), PAGE_W - chipW - 26), x + 20, y + 3, INK, false);
                levelSquares(graphics1, x + 20, y + 12, building.getBuildingLevel(), building.getBuildingMaxLevel());
            }, () -> HutScreen.openWith(building, this)));
        }
        renderList(graphics, mouseX, mouseY, buildings.size() + " bâtiments", "Les problèmes en tête", "Aucun bâtiment pour l'instant.", rows);
    }

    /**
     * One square per level in its colour, filled up to the building's level.
     */
    private static void levelSquares(final GuiGraphics graphics, int x, final int y, final int level, final int max)
    {
        for (int l = 1; l <= max; l++)
        {
            graphics.fill(x, y, x + 6, y + 6, l <= level ? Palette.levelEdge(l) : RULE);
            graphics.fill(x + 1, y + 1, x + 5, y + 5, l <= level ? Palette.levelFill(l) : Palette.PAPER_LIGHT);
            x += 8;
        }
    }

    // --- Citizens ---

    private void renderCitizens(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final List<ICitizenDataView> citizens = new ArrayList<>(colony.getCitizens().values());
        final Map<ICitizenDataView, List<CitizenBits.Chip>> chips = new LinkedHashMap<>();
        citizens.forEach(c -> chips.put(c, CitizenBits.chips(c)));
        // Those with something to settle first
        citizens.sort(Comparator.<ICitizenDataView>comparingInt(c -> -(int) chips.get(c).stream().filter(ch -> ch.action() != null).count())
          .thenComparing(ICitizenDataView::getName));
        final List<Row> rows = new ArrayList<>();
        for (final ICitizenDataView citizen : citizens)
        {
            rows.add(new Row((graphics1, x, y, mx, my) ->
            {
                CitizenBits.face(graphics1, citizen, x, y + 3, 16);
                graphics1.drawString(font, fit(citizen.getName(), PAGE_W - 20), x + 20, y + 2, INK, false);

                // Chips on the second line, right-aligned; a click on one settles the problem
                int chipX = x + PAGE_W;
                final List<CitizenBits.Chip> list = chips.get(citizen);
                for (int i = list.size() - 1; i >= 0; i--)
                {
                    final CitizenBits.Chip chip = list.get(i);
                    final int chipW = font.width(chip.text()) + 8;
                    chipX -= chipW;
                    final boolean overChip = chip.action() != null && Ui.inside(mx, my, chipX, y + 10, chipW, 11);
                    Ui.chip(graphics1, font, chipX, y + 10, chip.text(), chip.soft(), chip.ink(), chip.action() != null, overChip);
                    if (chip.action() != null)
                    {
                        // Added after the row's own hit would lose: put it first
                        hits.add(0, new Hit(chipX, y + 10, chipW, 11, chip.action()));
                    }
                    chipX -= 2;
                }
                final String job = citizen.getJob() == null || citizen.getJob().isEmpty() ? (citizen.isChild() ? "Enfant" : "Sans emploi")
                                     : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());
                graphics1.drawString(font, fit(job, chipX - x - 22), x + 20, y + 12, MUTED, false);
            }, () -> CitizenScreen.open(citizen, this)));
        }
        renderList(graphics, mouseX, mouseY, citizens.size() + " / " + colony.getCitizenCountLimit() + " citoyens", "À régler en tête",
          "Personne n'habite encore la colonie.", rows);
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

    private void renderRequests(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final List<OpenRequest> requests = requests();
        final List<Row> rows = new ArrayList<>();
        for (final OpenRequest open : requests)
        {
            final IRequest<?> request = open.request();
            rows.add(new Row((graphics1, x, y, mx, my) ->
            {
                final List<ItemStack> stacks = request.getDisplayStacks();
                final ItemStack stack = stacks.isEmpty() ? new ItemStack(Items.PAPER) : stacks.get(0);
                Icon.of(stack).draw(graphics1, x, y + 3, 16);
                slots.add(new Ui.Slot(x, y + 3, 16, stack));
                final String state = CitizenBits.requestStateText(request);
                final int chipW = font.width(state) + 8;
                Ui.chip(graphics1, font, x + PAGE_W - chipW, y + 5, state, CitizenBits.requestState(request));
                final int textW = PAGE_W - chipW - 24;
                graphics1.drawString(font, fit(request.getShortDisplayString().getString(), textW), x + 20, y + 2, INK, false);
                graphics1.drawString(font, fit("pour " + nameOf(open.building()), textW), x + 20, y + 12, MUTED, false);
            }, () -> HutScreen.openWith(open.building(), this)));
        }
        renderList(graphics, mouseX, mouseY, requests.size() + (requests.size() > 1 ? " demandes ouvertes" : " demande ouverte"), "Qui attend quoi",
          "Personne n'attend rien : la colonie ne manque de rien.", rows);
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
        // The wheel turns the pages of a list
        final int target = Math.max(0, Math.min(spreads - 1, spread + (scrollY < 0 ? 1 : -1)));
        if (page != Page.OVERVIEW && target != spread)
        {
            spread = target;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
        return true;
    }
}
