package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IStackBasedTask;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.moduleviews.RequestTaskModuleView;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A building's queue of jobs (the courier's deliveries and pickups…): what to carry, from whom to whom, its priority
 * and whether it is under way. Each row opens the request in full. Replaces MineColonies' task list.
 */
public final class TasksScreen extends ModuleScreen
{
    private static final int ROW_H = 24;

    private TasksScreen(final IBuildingView building, @Nullable final Screen parent)
    {
        super("Tâches", building, parent, 380);
    }

    public static void open(final IBuildingView building, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new TasksScreen(building, parent));
    }

    private List<IRequest<?>> tasks()
    {
        final List<IRequest<?>> tasks = new ArrayList<>();
        final RequestTaskModuleView module = building.getModuleViewByType(RequestTaskModuleView.class);
        final IRequestManager manager = building.getColony().getRequestManager();
        if (module == null || manager == null)
        {
            return tasks;
        }
        try
        {
            for (final IToken<?> token : module.getTasks())
            {
                final IRequest<?> request = manager.getRequestForToken(token);
                if (request != null)
                {
                    tasks.add(request);
                }
            }
        }
        catch (final RuntimeException e)
        {
            // Request system not synced yet
        }
        return tasks;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final List<IRequest<?>> tasks = tasks();
        final long running = tasks.stream().filter(t -> t.getState() == RequestState.IN_PROGRESS).count();
        if (tasks.isEmpty())
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.OK, "Rien en attente", "aucune tâche pour ce bâtiment");
        }
        else
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, tasks.size() + (tasks.size() > 1 ? " tâches" : " tâche"),
              running > 0 ? running + " en cours, la première en haut" : "la première en haut");
        }
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IColonyView colony = building.getColony();
        final IRequestManager manager = colony.getRequestManager();
        for (final IRequest<?> request : tasks())
        {
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, ROW_H - 2);
            Ui.card(graphics, x, y, width, ROW_H - 2, false, over, false);

            // What is carried
            final ItemStack stack;
            final String what;
            if (request instanceof IStackBasedTask task)
            {
                stack = task.getTaskStack().copy();
                stack.setCount(task.getDisplayCount());
                what = task.getDisplayPrefix().getString().trim() + " " + stack.getHoverName().getString();
            }
            else
            {
                stack = RequestScreen.shown(request);
                what = request.getShortDisplayString().getString().replace("§f", "");
            }
            Icon.of(stack).draw(graphics, x + 3, y + 3, 16);
            graphics.renderItemDecorations(font, stack, x + 3, y + 3);
            addSlot(x + 3, y + 3, 16, stack, mouseY);

            // Right: priority and state
            int right = x + width - 4;
            final boolean running = request.getState() == RequestState.IN_PROGRESS;
            final String state = running ? "En cours" : "En attente";
            right -= font.width(state) + 8;
            Ui.chip(graphics, font, right, y + 6, state, running ? Ui.State.OK : Ui.State.WAIT);
            if (request.getRequest() instanceof IDeliverymanRequestable delivery)
            {
                final String priority = "Priorité " + delivery.getPriority();
                right -= font.width(priority) + 11;
                Ui.chip(graphics, font, right, y + 6, priority, Palette.PAPER_DARK, Palette.INK, false, false);
            }

            final int textW = right - x - 30;
            graphics.drawString(font, Component.literal(fit(what, textW)).withStyle(ChatFormatting.BOLD), x + 23, y + 3, Palette.INK, false);
            graphics.drawString(font, fit(route(manager, request), textW), x + 23, y + 12, Palette.MUTED, false);
            addHit(x, y, width, ROW_H - 2, () -> RequestScreen.open(request, colony, null, this));
            if (over && hint == null)
            {
                hint = "Voir la demande en entier";
            }
            y += ROW_H;
        }
        return y;
    }

    /**
     * « for X » or « X → Y » when the task serves another building's request.
     */
    private static String route(final IRequestManager manager, final IRequest<?> request)
    {
        final String requester = request.getRequester().getRequesterDisplayName(manager, request).getString();
        IRequest<?> parent = request.hasParent() ? manager.getRequestForToken(request.getParent()) : null;
        // Climb past parents made by the same requester: the one that matters is who needs it in the end
        while (parent != null && parent.getRequester().getLocation().equals(request.getRequester().getLocation()) && parent.hasParent())
        {
            final IRequest<?> next = manager.getRequestForToken(parent.getParent());
            if (next == null)
            {
                break;
            }
            parent = next;
        }
        if (parent == null)
        {
            return "pour " + requester;
        }
        return requester + " → " + parent.getRequester().getRequesterDisplayName(manager, parent).getString();
    }
}
