package fr.kodistan.interregne.client.commerce;

import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.client.walls.ClientRoads;
import fr.kodistan.interregne.commerce.MapRequestPayload;
import fr.kodistan.interregne.commerce.MapStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The kingdom map: a simplified view from above of the colonies, the trade roads and the caravans walking them right
 * now, the same for every player. Drag to move, wheel to zoom. The server refreshes it once a second while it is open.
 */
public final class KingdomMapScreen extends ParchmentScreen
{
    private static final int[] COLONY_COLORS = {0xFFA32D2D, 0xFF3A7BD5, 0xFF3B6D11, 0xFF9B4DCA, 0xFFC08A1E, 0xFF1F8A8A, 0xFF8A4B2A, 0xFF5C5C5C};
    private static final int[] SCALE_BARS = {50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000};
    private static final double MIN_SCALE = 0.01;
    private static final double MAX_SCALE = 6.0;
    private static final int STRIP = 16;

    private MapStatePayload data;
    private List<int[]> roads;
    private int roadVersion = -1;
    /** False for the UiShot preview: its fake data must not be replaced by the real server's answer. */
    private boolean live = true;
    private boolean fitted;
    private boolean dragging;
    private int ticks;

    private double centerX;
    private double centerZ;
    /** Pixels per block. */
    private double scale = 0.2;

    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;

    public KingdomMapScreen(@Nullable final Screen parent)
    {
        super(Component.literal("Carte du royaume"), parent);
        this.data = new MapStatePayload(List.of(), List.of());
        this.roads = ClientRoads.cellList();
        this.roadVersion = ClientRoads.version();
        request();
    }

    /**
     * Shows fake colonies, roads and caravans that are never refreshed (UiShot).
     */
    public static KingdomMapScreen preview(final MapStatePayload data, final List<int[]> roads)
    {
        final KingdomMapScreen screen = new KingdomMapScreen(null);
        screen.data = data;
        screen.roads = roads;
        screen.live = false;
        return screen;
    }

    /**
     * The server's answer: shown when the map is open.
     */
    public static void receive(final MapStatePayload payload)
    {
        if (Minecraft.getInstance().screen instanceof KingdomMapScreen screen && screen.live)
        {
            screen.data = payload;
        }
    }

    private void request()
    {
        if (live && Minecraft.getInstance().getConnection() != null)
        {
            PacketDistributor.sendToServer(new MapRequestPayload());
        }
    }

    @Override
    public void tick()
    {
        if (live && ++ticks % 20 == 0)
        {
            request();
        }
        if (live && roadVersion != ClientRoads.version())
        {
            roads = ClientRoads.cellList();
            roadVersion = ClientRoads.version();
        }
    }

