package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.mojang.blaze3d.platform.NativeImage;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.hud.InterregneHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The style gallery: every style with its picture, in French; picking one goes back to the build catalogue in that style.
 */
public final class StyleScreen extends Screen
{
    private static final int PAPER       = 0xFFE9D8A6;
    private static final int PAPER_DARK  = 0xFFDCC48E;
    private static final int PAPER_LIGHT = 0xFFF8EED6;
    private static final int PANEL       = 0xFFF1E3BD;
    private static final int EDGE        = 0xFF4A2E16;
    private static final int RULE        = 0xFFB9976A;
    private static final int INK         = 0xFF4A2A10;
    private static final int MUTED       = 0xFF7A5232;
    private static final int RED         = 0xFFA32D2D;
    private static final int GREEN       = 0xFF3B6D11;
    private static final int GREEN_TEXT  = 0xFFEAF3DE;
    private static final int SELECTED    = 0xFFC08A1E;

    private static final int RIGHT_W = 170;
    private static final int GAP = 5;
    private static final int COLUMNS = 3;

    /**
     * Pictures already read, by pack name; null when a pack has none.
     */
    private static final Map<String, Picture> PICTURES = new HashMap<>();

    private record Picture(ResourceLocation texture, int width, int height)
    {
    }

    private final CatalogueScreen parent;
    private final List<StructurePackMeta> packs;
    private StructurePackMeta selected;
    private int scroll;

    private int x0;
    private int y0;
    private int w;
    private int h;

    StyleScreen(final CatalogueScreen parent)
    {
        super(Component.literal("Styles"));
        this.parent = parent;
        this.packs = StructurePacks.getPackMetas().stream().filter(p -> !p.isDisabled())
          .sorted(Comparator.comparing(StyleNames::name)).toList();
        this.selected = StructurePacks.getSelectedPack();
    }

    @Override
    protected void init()
    {
        w = Math.min(width - 16, 480);
        h = Math.min(height - 16, 260);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        graphics.fill(0, 0, width, height, 0x88000000);
    }

    private int gridLeft()
    {
        return x0 + 6;
    }

    private int gridWidth()
    {
        return w - RIGHT_W - 14;
    }

    private int cardWidth()
    {
        return (gridWidth() - (COLUMNS - 1) * GAP) / COLUMNS;
    }

    private int imageHeight()
    {
        return cardWidth() * 9 / 16;
    }

    private int cardHeight()
    {
        return imageHeight() + 14;
    }

    private int gridTop()
    {
        return y0 + 25;
    }

    private int visibleRows()
    {
        return Math.max(1, (y0 + h - 6 - gridTop() + GAP) / (cardHeight() + GAP));
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        InterregneHud.roundedPlate(graphics, x0, y0, x0 + w, y0 + h, PAPER, EDGE, PAPER_LIGHT);
        graphics.drawString(font, "Choisissez le style de votre cité", x0 + 8, y0 + 7, INK, false);
        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, inside(mouseX, mouseY, x0 + w - 16, y0 + 4, 12, 12) ? RED : MUTED, false);
        graphics.fill(x0 + 4, y0 + 19, x0 + w - 4, y0 + 20, RULE);

