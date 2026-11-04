package fr.kodistan.interregne.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * An icon that means something: a game item, or a pixel-art texture such as MineColonies' module icons, whole or one
 * cell of a sheet.
 */
public record Icon(@Nullable ItemStack item, @Nullable ResourceLocation texture, int u, int v, int cell, int sheetWidth, int sheetHeight)
{
    public static Icon of(final ItemStack item)
    {
        return new Icon(item, null, 0, 0, 0, 0, 0);
    }

    public static Icon of(final ItemLike item)
    {
        return of(new ItemStack(item));
    }

    /**
     * A square texture of this many pixels, drawn whole.
     */
    public static Icon texture(final ResourceLocation texture, final int size)
    {
        return new Icon(null, texture, 0, 0, size, size, size);
    }

    /**
     * One square cell of a sheet of icons.
     */
    public static Icon cell(final ResourceLocation sheet, final int sheetWidth, final int sheetHeight, final int u, final int v, final int size)
    {
        return new Icon(null, sheet, u, v, size, sheetWidth, sheetHeight);
    }

    /**
     * One of MineColonies' module icons (textures/gui/modules/name.png, 20 × 20 pixel art).
     */
    public static Icon module(final String name)
    {
        return texture(ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/modules/" + name + ".png"), 20);
    }

    public boolean isItem()
    {
        return item != null;
    }

    /**
     * Draw it in a size × size square, in GUI pixels.
     */
    public void draw(final GuiGraphics graphics, final int x, final int y, final int size)
    {
        if (item != null)
        {
            if (size == 16)
            {
                graphics.renderItem(item, x, y);
                return;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(size / 16F, size / 16F, 1);
            graphics.renderItem(item, 0, 0);
            graphics.pose().popPose();
        }
        else if (texture != null)
        {
            graphics.blit(texture, x, y, size, size, u, v, cell, cell, sheetWidth, sheetHeight);
        }
    }
}
