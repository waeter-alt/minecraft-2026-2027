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
    private final WallLevels.Level level = WallLevels.of(1);

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
    }

    /**
     * Dev captures only: the planner with a trace already placed around the town hall, closed or left open.
     */
    public static void openDemo(final IBuildingView townHall, final boolean closed)
    {
        final WallPlannerScreen screen = new WallPlannerScreen(townHall);
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
        final int side = 14;
        for (int ring = 1; ring <= 6; ring++)
        {
            for (int k = 0; k < 8; k++)
            {
                final double angle = k * Math.PI / 4;
                final int x = c.getX() + (int) Math.round(Math.cos(angle) * ring * 14);
                final int z = c.getZ() + (int) Math.round(Math.sin(angle) * ring * 14);
                final List<WallTrace.Point> square = List.of(new WallTrace.Point(x, z), new WallTrace.Point(x + side, z),
                  new WallTrace.Point(x + side, z + side), new WallTrace.Point(x, z + side));
                final WallLayout test = WallLayout.of(square, true, List.of(), screen.level, WallPlanner::probe, screen::inColony);
                if (test.blocked() == 0)
                {
                    PacketDistributor.sendToServer(new WallBuildPayload(screen.colony.getID(), WallTrace.flat(square),
                      WallTrace.flat(List.of(new WallTrace.Point(x + side / 2, z))), true));
                    screen.focus = new BlockPos(x + side / 2, c.getY(), z + side / 2);
                    demoFocus = screen.focus;
                    net.minecraft.client.Minecraft.getInstance().setScreen(screen);
                    return true;
                }
            }
        }
        return false;
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
        }
    }

    @Override
    public void removed()
    {
        WallPlanner.stop();
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

        buttons.clear();
        renderPanel(graphics);
        renderActions(graphics, mouseX, mouseY);
        final String hint = trace.isEmpty() ? "Cliquez au sol pour poser le premier point de la muraille"
                              : gateMode && gates.isEmpty() ? "Cliquez sur un pan droit de la muraille pour poser sa porte (offerte)"
                                  : gateMode && gateSpot == null && gateHover != null && gates.size() < WallLevels.MAX_GATES
                                      ? "Une porte se pose sur un pan droit, nord-sud ou est-ouest, loin des tours"
                                      : null;
        if (hint != null)
        {
            final int hw = font.width(hint) + 16;
            final int hx = (width - hw) / 2;
            Ui.plate(graphics, hx, 10, hx + hw, 28, Palette.PAPER_LIGHT, Palette.EDGE, Palette.PAPER_LIGHT);
            graphics.drawString(font, hint, hx + 8, 15, Palette.INK, false);
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
        final String key = trace.version() + "|" + candidate + "|" + closing + "|" + finishing + "|" + gates;
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
        WallPlanner.show(layout, candidate, closing || finishing, null);
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
        final String committedKey = trace.version() + "|" + gates;
        if (committed == null || !committedKey.equals(this.committedKey))
        {
            this.committedKey = committedKey;
            committed = WallLayout.of(trace.points(), trace.closed(), gates, level, WallPlanner::probe, this::inColony);
        }
        gateSpot = hover == null || gates.size() >= WallLevels.MAX_GATES || committed.gateAt(hover) != null ? null : committed.gateSpot(hover, gates);
        final String key = trace.version() + "|gate|" + gateSpot + "|" + gates;
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

    private void renderPanel(final GuiGraphics graphics)
    {
        final int panelW = Math.min(196, width - 2 * PAD);
        final int x = PAD;
        final int y = PAD;
        final int inner = panelW - 16;
        final List<String[]> help = gateMode ? List.of(
          new String[] {"Clic sur un pan", "poser une porte"},
          new String[] {"Clic droit porte", "la retirer"},
          new String[] {"G", "tracé"},
          new String[] {keys(), "se déplacer"},
          new String[] {"Molette", "zoom"},
          new String[] {"Clic droit glissé", "pivoter"}) : List.of(
          new String[] {"Clic", "poser un point"},
          new String[] {"Clic dernière tour", "finir ouverte"},
          new String[] {"Clic droit", "annuler"},
          new String[] {"G", "portes"},
          new String[] {keys(), "se déplacer"},
          new String[] {"Molette", "zoom"},
          new String[] {"Clic droit glissé", "pivoter"},
          new String[] {"Maj", "angle libre"});
        final int blocked = layout == null ? 0 : layout.blocked();
        final boolean showHelp = helpOpen == null ? width >= 500 && this.height >= 300 : helpOpen;
        final int helpLines = showHelp ? help.size() : 1;
        final int height = 16 + LINE + 4 + LINE * 3 + (blocked > 0 ? LINE * 2 : 0) + 11 + helpLines * LINE + 4;
        panelX1 = x + panelW;
        panelY1 = y + height;
        Ui.plate(graphics, x, y, x + panelW, y + height, Palette.PANEL, Palette.EDGE, Palette.PAPER_LIGHT);
        int ty = y + 8;
        graphics.drawString(font, Ui.fitBold(font, gateMode ? "Portes de la muraille" : "Tracé de la muraille", inner), x + 8, ty, Palette.INK, false);
        ty += LINE;
        graphics.drawString(font, Ui.fit(font, "Niv. 1 · " + level.name() + " · " + level.height() + " blocs de haut", inner), x + 8, ty, Palette.MUTED, false);
        ty += LINE + 4;
        final int length = layout == null ? 0 : layout.length();
        final int towers = layout == null ? 0 : layout.towers().size();
        ty = stat(graphics, x + 8, ty, inner, "Longueur", length + (length > 1 ? " blocs" : " bloc"));
        ty = stat(graphics, x + 8, ty, inner, "Tours", Integer.toString(towers));
        final int next = gates.size();
        ty = stat(graphics, x + 8, ty, inner, "Portes", gates.size() + " / " + WallLevels.MAX_GATES + (next >= WallLevels.MAX_GATES ? ""
          : " · suiv. " + (level.gatePrice(next) == 0 ? "offerte" : Integer.toString(level.gatePrice(next)))));
        if (blocked > 0)
        {
            graphics.drawString(font, Ui.fit(font, blocked + " blocs gênés", inner), x + 8, ty, Palette.RED, false);
            ty += LINE;
            int lx = x + 8;
            graphics.fill(lx, ty, lx + 7, ty + 7, 0xFFE0473A);
            lx += 10;
            graphics.drawString(font, "obstacle", lx, ty, Palette.MUTED, false);
            lx += font.width("obstacle") + 10;
            graphics.fill(lx, ty, lx + 7, ty + 7, 0xFFF0A030);
            graphics.drawString(font, Ui.fit(font, "hors zone", x + 8 + inner - lx - 10), lx + 10, ty, Palette.MUTED, false);
            ty += LINE;
        }
        ty = Ui.separator(graphics, x + 8, ty - 4, inner);
        if (!showHelp)
        {
            graphics.drawString(font, "H", x + 8, ty, Palette.INK, false);
            graphics.drawString(font, Ui.fit(font, "afficher les commandes", inner - 14), x + 22, ty, Palette.MUTED, false);
            return;
        }
        int keyW = 0;
        for (final String[] row : help)
        {
            keyW = Math.max(keyW, font.width(row[0]));
        }
        keyW = Math.min(keyW + 8, inner * 3 / 5);
        for (final String[] row : help)
        {
            graphics.drawString(font, Ui.fit(font, row[0], keyW - 4), x + 8, ty, Palette.INK, false);
            graphics.drawString(font, Ui.fit(font, row[1], inner - keyW), x + 8 + keyW, ty, Palette.MUTED, false);
            ty += LINE;
        }
    }

    private int stat(final GuiGraphics graphics, final int x, final int y, final int width, final String label, final String value)
    {
        graphics.drawString(font, label, x, y, Palette.MUTED, false);
        final String shown = Ui.fit(font, value, width - font.width(label) - 6);
        graphics.drawString(font, shown, x + width - font.width(shown), y, Palette.INK, false);
        return y + LINE;
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
        final int cost = layout == null || creative ? 0 : layout.cost(level);
        final long money = ClientTreasury.of(colony) + (minecraft.player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory())));
        final String refusal = ClientWalls.of(colony) != null ? "Muraille déjà bâtie"
                                 : trace.points().size() < 2 ? null
                                     : !trace.done() ? "Finir le tracé"
                                         : layout != null && layout.blocked() > 0 ? "Obstacles en rouge"
                                             : gates.isEmpty() ? "Placer une porte"
                                                 : null;
        final boolean affordable = money >= cost;
        final boolean ready = trace.points().size() >= 2 && trace.done() && refusal == null && affordable;
        final String build = refusal != null ? refusal : "Bâtir";
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

        if (trace.done())
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

        if (!trace.isEmpty())
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
        PacketDistributor.sendToServer(new WallBuildPayload(colony.getID(), WallTrace.flat(trace.points()), WallTrace.flat(gates), trace.closed()));
        minecraft.setScreen(null);
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
        if (gateMode)
        {
            if (button == 0 && gateSpot != null)
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
            final boolean shown = helpOpen == null ? width >= 500 && height >= 300 : helpOpen;
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
