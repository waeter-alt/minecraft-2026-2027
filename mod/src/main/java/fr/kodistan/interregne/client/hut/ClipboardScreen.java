package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The clipboard: what the colony is waiting for the player to bring, nearest first, with what they already carry.
 * Each row opens the request in full. Replaces MineColonies' clipboard window.
 */
public final class ClipboardScreen extends ParchmentScreen
{
    private static final int ROW_H = 24;

    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private record Entry(IRequest<?> request, @Nullable IBuildingView building, int distance, boolean secondary)
    {
    }

    private final IColonyView colony;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    private String hint;
    private int scroll;
    private int contentHeight;
    /**
     * Hides what workers asked for themselves and can wait (their « async » requests), like MineColonies' toggle.
     */
    private static boolean urgentOnly;

    private ClipboardScreen(final IColonyView colony)
    {
        super(Component.literal("Presse-papiers"), null);
        this.colony = colony;
    }

    public static void open(final IColonyView colony)
    {
        Minecraft.getInstance().setScreen(new ClipboardScreen(colony));
    }

    @Override
    @Nullable
    protected String crumb()
    {
        return colony.getName();
    }

    /**
     * The requests handed to players (MineColonies' player and retrying resolvers), each brought back to the request
     * someone actually made.
     */
    private List<Entry> entries()
    {
        final List<Entry> entries = new ArrayList<>();
        final IRequestManager manager = colony.getRequestManager();
        if (manager == null)
        {
            return entries;
        }
        final Set<IToken<?>> secondary = new HashSet<>();
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            if (citizen.getJobView() != null)
            {
                secondary.addAll(citizen.getJobView().getAsyncRequests());
            }
        }
        try
        {
            final Set<IToken<?>> tokens = new HashSet<>();
            tokens.addAll(manager.getPlayerResolver().getAllAssignedRequests());
            tokens.addAll(manager.getRetryingRequestResolver().getAllAssignedRequests());
            final Set<IToken<?>> seen = new HashSet<>();
            final BlockPos player = minecraft.player.blockPosition();
            for (final IToken<?> token : tokens)
            {
                IRequest<?> request = manager.getRequestForToken(token);
                while (request != null && request.hasParent())
                {
                    request = manager.getRequestForToken(request.getParent());
                }
                if (request == null || !seen.add(request.getId()))
                {
                    continue;
                }
                final BlockPos pos = request.getRequester().getLocation().getInDimensionLocation();
                entries.add(new Entry(request, colony.getBuilding(pos), (int) Math.sqrt(player.distSqr(pos)), secondary.contains(request.getId())));
            }
        }
        catch (final RuntimeException e)
        {
            // The colony's request system is not synced yet: nothing to show
            return new ArrayList<>();
        }
        entries.sort(Comparator.comparingInt(Entry::distance));
        return entries;
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        hits.clear();
        slots.clear();
        hint = null;
        final int x = x0 + 10;
        final int width = w - 24;
        int top = y0 + HEADER + 6;

        final List<Entry> all = entries();
        final List<Entry> entries = urgentOnly ? all.stream().filter(e -> !e.secondary()).toList() : all;
        final int carrying = (int) entries.stream().filter(e -> CitizenBits.carried(e.request()) > 0).count();
        if (entries.isEmpty())
        {
            Ui.banner(graphics, font, x, top, width, Ui.State.OK, "Rien à apporter", "la colonie ne t'attend pour rien");
        }
        else
        {
            Ui.banner(graphics, font, x, top, width, Ui.State.WAIT, entries.size() + (entries.size() > 1 ? " demandes" : " demande") + " pour toi",
              carrying > 0 ? "tu as déjà de quoi en régler " + carrying : "les plus proches en premier");
        }
        top += 19;