    @Override
    protected void init()
    {
        super.init();
        mapX = x0 + 6;
        mapY = y0 + HEADER + 4;
        mapW = w - 12;
        mapH = h - HEADER - 4 - STRIP - 4;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        if (MapKey.MAPPING.matches(keyCode, scanCode))
        {
            onClose();
            return true;
        }
        if (keyCode == 268)
        {
            fitted = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ view

    private int sx(final double worldX)
    {
        return (int) Math.round(mapX + mapW / 2.0 + (worldX - centerX) * scale);
    }

    private int sy(final double worldZ)
    {
        return (int) Math.round(mapY + mapH / 2.0 + (worldZ - centerZ) * scale);
    }

    /**
     * Frames everything there is to see.
     */
    private void fit()
    {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (final MapStatePayload.Colony colony : data.colonies())
        {
            minX = Math.min(minX, colony.x());
            maxX = Math.max(maxX, colony.x());
            minZ = Math.min(minZ, colony.z());
            maxZ = Math.max(maxZ, colony.z());
        }
        for (final MapStatePayload.Caravan caravan : data.caravans())
        {
            minX = Math.min(minX, caravan.x());
            maxX = Math.max(maxX, caravan.x());
            minZ = Math.min(minZ, caravan.z());
            maxZ = Math.max(maxZ, caravan.z());
        }
        if (minX > maxX)
        {
            centerX = 0;
            centerZ = 0;
            scale = 0.2;
            return;
        }
        centerX = (minX + maxX) / 2;
        centerZ = (minZ + maxZ) / 2;
        final double spanX = Math.max(60, maxX - minX) * 1.35;
        final double spanZ = Math.max(60, maxZ - minZ) * 1.35;
        scale = Math.clamp(Math.min(mapW / spanX, mapH / spanZ), MIN_SCALE, MAX_SCALE);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (!Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH))
        {
            return false;
        }
        final double worldX = centerX + (mouseX - mapX - mapW / 2.0) / scale;
        final double worldZ = centerZ + (mouseY - mapY - mapH / 2.0) / scale;
        scale = Math.clamp(scale * Math.pow(1.25, scrollY), MIN_SCALE, MAX_SCALE);
        // The block under the cursor stays under the cursor
        centerX = worldX - (mouseX - mapX - mapW / 2.0) / scale;
        centerZ = worldZ - (mouseY - mapY - mapH / 2.0) / scale;
        fitted = true;
        return true;
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        dragging = Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH);
        return dragging;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (dragging && button == 0)
        {
            centerX -= dragX / scale;
            centerZ -= dragY / scale;
            fitted = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ drawing

    private static int colonyColor(final int id)
    {
        return COLONY_COLORS[Math.floorMod(id, COLONY_COLORS.length)];
    }

    /** Text already placed this frame: a label that would land on one is left out. */
    private final List<int[]> placed = new ArrayList<>();

    private boolean place(final int x, final int y, final int width)
    {
        final int[] rect = {x - 1, y - 1, x + width + 1, y + 10};
        if (rect[0] < mapX + 1 || rect[2] > mapX + mapW - 1 || rect[1] < mapY + 1 || rect[3] > mapY + mapH - 1)
        {
            return false;
        }
        for (final int[] other : placed)
        {
            if (rect[0] < other[2] && rect[2] > other[0] && rect[1] < other[3] && rect[3] > other[1])
            {
                return false;
            }
        }
        placed.add(rect);
        return true;
    }

    private void label(final GuiGraphics graphics, final String text, final int markerX, final int markerY, final int color)
    {
        final int width = font.width(text);
        // Right of the marker, else left of it
        if (place(markerX + 7, markerY - 4, width))
        {
            graphics.drawString(font, text, markerX + 7, markerY - 4, color, false);
        }
        else if (place(markerX - 7 - width, markerY - 4, width))
        {
            graphics.drawString(font, text, markerX - 7 - width, markerY - 4, color, false);
        }
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        if (!fitted && (!data.colonies().isEmpty() || !data.caravans().isEmpty()))
        {
            fit();
            fitted = true;
        }
        placed.clear();
        graphics.fill(mapX - 1, mapY - 1, mapX + mapW + 1, mapY + mapH + 1, Palette.EDGE);
        graphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, Palette.PAPER_DARK);
        graphics.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        drawGrid(graphics);
        drawRoads(graphics);
        final MapStatePayload.Colony hotColony = drawColonies(graphics, mouseX, mouseY);
        final MapStatePayload.Caravan hotCaravan = drawCaravans(graphics, mouseX, mouseY);
        graphics.disableScissor();
        drawScaleBar(graphics);
        drawStrip(graphics);
        if (data.colonies().isEmpty() && data.caravans().isEmpty())
        {
            final String none = live ? "Aucune colonie connue pour l'instant." : "Aucune donnée.";
            graphics.drawString(font, none, mapX + (mapW - font.width(none)) / 2, mapY + mapH / 2 - 4, Palette.MUTED, false);
        }
        if (hotCaravan != null)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hotCaravan.label()));
            lines.add(Component.literal(hotCaravan.percent() + " % du trajet"));
            if (hotCaravan.escort() > 0)
            {
                lines.add(Component.literal("Escorte de palier " + hotCaravan.escort() + (hotCaravan.fighting() ? " : au combat" : "")));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        else if (hotColony != null)
        {
            graphics.renderTooltip(font, Component.literal(hotColony.name() + " (" + hotColony.x() + ", " + hotColony.z() + ")"), mouseX, mouseY);
        }
    }

    private void drawGrid(final GuiGraphics graphics)
    {
        // A faint line every 100 blocks once it is far enough apart to read as a grid
        final int step = scale * 100 >= 18 ? 100 : scale * 500 >= 18 ? 500 : scale * 1000 >= 18 ? 1000 : 0;
        if (step == 0)
        {
            return;
        }
        final double left = centerX - mapW / 2.0 / scale;
        final double right = centerX + mapW / 2.0 / scale;
        final double top = centerZ - mapH / 2.0 / scale;
        final double bottom = centerZ + mapH / 2.0 / scale;
        for (long gx = (long) Math.floor(left / step) * step; gx <= right; gx += step)
        {
            graphics.fill(sx(gx), mapY, sx(gx) + 1, mapY + mapH, 0x22B9976A);
        }
        for (long gz = (long) Math.floor(top / step) * step; gz <= bottom; gz += step)
        {
            graphics.fill(mapX, sy(gz), mapX + mapW, sy(gz) + 1, 0x22B9976A);
        }
    }

    private void drawRoads(final GuiGraphics graphics)
    {
        final int width = scale >= 1.5 ? 3 : scale >= 0.4 ? 2 : 1;
        for (final int[] cell : roads)
        {
            final int px = sx(cell[0]);
            final int py = sy(cell[1]);
            if (px < mapX - 3 || px > mapX + mapW || py < mapY - 3 || py > mapY + mapH)
            {
                continue;
            }
            graphics.fill(px, py, px + width, py + width, Palette.LEATHER_LIGHT);
        }
    }

