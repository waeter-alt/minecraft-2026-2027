package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.core.network.messages.server.colony.building.GiveToolMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A building's own tool (the guards' rally banner, the warehouse's scepter…): what it does, and a button to get it.
 * Replaces MineColonies' tool page.
 */
public final class ToolScreen extends ModuleScreen
{
    private final ToolModuleView module;

    private ToolScreen(final IBuildingView building, final ToolModuleView module, @Nullable final Screen parent)
    {
        super("Outil", building, parent, 320);
        this.module = module;
    }

    public static void open(final IBuildingView building, final ToolModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new ToolScreen(building, module, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        if (!framed())
        {
            h = Math.min(h, 120);
            y0 = (height - h) / 2;
        }
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final ItemStack tool = new ItemStack(module.getTool());
        Icon.of(tool).draw(graphics, x + 2, y + 2, 32);
        addSlot(x + 2, y + 2, 32, tool, mouseY);
        graphics.drawString(font, Component.literal(tool.getHoverName().getString()).withStyle(ChatFormatting.BOLD), x + 42, y + 4, Palette.INK, false);
        final Component description = Component.translatable("com.minecolonies.coremod.gui.tooldesc." + BuiltInRegistries.ITEM.getKey(module.getTool()).getPath());
        y = Ui.wrapped(graphics, font, description, x + 42, y + 16, width - 44, Palette.MUTED, 10);
        y = Math.max(y, y0 + HEADER + 46) + 6;
        final String give = "Recevoir l'outil";
        button(graphics, mouseX, mouseY, x + width - buttonWidth(give), y, give, Ui.ButtonStyle.PRIMARY, true, "L'outil arrive dans ton inventaire.",
          () -> new GiveToolMessage(building, module.getTool()).sendToServer());
        return y + 18;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }
}