        // The toggle: only what blocks someone
        final String toggle = (urgentOnly ? "☑" : "☐") + " Seulement l'urgent";
        final boolean overToggle = Ui.inside(mouseX, mouseY, x - 2, top - 1, font.width(toggle) + 4, 11);
        graphics.drawString(font, toggle, x, top, overToggle ? Palette.SELECTED : Palette.INK, false);
        hits.add(new Hit(x - 2, top - 1, font.width(toggle) + 4, 11, () -> urgentOnly = !urgentOnly));
        if (overToggle)
        {
            hint = "Cache ce que les ouvriers demandent pour plus tard et qui ne les bloque pas.";
        }
        if (all.size() != entries.size())
        {
            final String hidden = (all.size() - entries.size()) + " masquées";
            graphics.drawString(font, hidden, x + width - font.width(hidden), top, Palette.MUTED, false);
        }
        top += 14;

        final int bottom = y0 + h - 5;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;
        for (final Entry entry : entries)
        {
            y = row(graphics, mouseX, my, x, y, width, entry, top, bottom);
        }
        graphics.disableScissor();
        contentHeight = y + scroll - top;
        if (contentHeight > bottom - top)
        {
            final int track = bottom - top;
            final int thumb = Math.max(12, track * track / contentHeight);
            final int thumbY = top + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            graphics.fill(x + width + 2, top, x + width + 4, bottom, Palette.PAPER_DARK);
            graphics.fill(x + width + 2, thumbY, x + width + 4, thumbY + thumb, Palette.RULE);
        }

        for (final Ui.Slot slot : slots)
        {
            if (Ui.inside(mouseX, mouseY, slot.x(), slot.y(), slot.size(), slot.size()))
            {
                graphics.renderTooltip(font, slot.stack(), mouseX, mouseY);
            }
        }
        if (hint != null)
        {
            graphics.renderTooltip(font, font.split(Component.literal(hint), 200), mouseX, mouseY);
        }
    }

    private int row(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final Entry entry,
      final int top, final int bottom)
    {
        final IRequest<?> request = entry.request();
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, ROW_H - 2);
        Ui.card(graphics, x, y, width, ROW_H - 2, false, over, false);
        final ItemStack stack = RequestScreen.shown(request);
        Icon.of(stack).draw(graphics, x + 3, y + 3, 16);
        slots.add(new Ui.Slot(x + 3, y + 3, 16, stack));

        // Right side: what the player already carries, then the state
        int right = x + width - 12;
        final String state = CitizenBits.requestStateText(request);
        final int stateW = font.width(state) + 8;
        right -= stateW;
        Ui.chip(graphics, font, right, y + 6, state, CitizenBits.requestState(request));
        final int carried = CitizenBits.carried(request);
        if (carried > 0)
        {
            final String has = "Tu en as " + carried;
            final int hasW = font.width(has) + 8;
            right -= hasW + 4;
            Ui.chip(graphics, font, right, y + 6, has, Ui.State.OK);
        }

        final int textW = right - x - 30;
        graphics.drawString(font, Component.literal(fit(request.getShortDisplayString().getString(), textW)).withStyle(ChatFormatting.BOLD), x + 23, y + 3,
          Palette.INK, false);
        final String where = (entry.building() == null ? "" : RequestScreen.nameOf(entry.building()) + " · ") + "à " + entry.distance()
                               + (entry.distance() > 1 ? " blocs" : " bloc") + (entry.secondary() ? " · peut attendre" : "");
        graphics.drawString(font, fit(where, textW), x + 23, y + 12, Palette.MUTED, false);
        graphics.drawString(font, "›", x + width - 9, y + 7, Palette.MUTED, false);

        final int hitTop = Math.max(y, top);
        final int hitBottom = Math.min(y + ROW_H - 2, bottom);
        if (hitBottom > hitTop)
        {
            hits.add(new Hit(x, hitTop, width, hitBottom - hitTop, () -> RequestScreen.open(request, colony, null, this)));
        }
        return y + ROW_H;
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        for (final Hit hit : List.copyOf(hits))
        {
            if (Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height()))
            {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        scroll -= (int) (scrollY * 12);
        return true;
    }
}
