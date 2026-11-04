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
 * request, each one click away from its own window. The seat of the colony looks the part: navy velvet and gold, the
 * colony's banner and name in large, the town hall's level as a medallion, where every hut is plain parchment.
 * MineColonies' rarer town hall pages stay reachable as tools.
 */
public final class ColonyHubScreen extends ParchmentScreen
{
    /**
     * The council colours, only for this window.
     */
    private static final int VELVET      = 0xFF1C2140;
    private static final int VELVET_DARK = 0xFF13162C;
    private static final int CARD        = 0xFF262C52;
    private static final int CARD_HOVER  = 0xFF343C6C;
    private static final int CARD_EDGE   = 0xFF4E4A36;
    private static final int GOLD        = 0xFFE8B021;
    private static final int GOLD_DEEP   = 0xFF8A6410;
    private static final int GOLD_PALE   = 0xFFFFE3A3;
    private static final int CREAM       = 0xFFF3E6C4;
    private static final int DIM         = 0xFFA49C80;
    private static final int OK          = 0xFF7CC444;
    private static final int WAIT        = 0xFFE89A30;
    private static final int BAD         = 0xFFE0594A;

    private static final int HDR = 40;
    private static final String[] ROMAN = {"–", "I", "II", "III", "IV", "V"};

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

    // --- Frame ---

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        hits.clear();
        hint = null;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderFrame(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        // Double gold border around navy velvet, the header a shade darker
        graphics.fill(x0, y0, x0 + w, y0 + h, GOLD_DEEP);
        graphics.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1, GOLD);
        graphics.fill(x0 + 2, y0 + 2, x0 + w - 2, y0 + h - 2, GOLD_DEEP);
        graphics.fill(x0 + 3, y0 + 3, x0 + w - 3, y0 + h - 3, VELVET);
        graphics.fill(x0 + 3, y0 + 3, x0 + w - 3, y0 + HDR, VELVET_DARK);
        graphics.fill(x0 + 3, y0 + HDR, x0 + w - 3, y0 + HDR + 1, GOLD);
        graphics.fill(x0 + 3, y0 + HDR + 2, x0 + w - 3, y0 + HDR + 3, GOLD_DEEP);
        corner(graphics, x0 + 3, y0 + 3);
        corner(graphics, x0 + w - 8, y0 + 3);
        corner(graphics, x0 + 3, y0 + h - 8);
        corner(graphics, x0 + w - 8, y0 + h - 8);

        // The colony's banner, its name in large, the town hall's level as a medallion
        final ItemStack banner = new ItemStack(Items.WHITE_BANNER);
        if (colony.getColonyFlag() != null)
        {
            banner.set(DataComponents.BANNER_PATTERNS, colony.getColonyFlag());
        }
        Icon.of(banner).draw(graphics, x0 + 12, y0 + 5, 32);

        final int level = townHall.getBuildingLevel();
        final String roman = ROMAN[Math.max(0, Math.min(5, level))];
        final Component name = Component.literal(colony.getName()).withStyle(ChatFormatting.BOLD);
        final int nameW = font.width(name) * 2;
        final int nameX = x0 + (w - nameW) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(nameX, y0 + 7, 0);
        graphics.pose().scale(2, 2, 1);
        graphics.drawString(font, name, 1, 1, GOLD_DEEP, false);
        graphics.drawString(font, name, 0, 0, GOLD_PALE, false);
        graphics.pose().popPose();

        final int penX = nameX + nameW + 5;
        final boolean overPen = Ui.inside(mouseX, mouseY, penX - 2, y0 + 9, 12, 12);
        graphics.drawString(font, "✎", penX, y0 + 12, overPen ? GOLD : DIM, false);
        hits.add(new Hit(penX - 2, y0 + 9, 12, 12, () -> new WindowTownHallNameEntry(colony).open()));
        if (overPen)
        {
            hint = "Renommer la colonie";
        }

        final String seat = "HÔTEL DE VILLE · NIVEAU " + roman;
        graphics.drawString(font, seat, x0 + (w - font.width(seat)) / 2, y0 + 28, DIM, false);

