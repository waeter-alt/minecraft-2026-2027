package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.network.messages.server.colony.building.guard.GuardSetMinePosMessage;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The guards protecting the mine: every guard whose post is set to « patrol the mine », sent here or called back in
 * one click, up to what the mine's level allows. Replaces MineColonies' mine guard page.
 */
public final class MineGuardsScreen extends ModuleScreen
{
    private static final int ROW_H = 24;

    private record Guard(ICitizenDataView citizen, AbstractBuildingGuards.View post)
    {
    }

    private MineGuardsScreen(final IBuildingView building, @Nullable final Screen parent)
    {
        super("Gardes de la mine", building, parent, 340);
    }

    public static void open(final IBuildingView building, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new MineGuardsScreen(building, parent));
    }

    private int maxGuards()
    {
        return switch (building.getBuildingLevel())
        {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            default -> 0;
        };
    }

    private List<Guard> guards()
    {
        final List<Guard> guards = new ArrayList<>();
        for (final IBuildingView view : building.getColony().getBuildings())
        {
            if (view instanceof AbstractBuildingGuards.View post
                  && view.getModuleViewByType(SettingsModuleView.class).getSetting(AbstractBuildingGuards.GUARD_TASK).getValue().equals(GuardTaskSetting.PATROL_MINE))
            {
                for (final int id : post.getGuards())
                {
                    final ICitizenDataView citizen = building.getColony().getCitizen(id);
                    if (citizen != null)
                    {
                        guards.add(new Guard(citizen, post));
                    }
                }
            }
        }
        return guards;
    }

    private boolean here(final AbstractBuildingGuards.View post)
    {
        return building.getPosition().equals(post.getMinePos());
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final long assigned = guards().stream().filter(g -> here(g.post())).count();
        y += Ui.banner(graphics, font, x, y, width, assigned == 0 ? Ui.State.WAIT : Ui.State.OK, assigned + " / " + maxGuards() + (maxGuards() > 1 ? " gardes" : " garde"),
          assigned == 0 ? "personne ne protège la mine" : "ils patrouillent dans la mine");
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<Guard> guards = guards();
        if (guards.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal("Aucun garde disponible. Dans les réglages d'une tour de garde, choisis la tâche « Patrouiller la mine »."),
              x + 2, y + 2, width - 4, Palette.MUTED, 10);
        }
        final long assigned = guards.stream().filter(g -> here(g.post())).count();
        for (final Guard guard : guards)
        {
            final boolean mine = here(guard.post());
            Ui.card(graphics, x, y, width, ROW_H - 2, mine, false, false);
            CitizenBits.face(graphics, guard.citizen(), x + 4, y + 3, 16);
            final String action = mine ? "Rappeler" : "Envoyer";
            boolean enabled = true;
            final String why;
            if (mine)
            {
                why = guard.citizen().getName() + " retourne à sa tour.";
            }
            else if (guard.post().getMinePos() != null)
            {
                enabled = false;
                why = "Garde déjà une autre mine.";
            }
            else
            {
                enabled = assigned < maxGuards();
                why = enabled ? guard.citizen().getName() + " vient patrouiller dans la mine." : "Assez de gardes : améliore la mine pour en avoir plus.";
            }
            final int bx = x + width - 4 - buttonWidth(action);
            button(graphics, mouseX, mouseY, bx, y + 4, action, mine ? Ui.ButtonStyle.SECONDARY : Ui.ButtonStyle.PRIMARY, enabled, why, () -> {
                if (mine)
                {
                    new GuardSetMinePosMessage(guard.post()).sendToServer();
                    guard.post().setMinePos(null);
                }
                else
                {
                    new GuardSetMinePosMessage(guard.post(), building.getPosition()).sendToServer();
                    guard.post().setMinePos(building.getPosition());
                }
            });
            graphics.drawString(font, Component.literal(fit(guard.citizen().getName(), bx - x - 30)).withStyle(ChatFormatting.BOLD), x + 24, y + 3, Palette.INK, false);
            graphics.drawString(font, fit(RequestScreen.nameOf(guard.post()), bx - x - 30), x + 24, y + 12, Palette.MUTED, false);
            y += ROW_H;
        }
        return y;
    }
}
