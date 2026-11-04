package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerLevelManagementModuleView;
import com.minecolonies.core.network.messages.server.colony.building.miner.MinerSetLevelMessage;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The mine's levels, top to bottom: depth and how many galleries are left, the one being worked highlighted, and a
 * button to send the miner to another. Replaces MineColonies' miner page (its repair button needed the builder, gone).
 */
public final class MineLevelsScreen extends ModuleScreen
{
    private static final int ROW_H = 22;

    private final int moduleId;
    private MinerLevelManagementModuleView module;

    private MineLevelsScreen(final IBuildingView building, final MinerLevelManagementModuleView module, @Nullable final Screen parent)
    {
        super("Niveaux de la mine", building, parent, 340);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    public static void open(final IBuildingView building, final MinerLevelManagementModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new MineLevelsScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        final MinerLevelManagementModuleView fresh = building.getModuleViewMatching(MinerLevelManagementModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final int levels = module.levelsInfo == null ? 0 : module.levelsInfo.size();
        if (levels == 0)
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "Pas encore de niveau", "le mineur creuse d'abord le puits");
        }
        else
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.OK, levels + (levels > 1 ? " niveaux" : " niveau"), "le mineur travaille au niveau " + (module.current + 1));
        }
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (module.levelsInfo == null)
        {
            return y;
        }
        for (int i = 0; i < module.levelsInfo.size(); i++)
        {
            final var info = module.levelsInfo.get(i);
            final boolean current = i == module.current;
            Ui.card(graphics, x, y, width, ROW_H - 2, current, false, false);
            final Component number = Component.literal(String.format("%02d", i + 1)).withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, number, x + 6, y + 6, current ? Palette.GREEN : Palette.INK, false);
            final int galleries = info.getA();
            graphics.drawString(font, "profondeur " + (info.getB() + 1) + " · " + (galleries == 0 ? "épuisé" : galleries + (galleries > 1 ? " galeries à creuser" : " galerie à creuser")),
              x + 26, y + 6, galleries == 0 ? Palette.MUTED : Palette.INK, false);
            if (current)
            {
                final String here = "Le mineur est ici";
                Ui.chip(graphics, font, x + width - 6 - font.width(here) - 8, y + 4, here, Ui.State.OK);
            }
            else
            {
                final String go = "Y travailler";
                final int index = i;
                button(graphics, mouseX, mouseY, x + width - 4 - buttonWidth(go), y + 3, go, Ui.ButtonStyle.PRIMARY, true, "Le mineur passe à ce niveau.", () -> {
                    module.current = index;
                    new MinerSetLevelMessage(building, index).sendToServer();
                });
            }
            y += ROW_H;
        }
        return y;
    }
}
