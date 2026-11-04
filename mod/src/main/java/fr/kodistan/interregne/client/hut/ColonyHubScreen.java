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
import fr.kodistan.interregne.colony.IntendanceDesk;
import fr.kodistan.interregne.client.catalogue.CatalogueIndex;
import fr.kodistan.interregne.client.ClientIntendance;
import net.minecraft.ChatFormatting;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.hud.Grievances;
import fr.kodistan.interregne.client.hud.InterregneHud;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.IntendanceActionPayload;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.client.CrateRows;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.colony.ColonyMaps;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
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

    /**
     * The seat's header: a leather band holding the colony's banner, its name and the town hall's level.
     */
    private static final int HDR = 38;
    private static final int SEPARATOR = 6;
    private static final int PRICE_ICON = 12;
    /**
     * One row of an Intendance list: two lines of text and an item icon, with air above and below.
     */
    private static final int ROW_H = 24;

    /**
     * The rune band around the window, in the town hall's level colour.
     */
    private static final int AURA = 11;
    private static final int CORNER = 16;
    private static final String RUNES = "abcdefghijklmnopqrstuvwxyz";
    private static final ResourceLocation RUNE_FONT = ResourceLocation.withDefaultNamespace("alt");

    /**
     * Set just before opening MineColonies' own town hall page on purpose (« Apparence »), so it is not replaced.
     */
    private static boolean openOriginal;

    private enum Page
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
    /**
     * The tool whose page shows in place of the hub's own (« Permissions »…), lit in the left column; null on the hub.
     */
    @Nullable
    private String litTool;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    private String hint;
    @Nullable
    private String notice;
    private long noticeUntil;
    private int scroll;
    private int navScroll;

    /**
     * Which detailed list of the Intendance is unfolded: none, the grievances or the supplies.
     */
    private int intendanceList;
    /**
     * Set when a list was just unfolded: the next frame scrolls so it shows, since it opens under the card and may
     * land below the window.
     */
    private boolean revealList;
    private int viewTop;
    private int viewBottom;
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
    private void levelAura(final GuiGraphics graphics)
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
    private static int shade(final int dark, final int fill, final int light, final double depth)
    {
        final double bulge = Math.sin(Math.PI * Math.max(0, Math.min(1, depth)));
        return bulge < 0.5 ? mix(dark, fill, (float) (bulge * 2)) : mix(fill, light, (float) ((bulge - 0.5) * 2));
    }

    /**
     * A point on the band's middle line, this far along it clockwise from the top left corner's end.
     */
    private static double[] runePoint(double along, final int left, final int top, final int right, final int bottom, final double across,
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

    private static int mix(final int from, final int to, final float amount)
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
            case OVERVIEW -> renderOverview(graphics, mouseX, my, x, y, width);
            case BUILDINGS -> renderBuildings(graphics, mouseX, my, x, y, width);
            case CITIZENS -> renderCitizens(graphics, mouseX, my, x, y, width);
            case INTENDANCE -> renderIntendance(graphics, mouseX, my, x, y, width);
            case WALLS -> renderWalls(graphics, mouseX, my, x, y, width);
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

    private void addHit(final int x, int y, final int width, final int height, final Runnable action)
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
    private void openMap()
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

    private void notice(final String text)
    {
        notice = text;
        noticeUntil = Util.getMillis() + 6000;
        scroll = 0;
    }

    /**
     * The left column: the colony's pages on top, its tools at the bottom. When both do not fit the window's height, the
     * tools become a grid of icons right under the pages; if even that is too tall, the column scrolls by whole rows.
     */
    private void renderNav(final GuiGraphics graphics, final int mouseX, final int mouseY)
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
    private int renderSoon(final GuiGraphics graphics, final int x, int y, final int width, final String title, final String text)
    {
        y += Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, title, "en préparation");
        return Ui.wrapped(graphics, font, Component.literal(text), x + 2, y + 20, width - 4, Palette.MUTED, 10) + 4;
    }

    /**
     * The walls: none yet, and the button that opens the aerial planner.
     */
    private int renderWalls(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final fr.kodistan.interregne.network.WallSyncPayload wall = fr.kodistan.interregne.client.walls.ClientWalls.of(colony);
        if (wall != null)
        {
            final String look = fr.kodistan.interregne.walls.WallLevels.of(wall.level()).name();
            y += Ui.banner(graphics, font, x, y, width, wall.building() ? Ui.State.WAIT : Ui.State.OK, "Muraille",
              wall.building() ? "en construction" : "niveau " + wall.level() + " · " + look);
            final int towers = wall.points().size() / 2;
            final int gates = wall.gates().size() / 2;
            return Ui.wrapped(graphics, font, Component.literal((wall.closed() ? "Enceinte fermée" : "Muraille ouverte") + ", " + towers + " tours, "
              + gates + (gates > 1 ? " portes. " : " porte. ") + "Améliorations et retouches du tracé arrivent à la prochaine étape."), x + 2, y + 20, width - 4, Palette.MUTED, 10) + 4;
        }
        y += Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "Murailles", "aucune muraille");
        y = Ui.wrapped(graphics, font, Component.literal("Tracez l'enceinte de la colonie vue du ciel : posez des points au sol, la muraille "
          + "suit le relief entre eux. Elle peut rester ouverte, une tour se dresse sur chaque point, et il lui faut au moins une porte (la première est offerte)."), x + 2, y + 20, width - 4, Palette.MUTED, 10) + 6;
        final int buttonW = Math.min(width, Math.max(140, font.width("Tracer la muraille") + 24));
        seatButton(graphics, mouseX, mouseY, x, y, buttonW, "Tracer la muraille", Ui.ButtonStyle.PRIMARY, true,
          () -> minecraft.setScreen(new fr.kodistan.interregne.client.walls.WallPlannerScreen(townHall)));
        return y + Ui.BUTTON_H + 6;
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
        // What the Intendance would settle, as its page lists it
        final IntendanceQuotePayload intendance = ClientIntendance.of(colony.getID());
        final int requests = intendance == null ? -1 : intendance.care().size() + intendance.supplies().size();

        if (blocked > 0)
        {
            y += Ui.banner(graphics, font, x, y, width, Ui.State.BAD, "À régler", blocked + (blocked == 1 ? " bâtiment bloqué" : " bâtiments bloqués"));
            addHit(x, y, width, 14, () -> show(Page.BUILDINGS));
        }
        else if (troubled > 0)
        {
            y += Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "À surveiller", troubled + (troubled == 1 ? " citoyen a" : " citoyens ont") + " des doléances");
            addHit(x, y, width, 14, () -> show(Page.CITIZENS));
        }
        else
        {
            y += Ui.banner(graphics, font, x, y, width, Ui.State.OK, "Tout va bien", "aucun bâtiment bloqué, aucune doléance");
        }
        y += 20;
        y = renderSeat(graphics, mouseX, mouseY, x, y, width) + 2;

        // Tiles: the colony's vital signs, each opening the page that explains it
        // Four tiles across, two when the page is narrow
        final int columns = width >= 360 ? 4 : 2;
        final int tileW = (width - (columns - 1) * 4) / columns;
        final double happiness = colony.getOverallHappiness();
        y = tileRow(graphics, mouseX, mouseY, x, y, tileW, columns, List.of(
          new Tile("Citoyens", citizens.size() + " / " + colony.getCitizenCountLimit(), Icon.module("workers"), Ui.State.OK, () -> show(Page.CITIZENS)),
          new Tile("Bonheur", format(happiness) + " / 10", Icon.module("happiness"), happiness < 4 ? Ui.State.BAD : happiness < 7 ? Ui.State.WAIT : Ui.State.OK,
            () -> show(Page.CITIZENS)),
          new Tile("Doléances", Integer.toString(grievances), Icon.of(Items.WRITABLE_BOOK), grievances > 0 ? Ui.State.WAIT : Ui.State.OK, () -> show(Page.CITIZENS)),
          new Tile("Intendance", requests < 0 ? "…" : Integer.toString(requests), Icon.of(Items.GOLD_INGOT), requests > 0 ? Ui.State.WAIT : Ui.State.OK, () -> show(Page.INTENDANCE))));
        y = tileRow(graphics, mouseX, mouseY, x, y + 4, tileW, columns, List.of(
          new Tile("Bâtiments", Integer.toString(buildings.size()), Icon.of(Items.BRICKS), blocked > 0 ? Ui.State.BAD : Ui.State.OK, () -> show(Page.BUILDINGS)),
          new Tile("Chantiers", Integer.toString(sites), Icon.of(Items.IRON_PICKAXE), Ui.State.OK, () -> show(Page.BUILDINGS)),
          mercenaries()));

        return y;
    }

    /**
     * The mercenaries' tile: ready, or resting for so many minutes.
     */
    private Tile mercenaries()
    {
        final long used = colony.getMercenaryUseTime();
        final long wait = used == 0 ? 0 : Math.max(0, com.minecolonies.api.util.constant.Constants.TICKS_FOURTY_MIN - (colony.getWorld().getGameTime() - used));
        return new Tile("Mercenaires", wait == 0 ? "Prêts" : (wait / 1200 + 1) + " min", Icon.of(Items.IRON_SWORD), wait == 0 ? Ui.State.OK : Ui.State.WAIT,
          () -> MercenaryScreen.open(townHall, this));
    }

    /**
     * The town hall's next level, as a hut shows it but laid out across the page: what it unlocks, what it costs and
     * the buttons; or its site while one is running.
     */
    private int renderSeat(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int level = townHall.getBuildingLevel();
        final boolean site = townHall.isBuilding() || townHall.isRepairing() || townHall.isDeconstructing();
        final boolean max = level >= townHall.getBuildingMaxLevel();
        final int tint = townHall.isBuilding() || !site && !max ? level + 1 : level;
        final UpgradeOffer offer = site || max ? null : UpgradeOffer.of(townHall);
        final int band = 18;
        final int pad = 8;
        // Three columns side by side when there is room, one under the other otherwise
        final boolean wide = width >= 380;
        final int gap = 12;
        final int buttonW = wide ? 112 : (width - 2 * pad - 4) / 2;
        final int textW = wide ? (width - 2 * pad - 2 * gap - buttonW) / 3 : width - 2 * pad;
        final String unlock = offer == null ? "" : "+ Bâtiments jusqu'au niveau " + offer.target();
        final boolean map = offer != null && offer.target() == ColonyMaps.UNLOCK_LEVEL;
        final int unlockH = offer == null ? 0 : (font.split(Component.literal(unlock), textW).size() + (map ? 1 : 0)) * 10;
        final int priceH = offer == null ? 0 : 11 * (1 + offer.crates().size());
        final int height;
        if (site || offer == null)
        {
            height = 84;
        }
        else if (wide)
        {
            height = Math.max(84, band + pad + 13 + Math.max(unlockH, priceH) + pad);
        }
        else
        {
            height = band + pad + 13 + unlockH + 6 + 13 + priceH + 6 + Ui.BUTTON_H + pad;
        }

        graphics.fill(x, y, x + width, y + height, Palette.levelSoft(tint));
        graphics.renderOutline(x, y, width, height, Palette.levelEdge(tint));
        Ui.plate(graphics, x, y, x + width, y + band, Palette.levelFill(tint), Palette.levelEdge(tint), Palette.levelFill(tint));
        Icon.of(townHall.getBuildingType().getBuildingBlock()).draw(graphics, x + 5, y + 1, 16);
        final String heading = townHall.isDeconstructing() ? "Démolition en cours"
                                 : townHall.isRepairing() ? "Réparation en cours"
                                     : townHall.isBuilding() ? "Amélioration en cours"
                                         : max ? "Niveau maximum" : "Passer au niveau " + (level + 1);
        final String open = width >= 300 ? "Voir l'hôtel de ville ›" : "Voir ›";
        final int openX = x + width - font.width(open) - 8;
        graphics.drawString(font, Component.literal(fit(heading, openX - x - 33)).withStyle(ChatFormatting.BOLD), x + 25, y + 5, Palette.levelText(tint), false);
        final boolean overOpen = Ui.inside(mouseX, mouseY, openX - 2, y, font.width(open) + 4, band);
        graphics.drawString(font, Component.literal(open).withStyle(overOpen ? ChatFormatting.UNDERLINE : ChatFormatting.RESET), openX, y + 5,
          Palette.levelText(tint), false);
        addHit(openX - 2, y, font.width(open) + 4, band, () -> HutScreen.openWith(townHall, this));

        final int bodyY = y + band + pad;
        final int c1 = x + pad;
        final int buttonY = y + height - pad - Ui.BUTTON_H;
        final int ruleColor = Palette.levelEdge(tint) & 0x00FFFFFF | 0x50000000;

        if (site)
        {
            Ui.label(graphics, font, "AVANCEMENT", c1, bodyY);
            final Float progress = UpgradeOffer.siteProgress(townHall, "Hôtel de ville");
            final int barW = width - 2 * pad;
            if (progress == null)
            {
                Ui.wrapped(graphics, font, Component.literal("Le chantier avance. Approchez-vous pour suivre sa barre."), c1, bodyY + 14, barW, Palette.MUTED, 10);
            }
            else
            {
                if (townHall.isDeconstructing())
                {
                    Ui.progressBar(graphics, c1, bodyY + 15, barW, 8, progress, Ui.State.BAD);
                }
                else
                {
                    Ui.progressBar(graphics, c1, bodyY + 15, barW, 8, progress, Palette.levelFill(tint), Palette.levelEdge(tint));
                }
                final int seconds = townHall.isBuilding() ? BuildCosts.secondsForLevel(level + 1) : BuildCosts.secondsForFlat();
                final int left = Math.max(1, Math.round((1 - progress) * seconds));
                graphics.drawString(font, fit(Math.round(progress * 100) + " % · encore " + BuildCosts.formatSeconds(left) + " environ", barW), c1, bodyY + 30,
                  Palette.INK, false);
            }
            return y + height + 6;
        }

        if (offer == null)
        {
            final int bw = wide ? buttonW : width - 2 * pad;
            final int bx = x + width - pad - bw;
            Ui.wrapped(graphics, font, Component.literal("L'hôtel de ville est au plus haut. Tous les bâtiments peuvent monter jusqu'au niveau " + level + "."),
              c1, bodyY, wide ? bx - c1 - gap : width - 2 * pad, Palette.MUTED, 10);
            seatButton(graphics, mouseX, mouseY, bx, buttonY, bw, "Réparer, démolir…", Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
            return y + height + 6;
        }

        // What the next level opens, what it costs, then the buttons
        final int c2 = wide ? c1 + textW + gap : c1;
        final int priceW = wide ? x + width - pad - buttonW - gap - c2 : width - 2 * pad;
        Ui.label(graphics, font, "DÉBLOQUE", c1, bodyY);
        int ly = Ui.wrapped(graphics, font, Component.literal(unlock), c1, bodyY + 13, textW, Palette.GREEN, 10);
        if (map)
        {
            ly = Ui.wrapped(graphics, font, Component.literal("+ Carte de la colonie"), c1, ly, textW, Palette.GREEN, 10);
        }
        final int priceY = wide ? bodyY : ly + 6;
        if (wide)
        {
            graphics.fill(c2 - gap / 2, bodyY, c2 - gap / 2 + 1, y + height - pad, ruleColor);
        }
        Ui.label(graphics, font, "PRIX", c2, priceY);
        int py = Ui.priceLine(graphics, font, c2, priceY + 13, priceW, offer.price(), offer.affordable());
        for (final CrateRows.Row row : offer.crates())
        {
            py = Ui.crateLine(graphics, font, c2, py, priceW, row);
        }

        final int c3 = x + width - pad - buttonW;
        if (wide)
        {
            graphics.fill(c3 - gap / 2, bodyY, c3 - gap / 2 + 1, y + height - pad, ruleColor);
            seatButton(graphics, mouseX, mouseY, c3, buttonY - Ui.BUTTON_H - 5, buttonW, "Réparer, démolir…", Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
        }
        else
        {
            seatButton(graphics, mouseX, mouseY, c1, buttonY, buttonW, "Réparer, démolir…", Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
        }
        // Green when it can be done now, grey with the reason otherwise
        seatButton(graphics, mouseX, mouseY, c3, buttonY, buttonW, offer.action(font, buttonW - 6), Ui.ButtonStyle.PRIMARY, offer.canBuy(), () ->
        {
            offer.buy(townHall);
            onClose();
        });
        return y + height + 6;
    }

    private void seatButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final String label,
      final Ui.ButtonStyle style, final boolean enabled, final Runnable action)
    {
        Ui.button(graphics, font, x, y, width, Ui.BUTTON_H, fit(label, width - 6), style, enabled, Ui.inside(mouseX, mouseY, x, y, width, Ui.BUTTON_H));
        if (enabled)
        {
            addHit(x, y, width, Ui.BUTTON_H, action);
        }
    }

    private void openBuildWindow()
    {
        new WindowBuildBuilding(colony, townHall).open();
    }

    private record Tile(String label, String value, Icon icon, Ui.State state, Runnable action)
    {
    }

    private int tileRow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int tileW, final int columns,
      final List<Tile> tiles)
    {
        for (int i = 0; i < tiles.size(); i++)
        {
            final Tile tile = tiles.get(i);
            final int tx = x + (i % columns) * (tileW + 4);
            final int ty = y + (i / columns) * 34;
            final boolean over = Ui.inside(mouseX, mouseY, tx, ty, tileW, 30);
            Ui.card(graphics, tx, ty, tileW, 30, false, over, false);
            graphics.fill(tx + 1, ty + 1, tx + 3, ty + 29, switch (tile.state())
            {
                case OK -> Palette.GREEN;
                case WAIT -> Palette.AMBER;
                case BAD -> Palette.RED;
            });
            tile.icon().draw(graphics, tx + 6, ty + 4, 12);
            graphics.drawString(font, fit(tile.label(), tileW - 24), tx + 21, ty + 5, Palette.MUTED, false);
            graphics.drawString(font, Component.literal(fit(tile.value(), tileW - 10)).withStyle(ChatFormatting.BOLD), tx + 7, ty + 18, Palette.INK, false);
            addHit(tx, ty, tileW, 30, tile.action());
        }
        return y + ((tiles.size() + columns - 1) / columns) * 34 - 4;
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
            // Some hut models stand taller than a block: keep them inside their row
            graphics.enableScissor(x + 1, y + 1, x + 21, y + 19);
            Icon.of(building.getBuildingType().getBuildingBlock()).draw(graphics, x + 3, y + 2, 16);
            graphics.disableScissor();
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
    private void levelSquares(final GuiGraphics graphics, int x, int y, final int level, final int max)
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

            // Chips right-aligned; a click on one settles the problem, elsewhere opens the sheet
            int chipX = x + width - 12;
            final List<CitizenBits.Chip> list = chips.get(citizen);
            int chipsW = 0;
            for (final CitizenBits.Chip chip : list)
            {
                chipsW += font.width(chip.text()) + 8 + 3;
            }
            // Name and job keep clear of the chips
            final int textW = width - 12 - chipsW - 23 - 8;
            final String name = citizen.getName();
            final String shownName = font.width(Component.literal(name).withStyle(ChatFormatting.BOLD)) <= textW ? name : fit(name, textW - 6);
            graphics.drawString(font, Component.literal(shownName).withStyle(ChatFormatting.BOLD), x + 23, y + 3, Palette.INK, false);
            graphics.drawString(font, fit(job, textW), x + 23, y + 12, Palette.MUTED, false);
            for (int i = list.size() - 1; i >= 0; i--)
            {
                final CitizenBits.Chip chip = list.get(i);
                final int chipW = font.width(chip.text()) + 8;
                chipX -= chipW;
                final boolean overChip = chip.action() != null && Ui.inside(mouseX, mouseY, chipX, y + 6, chipW, 11);
                if (Ui.inside(mouseX, mouseY, chipX, y + 6, chipW, 11) && chip.hover() != null)
                {
                    hint = chip.hover();
                }
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

    /**
     * The Intendance: two cards, what it would do for the citizens and what it would deliver to the workers, each
     * showing its price first and settled in one click. Rows come from the server and are exactly what it would settle:
     * what the colony already handles never shows here.
     */
    private int renderIntendance(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IntendanceQuotePayload quote = ClientIntendance.of(colony.getID());
        y = Ui.wrapped(graphics, font, Component.literal("Construire le bon bâtiment revient toujours moins cher que de payer l'Intendance."),
          x, y, width, Palette.MUTED, 10) + 6;

        final List<IntendanceQuotePayload.Care> care = quote == null ? List.of() : quote.care();
        final long sick = care.stream().filter(IntendanceQuotePayload.Care::sick).count();
        final long hungry = care.size() - sick;
        final List<String> careParts = new ArrayList<>();
        if (sick > 0)
        {
            careParts.add(sick + (sick == 1 ? " malade à soigner" : " malades à soigner"));
        }
        if (hungry > 0)
        {
            careParts.add(hungry + (hungry == 1 ? " affamé à nourrir" : " affamés à nourrir"));
        }
        final int careTop = y;
        final String careText = quote == null ? "…" : care.isEmpty() ? "Aucun malade ni affamé." : String.join(", ", careParts) + ".";
        y = desk(graphics, mouseX, mouseY, x, y, width, Icon.of(Items.WRITABLE_BOOK), "DOLÉANCES", careText, quote == null ? -1 : quote.careGold(),
          quote == null ? 0 : quote.careInfluence(), !care.isEmpty(), 1, IntendanceActionPayload.SETTLE_GRIEVANCES);
        if (intendanceList == 1)
        {
            y = renderCareRows(graphics, mouseX, mouseY, x, y, width, care) + 4;
            reveal(careTop, y);
        }
        y += 6;

        final List<IntendanceQuotePayload.Supply> supplies = quote == null ? List.of() : quote.supplies();
        final long rare = supplies.stream().filter(IntendanceQuotePayload.Supply::rare).count();
        String supplyText = quote == null ? "…" : supplies.isEmpty() ? "Aucune fourniture ne manque." : supplies.size() + (supplies.size() == 1 ? " fourniture manque." : " fournitures manquent.");
        if (rare > 0)
        {
            supplyText += rare == supplies.size() ? (rare == 1 ? " Objet rare : à fournir toi-même." : " Objets rares : à fournir toi-même.")
                            : " Dont " + rare + (rare == 1 ? " rare" : " rares") + ", à fournir toi-même.";
        }
        final int supplyTop = y;
        y = desk(graphics, mouseX, mouseY, x, y, width, Icon.module("requests"), "FOURNITURES DES TRAVAILLEURS", supplyText, quote == null ? -1 : quote.supplyGold(),
          quote == null ? 0 : quote.supplyInfluence(), !supplies.isEmpty(), 2, IntendanceActionPayload.SETTLE_SUPPLIES);
        if (intendanceList == 2)
        {
            y = renderSupplyRows(graphics, mouseX, mouseY, x, y, width, supplies) + 4;
            reveal(supplyTop, y);
        }
        return y;
    }

    /**
     * Once, after a list was unfolded: scroll just enough for the list's end to show, never past its card's top.
     */
    private void reveal(final int cardTop, final int listEnd)
    {
        if (revealList)
        {
            revealList = false;
            scroll += Math.max(0, Math.min(listEnd - viewBottom, cardTop - viewTop));
        }
    }

    /**
     * One card of the Intendance: title, one sentence, then the big settle button carrying its own price (coin and
     * figure, star and figure). Green when it can be paid, red when it cannot (the hover says what is missing), grey
     * when there is nothing to settle. The list button sits beside it when wide, under it when narrow.
     */
    private int desk(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final Icon icon, final String title,
      final String text, final long gold, final long influence, final boolean hasList, final int list, final int action)
    {
        final int pad = 6;
        final int inner = width - 2 * pad;
        final boolean wide = width >= 300;
        final List<FormattedCharSequence> lines = font.split(Component.literal(text), inner);
        final int buttonsH = wide ? 22 : 22 + 4 + 18;
        final int height = 24 + lines.size() * 10 + 4 + buttonsH + pad;

        Ui.card(graphics, x, y, width, height, false, false, false);
        icon.draw(graphics, x + pad, y + 5, 16);
        Ui.label(graphics, font, fit(title, inner - 22), x + pad + 22, y + 9);
        int ty = y + 24;
        for (final FormattedCharSequence line : lines)
        {
            graphics.drawString(font, line, x + pad, ty, Palette.INK, false);
            ty += 10;
        }
        ty += 4;

        final long money = money();
        final long ownInfluence = InterregneHud.influence();
        final boolean priced = gold > 0;
        final List<String> lacks = new ArrayList<>();
        if (priced && money < gold)
        {
            lacks.add(Wallet.format(gold - money) + " Couronnes (trésor de la colonie et ta bourse réunis)");
        }
        if (priced && ownInfluence < influence)
        {
            lacks.add((influence - ownInfluence) + " Influence");
        }
        final String missing = lacks.isEmpty() ? null : "Il te manque " + String.join(" et ", lacks);

        final int listW = font.width("Masquer la liste") + 16;
        final int settleW = wide ? inner - listW - 6 : inner;
        final boolean over = Ui.inside(mouseX, mouseY, x + pad, ty, settleW, 22);
        if (!priced)
        {
            Ui.button(graphics, font, x + pad, ty, settleW, 22, fit(gold < 0 ? "Calcul du prix…" : "Rien à régler", settleW - 8), Ui.ButtonStyle.PRIMARY, false, over);
        }
        else
        {
            priceButton(graphics, x + pad, ty, settleW, 22, "Tout régler", gold, money, influence, ownInfluence, over);
            if (missing == null)
            {
                addHit(x + pad, ty, settleW, 22, () -> ClientIntendance.settle(colony.getID(), action));
            }
            if (over)
            {
                hint = missing != null ? missing + "." : "Payé avec le trésor de la colonie, complété par les couronnes sur toi ; l'Influence est la tienne.";
            }
        }

        final String listLabel = intendanceList == list ? "Masquer la liste" : "Voir la liste";
        final int listX = wide ? x + width - pad - listW : x + pad;
        final int listY = wide ? ty : ty + 26;
        final int listH = wide ? 22 : 18;
        final int listWidth = wide ? listW : inner;
        final boolean overList = Ui.inside(mouseX, mouseY, listX, listY, listWidth, listH);
        Ui.button(graphics, font, listX, listY, listWidth, listH, listLabel, Ui.ButtonStyle.SECONDARY, hasList, overList);
        if (hasList)
        {
            addHit(listX, listY, listWidth, listH, () -> {
                intendanceList = intendanceList == list ? 0 : list;
                revealList = intendanceList != 0;
            });
        }
        return y + height + 4;
    }

    /**
     * The settle button with its price inside, the way games show a cost: icons and figures, no unit names.
     * Payable: green, white figures. Not payable: the button turns grey like any unavailable action, and only the
     * figure that falls short turns red, written « what you have / what it costs », so the eye goes straight to what
     * is missing; a figure that is covered stays plain. When narrow, « have / » goes first, then the words shorten.
     */
    private void priceButton(final GuiGraphics graphics, final int x, int y, final int width, final int height, final String fullVerb, final long gold,
      final long money, final long influence, final long ownInfluence, final boolean hover)
    {
        final boolean goldOk = money >= gold;
        final boolean influenceOk = ownInfluence >= influence;
        final boolean payable = goldOk && influenceOk;
        if (payable)
        {
            Ui.plate(graphics, x, y, x + width, y + height, hover ? Palette.GREEN_HOVER : Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
        }
        else
        {
            Ui.plate(graphics, x, y, x + width, y + height, Palette.PAPER_DARK, Palette.EDGE, Palette.PAPER_DARK);
        }
        final int plain = payable ? Palette.GREEN_TEXT : Palette.INK;
        final int verbColor = payable ? Palette.GREEN_TEXT : Palette.MUTED;

        boolean showHave = !payable;
        String verb = fullVerb;
        List<Component> parts = priceParts(gold, money, goldOk, influence, ownInfluence, influenceOk, showHave);
        // The verb is drawn bold: measure it bold, or the button overflows by a pixel per letter
        while (font.width(Component.literal(verb).withStyle(ChatFormatting.BOLD)) + (verb.isEmpty() ? 0 : 12) + priceWidth(parts) > width - 12)
        {
            if (showHave)
            {
                showHave = false;
            }
            else if (verb.startsWith("Tout "))
            {
                verb = "Régler";
            }
            else if (!verb.isEmpty())
            {
                verb = "";
            }
            else
            {
                break;
            }
            parts = priceParts(gold, money, goldOk, influence, ownInfluence, influenceOk, showHave);
        }

        final Component verbText = Component.literal(verb).withStyle(ChatFormatting.BOLD);
        final int total = (verb.isEmpty() ? 0 : font.width(verbText) + 12) + priceWidth(parts);
        int cx = x + (width - total) / 2;
        final int textY = y + (height - 8) / 2;
        final int iconY = y + (height - PRICE_ICON) / 2;
        if (!verb.isEmpty())
        {
            graphics.drawString(font, verbText, cx, textY, verbColor, false);
            cx += font.width(verbText) + 12;
        }
        Ui.coinTight(graphics, cx, iconY, PRICE_ICON);
        cx += PRICE_ICON + 3;
        graphics.drawString(font, parts.get(0), cx, textY, goldOk ? plain : Palette.RED, false);
        cx += font.width(parts.get(0)) + 10;
        Ui.influence(graphics, cx, iconY, PRICE_ICON);
        cx += PRICE_ICON + 2;
        graphics.drawString(font, parts.get(1), cx, textY, influenceOk ? plain : Palette.RED, false);
    }

    /**
     * Each figure of the price: just the cost when covered, « have / cost » when short (if asked).
     */
    private static List<Component> priceParts(final long gold, final long money, final boolean goldOk, final long influence, final long ownInfluence,
      final boolean influenceOk, final boolean showHave)
    {
        return List.of(figure(gold, money, goldOk, showHave), figure(influence, ownInfluence, influenceOk, showHave));
    }

    private static Component figure(final long cost, final long have, final boolean ok, final boolean showHave)
    {
        final String text = !ok && showHave ? Wallet.format(have) + "/" + Wallet.format(cost) : Wallet.format(cost);
        return Component.literal(text).withStyle(ChatFormatting.BOLD);
    }

    private int priceWidth(final List<Component> parts)
    {
        return PRICE_ICON + 3 + font.width(parts.get(0)) + 10 + PRICE_ICON + 2 + font.width(parts.get(1));
    }

    /**
     * What can pay the Intendance: the colony treasury, then the Couronnes on the player.
     */
    private long money()
    {
        final long purse = fr.kodistan.interregne.client.dev.DevOverrides.purse != null ? fr.kodistan.interregne.client.dev.DevOverrides.purse
                             : minecraft.player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory()));
        return ClientTreasury.of(colony) + purse;
    }

    /**
     * A row's own pay button at its right edge, its price inside like the card's: settles that row alone.
     *
     * @return its width.
     */
    private int rowPay(final GuiGraphics graphics, final int mouseX, final int mouseY, final int right, final int y, final int width, final int cost,
      final String what, final Runnable pay)
    {
        final long influence = IntendanceDesk.influenceFor(cost);
        final long money = money();
        final long ownInfluence = InterregneHud.influence();
        final int x = right - width;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 18);
        priceButton(graphics, x, y, width, 18, "Régler", cost, money, influence, ownInfluence, over);
        final boolean payable = money >= cost && ownInfluence >= influence;
        if (payable)
        {
            addHit(x, y, width, 18, pay);
        }
        if (over)
        {
            hint = payable ? "Régler seulement " + what + "." : "Il te manque de quoi régler " + what + ".";
        }
        return width;
    }

    /**
     * One width for every pay button of a list, the widest price's, so they line up in a column.
     */
    private int payWidth(final java.util.stream.IntStream costs)
    {
        final int widest = costs.map(cost -> priceWidth(priceParts(cost, 0, true, IntendanceDesk.influenceFor(cost), 0, true, false))).max().orElse(0);
        return Math.min(font.width(Component.literal("Régler").withStyle(ChatFormatting.BOLD)) + 12 + widest + 14, 150);
    }

    private int renderCareRows(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width,
      final List<IntendanceQuotePayload.Care> care)
    {
        final int payW = payWidth(care.stream().mapToInt(IntendanceQuotePayload.Care::cost));
        for (final IntendanceQuotePayload.Care row : care)
        {
            final ICitizenDataView citizen = colony.getCitizen(row.citizen());
            final boolean over = citizen != null && Ui.inside(mouseX, mouseY, x, y, width, ROW_H);
            Ui.card(graphics, x, y, width, ROW_H, false, over, false);
            Icon.of(row.sick() ? new ItemStack(Items.POTION) : new ItemStack(Items.BREAD)).draw(graphics, x + 3, y + 4, 16);
            // Its own button first, so a click on it pays rather than opening the sheet
            final int priceW = rowPay(graphics, mouseX, mouseY, x + width - 3, y + 3, payW, row.cost(), (row.sick() ? "les soins de " : "le repas de ") + row.name(),
              () -> ClientIntendance.settle(colony.getID(), row.sick() ? IntendanceActionPayload.CURE : IntendanceActionPayload.FEED, row.citizen()));
            final int textW = width - 23 - priceW - 12;
            graphics.drawString(font, fit(row.name(), textW), x + 23, y + 4, Palette.INK, false);
            graphics.drawString(font, fit(row.sick() ? "malade, à soigner" : "affamé, à nourrir", textW), x + 23, y + 14, Palette.MUTED, false);
            if (citizen != null)
            {
                addHit(x, y, width, ROW_H, () -> CitizenScreen.open(citizen, this));
            }
            y += ROW_H + 2;
        }
        return y;
    }

    private int renderSupplyRows(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width,
      final List<IntendanceQuotePayload.Supply> supplies)
    {
        final int payW = payWidth(supplies.stream().filter(row -> !row.rare()).mapToInt(IntendanceQuotePayload.Supply::cost));
        for (final IntendanceQuotePayload.Supply row : supplies)
        {
            Ui.card(graphics, x, y, width, ROW_H, false, false, false);
            Icon.of(row.stack()).draw(graphics, x + 3, y + 4, 16);
            slots.add(new Ui.Slot(x + 3, y + 4, 16, row.stack()));
            final int rightW;
            if (row.rare())
            {
                // Never sold: the player brings it
                final String chip = "À fournir";
                rightW = font.width(chip) + 8;
                Ui.chip(graphics, font, x + width - 6 - rightW, y + 7, chip, Ui.State.BAD);
            }
            else
            {
                rightW = rowPay(graphics, mouseX, mouseY, x + width - 3, y + 3, payW, row.cost(), "cette fourniture",
                  () -> ClientIntendance.settle(colony.getID(), IntendanceActionPayload.SUPPLY, row.key()));
            }
            final int textW = width - 23 - rightW - 12;
            graphics.drawString(font, fit(row.stack().getCount() + " × " + row.stack().getHoverName().getString(), textW), x + 23, y + 4, Palette.INK, false);
            graphics.drawString(font, fit("pour " + row.forWhom().getString(), textW), x + 23, y + 14, Palette.MUTED, false);
            y += ROW_H + 2;
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
        if (navScrolled(mouseX, mouseY, scrollY))
        {
            return true;
        }
        scroll -= (int) (scrollY * 12);
        return true;
    }
}
