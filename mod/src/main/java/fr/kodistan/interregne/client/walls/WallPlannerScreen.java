package fr.kodistan.interregne.client.walls;

import com.mojang.blaze3d.platform.InputConstants;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.hut.ColonyHubScreen;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.WallBuildPayload;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.walls.WallLayout;
import fr.kodistan.interregne.walls.WallLevels;
import fr.kodistan.interregne.walls.WallTrace;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The wall planner, opened from the hub's « Murailles » page: the camera hovers above the colony, the player places
 * points on the ground and the wall's ghost follows the relief between them. Step 1: tracing only, nothing is built.
 */
public final class WallPlannerScreen extends Screen
{
    private static final double RANGE = 128;
    private static final int PAD = 8;
    private static final int LINE = 11;
    /**
     * Pixels of right-button travel after which the press rotates the view instead of undoing.
     */
    private static final double DRAG_SLOP = 4;

    private final IBuildingView townHall;
    private final IColonyView colony;
    private final WallTrace trace = new WallTrace();
    private WallLevels.Level level = WallLevels.of(1);
    private int levelNumber = 1;
    /**
     * The colony's built wall, loaded into the planner to be retraced; null for a first wall.
     */
    @Nullable
    private fr.kodistan.interregne.network.WallSyncPayload existing;
    /**
     * Previewing the wall at its next level, to upgrade it.
     */
    private boolean upgrade;
    /**
     * The gate being moved, taken off the wall until it is set down again.
     */
    @Nullable
    private WallTrace.Point heldGate;
    private long lastClick;
    @Nullable
    private WallTrace.Point lastClickAt;
    /**
     * When the last order went to the server: the button waits for its answer.
     */
    private long sentAt;
    /**
     * When « Démolir » was first clicked: a second click within a few seconds tears the wall down.
     */
    private long demolishArmedAt;
    private int originalCost;
    private String originalKey = "";
    /**
     * The tower being dragged, -1 when none.
     */
    private int dragging = -1;
    @Nullable
    private WallTrace.Point grab;

    private boolean hiddenGui;
    private long lastFrame;

    @Nullable
    private WallTrace.Point candidate;
    private boolean closing;
    private boolean finishing;

    /**
     * The gates placed so far, by their centre cell on the trace, in the order they were placed (the first is free).
     */
    private final List<WallTrace.Point> gates = new ArrayList<>();
    private boolean gateMode;
    private boolean wasDone;
    @Nullable
    private WallTrace.Point gateSpot;
    @Nullable
    private WallTrace.Point gateHover;
    @Nullable
    private WallLayout committed;
    private String committedKey = "";
    @Nullable
    private WallLayout layout;
    private String layoutKey = "";

    /**
     * The commands list, opened or closed with H; null follows the window size.
     */
    @Nullable
    private Boolean helpOpen;
    /**
     * The information panel folded into a small tab.
     */
    private boolean panelHidden;
    private int borderAge = Integer.MAX_VALUE / 2;

    private double rightTravel;
    private boolean rightDown;

    private record Button(int x, int y, int width, int height, Runnable action)
    {
    }

    private final List<Button> buttons = new ArrayList<>();
    private int panelX1;
    private int panelY1;

    public WallPlannerScreen(final IBuildingView townHall)
    {
        super(Component.literal("Murailles"));
        this.townHall = townHall;
        this.colony = townHall.getColony();
        adopt(ClientWalls.of(colony));
    }

    /**
     * Loads the colony's wall as the server last sent it (or nothing): its trace, gates and level.
     */
    private void adopt(@Nullable final fr.kodistan.interregne.network.WallSyncPayload wall)
    {
        existing = wall;
        upgrade = false;
        dragging = -1;
        heldGate = null;
        trace.clear();
        gates.clear();
        layoutKey = "";
        committedKey = "";
        levelNumber = wall == null ? 1 : Math.max(1, wall.level());
        level = WallLevels.of(levelNumber);
        if (wall == null)
        {
            return;
        }
        for (final WallTrace.Point point : WallTrace.unflat(wall.points()))
        {
            trace.add(point);
        }
        if (wall.closed())
        {
            trace.close();
        }
        else
        {
            trace.finish();
        }
        gates.addAll(WallTrace.unflat(wall.gates()));
        originalKey = shapeKey();
        wasDone = true;
        gateMode = false;
    }

    /**
     * Previews the wall at its next level (or back to its own).
     */
    private void setUpgrade(final boolean on)
    {
        upgrade = on && existing != null && levelNumber < WallLevels.MAX;
        level = WallLevels.of(target());
        layoutKey = "";
        committedKey = "";
    }

    /**
     * The level the planner lays the wall out at.
     */
    private int target()
    {
        return upgrade ? levelNumber + 1 : levelNumber;
    }

    private boolean building()
    {
        return existing != null && existing.building();
    }