        final int medalX = x0 + w - 50;
        final int medalY = y0 + 7;
        graphics.fill(medalX - 1, medalY - 1, medalX + 27, medalY + 27, GOLD);
        Ui.plate(graphics, medalX, medalY, medalX + 26, medalY + 26, Palette.levelFill(level), Palette.levelEdge(level), Palette.levelFill(level));
        graphics.pose().pushPose();
        graphics.pose().translate(medalX + 13 - font.width(roman), medalY + 6, 0);
        graphics.pose().scale(2, 2, 1);
        graphics.drawString(font, roman, 0, 0, Palette.levelText(level), false);
        graphics.pose().popPose();
        if (Ui.inside(mouseX, mouseY, medalX, medalY, 26, 26))
        {
            hint = "Hôtel de ville niveau " + level + " : cliquez pour l'améliorer";
        }
        hits.add(new Hit(medalX, medalY, 26, 26, () -> HutScreen.openWith(townHall, this)));

        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, overClose(mouseX, mouseY) ? BAD : DIM, false);
    }

    /**
     * A small gold diamond in a corner of the frame.
     */
    private static void corner(final GuiGraphics graphics, final int x, final int y)
    {
        graphics.fill(x + 2, y, x + 3, y + 5, GOLD);
        graphics.fill(x, y + 2, x + 5, y + 3, GOLD);
        graphics.fill(x + 1, y + 1, x + 4, y + 4, GOLD_DEEP);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, GOLD_PALE);
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        // The frame drew first and may have set a hint and hits: keep those
        slots.clear();

        final int tabsY = y0 + HDR + 6;
        renderTabs(graphics, mouseX, mouseY, tabsY);
        graphics.fill(x0 + 8, tabsY + 16, x0 + w - 8, tabsY + 17, CARD_EDGE);

        final int x = x0 + 10;
        final int width = w - 24;
        final int top = contentTop();
        final int bottom = y0 + h - 8;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;
        if (notice != null && Util.getMillis() < noticeUntil)
        {
            ribbon(graphics, x, y, width, WAIT, "Pas encore", notice);
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
            graphics.fill(x + width + 2, top, x + width + 4, bottom, VELVET_DARK);
            graphics.fill(x + width + 2, thumbY, x + width + 4, thumbY + thumb, GOLD_DEEP);
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

    private int contentTop()
    {
        return y0 + HDR + 27;
    }

    private void addHit(final int x, final int y, final int width, final int height, final Runnable action)
    {
        final int top = Math.max(y, contentTop());
        final int bottom = Math.min(y + height, y0 + h - 8);
        if (bottom > top)
        {
            hits.add(new Hit(x, top, width, bottom - top, action));
        }
    }

    private void notice(final String text)
    {
        notice = text;
        noticeUntil = Util.getMillis() + 6000;
        scroll = 0;
    }

    // --- Tabs and tools ---

    private record Tool(String label, Icon icon, boolean locked, Runnable action)
    {
    }

    private List<Tool> tools()
    {
        final boolean mapLocked = townHall.getBuildingLevel() < ColonyMaps.UNLOCK_LEVEL;
        return List.of(
          new Tool(mapLocked ? "Carte : se débloque au niveau " + ColonyMaps.UNLOCK_LEVEL + " de l'hôtel de ville" : "Carte de la colonie",
            Icon.of(Items.FILLED_MAP), mapLocked, this::openMap),
          new Tool("Statistiques", Icon.module("stats"), false, () -> new WindowStatsPage((BuildingTownHall.View) townHall).open()),
          new Tool("Réglages", Icon.module("settings"), false, () -> new WindowSettings((BuildingTownHall.View) townHall).open()),
          new Tool("Permissions", Icon.module("scepter"), false, () -> new WindowPermissionsPage((BuildingTownHall.View) townHall).open()),
          new Tool("Alliances", Icon.of(Items.WHITE_BANNER), false, () -> new WindowAlliancePage((BuildingTownHall.View) townHall).open()),
          new Tool("Apparence : style, bannière, couleur", Icon.module("domum"), false, () ->
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

    private void renderTabs(final GuiGraphics graphics, final int mouseX, final int mouseY, final int y)
    {
        int x = x0 + 10;
        for (final Page p : Page.values())
        {
            final int tabW = 16 + font.width(p.label) + 10;
            final boolean on = p == page;
            final boolean over = Ui.inside(mouseX, mouseY, x, y, tabW, 15);
            if (on)
            {
                graphics.fill(x, y, x + tabW, y + 15, CARD);
                graphics.fill(x, y + 14, x + tabW, y + 16, GOLD);
            }
            else if (over)
            {
                graphics.fill(x, y, x + tabW, y + 15, VELVET_DARK);
            }
            p.icon.draw(graphics, x + 3, y + 1, 12);
            graphics.drawString(font, p.label, x + 18, y + 4, on ? GOLD_PALE : over ? CREAM : DIM, false);
            hits.add(new Hit(x, y, tabW, 15, () -> show(p)));
            x += tabW + 2;
        }

        // Tools as icons on the right, their name on hover
        final List<Tool> tools = tools();
        int toolX = x0 + w - 10 - tools.size() * 19 + 1;
        for (final Tool tool : tools)
        {
            final boolean over = Ui.inside(mouseX, mouseY, toolX, y - 1, 18, 18);
            graphics.fill(toolX, y - 1, toolX + 18, y + 17, over ? CARD_HOVER : CARD);
            graphics.renderOutline(toolX, y - 1, 18, 18, over ? GOLD : CARD_EDGE);
            tool.icon().draw(graphics, toolX + 1, y, 16);
            if (tool.locked())
            {
                graphics.fill(toolX + 1, y, toolX + 17, y + 16, 0xB013162C);
                graphics.drawString(font, Integer.toString(ColonyMaps.UNLOCK_LEVEL), toolX + 11, y + 8, GOLD, true);
            }
            if (over)
            {
                hint = tool.label();
            }
            hits.add(new Hit(toolX, y - 1, 18, 18, tool.action()));
            toolX += 19;
        }
    }

    private void show(final Page target)
    {
        page = target;
        scroll = 0;
    }

    // --- Council widgets ---

    private static void card(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final boolean hover)
    {
        graphics.fill(x, y, x + width, y + height, hover ? CARD_HOVER : CARD);
        graphics.renderOutline(x, y, width, height, hover ? GOLD_DEEP : CARD_EDGE);
    }

    /**
     * A section title in gold, followed by a thin rule to the right edge.
     */
    private void section(final GuiGraphics graphics, final String text, final int x, final int y, final int width)
    {
        graphics.drawString(font, text, x, y, GOLD, false);
        final int end = x + font.width(text) + 5;
        graphics.fill(end, y + 3, x + width, y + 4, CARD_EDGE);
    }

    /**
     * A one-line state ribbon: coloured stripe, the state in bold and its colour, then what it means.
     */
    private void ribbon(final GuiGraphics graphics, final int x, final int y, final int width, final int color, final String title, final String text)
    {
        card(graphics, x, y, width, 16, false);
        graphics.fill(x, y, x + 3, y + 16, color);
        final Component bold = Component.literal(title).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, bold, x + 9, y + 4, color, false);
        final int after = x + 9 + font.width(bold);
        graphics.drawString(font, fit(" · " + text, x + width - 4 - after), after, y + 4, CREAM, false);
    }

    private static int color(final Ui.State state)
    {
        return switch (state)
        {
            case OK -> OK;
            case WAIT -> WAIT;
            case BAD -> BAD;
        };
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
            ribbon(graphics, x, y, width, BAD, "À régler", blocked + (blocked == 1 ? " bâtiment bloqué" : " bâtiments bloqués"));
            addHit(x, y, width, 16, () -> show(Page.BUILDINGS));
        }
        else if (troubled > 0)
        {
            ribbon(graphics, x, y, width, WAIT, "À surveiller", troubled + (troubled == 1 ? " citoyen a" : " citoyens ont") + " des doléances");
            addHit(x, y, width, 16, () -> show(Page.CITIZENS));
        }
        else
        {
            ribbon(graphics, x, y, width, OK, "Tout va bien", "aucun bâtiment bloqué, aucune doléance");
        }
        y += 22;

        // The colony's vital signs, each opening the page that explains it
        section(graphics, "LA COLONIE EN CHIFFRES", x, y, width);
        y += 12;
        final double happiness = colony.getOverallHappiness();
        final List<Tile> tiles = List.of(
          new Tile("Citoyens", citizens.size() + "/" + colony.getCitizenCountLimit(), Icon.module("workers"), Ui.State.OK, () -> show(Page.CITIZENS)),
          new Tile("Bonheur sur 10", format(happiness), Icon.module("happiness"), happiness < 4 ? Ui.State.BAD : happiness < 7 ? Ui.State.WAIT : Ui.State.OK,
            () -> show(Page.CITIZENS)),
          new Tile("Doléances", Integer.toString(grievances), Icon.of(Items.WRITABLE_BOOK), grievances > 0 ? Ui.State.WAIT : Ui.State.OK, () -> show(Page.CITIZENS)),
          new Tile("Demandes ouvertes", Integer.toString(requests), Icon.module("requests"), requests > 0 ? Ui.State.WAIT : Ui.State.OK, () -> show(Page.REQUESTS)),
          new Tile("Bâtiments", Integer.toString(buildings.size()), Icon.of(Items.BRICKS), blocked > 0 ? Ui.State.BAD : Ui.State.OK, () -> show(Page.BUILDINGS)),
          new Tile("Chantiers en cours", Integer.toString(sites), Icon.of(Items.IRON_PICKAXE), Ui.State.OK, () -> show(Page.BUILDINGS)));
        final int tileW = (width - 2 * 5) / 3;
        for (int i = 0; i < tiles.size(); i++)
        {
            tile(graphics, mouseX, mouseY, x + (i % 3) * (tileW + 5), y + (i / 3) * 39, tileW, tiles.get(i));
        }
        y += 2 * 39 + 4;

        section(graphics, "LE SIÈGE DE LA COLONIE", x, y, width);
        y += 12;
        final int level = townHall.getBuildingLevel();
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 26);
        card(graphics, x, y, width, 26, over);
        graphics.fill(x, y, x + 3, y + 26, Palette.levelFill(level));
        Icon.of(townHall.getBuildingType().getBuildingBlock()).draw(graphics, x + 8, y + 5, 16);
        graphics.drawString(font, Component.literal("Hôtel de ville").withStyle(ChatFormatting.BOLD), x + 29, y + 5, CREAM, false);
        final String sub = level < ColonyMaps.UNLOCK_LEVEL ? "Améliorez-le pour débloquer la carte de la colonie" : "Améliorer, réparer, voir le stock";
        graphics.drawString(font, fit(sub, width - 120), x + 29, y + 15, DIM, false);
        final int badgeW = Ui.levelBadge(graphics, font, x + width - 74, y + 7, "Niveau " + level, level);
        graphics.drawString(font, "›", x + width - 74 + badgeW + 6, y + 9, GOLD, false);
        addHit(x, y, width, 26, () -> HutScreen.openWith(townHall, this));
        return y + 30;
    }

    private record Tile(String label, String value, Icon icon, Ui.State state, Runnable action)
    {
    }

    /**
     * A vital sign: a stripe in its state's colour, the number in large, what it counts below.
     */
    private void tile(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final Tile tile)
    {
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 35);
        card(graphics, x, y, width, 35, over);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, color(tile.state()));
        tile.icon().draw(graphics, x + 7, y + 11, 16);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 30, y + 7, 0);
        graphics.pose().scale(2, 2, 1);
        graphics.drawString(font, tile.value(), 0, 0, CREAM, false);
        graphics.pose().popPose();
        graphics.drawString(font, fit(tile.label(), width - 34), x + 30, y + 25, DIM, false);
        addHit(x, y, width, 35, tile.action());
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
        section(graphics, buildings.size() + " BÂTIMENTS", x, y, width);
        y += 12;
        for (final IBuildingView building : buildings)
        {
            final BuildingState state = states.get(building);
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 20);
            card(graphics, x, y, width, 20, over);
            graphics.fill(x, y, x + 2, y + 20, color(state.state()));
            Icon.of(building.getBuildingType().getBuildingBlock()).draw(graphics, x + 5, y + 2, 16);
            final int level = building.getBuildingLevel();
            final int squaresX = x + width - 150;
            graphics.drawString(font, fit(nameOf(building), squaresX - x - 30), x + 25, y + 6, CREAM, false);
            levelSquares(graphics, squaresX, y + 6, level, building.getBuildingMaxLevel());
            final int chipW = font.width(state.text()) + 8;
            Ui.chip(graphics, font, x + width - chipW - 14, y + 5, state.text(), state.state());
            graphics.drawString(font, "›", x + width - 9, y + 6, GOLD, false);
            addHit(x, y, width, 20, () -> HutScreen.openWith(building, this));
            y += 22;
        }
        return y;
    }

    /**
     * One square per level in its colour, filled up to the building's level.
     */
    private static void levelSquares(final GuiGraphics graphics, int x, final int y, final int level, final int max)
    {
        for (int l = 1; l <= max; l++)
        {
            graphics.fill(x, y, x + 6, y + 7, l <= level ? Palette.levelEdge(l) : CARD_EDGE);
            graphics.fill(x + 1, y + 1, x + 5, y + 6, l <= level ? Palette.levelFill(l) : VELVET_DARK);
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
        section(graphics, citizens.size() + " / " + colony.getCitizenCountLimit() + " CITOYENS", x, y, width);
        y += 12;
        for (final ICitizenDataView citizen : citizens)
        {
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 22);
            card(graphics, x, y, width, 22, over);
            CitizenBits.face(graphics, citizen, x + 3, y + 3, 16);
            final String job = citizen.getJob() == null || citizen.getJob().isEmpty() ? (citizen.isChild() ? "Enfant" : "Sans emploi")
                                 : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());
            graphics.drawString(font, Component.literal(citizen.getName()).withStyle(ChatFormatting.BOLD), x + 23, y + 3, CREAM, false);
            graphics.drawString(font, fit(job, width / 2 - 26), x + 23, y + 12, DIM, false);

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
            graphics.drawString(font, "›", x + width - 9, y + 7, GOLD, false);
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
            ribbon(graphics, x, y, width, OK, "Rien à livrer", "personne n'attend rien, la colonie ne manque de rien");
            return y + 18;
        }
        section(graphics, requests.size() + " DEMANDES OUVERTES", x, y, width);
        y += 12;
        for (final OpenRequest open : requests)
        {
            final IRequest<?> request = open.request();
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 22);
            card(graphics, x, y, width, 22, over);
            final List<ItemStack> stacks = request.getDisplayStacks();
            final ItemStack stack = stacks.isEmpty() ? new ItemStack(Items.PAPER) : stacks.get(0);
            Icon.of(stack).draw(graphics, x + 3, y + 3, 16);
            slots.add(new Ui.Slot(x + 3, y + 3, 16, stack));
            final String state = CitizenBits.requestStateText(request);
            final int chipW = font.width(state) + 8;
            Ui.chip(graphics, font, x + width - chipW - 12, y + 6, state, CitizenBits.requestState(request));
            final int textW = width - chipW - 40;
            graphics.drawString(font, fit(request.getShortDisplayString().getString(), textW), x + 23, y + 3, CREAM, false);
            graphics.drawString(font, fit("pour " + nameOf(open.building()), textW), x + 23, y + 12, DIM, false);
            graphics.drawString(font, "›", x + width - 9, y + 7, GOLD, false);
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
