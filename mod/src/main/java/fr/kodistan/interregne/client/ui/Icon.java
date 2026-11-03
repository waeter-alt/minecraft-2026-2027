package fr.kodistan.interregne.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * An icon that means something: a game item, or a pixel-art texture such as MineColonies' module icons.
 */
public record Icon(@Nullable ItemStack item, @Nullable ResourceLocation texture, int textureSize)
{
    public static Icon of(final ItemStack item)
    {
        return new Icon(item, null, 0);
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
        return new Icon(null, texture, size);
    }

    /**
     * One of MineColonies' module icons (textures/gui/modules/name.png, 20 × 20 pixel art).
     */
    public static Icon module(final String name)
    {
        return texture(ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/modules/" + name + ".png"), 20);
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
            graphics.blit(texture, x, y, size, size, 0, 0, textureSize, textureSize, textureSize, textureSize);
        }
    }
}
