package fr.kodistan.interregne.client.walls;

import com.mojang.blaze3d.platform.InputConstants;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.hut.ColonyHubScreen;
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
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected void init()
    {
        if (!WallPlanner.active())
        {
            final BlockPos at = townHall.getPosition();
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
        if (trace.isEmpty())
        {
            final String hint = "Cliquez au sol pour poser le premier point de la muraille";
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
        final WallTrace.Point hover = overUi || trace.closed() ? null : WallPlanner.pick(mouseX / (double) width, mouseY / (double) height);
        candidate = hover == null ? null : trace.snap(hover, hasShiftDown());
        closing = false;
        if (candidate != null && trace.points().size() >= 3)
        {
            final WallTrace.Point first = trace.points().getFirst();
            if (candidate.distanceSq(first) <= 4 || hover.distanceSq(first) <= 4)
            {
                candidate = first;
                closing = true;
            }
        }
        final String key = trace.version() + "|" + candidate + "|" + closing;
        if (!key.equals(layoutKey))
        {
            layoutKey = key;
            final List<WallTrace.Point> points = new ArrayList<>(trace.points());
            boolean closed = trace.closed();
            if (closing)
            {
                closed = true;
            }
            else if (candidate != null)
            {
                points.add(candidate);
            }
            layout = points.isEmpty() ? null : WallLayout.of(points, closed, level, WallPlanner::probe, this::inColony);
        }
        WallPlanner.show(layout, candidate, closing);
    }

    private boolean inColony(final int x, final int z)
    {
        return minecraft.level != null && colony.isCoordInColony(minecraft.level, new BlockPos(x, 64, z));
    }

    // --- Panel ---

    private void renderPanel(final GuiGraphics graphics)
    {
        final int panelW = Math.min(196, width - 2 * PAD);
        final int x = PAD;
        final int y = PAD;
        final int inner = panelW - 16;
        final List<String[]> help = List.of(
          new String[] {"Clic", "poser un point"},
          new String[] {"Clic droit", "annuler"},
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
        graphics.drawString(font, Ui.fitBold(font, "Tracé de la muraille", inner), x + 8, ty, Palette.INK, false);
        ty += LINE;
        graphics.drawString(font, Ui.fit(font, "Niv. 1 · " + level.name() + " · " + level.height() + " blocs de haut", inner), x + 8, ty, Palette.MUTED, false);
        ty += LINE + 4;
        final int length = layout == null ? 0 : layout.length();
        final int towers = layout == null ? 0 : layout.towers().size();
        ty = stat(graphics, x + 8, ty, inner, "Longueur", length + (length > 1 ? " blocs" : " bloc"));
        ty = stat(graphics, x + 8, ty, inner, "Tours", Integer.toString(towers));
        ty = stat(graphics, x + 8, ty, inner, "Portes", "0 / 5 · bientôt");
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

        // Main action: the price inside, grey until construction arrives
        final int cost = layout == null ? 0 : layout.cost(level);
        final String price = Integer.toString(cost);
        final String build = "Bâtir";
        final int buildW = font.width(build) + 10 + 12 + font.width(price) + 14;
        final int bx = right - buildW;
        final boolean overBuild = Ui.inside(mouseX, mouseY, bx, y, buildW, buttonH);
        Ui.plate(graphics, bx, y, bx + buildW, y + buttonH, Palette.PAPER_DARK, Palette.EDGE, Palette.PAPER_DARK);
        int cx = bx + 7;
        final int ty = y + (buttonH - 8) / 2;
        graphics.drawString(font, build, cx, ty, Palette.MUTED, false);
        cx += font.width(build) + 10;
        Ui.coin(graphics, cx, ty - 1, 10);
        graphics.drawString(font, price, cx + 12, ty, Palette.MUTED, false);
        buttons.add(new Button(bx, y, buildW, buttonH, () -> { }));
        right = bx - 6;

        final String quit = "Quitter";
        final int quitW = font.width(quit) + 16;
        final int qx = right - quitW;
        Ui.button(graphics, font, qx, y, quitW, buttonH, quit, Ui.ButtonStyle.SECONDARY, true, Ui.inside(mouseX, mouseY, qx, y, quitW, buttonH));
        buttons.add(new Button(qx, y, quitW, buttonH, this::onClose));
        right = qx - 6;

        if (!trace.isEmpty())
        {
            final String clear = "Effacer";
            final int clearW = font.width(clear) + 16;
            final int ex = right - clearW;
            Ui.button(graphics, font, ex, y, clearW, buttonH, clear, Ui.ButtonStyle.DANGER, true, Ui.inside(mouseX, mouseY, ex, y, clearW, buttonH));
            buttons.add(new Button(ex, y, clearW, buttonH, trace::clear));
        }

        if (overBuild)
        {
            graphics.renderTooltip(font, font.split(Component.literal("La construction des murailles arrive à la prochaine étape. Pour l'instant, le tracé sert à essayer."), 200),
              mouseX, mouseY);
        }
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
        if (button == 0 && candidate != null)
        {
            if (closing)
            {
                trace.close();
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
                trace.undo();
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
            trace.undo();
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