    /**
     * Opens the planner on the colony's wall, previewing its next level.
     */
    public static void openUpgrade(final IBuildingView townHall)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        screen.setUpgrade(true);
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Dev captures only: the planner on the last demo wall previewing its next level; build sends the upgrade.
     */
    public static void upgradeDemo(final IBuildingView townHall, final boolean build)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        screen.focus = demoFocus;
        screen.setUpgrade(true);
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
        if (build)
        {
            screen.build();
        }
    }

    private String shapeKey()
    {
        return trace.points() + "|" + trace.closed() + "|" + gates;
    }

    /**
     * Dev captures only: the planner with a trace already placed around the town hall, closed or left open.
     */
    public static void openDemo(final IBuildingView townHall, final boolean closed)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        screen.trace.clear();
        screen.gates.clear();
        final BlockPos c = townHall.getPosition();
        final int r = 20;
        screen.trace.add(new WallTrace.Point(c.getX() - r, c.getZ() - r));
        screen.trace.add(new WallTrace.Point(c.getX() + r, c.getZ() - r));
        screen.trace.add(new WallTrace.Point(c.getX() + r + 10, c.getZ()));
        screen.trace.add(new WallTrace.Point(c.getX() + r, c.getZ() + r));
        screen.trace.add(new WallTrace.Point(c.getX() - r, c.getZ() + r));
        if (closed)
        {
            screen.trace.close();
            screen.gates.add(new WallTrace.Point(c.getX(), c.getZ() - r));
            screen.gates.add(new WallTrace.Point(c.getX(), c.getZ() + r));
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Dev captures only: finds a free square of ground near the town hall, has the server build a closed wall on it,
     * and opens the planner looking at it. False when no free square was found.
     */
    public static boolean buildDemo(final IBuildingView townHall)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        final BlockPos c = townHall.getPosition();
        int least = Integer.MAX_VALUE;
        // A closed square first, then a single straight stretch: a dense colony rarely has room for a whole enclosure
        for (final int shape : new int[] {16, 0})
        {
            for (int ring = 1; ring <= 12; ring++)
            {
                for (int k = 0; k < 16; k++)
                {
                    final double angle = k * Math.PI / 8;
                    final int x = c.getX() + (int) Math.round(Math.cos(angle) * ring * 8);
                    final int z = c.getZ() + (int) Math.round(Math.sin(angle) * ring * 8);
                    final boolean closed = shape > 0;
                    final int side = closed ? shape : 24;
                    final List<WallTrace.Point> points = closed ? List.of(new WallTrace.Point(x, z), new WallTrace.Point(x + side, z),
                      new WallTrace.Point(x + side, z + side), new WallTrace.Point(x, z + side)) : List.of(new WallTrace.Point(x, z), new WallTrace.Point(x + side, z));
                    final List<WallTrace.Point> gate = List.of(new WallTrace.Point(x + side / 2, z));
                    final WallLayout test = WallLayout.of(points, closed, gate, screen.level, WallPlanner::probe, screen::inColony);
                    least = Math.min(least, test.blocked());
                    if (test.blocked() == 0)
                    {
                        PacketDistributor.sendToServer(new WallBuildPayload(screen.colony.getID(), WallTrace.flat(points), WallTrace.flat(gate), closed, 1));
                        screen.focus = new BlockPos(x + side / 2, c.getY(), z + (closed ? side / 2 : 0));
                        demoFocus = screen.focus;
                        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
                        return true;
                    }
                }
            }
        }
        fr.kodistan.interregne.InterregneMod.LOGGER.warn("UiShot: demo wall, fewest blocked cells {}", least);
        return false;
    }

    /**
     * Dev captures only: the planner on the last demo wall, tearing it down.
     */
    public static void demolishDemo(final IBuildingView townHall)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        screen.focus = demoFocus;
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
        PacketDistributor.sendToServer(new fr.kodistan.interregne.network.WallDemolishPayload(screen.colony.getID()));
        screen.sentAt = Util.getMillis();
    }

    /**
     * Dev captures only: the planner retracing the colony's built wall, its first tower pulled out by six blocks.
     */
    public static void openEditDemo(final IBuildingView townHall)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        if (screen.existing != null && !screen.trace.points().isEmpty())
        {
            final WallTrace.Point first = screen.trace.points().getFirst();
            screen.focus = new BlockPos(first.x(), townHall.getPosition().getY(), first.z());
            screen.trace.move(0, new WallTrace.Point(first.x() - 6, first.z() - 6));
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Dev captures only: the planner again, looking at the last demo wall.
     */
    public static void reopenDemo(final IBuildingView townHall)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        screen.focus = demoFocus;
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    @Nullable
    private static BlockPos demoFocus;
    /**
     * Dev captures only: the camera brought close and turned, to look at the wall's details.
     */
    private double closeYaw = Double.NaN;

    /**
     * Dev captures only: the last demo wall seen from close, turned by this many degrees.
     */
    public static void closeUpDemo(final IBuildingView townHall, final double yaw)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
        screen.focus = demoFocus;
        screen.closeYaw = yaw;
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }
    @Nullable
    private BlockPos focus;

    @Override
    protected void init()
    {
        if (!WallPlanner.active())
        {
            final BlockPos at = focus != null ? focus : townHall.getPosition();
            final float yaw = minecraft.player == null ? 0 : minecraft.player.getYRot();
            WallPlanner.start(at.getX(), at.getY(), at.getZ(), yaw);
            hiddenGui = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
            lastFrame = Util.getMillis();
            if (!Double.isNaN(closeYaw))
            {
                WallPlanner.zoom(0.6);
                WallPlanner.rotate(closeYaw, -25);
                helpOpen = false;
            }
        }
        seeThrough();
    }

    /**
     * Sees through the colony's built wall to the ground under it, and prices its trace once to charge only the
     * difference on a retrace.
     */
    private void seeThrough()
    {
        WallPlanner.own(List.of());
        if (existing != null)
        {
            final List<WallTrace.Point> old = WallTrace.unflat(existing.points());
            final java.util.Set<Long> cells = WallLayout.ownCells(old, existing.closed());
            WallPlanner.own(cells);
            final WallLevels.Level own = WallLevels.of(levelNumber);
            originalCost = WallLayout.of(old, existing.closed(), WallTrace.unflat(existing.gates()), own, WallPlanner::probe, this::inColony).cost(own);
        }
    }

    @Override
    public void removed()
    {
        WallPlanner.stop();
        WallPlanner.own(List.of());
        minecraft.options.hideGui = hiddenGui;
    }

    @Override
    public void onClose()
    {
        ColonyHubScreen.openWalls(townHall);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        // The world is the background
    }

    // --- Frame ---

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        final long now = Util.getMillis();
        final double seconds = Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        steer(seconds);
        aim(mouseX, mouseY);

        if (++borderAge > 40)
        {
            borderAge = 0;
            WallPlanner.border(colonyBorder());
        }
        buttons.clear();
        renderPanel(graphics, mouseX, mouseY);
        renderDemolish(graphics, mouseX, mouseY);
        renderActions(graphics, mouseX, mouseY);
        final int barBottom = renderSiteBar(graphics);
        final String hint = trace.isEmpty() ? "Cliquez au sol pour poser le premier point de la muraille"
                              : gateMode && gates.isEmpty() ? "Cliquez sur un pan droit de la muraille pour poser sa porte (offerte)"
                                  : gateMode && gateSpot == null && gateHover != null && gates.size() < WallLevels.MAX_GATES
                                      ? "Une porte se pose sur un pan droit, nord-sud ou est-ouest, loin des tours"
                                      : null;
        if (hint != null)
        {
            // Beside the panel, never over it: wrapped onto several lines when the room is short
            final int left = panelX1 + 8;
            final int room = Math.max(60, width - PAD - left);
            final List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(hint), room - 16);
            int textW = 0;
            for (final net.minecraft.util.FormattedCharSequence line : lines)
            {
                textW = Math.max(textW, font.width(line));
            }
            final int hw = textW + 16;
            final int hx = Math.max(left, Math.min((width - hw) / 2, width - PAD - hw));
            final int hh = 8 + lines.size() * 10;
            final int hy = barBottom > 0 ? barBottom + 4 : PAD;
            Ui.plate(graphics, hx, hy, hx + hw, hy + hh, Palette.PAPER_LIGHT, Palette.EDGE, Palette.PAPER_LIGHT);
            for (int i = 0; i < lines.size(); i++)
            {
                graphics.drawString(font, lines.get(i), hx + 8, hy + 5 + i * 10, Palette.INK, false);
            }
        }
    }

    /**
     * Moves the camera with the player's own movement keys (so ZQSD on an AZERTY keyboard), faster with sprint.
     */
    private void steer(final double seconds)
    {
        final double forward = (held(minecraft.options.keyUp) ? 1 : 0) - (held(minecraft.options.keyDown) ? 1 : 0);
        final double right = (held(minecraft.options.keyRight) ? 1 : 0) - (held(minecraft.options.keyLeft) ? 1 : 0);
        final double speed = WallPlanner.distance() * (held(minecraft.options.keySprint) ? 1.8 : 0.9) * seconds;
        final BlockPos anchor = townHall.getPosition();
        WallPlanner.move(forward * speed, right * speed, anchor.getX() + 0.5, anchor.getZ() + 0.5, RANGE, seconds);
    }

    private boolean held(final KeyMapping key)
    {
        final InputConstants.Key bound = key.getKey();
        return bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), bound.getValue());
    }

    /**
     * Finds the column under the mouse, where the next point would go, and lays the wall out again when that changes.
     */
    private void aim(final int mouseX, final int mouseY)
    {
        final fr.kodistan.interregne.network.WallSyncPayload live = ClientWalls.of(colony);
        if (live != existing && dragging < 0 && heldGate == null)
        {
            // The server's wall changed (a site started or ended): show it as it now stands
            adopt(live);
            seeThrough();
        }
        if (building())
        {
            // The site runs below: no ghost over it, the player watches it rise
            candidate = null;
            grab = null;
            gateSpot = null;
            closing = false;
            finishing = false;
            // Keep the figures of the wall being built in the panel
            final String key = "site|" + trace.version() + "|" + gates;
            if (!key.equals(layoutKey) && !trace.isEmpty())
            {
                layoutKey = key;
                layout = WallLayout.of(trace.points(), trace.closed(), gates, level, WallPlanner::probe, this::inColony);
            }
            WallPlanner.show(null, null, false, null);
            return;
        }
        final boolean overUi = overUi(mouseX, mouseY);
        // Finishing the trace goes straight to placing gates, while it has none
        if (trace.done() && !wasDone && gates.isEmpty())
        {
            gateMode = true;
        }
        wasDone = trace.done();
        if (!trace.done())
        {
            gateMode = false;
        }
        if (gateMode)
        {
            aimGate(overUi ? null : WallPlanner.pick(mouseX / (double) width, mouseY / (double) height));
            return;
        }
        gateSpot = null;
        grab = null;
        if (trace.done() && !overUi)
        {
            final WallTrace.Point at = WallPlanner.pick(mouseX / (double) width, mouseY / (double) height);
            if (dragging >= 0 && at != null)
            {
                trace.move(dragging, at);
            }
            grab = dragging >= 0 ? trace.points().get(dragging) : at == null ? null : towerNear(at);
        }
        final WallTrace.Point hover = overUi || trace.done() ? null : WallPlanner.pick(mouseX / (double) width, mouseY / (double) height);
        candidate = hover == null ? null : trace.snap(hover, hasShiftDown());
        closing = false;
        finishing = false;
        if (hover != null && trace.points().size() >= 2 && hover.distanceSq(trace.points().getLast()) <= 2)
        {
            // Over the last tower: a click ends the wall there, open
            candidate = trace.points().getLast();
            finishing = true;
        }
        else if (candidate != null && trace.points().size() >= 3)
        {
            final WallTrace.Point first = trace.points().getFirst();
            if (candidate.distanceSq(first) <= 4 || hover.distanceSq(first) <= 4)
            {
                candidate = first;
                closing = true;
            }
        }
        final String key = trace.version() + "|" + candidate + "|" + closing + "|" + finishing + "|" + gates + "|" + target();
        if (!key.equals(layoutKey))
        {
            layoutKey = key;
            final List<WallTrace.Point> points = new ArrayList<>(trace.points());
            boolean closed = trace.closed();
            if (closing)
            {
                closed = true;
            }
            else if (candidate != null && !finishing)
            {
                points.add(candidate);
            }
            layout = points.isEmpty() ? null : WallLayout.of(points, closed, gates, level, WallPlanner::probe, this::inColony);
        }
        WallPlanner.show(Double.isNaN(closeYaw) ? layout : null, candidate != null ? candidate : grab, closing || finishing || grab != null, null);
    }

    /**
     * Index of the tower within two blocks of this cell, -1 when none.
     */
    private int towerIndex(final WallTrace.Point at)
    {
        int best = -1;
        int bestDistance = 5;
        for (int i = 0; i < trace.points().size(); i++)
        {
            final int distance = trace.points().get(i).distanceSq(at);
            if (distance < bestDistance)
            {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * A new tower on the stretch of wall within two blocks of this cell, if any.
     */
    private void insertTower(final WallTrace.Point at)
    {
        final List<WallTrace.Point> points = trace.points();
        final int n = points.size();
        final int stretches = trace.closed() ? n : n - 1;
        int best = -1;
        int bestDistance = 5;
        WallTrace.Point bestCell = null;
        for (int i = 0; i < stretches; i++)
        {
            for (final WallTrace.Point cell : WallTrace.line(points.get(i), points.get((i + 1) % n)))
            {
                final int distance = cell.distanceSq(at);
                if (distance < bestDistance)
                {
                    best = i;
                    bestDistance = distance;
                    bestCell = cell;
                }
            }
        }
        if (bestCell != null && !points.contains(bestCell))
        {
            trace.insert(best + 1, bestCell);
        }
    }

    /**
     * The placed gate whose gatehouse covers this cell, if any.
     */
    @Nullable
    private WallTrace.Point gateNear(final WallTrace.Point at)
    {
        WallTrace.Point best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (final WallTrace.Point gate : gates)
        {
            final int reach = Math.max(Math.abs(gate.x() - at.x()), Math.abs(gate.z() - at.z()));
            final int distance = gate.distanceSq(at);
            if (reach <= WallLayout.GATE_HALF + 1 && distance < bestDistance)
            {
                best = gate;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Nullable
    private WallTrace.Point towerNear(final WallTrace.Point at)
    {
        final int index = towerIndex(at);
        return index < 0 ? null : trace.points().get(index);
    }

    /**
     * In gate mode: the gate would go on the straight stretch nearest the mouse; the layout shows it as a preview.
     */
    private void aimGate(@Nullable final WallTrace.Point hover)
    {
        candidate = null;
        closing = false;
        finishing = false;
        gateHover = hover;
        final String committedKey = trace.version() + "|" + gates + "|" + target();
        if (committed == null || !committedKey.equals(this.committedKey))
        {
            this.committedKey = committedKey;
            committed = WallLayout.of(trace.points(), trace.closed(), gates, level, WallPlanner::probe, this::inColony);
        }
        gateSpot = hover == null || gates.size() >= WallLevels.MAX_GATES || committed.gateAt(hover) != null ? null : committed.gateSpot(hover, gates);
        final String key = trace.version() + "|gate|" + gateSpot + "|" + gates + "|" + target();
        if (!key.equals(layoutKey))
        {
            layoutKey = key;
            if (gateSpot == null)
            {
                layout = committed;
            }
            else
            {
                final List<WallTrace.Point> preview = new ArrayList<>(gates);
                preview.add(gateSpot);
                layout = WallLayout.of(trace.points(), trace.closed(), preview, level, WallPlanner::probe, this::inColony);
            }
        }
        WallPlanner.show(layout, null, false, gateSpot);
    }

    private boolean inColony(final int x, final int z)
    {
        final net.minecraft.client.multiplayer.ClientLevel world = net.minecraft.client.Minecraft.getInstance().level;
        return world != null && colony.isCoordInColony(world, new BlockPos(x, 64, z));
    }

    // --- Panel ---

    private static int translucent(final int color)
    {
        return (color & 0x00FFFFFF) | 0xD0000000;
    }

    /**
     * The colony's limits around the town hall, as the unit edges between a claimed chunk and one that is not.
     */
    private java.util.List<int[]> colonyBorder()
    {
        final java.util.List<int[]> edges = new ArrayList<>();
        final int cx = townHall.getPosition().getX() >> 4;
        final int cz = townHall.getPosition().getZ() >> 4;
        final int reach = (int) (RANGE / 16) + 3;
        final java.util.Map<Long, Boolean> claimed = new java.util.HashMap<>();
        final java.util.function.BiPredicate<Integer, Integer> in = (x, z) -> claimed.computeIfAbsent(WallRegistryCell.of(x, z), k -> inColony(x * 16 + 8, z * 16 + 8));
        for (int x = cx - reach; x <= cx + reach; x++)
        {
            for (int z = cz - reach; z <= cz + reach; z++)
            {
                if (!in.test(x, z))
                {
                    continue;
                }
                for (int i = 0; i < 16; i++)
                {
                    if (!in.test(x, z - 1))
                    {
                        edges.add(new int[] {x * 16 + i, z * 16, 0});
                    }
                    if (!in.test(x, z + 1))
                    {
                        edges.add(new int[] {x * 16 + i, z * 16 + 16, 0});
                    }
                    if (!in.test(x - 1, z))
                    {
                        edges.add(new int[] {x * 16, z * 16 + i, 1});
                    }
                    if (!in.test(x + 1, z))
                    {
                        edges.add(new int[] {x * 16 + 16, z * 16 + i, 1});
                    }
                }
            }
        }
        return edges;
    }

    private static final class WallRegistryCell
    {
        static long of(final int x, final int z)
        {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }
    }

    /**
     * The bar of the wall's site (building, retouch, upgrade, demolition), drawn beside the panel since the aerial view
     * hides the usual HUD. Returns its bottom, or 0 when there is none.
     */
    private int renderSiteBar(final GuiGraphics graphics)
    {
        final java.util.Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent> events =
          ((fr.kodistan.interregne.mixin.client.BossHealthOverlayAccessor) minecraft.gui.getBossOverlay()).interregne$events();
        net.minecraft.client.gui.components.LerpingBossEvent bar = null;
        for (final net.minecraft.client.gui.components.LerpingBossEvent event : events.values())
        {
            if (bar == null || event.getName().getString().toLowerCase(java.util.Locale.ROOT).contains("muraille"))
            {
                bar = event;
            }
        }
        if (bar == null)
        {
            return 0;
        }
        final int left = panelX1 + 8;
        final int right = width - PAD;
        final int w = Math.min(220, right - left);
        if (w < 60)
        {
            return 0;
        }
        final int x = Math.max(left, Math.min((width - w) / 2, right - w));
        final int y = PAD;
        final int h = 24;
        Ui.plate(graphics, x, y, x + w, y + h, translucent(Palette.PAPER_LIGHT), Palette.EDGE, translucent(Palette.PAPER_LIGHT));
        graphics.drawString(font, Ui.fit(font, bar.getName().getString(), w - 16), x + 8, y + 5, Palette.INK, false);
        final int bx = x + 8;
        final int by = y + 16;
        final int bw = w - 16;
        graphics.fill(bx, by, bx + bw, by + 3, Palette.PAPER_DARK);
        graphics.fill(bx, by, bx + Math.round(bw * Math.max(0, Math.min(1, bar.getProgress()))), by + 3, Palette.GREEN_LIGHT);
        return y + h;
    }

    private void renderPanel(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        if (panelHidden)
        {
            // Folded into a tab: a click brings the panel back
            final String label = "Infos ▸";
            final int w = font.width(label) + 14;
            final int h = Ui.BUTTON_H + 2;
            final boolean over = Ui.inside(mouseX, mouseY, PAD, PAD, w, h);
            Ui.plate(graphics, PAD, PAD, PAD + w, PAD + h, over ? Palette.PAPER_LIGHT : translucent(Palette.PANEL), Palette.EDGE, translucent(Palette.PAPER_LIGHT));
            graphics.drawString(font, label, PAD + 7, PAD + (h - 8) / 2, Palette.INK, false);
            buttons.add(new Button(PAD, PAD, w, h, () -> panelHidden = false));
            panelX1 = PAD + w;
            panelY1 = PAD + h;
            return;
        }
        final int panelW = Math.min(168, width - 2 * PAD);
        final int x = PAD;
        final int y = PAD;
        final int pad = 10;
        final int row = 12;
        final int inner = panelW - 2 * pad;
        final List<String[]> help = gateMode ? List.of(
          new String[] {"Clic sur un pan", "poser une porte"},
          new String[] {"Glisser une porte", "la déplacer"},
          new String[] {"Clic droit porte", "la retirer"},
          new String[] {"G", "tracé"},
          new String[] {keys(), "se déplacer"},
          new String[] {"Molette", "zoom"},
          new String[] {"Clic droit glissé", "pivoter"}) : List.of(
          new String[] {"Clic", "poser un point"},
          new String[] {"Clic dernière tour", "finir ouverte"},
          new String[] {"Clic droit", "annuler"},
          new String[] {"Glisser une tour", "la déplacer"},
          new String[] {"Double clic", "nouvelle tour"},
          new String[] {"G", "portes"},
          new String[] {keys(), "se déplacer"},
          new String[] {"Molette", "zoom"},
          new String[] {"Clic droit glissé", "pivoter"},
          new String[] {"Maj", "angle libre"});
        final int blocked = layout == null ? 0 : layout.blocked();
        final boolean showHelp = helpOpen != null && helpOpen;
        final int helpLines = showHelp ? help.size() : 1;
        final int height = pad + 10 + row + 8 + row * 3 + (blocked > 0 ? row * 2 : 0) + 5 + 1 + 7 + helpLines * row + pad - 4;
        panelX1 = x + panelW;
        panelY1 = y + height;
        Ui.plate(graphics, x, y, x + panelW, y + height, translucent(Palette.PANEL), Palette.EDGE, translucent(Palette.PAPER_LIGHT));
        // Fold button, top right
        final int fold = 11;
        final int fx = x + panelW - pad - fold + 3;
        final int fy = y + pad - 2;
        final boolean overFold = Ui.inside(mouseX, mouseY, fx - 2, fy - 2, fold + 4, fold + 4);
        graphics.fill(fx, fy, fx + fold, fy + fold, overFold ? 0x40000000 : 0x18000000);
        graphics.fill(fx + 3, fy + 5, fx + fold - 3, fy + 6, Palette.INK);
        buttons.add(new Button(fx - 2, fy - 2, fold + 4, fold + 4, () -> panelHidden = true));
        int ty = y + pad;
        graphics.drawString(font, Ui.fitBold(font, building() ? "Muraille en chantier" : gateMode ? "Portes de la muraille" : upgrade ? "Amélioration"
          : existing != null ? "Retouche" : "Tracé de la muraille", inner - fold - 4), x + pad, ty, Palette.INK, false);
        ty += 10 + 2;
        graphics.drawString(font, Ui.fit(font, levelLine(inner), inner), x + pad, ty, Palette.MUTED, false);
        ty += row + 8;
        final int length = layout == null ? 0 : layout.length();
        final int towers = layout == null ? 0 : layout.towers().size();
        ty = stat(graphics, x + pad, ty, inner, "Longueur", length + (length > 1 ? " blocs" : " bloc"), row);
        ty = stat(graphics, x + pad, ty, inner, "Tours", Integer.toString(towers), row);
        final int next = gates.size();
        ty = stat(graphics, x + pad, ty, inner, "Portes", gates.size() + " / " + WallLevels.MAX_GATES + (next >= WallLevels.MAX_GATES ? ""
          : " · +" + (level.gatePrice(next) == 0 ? "0" : Integer.toString(level.gatePrice(next)))), row);
        if (blocked > 0)
        {
            graphics.drawString(font, Ui.fit(font, blocked + " blocs gênés", inner), x + pad, ty, Palette.RED, false);
            ty += row;
            int lx = x + pad;
            graphics.fill(lx, ty, lx + 7, ty + 7, 0xFFE0473A);
            lx += 10;
            graphics.drawString(font, "obstacle", lx, ty, Palette.MUTED, false);
            lx += font.width("obstacle") + 10;
            graphics.fill(lx, ty, lx + 7, ty + 7, 0xFFF0A030);
            graphics.drawString(font, Ui.fit(font, "hors zone", x + pad + inner - lx - 10), lx + 10, ty, Palette.MUTED, false);
            ty += row;
        }
        ty += 5;
        graphics.fill(x + pad, ty, x + pad + inner, ty + 1, 0x30000000);
        ty += 1 + 7;
        if (!showHelp)
        {
            graphics.drawString(font, "H", x + pad, ty, Palette.INK, false);
            graphics.drawString(font, Ui.fit(font, "commandes", inner - 14), x + pad + 12, ty, Palette.MUTED, false);
            return;
        }
        int keyW = 0;
        for (final String[] line : help)
        {
            keyW = Math.max(keyW, font.width(line[0]));
        }
        keyW = Math.min(keyW + 10, inner * 3 / 5);
        for (final String[] line : help)
        {
            graphics.drawString(font, Ui.fit(font, line[0], keyW - 6), x + pad, ty, Palette.INK, false);
            graphics.drawString(font, Ui.fit(font, line[1], inner - keyW), x + pad + keyW, ty, Palette.MUTED, false);
            ty += row;
        }
    }

    /**
     * « Niv. 2 · Pierre brute · 5 blocs de haut », shortened to what fits.
     */
    private String levelLine(final int width)
    {
        final String head = (upgrade ? "Niv. " + levelNumber + " → " + target() : "Niv. " + target()) + " · " + level.name();
        for (final String tail : new String[] {" · " + level.height() + " blocs de haut", " · " + level.height() + " de haut", ""})
        {
            if (font.width(head + tail) <= width)
            {
                return head + tail;
            }
        }
        return head;
    }

    /**
     * « Démolir la muraille » under the panel; the first click arms it, the second (within 4 s) tears the whole wall
     * down, gates included.
     */
    private void renderDemolish(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        if (existing == null || building() || Util.getMillis() - sentAt < 2500)
        {
            return;
        }
        final boolean armed = Util.getMillis() - demolishArmedAt < 4000;
        final String label = armed ? "Confirmer" : "Démolir la muraille";
        final boolean creative = minecraft.player != null && minecraft.player.isCreative();
        final long refund = creative ? 0 : existing.refund();
        final String back = refund > 0 ? "+" + compact(refund) : null;
        final int w = Math.min(width - 2 * PAD, font.width(label) + 16 + (back == null ? 0 : 8 + 12 + font.width(back)));
        final int h = Ui.BUTTON_H + 2;
        final int x = PAD;
        final int y = panelY1 + 4;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, w, h);
        final int ty = y + (h - 8) / 2;
        if (armed)
        {
            Ui.plate(graphics, x, y, x + w, y + h, 0xFFB03A2E, Palette.EDGE, 0xFFD2574A);
        }
        else
        {
            Ui.button(graphics, font, x, y, w, h, "", Ui.ButtonStyle.DANGER, true, over);
        }
        graphics.drawString(font, label, x + 8, ty, armed ? 0xFFFFFFFF : Palette.RED, false);
        if (back != null)
        {
            // What comes back to the treasury, inside the button
            final int cx = x + 8 + font.width(label) + 8;
            Ui.coin(graphics, cx, ty - 1, 10);
            graphics.drawString(font, back, cx + 12, ty, armed ? 0xFFFFFFFF : Palette.GREEN, false);
        }
        buttons.add(new Button(x, y, w, h, () -> {
            if (Util.getMillis() - demolishArmedAt < 4000)
            {
                PacketDistributor.sendToServer(new fr.kodistan.interregne.network.WallDemolishPayload(colony.getID()));
                demolishArmedAt = 0;
                sentAt = Util.getMillis();
            }
            else
            {
                demolishArmedAt = Util.getMillis();
            }
        }));
        if (over)
        {
            final String refunded = refund > 0 ? " Ses " + refund + " Couronnes reviennent au trésor régional." : "";
            graphics.renderTooltip(font, font.split(Component.literal(armed ? "Cliquez encore : toute la muraille et ses portes tombent en quelques secondes." + refunded
              : "Abat toute la muraille et ses portes d'un coup." + refunded + " Un second clic confirme."), 200), mouseX, mouseY);
        }
    }

    private int stat(final GuiGraphics graphics, final int x, final int y, final int width, final String label, final String value, final int row)
    {
        graphics.drawString(font, label, x, y, Palette.MUTED, false);
        final String shown = Ui.fit(font, value, width - font.width(label) - 6);
        graphics.drawString(font, shown, x + width - font.width(shown), y, Palette.INK, false);
        return y + row;
    }

    private String keys()
    {
        return label(minecraft.options.keyUp) + label(minecraft.options.keyLeft) + label(minecraft.options.keyDown) + label(minecraft.options.keyRight);
    }

    private static String label(final KeyMapping key)
    {
        final String text = key.getTranslatedKeyMessage().getString();
        return text.length() == 1 ? text.toUpperCase(java.util.Locale.ROOT) : text + " ";
    }

    // --- Actions ---

    private void renderActions(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int buttonH = Ui.BUTTON_H + 4;
        final int y = height - PAD - buttonH;
        int right = width - PAD;

        // Main action: the price inside; green when it can be built, grey with the reason otherwise
        final boolean creative = minecraft.player != null && minecraft.player.isCreative();
        final boolean editing = existing != null;
        final int cost = layout == null || creative ? 0 : upgrade || !editing ? layout.cost(level)
                                                                 : (int) WallLevels.editCost(layout.cost(level), originalCost, level);
        final long money = ClientTreasury.of(colony) + (minecraft.player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory())));
        final fr.kodistan.interregne.network.WallSyncPayload live = ClientWalls.of(colony);
        final String refusal = live != null && live.building() || Util.getMillis() - sentAt < 2500 ? "Chantier en cours"
                                 : trace.points().size() < 2 ? null
                                     : !trace.done() ? "Finir le tracé"
                                     : upgrade && townHall.getBuildingLevel() < target() ? "Hôtel de ville niv. " + target() + " requis"
                                         : layout != null && layout.blocked() > 0 ? "Obstacles en rouge"
                                             : gates.isEmpty() ? "Placer une porte"
                                                 : null;
        // An unchanged trace is not useless: it is the repair of a wall whose ladders were knocked off or whose
        // blocks (the gravel of the early levels) fell away. The server rebuilds only what is missing.
        final boolean repair = editing && !upgrade && shapeKey().equals(originalKey);
        final boolean affordable = money >= cost;
        final boolean ready = trace.points().size() >= 2 && trace.done() && refusal == null && affordable;
        final String build = refusal != null ? refusal : upgrade ? "Améliorer · niv. " + target() : repair ? "Réparer" : editing ? "Modifier" : "Bâtir";
        final String price = creative ? "gratuit" : affordable ? Integer.toString(cost) : compact(money) + "/" + compact(cost);
        final boolean showPrice = refusal == null;
        final int buildW = font.width(build) + 14 + (showPrice ? 10 + 12 + font.width(price) : 0);
        final int bx = right - buildW;
        final boolean overBuild = Ui.inside(mouseX, mouseY, bx, y, buildW, buttonH);
        if (ready)
        {
            Ui.plate(graphics, bx, y, bx + buildW, y + buttonH, overBuild ? Palette.GREEN_HOVER : Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
        }
        else
        {
            Ui.plate(graphics, bx, y, bx + buildW, y + buttonH, Palette.PAPER_DARK, Palette.EDGE, Palette.PAPER_DARK);
        }
        int cx = bx + 7;
        final int ty = y + (buttonH - 8) / 2;
        graphics.drawString(font, build, cx, ty, ready ? Palette.GREEN_TEXT : Palette.MUTED, false);
        if (showPrice)
        {
            cx += font.width(build) + 10;
            Ui.coin(graphics, cx, ty - 1, 10);
            graphics.drawString(font, price, cx + 12, ty, ready ? Palette.GREEN_TEXT : affordable ? Palette.MUTED : Palette.RED, false);
        }
        buttons.add(new Button(bx, y, buildW, buttonH, ready ? this::build : () -> { }));
        right = bx - 6;

        final String quit = "Quitter";
        final int quitW = font.width(quit) + 16;
        final int qx = right - quitW;
        Ui.button(graphics, font, qx, y, quitW, buttonH, quit, Ui.ButtonStyle.SECONDARY, true, Ui.inside(mouseX, mouseY, qx, y, quitW, buttonH));
        buttons.add(new Button(qx, y, quitW, buttonH, this::onClose));
        right = qx - 6;

        if (trace.done() && !building())
        {
            final String label = gateMode ? "Portes ✔" : "Portes";
            final int gateW = font.width(label) + 16;
            final int gx = right - gateW;
            final boolean over = Ui.inside(mouseX, mouseY, gx, y, gateW, buttonH);
            if (gateMode)
            {
                Ui.plate(graphics, gx, y, gx + gateW, y + buttonH, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
                graphics.drawCenteredString(font, label, gx + gateW / 2, y + (buttonH - 8) / 2, Palette.GOLD_TEXT);
            }
            else
            {
                Ui.button(graphics, font, gx, y, gateW, buttonH, label, Ui.ButtonStyle.SECONDARY, true, over);
            }
            buttons.add(new Button(gx, y, gateW, buttonH, () -> gateMode = !gateMode));
            right = gx - 6;
        }

        if (existing != null && !building() && levelNumber < WallLevels.MAX)
        {
            final String label = upgrade ? "◀ Retouche" : "Améliorer…";
            final int upW = font.width(label) + 16;
            final int ux = right - upW;
            if (upgrade)
            {
                Ui.plate(graphics, ux, y, ux + upW, y + buttonH, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
                graphics.drawCenteredString(font, label, ux + upW / 2, y + (buttonH - 8) / 2, Palette.GOLD_TEXT);
            }
            else
            {
                Ui.button(graphics, font, ux, y, upW, buttonH, label, Ui.ButtonStyle.SECONDARY, true, Ui.inside(mouseX, mouseY, ux, y, upW, buttonH));
            }
            buttons.add(new Button(ux, y, upW, buttonH, () -> setUpgrade(!upgrade)));
            right = ux - 6;
        }

        if (!trace.isEmpty() && !building() && !upgrade)
        {
            final String clear = "Effacer";
            final int clearW = font.width(clear) + 16;
            final int ex = right - clearW;
            Ui.button(graphics, font, ex, y, clearW, buttonH, clear, Ui.ButtonStyle.DANGER, true, Ui.inside(mouseX, mouseY, ex, y, clearW, buttonH));
            buttons.add(new Button(ex, y, clearW, buttonH, () -> {
                trace.clear();
                gates.clear();
            }));
        }

        if (overBuild)
        {
            final String tip = refusal == null && !affordable ? "Il manque " + (cost - money) + " Couronnes (trésor régional et bourse comptés)."
                                 : "Finir le tracé".equals(refusal) ? "Cliquez sur le premier point pour fermer l'enceinte, ou sur la dernière tour pour la laisser ouverte."
                                     : "Obstacles en rouge".equals(refusal) ? "Le tracé traverse un bâtiment, sort de la colonie, ou une porte ne tient plus sur un pan droit : corrigez les parties rouges."
                                     : "Placer une porte".equals(refusal) ? "Une muraille a besoin d'au moins une porte, posée sur un pan droit. La première est offerte."
                                     : "Chantier en cours".equals(refusal) ? "La muraille se construit : attendez la fin du chantier pour la retoucher."
                                     : refusal != null && refusal.startsWith("Hôtel") ? "Une muraille ne dépasse pas le niveau de l'hôtel de ville : améliorez-le d'abord."
                                         : ready && repair ? "Rebouche ce qui manque (échelles décrochées, blocs tombés) : seuls les blocs absents sont reposés, au prix plancher d'une retouche (" + level.retouchPrice() + " Couronnes)."
                                         : ready && upgrade ? "Rebâtit toute la muraille au niveau " + target() + " (" + level.name() + ", " + level.height() + " blocs de haut) sur ce tracé. Chantier : " + seconds() + " s."
                                         : ready && editing ? "Retouche la muraille : ce que le tracé ajoute, au moins " + level.retouchPrice() + " Couronnes. Seuls les pans touchés sont rebâtis, les pans retirés démontés."
                                         : ready ? "Paie la muraille (trésor régional d'abord) et lance le chantier : " + seconds() + " s." : null;
            if (tip != null)
            {
                graphics.renderTooltip(font, font.split(Component.literal(tip), 200), mouseX, mouseY);
            }
        }
    }

    private void removeGateUnderMouse()
    {
        if (gateHover == null || layout == null)
        {
            return;
        }
        final WallLayout.Gate gate = layout.gateAt(gateHover);
        if (gate != null)
        {
            gates.remove(new WallTrace.Point(gate.x(), gate.z()));
        }
    }

    private static String compact(final long amount)
    {
        return amount >= 10_000 ? amount / 1000 + "k" : Long.toString(amount);
    }

    private int seconds()
    {
        return layout == null ? 0 : fr.kodistan.interregne.walls.Walls.seconds(layout);
    }

    /**
     * Sends the trace to the server, which checks it again, charges it and starts the site.
     */
    private void build()
    {
        PacketDistributor.sendToServer(new WallBuildPayload(colony.getID(), WallTrace.flat(trace.points()), WallTrace.flat(gates), trace.closed(), target()));
        // The planner stays open: the player watches the site from above, and leaves when they like
        sentAt = Util.getMillis();
    }

    private boolean overUi(final double mouseX, final double mouseY)
    {
        if (mouseX < panelX1 && mouseY < panelY1)
        {
            return true;
        }
        for (final Button button : buttons)
        {
            if (Ui.inside(mouseX, mouseY, button.x, button.y, button.width, button.height))
            {
                return true;
            }
        }
        return false;
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        for (final Button b : buttons)
        {
            if (button == 0 && Ui.inside(mouseX, mouseY, b.x, b.y, b.width, b.height))
            {
                b.action.run();
                return true;
            }
        }
        if (overUi(mouseX, mouseY))
        {
            return true;
        }
        if (building())
        {
            if (button == 1)
            {
                rightDown = true;
                rightTravel = 0;
            }
            return true;
        }
        if (gateMode)
        {
            final WallTrace.Point at = WallPlanner.pick(mouseX / (double) width, mouseY / (double) height);
            final WallTrace.Point gate = button == 0 && at != null ? gateNear(at) : null;
            if (gate != null)
            {
                heldGate = gate;
                gates.remove(gate);
            }
            else if (button == 0 && gateSpot != null)
            {
                gates.add(gateSpot);
            }
            else if (button == 1)
            {
                rightDown = true;
                rightTravel = 0;
            }
            return true;
        }
        if (button == 0 && trace.done() && grab != null)
        {
            dragging = trace.points().indexOf(grab);
            return true;
        }
        if (button == 0 && trace.done())
        {
            final WallTrace.Point at = WallPlanner.pick(mouseX / (double) width, mouseY / (double) height);
            final long now = Util.getMillis();
            if (at != null && lastClickAt != null && now - lastClick < 400 && at.distanceSq(lastClickAt) <= 4)
            {
                insertTower(at);
                lastClick = 0;
                lastClickAt = null;
            }
            else
            {
                lastClick = now;
                lastClickAt = at;
            }
            return true;
        }
        if (button == 0 && candidate != null)
        {
            if (closing)
            {
                trace.close();
            }
            else if (finishing)
            {
                trace.finish();
            }
            else
            {
                trace.add(candidate);
            }
            return true;
        }
        if (button == 1)
        {
            rightDown = true;
            rightTravel = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (button == 1 || button == 2)
        {
            rightTravel += Math.abs(dragX) + Math.abs(dragY);
            if (button == 2 || rightTravel > DRAG_SLOP)
            {
                WallPlanner.rotate(dragX * 0.5, dragY * 0.4);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        if (button == 0 && dragging >= 0)
        {
            dragging = -1;
            return true;
        }
        if (button == 0 && heldGate != null)
        {
            // Set down where it now snaps, or back where it was
            gates.add(gateSpot != null ? gateSpot : heldGate);
            heldGate = null;
            return true;
        }
        if (button == 1 && rightDown)
        {
            rightDown = false;
            if (rightTravel <= DRAG_SLOP)
            {
                if (gateMode)
                {
                    removeGateUnderMouse();
                }
                else
                {
                    trace.undo();
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        WallPlanner.zoom(Math.pow(0.88, scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE || (keyCode == GLFW.GLFW_KEY_Z && hasControlDown()) || (keyCode == GLFW.GLFW_KEY_W && hasControlDown()))
        {
            if (gateMode && !gates.isEmpty())
            {
                gates.removeLast();
            }
            else
            {
                trace.undo();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G && trace.done())
        {
            gateMode = !gateMode;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_H)
        {
            final boolean shown = helpOpen != null && helpOpen;
            helpOpen = !shown;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F5)
        {
            WallPlanner.forgetTerrain();
            layoutKey = "";
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
