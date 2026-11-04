package fr.kodistan.interregne.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field drawn like the rest of our windows: a leather inset, like the account panels, with cream text and a
 * gold edge when typing, instead of vanilla's black box. Dark rather than light because Minecraft always draws a
 * shadow under field text, which smears on light paper.
 */
public class ParchmentField extends EditBox
{
    private static final int PAD = 4;
    private static final int TEXT = 0xFFF3E6C8;

    public ParchmentField(final Font font, final int x, final int y, final int width, final int height, final Component placeholder)
    {
        super(font, x, y, width, height, placeholder);
        setBordered(false);
        setTextColor(TEXT);
        setTextColorUneditable(Palette.PAPER_DARK);
        setHint(placeholder);
    }

    @Override
    public void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        if (!isVisible())
        {
            return;
        }
        final int x = getX();
        final int y = getY();
        graphics.fill(x, y, x + width, y + height, isFocused() ? Palette.SELECTED : Palette.EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, Palette.LEATHER);
        // A thin shade along the top: the field reads as sunk into the page
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF432916);
        graphics.pose().pushPose();
        graphics.pose().translate(PAD, (height - 8) / 2F, 0);
        super.renderWidget(graphics, mouseX - PAD, mouseY, partialTick);
        graphics.pose().popPose();
    }

    @Override
    public int getInnerWidth()
    {
        return width - 2 * PAD;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY)
    {
        super.onClick(mouseX - PAD, mouseY);
    }
}
