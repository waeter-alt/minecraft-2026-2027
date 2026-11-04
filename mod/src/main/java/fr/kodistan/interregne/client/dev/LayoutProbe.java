package fr.kodistan.interregne.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dev-only layout check for {@link UiShot}: while active it records every text and item drawn on screen, then reports
 * what a player would see as broken: text cut with an ellipsis by {@code Ui.fit}, text cropped sideways by a scissor
 * or the window edge, text overlapping other text, text drawn over an item icon.
 * <p>
 * Only things on the same layer are compared: each frame the HUD is layer 0 and the open screen layer 1, so the HUD
 * under an open window does not count. Item counts drawn by Minecraft over their own item are ignored.
 */
public final class LayoutProbe
{
    /** Overlaps smaller than this on either side are ignored: touching letters and shadows are fine. */
    private static final int MIN_OVERLAP = 2;
    private static final int MAX_LINES = 15;

    private record Box(int layer, String text, float x0, float y0, float x1, float y1, float cx0, float cx1, float cy0, float cy1)
    {
        boolean visible()
        {
            return cx1 - cx0 > 0 && cy1 - cy0 > 0;
        }

        static float overlap(final float a0, final float a1, final float b0, final float b1)
        {
            return Math.min(a1, b1) - Math.max(a0, b0);
        }

        boolean overlaps(final Box o)
        {
            return overlap(cx0, cx1, o.cx0, o.cx1) >= MIN_OVERLAP && overlap(cy0, cy1, o.cy0, o.cy1) >= MIN_OVERLAP;
        }
    }

    private static boolean active;
    private static int layer;
    private static int decorations;
    private static final Map<String, Box> texts = new LinkedHashMap<>();
    private static final Map<String, Box> items = new LinkedHashMap<>();
    private static final Deque<float[]> scissors = new ArrayDeque<>();
    private static final Set<String> cut = new HashSet<>();

    private LayoutProbe()
    {
    }

    public static boolean active()
    {
        return active;
    }

    public static void start()
    {
        texts.clear();
        items.clear();
        cut.clear();
        layer = 0;
        decorations = 0;
        active = true;
    }

    /** Each frame draws the HUD (layer 0), then the open screen over it (layer 1). */
    public static void layer(final int value)
    {
        layer = value;
    }

    /** {@code Ui.fit} gave up part of a text: if that string gets drawn, the player sees it cut. */
    public static void cut(final String shown)
    {
        if (active)
        {
            cut.add(shown);
        }
    }

    public static void decorations(final boolean entering)
    {
        decorations += entering ? 1 : -1;
    }

    public static void pushScissor(final int x0, final int y0, final int x1, final int y1)
    {
        final float[] top = scissors.peek();
        scissors.push(top == null ? new float[] {x0, y0, x1, y1}
            : new float[] {Math.max(x0, top[0]), Math.max(y0, top[1]), Math.min(x1, top[2]), Math.min(y1, top[3])});
    }

    public static void popScissor()
    {
        scissors.poll();
    }

    public static void text(final GuiGraphics graphics, final Font font, final String text, final float x, final float y)
    {
        if (decorations == 0 && text != null && !text.isBlank())
        {
            record(texts, graphics, text, x, y, font.width(text), 8);
        }
    }

    public static void text(final GuiGraphics graphics, final Font font, final FormattedCharSequence text, final float x, final float y)
    {
        if (decorations > 0)
        {
            return;
        }
        final StringBuilder plain = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            plain.appendCodePoint(codePoint);
            return true;
        });
        if (!plain.toString().isBlank())
        {
            record(texts, graphics, plain.toString(), x, y, font.width(text), 8);
        }
    }

    public static void item(final GuiGraphics graphics, final int x, final int y)
    {
        record(items, graphics, "item", x, y, 16, 16);
    }

    private static void record(final Map<String, Box> into, final GuiGraphics graphics, final String text, final float x, final float y, final int width, final int height)
    {
        final Matrix4f pose = graphics.pose().last().pose();
        final Vector3f a = pose.transformPosition(x, y, 0, new Vector3f());
        final Vector3f b = pose.transformPosition(x + width, y + height, 0, new Vector3f());
        final float x0 = Math.min(a.x, b.x);
        final float x1 = Math.max(a.x, b.x);
        final float y0 = Math.min(a.y, b.y);
        final float y1 = Math.max(a.y, b.y);
        final float[] clip = scissors.peek();
        final Box box = clip == null ? new Box(layer, text, x0, y0, x1, y1, x0, x1, y0, y1)
            : new Box(layer, text, x0, y0, x1, y1, Math.max(x0, clip[0]), Math.min(x1, clip[2]), Math.max(y0, clip[1]), Math.min(y1, clip[3]));
        // The same thing drawn again on the next frame
        into.putIfAbsent(layer + "|" + text + "|" + Math.round(x0) + "|" + Math.round(y0) + "|" + Math.round(x1), box);
    }

    /** Stops recording and lists the problems, worst first. */
    public static List<String> stop()
    {
        active = false;
        final int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        final Set<String> found = new LinkedHashSet<>();
        final List<Box> shown = texts.values().stream().filter(Box::visible).toList();
        final List<Box> icons = items.values().stream().filter(Box::visible).toList();
        for (int i = 0; i < shown.size(); i++)
        {
            final Box a = shown.get(i);
            for (int j = i + 1; j < shown.size(); j++)
            {
                final Box b = shown.get(j);
                // The same text drawn twice on purpose (outline, emboss) is not a collision
                if (a.layer() == b.layer() && !a.text().equals(b.text()) && a.overlaps(b))
                {
                    found.add("overlap   " + quote(a) + " x " + quote(b));
                }
            }
            for (final Box icon : icons)
            {
                // One-character labels over an item are badges (hotkeys, counts) put there on purpose
                if (icon.layer() == a.layer() && a.text().codePointCount(0, a.text().length()) > 1 && a.overlaps(icon))
                {
                    found.add("on item   " + quote(a));
                }
            }
        }
        for (final Box box : shown)
        {
            if (box.x0() < -0.5F || box.x1() > screenWidth + 0.5F)
            {
                found.add("offscreen " + quote(box));
            }
            else if (box.cx0() > box.x0() + 1 || box.cx1() < box.x1() - 1)
            {
                found.add("cropped   " + quote(box));
            }
        }
        for (final Box box : shown)
        {
            if (cut.contains(box.text()))
            {
                found.add("cut       " + quote(box));
            }
        }
        texts.clear();
        items.clear();
        cut.clear();
        final List<String> lines = new ArrayList<>(found);
        if (lines.size() > MAX_LINES)
        {
            final int more = lines.size() - MAX_LINES;
            lines.subList(MAX_LINES, lines.size()).clear();
            lines.add("... and " + more + " more");
        }
        return lines;
    }

    private static String quote(final Box box)
    {
        return "\"" + box.text() + "\" @" + Math.round(box.x0()) + "," + Math.round(box.y0());
    }
}
