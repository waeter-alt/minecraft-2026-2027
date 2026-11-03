package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.mojang.blaze3d.platform.NativeImage;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.ScrollGrid;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
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
 * Escape or ✕ go back to the catalogue, not out of the build tool.
 */
public final class StyleScreen extends ParchmentScreen
{
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

    private final CatalogueScreen catalogue;
    private final List<StructurePackMeta> packs;
    private final ScrollGrid grid = new ScrollGrid(COLUMNS, GAP);
    private StructurePackMeta selected;

    StyleScreen(final CatalogueScreen catalogue)
    {
        super(Component.literal("Choisissez le style de votre cité"), catalogue);
        this.catalogue = catalogue;
        this.packs = StructurePacks.getPackMetas().stream().filter(p -> !p.isDisabled())
          .sorted(Comparator.comparing(StyleNames::name)).toList();
        this.selected = StructurePacks.getSelectedPack();
    }

    private int imageHeight()
    {
        return grid.cardWidth() * 9 / 16;
    }

    private int cardHeight()
    {
        return imageHeight() + 14;
    }

    private ScrollGrid grid()
    {
        final int width = w - RIGHT_W - 14;
        final int cardW = (width - (COLUMNS - 1) * GAP) / COLUMNS;
        grid.layout(x0 + 6, y0 + 25, width, y0 + h - 6, cardW * 9 / 16 + 14);
        return grid;
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final ScrollGrid grid = grid();
        grid.clamp(packs.size());
        final StructurePackMeta current = StructurePacks.getSelectedPack();
        final int cardW = grid.cardWidth();
        for (int i = grid.first(); i < grid.end(packs.size()); i++)
        {
            final StructurePackMeta pack = packs.get(i);
            final int cx = grid.x(i);
            final int cy = grid.y(i);
            Ui.card(graphics, cx, cy, cardW, cardHeight(), pack == selected, Ui.inside(mouseX, mouseY, cx, cy, cardW, cardHeight()), false);
            drawPicture(graphics, pack, cx + 2, cy + 2, cardW - 4, imageHeight() - 2);
            final String name = fit(StyleNames.name(pack) + (pack == current ? " ✔" : ""), cardW - 6);
            graphics.drawString(font, name, cx + 4, cy + imageHeight() + 3, Palette.INK, false);
        }
        grid.renderScrollbar(graphics, packs.size(), 2);
        renderDetail(graphics, mouseX, mouseY, current);
    }

    private void renderDetail(final GuiGraphics graphics, final int mouseX, final int mouseY, @Nullable final StructurePackMeta current)
    {
        final int left = detailPanel(graphics, RIGHT_W);
        if (selected == null)
        {
            return;
        }
        final int x = left + 6;
        final int width = RIGHT_W - 14;
        int y = y0 + 26;
        final int pictureH = width * 9 / 16;
        drawPicture(graphics, selected, x, y, width, pictureH);
        graphics.renderOutline(x - 1, y - 1, width + 2, pictureH + 2, Palette.RULE);
        y += pictureH + 6;
        y = Ui.wrapped(graphics, font, Component.literal(StyleNames.name(selected)).withStyle(ChatFormatting.BOLD), x, y, width, Palette.INK, 10);
        final int buttonY = actionY();
        for (final FormattedCharSequence line : font.split(Component.literal(StyleNames.desc(selected)), width))
        {
            if (y > buttonY - 22)
            {
                break;
            }
            graphics.drawString(font, line, x, y, Palette.MUTED, false);
            y += 9;
        }
        final String authors = "par " + String.join(", ", selected.getAuthors());
        graphics.drawString(font, fit(authors, width), x, buttonY - 12, Palette.MUTED, false);

        final String label = selected == current ? "Garder ce style" : "Choisir ce style";
        Ui.button(graphics, font, x, buttonY, width, Ui.BUTTON_H, label, Ui.ButtonStyle.PRIMARY, true, Ui.inside(mouseX, mouseY, x, buttonY, width, Ui.BUTTON_H));
    }

    /**
     * The style's picture, cropped to fill the box without stretching.
     */
    private void drawPicture(final GuiGraphics graphics, final StructurePackMeta pack, final int x, final int y, final int width, final int height)
    {
        final Picture picture = picture(pack);
        if (picture == null)
        {
            graphics.fill(x, y, x + width, y + height, Palette.PAPER_DARK);
            graphics.drawCenteredString(font, "?", x + width / 2, y + height / 2 - 4, Palette.MUTED);
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
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        final int hit = grid().hit(mouseX, mouseY, packs.size());
        if (hit >= 0)
        {
            selected = packs.get(hit);
            return true;
        }
        final int x = x0 + w - RIGHT_W + 6;
        if (selected != null && Ui.inside(mouseX, mouseY, x, actionY(), RIGHT_W - 14, Ui.BUTTON_H))
        {
            StructurePacks.switchSelectedPack(selected);
            catalogue.reload();
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        grid.scroll(scrollY);
        return true;
    }
}
