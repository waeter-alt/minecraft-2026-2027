package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.CompactColonyReference;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.network.messages.server.colony.TeleportToColonyMessage;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The colonies this one is allied with, with a way to travel there, and those it is feuding with. Replaces
 * MineColonies' alliance page.
 */
public final class AlliancesScreen extends ModuleScreen
{
    /**
     * The ally whose « Y aller » was clicked once: a second click confirms the journey.
     */
    private int confirming = -1;

    private AlliancesScreen(final BuildingTownHall.View townHall, @Nullable final Screen parent)
    {
        super("Alliances", townHall, parent, 380);
    }

    public static void open(final BuildingTownHall.View townHall, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new AlliancesScreen(townHall, parent));
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int allies = building.getColony().getAllies().size();
        final int feuds = building.getColony().getFeuds().size();
        y += Ui.banner(graphics, font, x, y, width, feuds > 0 ? Ui.State.WAIT : Ui.State.OK, allies + (allies > 1 ? " alliés" : " allié"),
          feuds == 0 ? "aucun ennemi" : feuds + (feuds > 1 ? " ennemis" : " ennemi"));
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<CompactColonyReference> allies = building.getColony().getAllies();
        Ui.label(graphics, font, "ALLIÉS", x, y);
        y += 11;
        if (allies.isEmpty())
        {
            graphics.drawString(font, fit("Aucun. Une colonie devient alliée quand ses joueurs ont un rang ici.", width - (2)), x + 2, y, Palette.MUTED, false);
            y += 14;
        }
        final int minLevel = MineColonies.getConfig().getServer().minThLevelToTeleport.get();
        for (int i = 0; i < allies.size(); i++)
        {
            final CompactColonyReference ally = allies.get(i);
            y = row(graphics, x, y, width, ally, Palette.GREEN);
            final boolean levelOk = building.getBuildingLevel() >= minLevel;
            final boolean allowed = ((BuildingTownHall.View) building).canPlayerUseTP();
            final boolean can = !ally.hasTownHall || levelOk && allowed;
            final String why = can ? "Te téléporte à leur hôtel de ville." : !levelOk ? "Possible à partir du niveau " + minLevel + " de l'hôtel de ville."
                                                                                       : "Ton rang ne te permet pas de voyager ainsi.";
            final int index = i;
            final String label = confirming == i ? "Confirmer le voyage" : "Y aller";
            button(graphics, mouseX, mouseY, x + width - 4 - buttonWidth(label), y - 20, label, Ui.ButtonStyle.PRIMARY, can, why, () -> {
                if (confirming != index)
                {
                    confirming = index;
                    return;
                }
                new TeleportToColonyMessage(ally.dimension, ally.id).sendToServer();
                minecraft.setScreen(null);
            });
        }
        y += 4;

        final List<CompactColonyReference> feuds = building.getColony().getFeuds();
        Ui.label(graphics, font, "ENNEMIS", x, y);
        y += 11;
        if (feuds.isEmpty())
        {
            graphics.drawString(font, "Aucun.", x + 2, y, Palette.MUTED, false);
            return y + 14;
        }
        for (final CompactColonyReference feud : feuds)
        {
            y = row(graphics, x, y, width, feud, Palette.RED);
        }
        return y;
    }

    private int row(final GuiGraphics graphics, final int x, int y, final int width, final CompactColonyReference colony, final int mark)
    {
        Ui.card(graphics, x, y, width, 20, false, false, false);
        graphics.fill(x + 1, y + 1, x + 4, y + 19, mark);
        graphics.drawString(font, Component.literal(fit(colony.name, width - 160)).withStyle(ChatFormatting.BOLD), x + 9, y + 6, Palette.INK, false);
        final long distance = BlockPosUtil.getDistance2D(colony.center, building.getPosition());
        graphics.drawString(font, "à " + distance + " blocs", x + 9 + Math.min(font.width(colony.name) + 8, width - 150), y + 6, Palette.MUTED, false);
        return y + 22;
    }
}
