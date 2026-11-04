package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.constant.TranslationConstants;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A building's help, every page one after the other instead of a little book to flip through. Replaces MineColonies'
 * info window.
 */
public final class HelpScreen extends ModuleScreen
{
    private HelpScreen(final IBuildingView building, @Nullable final Screen parent)
    {
        super("Aide", building, parent, 340);
        navCurrent = "Aide";
    }

    public static void open(final IBuildingView building, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new HelpScreen(building, parent));
    }

    /**
     * The translation key of the building's help pages, numbered from 0.
     */
    public static String prefix(final IBuildingView building)
    {
        return TranslationConstants.PARTIAL_INFO_TEXT + building.getBuildingType().getTranslationKey().replace("com.minecolonies.building.", "") + ".";
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final String prefix = prefix(building);
        for (int i = 0; I18n.exists(prefix + i); i++)
        {
            graphics.drawString(font, Component.translatable(prefix + i + ".name").withStyle(ChatFormatting.BOLD), x + 2, y, Palette.INK, false);
            y = Ui.wrapped(graphics, font, Component.translatable(prefix + i), x + 2, y + 12, width - 4, Palette.MUTED, 10) + 8;
        }
        return y;
    }
}