        final int rows = (packs.size() + COLUMNS - 1) / COLUMNS;
        scroll = Math.max(0, Math.min(scroll, rows - visibleRows()));
        final StructurePackMeta current = StructurePacks.getSelectedPack();
        for (int i = scroll * COLUMNS; i < packs.size() && i < (scroll + visibleRows()) * COLUMNS; i++)
        {
            final StructurePackMeta pack = packs.get(i);
            final int cx = gridLeft() + (i % COLUMNS) * (cardWidth() + GAP);
            final int cy = gridTop() + (i / COLUMNS - scroll) * (cardHeight() + GAP);
            final boolean hover = inside(mouseX, mouseY, cx, cy, cardWidth(), cardHeight());
            final boolean on = pack == selected;
            final int fill = hover ? 0xFFFFF6DD : PAPER_LIGHT;
            InterregneHud.roundedPlate(graphics, cx, cy, cx + cardWidth(), cy + cardHeight(), fill, on ? SELECTED : RULE, fill);
            drawPicture(graphics, pack, cx + 2, cy + 2, cardWidth() - 4, imageHeight() - 2);
            final String name = fit(StyleNames.name(pack) + (pack == current ? " ✔" : ""), cardWidth() - 6);
            graphics.drawString(font, name, cx + 4, cy + imageHeight() + 3, INK, false);
            if (on)
            {
                graphics.renderOutline(cx + 1, cy + 1, cardWidth() - 2, cardHeight() - 2, SELECTED);
            }
        }
        if (rows > visibleRows())
        {
            final int trackH = visibleRows() * (cardHeight() + GAP) - GAP;
            final int thumbH = Math.max(10, trackH * visibleRows() / rows);
            final int thumbY = gridTop() + (trackH - thumbH) * scroll / Math.max(1, rows - visibleRows());
            final int sx = gridLeft() + gridWidth() + 2;
            graphics.fill(sx, gridTop(), sx + 2, gridTop() + trackH, PAPER_DARK);
            graphics.fill(sx, thumbY, sx + 2, thumbY + thumbH, 0xFF5C3A21);
        }
        renderDetail(graphics, mouseX, mouseY, current);
    }

    private void renderDetail(final GuiGraphics graphics, final int mouseX, final int mouseY, @Nullable final StructurePackMeta current)
    {
        final int left = x0 + w - RIGHT_W;
        graphics.fill(left - 1, y0 + 22, left, y0 + h - 4, RULE);
        graphics.fill(left, y0 + 20, x0 + w - 3, y0 + h - 3, PANEL);
        if (selected == null)
        {
            return;
        }
        final int x = left + 6;
        final int width = RIGHT_W - 14;
        int y = y0 + 26;
        final int pictureH = width * 9 / 16;
        drawPicture(graphics, selected, x, y, width, pictureH);
        graphics.renderOutline(x - 1, y - 1, width + 2, pictureH + 2, RULE);
        y += pictureH + 6;
        for (final FormattedCharSequence line : font.split(Component.literal(StyleNames.name(selected)).withStyle(ChatFormatting.BOLD), width))
        {
            graphics.drawString(font, line, x, y, INK, false);
            y += 10;
        }
        final int buttonY = y0 + h - 26;
        for (final FormattedCharSequence line : font.split(Component.literal(StyleNames.desc(selected)), width))
        {
            if (y > buttonY - 22)
            {
                break;
            }
            graphics.drawString(font, line, x, y, MUTED, false);
            y += 9;
        }
        final String authors = "par " + String.join(", ", selected.getAuthors());
        graphics.drawString(font, fit(authors, width), x, buttonY - 12, MUTED, false);

        final boolean isCurrent = selected == current;
        final boolean hover = inside(mouseX, mouseY, x, buttonY, width, 18);
        InterregneHud.roundedPlate(graphics, x, buttonY, x + width, buttonY + 18, hover ? 0xFF4A8516 : GREEN, EDGE, 0xFF639922);
        graphics.drawCenteredString(font, isCurrent ? "Garder ce style" : "Choisir ce style", x + width / 2, buttonY + 5, GREEN_TEXT);
    }

    /**
     * The style's picture, cropped to fill the box without stretching.
     */
    private void drawPicture(final GuiGraphics graphics, final StructurePackMeta pack, final int x, final int y, final int width, final int height)
    {
        final Picture picture = picture(pack);
        if (picture == null)
        {
            graphics.fill(x, y, x + width, y + height, PAPER_DARK);
            graphics.drawCenteredString(font, "?", x + width / 2, y + height / 2 - 4, MUTED);
            return;
        }
        final float boxRatio = (float) width / height;
        final float picRatio = (float) picture.width() / picture.height();
        int srcW = picture.width();
        int srcH = picture.height();
        if (picRatio > boxRatio)
        {
            srcW = Math.round(picture.height() * boxRatio);
        }
        else
        {
            srcH = Math.round(picture.width() / boxRatio);
        }
        final int u = (picture.width() - srcW) / 2;
        final int v = (picture.height() - srcH) / 2;
        graphics.blit(picture.texture(), x, y, width, height, u, v, srcW, srcH, picture.width(), picture.height());
    }

    @Nullable
    private Picture picture(final StructurePackMeta pack)
    {
        if (PICTURES.containsKey(pack.getName()))
        {
            return PICTURES.get(pack.getName());
        }
        Picture picture = null;
        try
        {
            final Path icon = pack.getPath().resolve(pack.getIconPath());
            try (InputStream in = Files.newInputStream(icon))
            {
                final NativeImage image = NativeImage.read(in);
                final ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID,
                  "style/" + pack.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
                picture = new Picture(texture, image.getWidth(), image.getHeight());
                minecraft.getTextureManager().register(texture, new DynamicTexture(image));
            }
        }
        catch (final Exception e)
        {
            // No picture: a plain box stands in
        }
        PICTURES.put(pack.getName(), picture);
        return picture;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button != 0)
        {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mouseX, mouseY, x0 + w - 16, y0 + 4, 12, 12))
        {
            onClose();
            return true;
        }
        for (int i = scroll * COLUMNS; i < packs.size() && i < (scroll + visibleRows()) * COLUMNS; i++)
        {
            final int cx = gridLeft() + (i % COLUMNS) * (cardWidth() + GAP);
            final int cy = gridTop() + (i / COLUMNS - scroll) * (cardHeight() + GAP);
            if (inside(mouseX, mouseY, cx, cy, cardWidth(), cardHeight()))
            {
                selected = packs.get(i);
                return true;
            }
        }
        final int x = x0 + w - RIGHT_W + 6;
        if (selected != null && inside(mouseX, mouseY, x, y0 + h - 26, RIGHT_W - 14, 18))
        {
            StructurePacks.switchSelectedPack(selected);
            parent.reload();
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    /**
     * Escape or ✕ go back to the catalogue, not out of the build tool.
     */
    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private String fit(final String text, final int width)
    {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private static boolean inside(final double mouseX, final double mouseY, final int x, final int y, final int width, final int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
