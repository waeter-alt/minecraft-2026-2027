package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.core.client.gui.map.WindowColonyMap;
import com.minecolonies.core.client.gui.townhall.WindowTownHallNameEntry;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.ChatFormatting;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.colony.ColonyMaps;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    /**
     * The seat's header: a leather band holding the colony's banner, its name and the town hall's level.
     */
    static final int HDR = 38;
    static final int SEPARATOR = 6;
    static final int PRICE_ICON = 12;
    /**
     * One row of an Intendance list: two lines of text and an item icon, with air above and below.
     */
    static final int ROW_H = 24;

    /**
     * The rune band around the window, in the town hall's level colour.
     */
    static final int AURA = 11;
    static final int CORNER = 16;
    static final String RUNES = "abcdefghijklmnopqrstuvwxyz";
    static final ResourceLocation RUNE_FONT = ResourceLocation.withDefaultNamespace("alt");

    /**
     * Set just before opening MineColonies' own town hall page on purpose (« Apparence »), so it is not replaced.
     */
    static boolean openOriginal;

    enum Page
    {
        OVERVIEW("Vue d'ensemble", Icon.module("main")),
        BUILDINGS("Bâtiments", Icon.of(Items.BRICKS)),
        CITIZENS("Citoyens", Icon.module("workers")),
        INTENDANCE("Intendance", Icon.of(Items.GOLD_INGOT)),
        WALLS("Murailles", Icon.of(Items.STONE_BRICK_WALL));

        private final String label;
        private final Icon icon;

        Page(final String label, final Icon icon)
        {
            this.label = label;
            this.icon = icon;
        }
    }

    record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    /**
     * A building's state in the list.
     */
    record BuildingState(Ui.State state, String text)
    {
    }

    record OpenRequest(IRequest<?> request, IBuildingView building)
    {
    }

    ITownHallView townHall;
    final IColonyView colony;
    Page page = Page.OVERVIEW;
    /**
     * The tool whose page shows in place of the hub's own (« Permissions »…), lit in the left column; null on the hub.
     */
    @Nullable
    String litTool;
    final List<Hit> hits = new ArrayList<>();
    final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    String hint;
    @Nullable
    String notice;
    long noticeUntil;
    int scroll;
    int navScroll;

    /**
     * Which detailed list of the Intendance is unfolded: none, the grievances or the supplies.
     */
    int intendanceList;
    /**
     * Set when a list was just unfolded: the next frame scrolls so it shows, since it opens under the card and may
     * land below the window.
     */
    boolean revealList;
    int viewTop;
    int viewBottom;
    int contentHeight;

    // Accès pour les classes de page : font, minecraft et fit sont protégés côté Screen/ParchmentScreen
    net.minecraft.client.gui.Font font()
    {
        return font;
    }

    Minecraft minecraft()
    {
        return minecraft;
    }

    String fitText(final String text, final int width)
    {
        return fit(text, width);
    }

    // Une classe par page : deux features qui touchent deux pages différentes ne modifient pas le même fichier
    private final HubOverviewPage overviewPage = new HubOverviewPage(this);
    private final HubBuildingsPage buildingsPage = new HubBuildingsPage(this);
    private final HubCitizensPage citizensPage = new HubCitizensPage(this);
    private final HubIntendancePage intendancePage = new HubIntendancePage(this);
    private final HubWallsPage wallsPage = new HubWallsPage(this);

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
     * Dev captures only: opens the hub straight on a page (by its enum name, any case), with an Intendance list unfolded.
     */
    public static void openDev(final IBuildingView townHall, final String page, final int intendanceList)
    {
        if (townHall instanceof ITownHallView view)
        {
            final ColonyHubScreen screen = new ColonyHubScreen(view);
            screen.page = Page.valueOf(page.toUpperCase(java.util.Locale.ROOT));
            screen.intendanceList = intendanceList;
            screen.revealList = intendanceList != 0;
            Minecraft.getInstance().setScreen(screen);
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
    protected void init()
    {
        super.init();
        // Leave room around the window for the rune band
        w = Math.min(width - 2 * AURA - 12, MAX_W);
        h = Math.min(height - 2 * AURA - 12, MAX_H);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
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
    protected boolean showWallet()
    {
        // Drawn in its own header, beside the level, with its breakdown as the hub's hint
        return false;
    }

    @Override
    protected void renderFrame(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        levelAura(graphics);
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

        // The town hall's level, plainly and large, in its colour
        final int level = townHall.getBuildingLevel();
        final Component label = Component.literal("Niveau " + level).withStyle(ChatFormatting.BOLD);
        final int plateW = font.width(label) + 32;
        final int plateX = x0 + w - 26 - plateW;
        final int plateY = y0 + 7;

        // The player's Influence and gold, left of the level, as every building window shows them
        final String subtitle = "Hôtel de ville · siège de la colonie";
        final Component fullName = Component.literal(colony.getName()).withStyle(ChatFormatting.BOLD);
        final int textRoom = Math.max(font.width(fullName) + 17, font.width("Hôtel de ville"));
        final int walletLeft = drawWallet(graphics, plateX - 10, plateY + 2, 36 + Math.min(textRoom, 120), Palette.GOLD_TEXT);
        if (Ui.inside(mouseX, mouseY, walletLeft, plateY + 4, walletWidth(), 15))
        {
            hint = walletHint();
        }
        final int textW = walletLeft - 10 - (x0 + 44);

        final Component name = font.width(fullName) + 17 <= textW ? fullName
                                 : Component.literal(fit(colony.getName(), Math.max(0, textW - 17 - colony.getName().length()))).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, name, x0 + 44, y0 + 10, Palette.GOLD_TEXT, true);
        final int penX = x0 + 44 + font.width(name) + 5;
        final boolean overPen = Ui.inside(mouseX, mouseY, penX - 2, y0 + 8, 12, 12);
        graphics.drawString(font, "✎", penX, y0 + 10, overPen ? Palette.WHITE : 0xFFC9A877, false);
        hits.add(new Hit(penX - 2, y0 + 8, 12, 12, () -> new WindowTownHallNameEntry(colony).open()));
        if (overPen)
        {
            hint = "Renommer la colonie";
        }
        // Short of room, the motto goes and the building's name stays whole
        graphics.drawString(font, fit(font.width(subtitle) <= textW ? subtitle : "Hôtel de ville", textW), x0 + 44, y0 + 22, 0xFFC9A877, false);
        Ui.plate(graphics, plateX, plateY, plateX + plateW, plateY + 25, Palette.levelFill(level), Palette.levelEdge(level), Palette.levelFill(level));
        graphics.renderOutline(plateX + 2, plateY + 2, plateW - 4, 21, mix(Palette.levelFill(level) & 0xFFFFFF, 0xFFFFFF, 0.45F) | 0xFF000000);
        graphics.drawString(font, label, plateX + 16, plateY + 9, Palette.levelText(level), false);

        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, overClose(mouseX, mouseY) ? Palette.RED_SOFT : Palette.GOLD_TEXT, false);
    }

    /**
     * A band of the town hall's level colour around the window, rounded at its corners and shaded like a tube, carrying
     * enchanting-table runes that drift slowly round it while a soft glow passes over them: the seat's level,
     * unmistakable from a glance.
     */
    void levelAura(final GuiGraphics graphics)
    {
        final int level = townHall.getBuildingLevel();
        final int fill = Palette.levelFill(level) & 0xFFFFFF;
        final int dark = Palette.levelEdge(level) & 0xFFFFFF;
        final int light = mix(fill, 0xFFFFFF, 0.4F);
        final int ink = Palette.levelText(level) & 0xFFFFFF;
        final int left = x0 - AURA;
        final int top = y0 - AURA;
        final int right = x0 + w + AURA;
        final int bottom = y0 + h + AURA;

        // Straight runs: one line per depth, so the shading runs across the band
        for (int t = 0; t < AURA; t++)
        {
            final int color = 0xFF000000 | shade(dark, fill, light, (t + 0.5) / AURA);
            graphics.fill(left + CORNER, top + t, right - CORNER, top + t + 1, color);
            graphics.fill(left + CORNER, bottom - t - 1, right - CORNER, bottom - t, color);
            graphics.fill(left + t, top + CORNER, left + t + 1, bottom - CORNER, color);
            graphics.fill(right - t - 1, top + CORNER, right - t, bottom - CORNER, color);
        }
        // Rounded corners, pixel by pixel, the outer rim softened
        for (int dx = 0; dx < CORNER; dx++)
        {
            for (int dy = 0; dy < CORNER; dy++)
            {
                final double distance = Math.hypot(CORNER - dx - 0.5, CORNER - dy - 0.5);
                final double depth = CORNER - distance;
                if (depth <= -0.5 || depth >= AURA)
                {
                    continue;
                }
                final double coverage = Math.min(1, depth + 0.5);
                final int color = ((int) (255 * coverage) << 24) | shade(dark, fill, light, Math.max(0, depth) / AURA);
                graphics.fill(left + dx, top + dy, left + dx + 1, top + dy + 1, color);
                graphics.fill(right - dx - 1, top + dy, right - dx, top + dy + 1, color);
                graphics.fill(left + dx, bottom - dy - 1, left + dx + 1, bottom - dy, color);
                graphics.fill(right - dx - 1, bottom - dy - 1, right - dx, bottom - dy, color);
            }
        }

        // The runes ride the band's middle line, two or three pixels a second, placed to the sub-pixel so they glide
        final double radius = CORNER - AURA / 2.0;
        final double arc = Math.PI / 2 * radius;
        final double across = right - left - 2.0 * CORNER;
        final double down = bottom - top - 2.0 * CORNER;
        final double perimeter = 2 * (across + down) + 4 * arc;
        final int count = (int) (perimeter / 9);
        final double step = perimeter / count;
        final long now = Util.getMillis();
        final double drift = now / 420.0;
        final double glow = now / 2600.0;
        for (int i = 0; i < count; i++)
        {
            final double along = (i * step + drift) % perimeter;
            final double[] point = runePoint(along, left, top, right, bottom, across, down, arc, radius);
            final Component glyph = Component.literal(String.valueOf(RUNES.charAt(i % RUNES.length()))).withStyle(style -> style.withFont(RUNE_FONT));
            final double wave = 0.5 + 0.5 * Math.sin(glow - along / 70.0);
            final int alpha = (int) (120 + 135 * wave);
            graphics.pose().pushPose();
            graphics.pose().translate(point[0] - font.width(glyph) / 2.0, point[1] - 4, 0);
            graphics.drawString(font, glyph, 0, 0, alpha << 24 | ink, false);
            graphics.pose().popPose();
        }
    }

    /**
     * The band's colour at this depth, from 0 at its outer rim to 1 at the window: dark at both rims, light in the
     * middle, like a rounded tube.
     */
    static int shade(final int dark, final int fill, final int light, final double depth)
    {
        final double bulge = Math.sin(Math.PI * Math.max(0, Math.min(1, depth)));
        return bulge < 0.5 ? mix(dark, fill, (float) (bulge * 2)) : mix(fill, light, (float) ((bulge - 0.5) * 2));
    }

    /**
     * A point on the band's middle line, this far along it clockwise from the top left corner's end.
     */
    static double[] runePoint(double along, final int left, final int top, final int right, final int bottom, final double across,
      final double down, final double arc, final double radius)
    {
        final double inset = CORNER - radius;
        final double[] centres = {right - CORNER, top + CORNER, right - CORNER, bottom - CORNER, left + CORNER, bottom - CORNER, left + CORNER, top + CORNER};
        final double[] straights = {across, down, across, down};
        for (int side = 0; side < 4; side++)
        {
            if (along < straights[side])
            {
                return switch (side)
                {
                    case 0 -> new double[] {left + CORNER + along, top + inset};
                    case 1 -> new double[] {right - inset, top + CORNER + along};
                    case 2 -> new double[] {right - CORNER - along, bottom - inset};
                    default -> new double[] {left + inset, bottom - CORNER - along};
                };
            }
            along -= straights[side];
            if (along < arc)
            {
                // Quarter turn round this corner's centre, starting where the straight run ended
                final double angle = -Math.PI / 2 + side * Math.PI / 2 + along / radius;
                return new double[] {centres[side * 2] + radius * Math.cos(angle), centres[side * 2 + 1] + radius * Math.sin(angle)};
            }
            along -= arc;
        }
        return new double[] {left + CORNER, top + inset};
    }

    static int mix(final int from, final int to, final float amount)
    {
        final int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        final int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        final int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return r << 16 | g << 8 | b;
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        renderNav(graphics, mouseX, mouseY);

        final int x = x0 + navW() + 7;
        final int width = w - navW() - 16;
        final int top = y0 + HDR + 7;
        final int bottom = y0 + h - 5;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        viewTop = top;
        viewBottom = bottom;
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;
        if (notice != null && Util.getMillis() < noticeUntil)
        {
            y += Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "Pas encore", notice);
            y += 20;
        }
        y = switch (page)
        {
            case OVERVIEW -> overviewPage.renderOverview(graphics, mouseX, my, x, y, width);
            case BUILDINGS -> buildingsPage.renderBuildings(graphics, mouseX, my, x, y, width);
            case CITIZENS -> citizensPage.renderCitizens(graphics, mouseX, my, x, y, width);
            case INTENDANCE -> intendancePage.renderIntendance(graphics, mouseX, my, x, y, width);
            case WALLS -> wallsPage.renderWalls(graphics, mouseX, my, x, y, width);
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
            graphics.renderTooltip(font, font.split(Component.literal(hint), 220), mouseX, mouseY);
        }
    }

    void addHit(final int x, int y, final int width, final int height, final Runnable action)
    {
        final int top = Math.max(y, y0 + HDR + 7);
        final int bottom = Math.min(y + height, y0 + h - 5);
        if (bottom > top)
        {
            hits.add(new Hit(x, top, width, bottom - top, action));
        }
    }

    // --- Navigation ---

    record Tool(String label, Icon icon, boolean locked, Runnable action)
    {
    }

    List<Tool> tools()
    {
        final BuildingTownHall.View view = (BuildingTownHall.View) townHall;
        return List.of(
          new Tool("Carte", Icon.of(Items.FILLED_MAP), townHall.getBuildingLevel() < ColonyMaps.UNLOCK_LEVEL, this::openMap),
          new Tool("Statistiques", Icon.module("stats"), false, () -> StatsScreen.openColony(townHall, this)),
          new Tool("Réglages", Icon.module("settings"), false, () -> SettingsScreen.open(townHall, view.getModuleView(BuildingModules.TOWNHALL_SETTINGS), this)),
          new Tool("Permissions", Icon.module("scepter"), false, () -> PermissionsScreen.open(view, this)),
          new Tool("Alliances", Icon.of(Items.WHITE_BANNER), false, () -> AlliancesScreen.open(view, this)),
          new Tool("Apparence", Icon.module("domum"), false, () -> AppearanceScreen.open(view, this)));
    }

    // --- Pages of the colony shown in the hub's frame (permissions, settings…): they keep its header and left column ---

    /**
     * Lays the hub out for this screen size, as the page showing in its frame is.
     */
    void sync(final Minecraft minecraft, final int width, final int height)
    {
        init(minecraft, width, height);
    }

    int frameX()
    {
        return x0;
    }

    int frameY()
    {
        return y0;
    }

    int frameW()
    {
        return w;
    }

    int frameH()
    {
        return h;
    }

    int navW()
    {
        return Ui.navWidth(w);
    }

    static int headerHeight()
    {
        return HDR;
    }

    /**
     * The hub's frame and left column around a page, that tool lit.
     */
    void chrome(final GuiGraphics graphics, final int mouseX, final int mouseY, final String tool)
    {
        hits.clear();
        hint = null;
        litTool = tool;
        renderFrame(graphics, mouseX, mouseY);
        renderNav(graphics, mouseX, mouseY);
        litTool = null;
    }

    /**
     * A click in the hub's frame or left column around a page.
     */
    String chromeHint()
    {
        return hint;
    }

    boolean chromeClicked(final double mouseX, final double mouseY)
    {
        return clicked(mouseX, mouseY);
    }

    /**
     * The colony map, drawn by the server from the terrain: no filled map to put in the chest any more.
     */
    void openMap()
    {
        final String why = openMap(townHall);
        if (why != null)
        {
            notice(why);
        }
    }

    /**
     * Opens the colony map, from the hub or any building; returns why it cannot open, or null when it did.
     */
    @Nullable
    public static String openMap(final ITownHallView townHall)
    {
        if (townHall.getBuildingLevel() < ColonyMaps.UNLOCK_LEVEL)
        {
            return "la carte se débloque au niveau " + ColonyMaps.UNLOCK_LEVEL + " de l'hôtel de ville. Améliorez-le pour la voir.";
        }
        if (townHall.getMapDataList().isEmpty())
        {
            return "la carte se dessine encore, revenez dans un instant.";
        }
        final WindowColonyMap map = new WindowColonyMap(true, townHall);
        map.findPaneByID("inventory").hide();
        map.findPaneByID("warning").hide();
        map.open();
        return null;
    }

    void notice(final String text)
    {
        notice = text;
        noticeUntil = Util.getMillis() + 6000;
        scroll = 0;
    }

    /**
     * The left column: the colony's pages on top, its tools at the bottom. When both do not fit the window's height, the
     * tools become a grid of icons right under the pages; if even that is too tall, the column scrolls by whole rows.
     */
    void renderNav(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int x = x0 + 5;
        final int width = navW() - 8;
        graphics.fill(x0 + navW(), y0 + HDR + 5, x0 + navW() + 1, y0 + h - 4, Palette.RULE);
        final int top = y0 + HDR + 7;
        final int bottom = y0 + h - 6;
        final int room = bottom - top;
        final int step = Ui.NAV_ROW + 1;
        final List<Tool> tools = tools();

        final int pagesH = Page.values().length * step;
        final int perRow = Math.max(1, (width + 1) / step);
        final boolean grid = !Ui.rail(navW()) && pagesH + SEPARATOR + tools.size() * step > room;
        final int toolsH = (grid ? (tools.size() + perRow - 1) / perRow : tools.size()) * step;
        final int total = pagesH + SEPARATOR + toolsH;
        final int maxScroll = Math.max(0, total - room);
        navScroll = Math.max(0, Math.min(navScroll, (maxScroll + step - 1) / step * step));

        int y = top - navScroll;
        for (final Page p : Page.values())
        {
            if (y >= top && y + Ui.NAV_ROW <= bottom)
            {
                final boolean lit = litTool == null && p == page;
                Ui.navItem(graphics, font, x, y, width, p.icon, p.label, lit, !lit && Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW));
                hits.add(new Hit(x, y, width, Ui.NAV_ROW, () -> {
                    show(p);
                    if (minecraft.screen != this)
                    {
                        minecraft.setScreen(this);
                    }
                }));
            }
            y += step;
        }

        // Anchored at the bottom when everything fits, right under the pages otherwise
        y = maxScroll == 0 ? bottom - toolsH : y + SEPARATOR;
        if (y - 3 >= top && y - 2 <= bottom)
        {
            graphics.fill(x + 2, y - 3, x + width - 2, y - 2, Palette.RULE);
        }
        for (int i = 0; i < tools.size(); i++)
        {
            final Tool tool = tools.get(i);
            final int tx = grid ? x + (i % perRow) * step : x;
            final int ty = grid ? y + (i / perRow) * step : y + i * step;
            final int tw = grid ? Ui.NAV_ROW : width;
            if (ty < top || ty + Ui.NAV_ROW > bottom)
            {
                continue;
            }
            final boolean lit = tool.label().equals(litTool);
            final boolean over = !lit && Ui.inside(mouseX, mouseY, tx, ty, tw, Ui.NAV_ROW);
            // A narrow item (grid cell or rail) shows the icon alone and names itself on hover
            Ui.navItem(graphics, font, tx, ty, tw, tool.icon(), tool.label(), lit, over);
            if (tool.locked())
            {
                final String lock = "niv. " + ColonyMaps.UNLOCK_LEVEL;
                if (tw >= 40)
                {
                    graphics.drawString(font, lock, tx + tw - 3 - font.width(lock), ty + 5, Palette.MUTED, false);
                }
                if (over)
                {
                    hint = "La carte se débloque au niveau " + ColonyMaps.UNLOCK_LEVEL + " de l'hôtel de ville";
                }
            }
            hits.add(new Hit(tx, ty, tw, Ui.NAV_ROW, tool.action()));
        }

        if (maxScroll > 0)
        {
            // A thin bar on the column's edge says there is more above or below
            final int thumb = Math.max(10, room * room / total);
            final int thumbY = top + (room - thumb) * Math.min(navScroll, maxScroll) / maxScroll;
            graphics.fill(x0 + navW() - 2, top, x0 + navW() - 1, bottom, Palette.PAPER_DARK);
            graphics.fill(x0 + navW() - 2, thumbY, x0 + navW() - 1, thumbY + thumb, Palette.RULE);
        }
    }

    /**
     * The wheel over the left column scrolls it, by whole rows; used by the pages shown in the hub's frame too.
     */
    boolean navScrolled(final double mouseX, final double mouseY, final double amount)
    {
        if (mouseX < x0 || mouseX >= x0 + navW() || mouseY < y0 + HDR || mouseY >= y0 + h)
        {
            return false;
        }
        navScroll = Math.max(0, navScroll - (int) Math.signum(amount) * (Ui.NAV_ROW + 1));
        return true;
    }

    /**
     * A page still to come: what it will hold, in a sentence.
     */
    int renderSoon(final GuiGraphics graphics, final int x, int y, final int width, final String title, final String text)
    {
        y += Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, title, "en préparation");
        return Ui.wrapped(graphics, font, Component.literal(text), x + 2, y + 20, width - 4, Palette.MUTED, 10) + 4;
    }

    /**
     * Reopens the hub on its « Murailles » page, coming back from the planner.
     */
    public static void openWalls(final IBuildingView townHall)
    {
        if (townHall instanceof ITownHallView view)
        {
            final ColonyHubScreen screen = new ColonyHubScreen(view);
            screen.page = Page.WALLS;
            Minecraft.getInstance().setScreen(screen);
        }
    }

    void show(final Page target)
    {
        page = target;
        scroll = 0;
    }

    void seatButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final String label,
      final Ui.ButtonStyle style, final boolean enabled, final Runnable action)
    {
        Ui.button(graphics, font, x, y, width, Ui.BUTTON_H, fit(label, width - 6), style, enabled, Ui.inside(mouseX, mouseY, x, y, width, Ui.BUTTON_H));
        if (enabled)
        {
            addHit(x, y, width, Ui.BUTTON_H, action);
        }
    }

    BuildingState stateOf(final IBuildingView building)
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

    // --- Requests ---

    List<IRequest<?>> requestsOf(final IBuildingView building)
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

    List<OpenRequest> requests()
    {
        final List<OpenRequest> all = new ArrayList<>();
        for (final IBuildingView building : colony.getBuildings())
        {
            requestsOf(building).forEach(r -> all.add(new OpenRequest(r, building)));
        }
        return all;
    }

    // --- Shared ---

    static String format(final double value)
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
        if (navScrolled(mouseX, mouseY, scrollY))
        {
            return true;
        }
        scroll -= (int) (scrollY * 12);
        return true;
    }
}