    @Nullable
    private MapStatePayload.Colony drawColonies(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        MapStatePayload.Colony hot = null;
        for (final MapStatePayload.Colony colony : data.colonies())
        {
            final int px = sx(colony.x());
            final int py = sy(colony.z());
            if (px < mapX - 8 || px > mapX + mapW + 8 || py < mapY - 8 || py > mapY + mapH + 8)
            {
                continue;
            }
            graphics.fill(px - 4, py - 4, px + 5, py + 5, Palette.EDGE);
            graphics.fill(px - 3, py - 3, px + 4, py + 4, colonyColor(colony.id()));
            graphics.fill(px - 1, py - 1, px + 2, py + 2, Palette.PAPER_LIGHT);
            if (Ui.inside(mouseX, mouseY, px - 5, py - 5, 11, 11) && Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH))
            {
                hot = colony;
            }
        }
        // Names after every marker so none is hidden under another colony's square
        for (final MapStatePayload.Colony colony : data.colonies())
        {
            final int px = sx(colony.x());
            final int py = sy(colony.z());
            if (px >= mapX && px <= mapX + mapW && py >= mapY && py <= mapY + mapH)
            {
                label(graphics, Ui.fit(font, colony.name(), 110), px, py, Palette.INK);
            }
        }
        return hot;
    }

    @Nullable
    private MapStatePayload.Caravan drawCaravans(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        MapStatePayload.Caravan hot = null;
        for (final MapStatePayload.Caravan caravan : data.caravans())
        {
            final int px = sx(caravan.x());
            final int py = sy(caravan.z());
            if (px < mapX - 8 || px > mapX + mapW + 8 || py < mapY - 8 || py > mapY + mapH + 8)
            {
                continue;
            }
            final double yaw = Math.toRadians(caravan.yaw());
            // Facing +Z (yaw 0) points down the map, -X (yaw 90) points left
            final double dx = -Math.sin(yaw);
            final double dz = Math.cos(yaw);
            final int edge = caravan.fighting() ? Palette.RED : caravan.escort() > 0 ? Palette.SELECTED : Palette.EDGE;
            graphics.fill(px - 3, py - 3, px + 4, py + 4, edge);
            graphics.fill(px - 2, py - 2, px + 3, py + 3, colonyColor(caravan.seller()));
            // The heading: a short tail of pixels ahead of the marker
            for (int i = 4; i <= 7; i++)
            {
                final int hx = px + (int) Math.round(dx * i);
                final int hy = py + (int) Math.round(dz * i);
                graphics.fill(hx, hy, hx + 2, hy + 2, Palette.INK);
            }
            if (Ui.inside(mouseX, mouseY, px - 6, py - 6, 13, 13) && Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH))
            {
                hot = caravan;
            }
        }
        for (final MapStatePayload.Caravan caravan : data.caravans())
        {
            final int px = sx(caravan.x());
            final int py = sy(caravan.z());
            if (px >= mapX && px <= mapX + mapW && py >= mapY && py <= mapY + mapH)
            {
                // On the map only what it carries; the full contract is in the tooltip
                final int colon = caravan.label().lastIndexOf(" : ");
                label(graphics, colon < 0 ? caravan.label() : caravan.label().substring(colon + 3), px, py + 8, Palette.GREEN);
            }
        }
        return hot;
    }

    private void drawScaleBar(final GuiGraphics graphics)
    {
        int blocks = SCALE_BARS[SCALE_BARS.length - 1];
        for (final int candidate : SCALE_BARS)
        {
            if (candidate * scale >= 40)
            {
                blocks = candidate;
                break;
            }
        }
        final int length = (int) Math.round(blocks * scale);
        final String text = blocks + " blocs";
        final int x = mapX + 6;
        final int y = mapY + mapH - 8;
        if (length < mapW / 2 - 20)
        {
            graphics.fill(x, y, x + length, y + 1, Palette.INK);
            graphics.fill(x, y - 2, x + 1, y + 3, Palette.INK);
            graphics.fill(x + length - 1, y - 2, x + length, y + 3, Palette.INK);
            graphics.drawString(font, text, x + length + 4, y - 4, Palette.MUTED, false);
        }
    }

    private void drawStrip(final GuiGraphics graphics)
    {
        final int y = mapY + mapH + 5;
        final int caravans = data.caravans().size();
        final String left = data.colonies().size() + " colonies · " + caravans + (caravans > 1 ? " caravanes en route" : " caravane en route");
        graphics.drawString(font, Ui.fit(font, left, mapW), mapX + 2, y, Palette.INK, false);
        final String hint = "Molette : zoom · Glisser : déplacer · Début : tout voir";
        final int room = mapW - font.width(left) - 14;
        if (font.width(hint) <= room)
        {
            graphics.drawString(font, hint, mapX + mapW - 2 - font.width(hint), y, Palette.MUTED, false);
        }
    }
}
