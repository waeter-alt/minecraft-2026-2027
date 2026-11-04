package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CombinedHiringLimitModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.minecolonies.api.util.constant.TranslationConstants.PARTIAL_STATS_MODIFIER_NAME;

/**
 * What a building did: one line per statistic with its number in bold, over yesterday, the last 7 days, the last 100
 * days or all time, biggest first; empty lines hidden on request. Replaces MineColonies' statistics page. For the
 * whole colony (no module), its population and trades come first: this replaces the town hall's statistics too.
 */
public final class StatsScreen extends ModuleScreen
{
    private static final List<String> PERIODS = List.of("Hier", "7 jours", "100 jours", "Depuis le début");
    private static final int[] DAYS = {1, 7, 100, -1};

    private final int moduleId;
    @Nullable
    private BuildingStatisticsModuleView module;
    private static int period;
    private static boolean hideZero = true;

    private StatsScreen(final IBuildingView building, final BuildingStatisticsModuleView module, @Nullable final Screen parent)
    {
        super("Statistiques", building, parent, 360);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    private StatsScreen(final IBuildingView townHall, @Nullable final Screen parent)
    {
        super("Statistiques", townHall, parent, 400);
        this.module = null;
        this.moduleId = -1;
    }

    public static void open(final IBuildingView building, final BuildingStatisticsModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new StatsScreen(building, module, parent));
    }

    /**
     * The whole colony's statistics, from its town hall.
     */
    public static void openColony(final IBuildingView townHall, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new StatsScreen(townHall, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        if (module == null)
        {
            return;
        }
        final BuildingStatisticsModuleView fresh = building.getModuleViewMatching(BuildingStatisticsModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
    }

    private record Line(String id, int value)
    {
    }

    private List<Line> lines()
    {
        final IStatisticsManager stats = module == null ? building.getColony().getStatisticsManager() : module.getBuildingStatisticsManager();
        final int days = DAYS[period];
        final int today = building.getColony().getDay();
        final List<Line> lines = new ArrayList<>();
        for (final String id : stats.getStatTypes())
        {
            final int value = days > 0 ? stats.getStatsInPeriod(id, today - days, today) : stats.getStatTotal(id);
            if (value > 0 || !hideZero)
            {
                lines.add(new Line(id, value));
            }
        }
        lines.sort(Comparator.comparingInt((Line l) -> -l.value()).thenComparing(Line::id));
        return lines;
    }

    /**
     * MineColonies' sentence for a statistic (« 12 arbres abattus »), its number pulled out to be shown in bold.
     */
    private static String sentence(final Line line)
    {
        final String id = line.id();
        if (id.contains(";"))
        {
            final String[] split = id.split(";");
            return Component.translatable(PARTIAL_STATS_MODIFIER_NAME + split[0], line.value(), Component.translatable(split[1])).getString();
        }
        return Component.translatable(PARTIAL_STATS_MODIFIER_NAME + id, line.value()).getString();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (module == null)
        {
            y = population(graphics, x, y, width);
        }
        final int segW = segmented(graphics, mouseX, mouseY, x, y, PERIODS, period, index -> {
            period = index;
            resetScroll();
        });
        final String label = "Masquer les zéros";
        final int boxW = font.width("☐ " + label);
        // Beside the periods when there is room, under them otherwise
        final boolean beside = segW + 8 + boxW <= width;
        checkbox(graphics, mouseX, mouseY, beside ? x + width - boxW : x, beside ? y + 3 : y + 19, label, hideZero, () -> hideZero = !hideZero);
        return y + (beside ? 20 : 34);
    }

    /**
     * The colony's headcount against its beds, as a banner.
     */
    private int population(final GuiGraphics graphics, final int x, final int y, final int width)
    {
        final IColonyView colony = building.getColony();
        final int citizens = colony.getCitizens().size();
        final int limit = Math.max(citizens, colony.getCitizenCountLimit());
        int children = 0;
        int idle = 0;
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            if (citizen.isChild())
            {
                children++;
            }
            else if (citizen.getJobView() == null)
            {
                idle++;
            }
        }
        final boolean full = citizens >= limit;
        Ui.banner(graphics, font, x, y, width, full ? Ui.State.WAIT : Ui.State.OK, citizens + " / " + limit + " citoyens",
          (full ? "plus de lit libre · " : "") + idle + " sans emploi · " + children + (children > 1 ? " enfants" : " enfant"));
        return y + 21;
    }

    /**
     * Each trade with how many work it and how many could, counted as MineColonies does (shared hiring limits
     * included).
     */
    private Map<String, int[]> trades()
    {
        final Map<String, int[]> trades = new TreeMap<>();
        for (final IBuildingView each : building.getColony().getBuildings())
        {
            if (!(each instanceof AbstractBuildingView))
            {
                continue;
            }
            for (final WorkerBuildingModuleView worker : each.getModuleViews(WorkerBuildingModuleView.class))
            {
                int max = worker.getMaxInhabitants();
                if (worker instanceof CombinedHiringLimitModuleView)
                {
                    int assigned = 0;
                    for (final WorkerBuildingModuleView combined : each.getModuleViews(WorkerBuildingModuleView.class))
                    {
                        assigned += combined.getAssignedCitizens().size();
                    }
                    max = max - assigned + worker.getAssignedCitizens().size();
                }
                String name = Component.translatable(worker.getJobDisplayName().toLowerCase(Locale.US)).getString();
                name = name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
                final int[] counts = trades.computeIfAbsent(name, key -> new int[2]);
                counts[0] += worker.getAssignedCitizens().size();
                counts[1] += max;
            }
        }
        return trades;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (module == null)
        {
            // Trades three across: every post filled in green, a free one in amber
            Ui.label(graphics, font, "MÉTIERS", x, y);
            y += 11;
            final int columnW = (width - 6) / 3;
            int i = 0;
            for (final Map.Entry<String, int[]> trade : trades().entrySet())
            {
                final int cx = x + (i % 3) * (columnW + 3);
                final int cy = y + (i / 3) * 13;
                final int[] counts = trade.getValue();
                final String count = counts[0] + "/" + counts[1];
                final int color = counts[1] == 0 ? Palette.MUTED : counts[0] >= counts[1] ? Palette.GREEN : Palette.AMBER_TEXT;
                graphics.drawString(font, fit(trade.getKey(), columnW - font.width(count) - 8), cx + 2, cy + 2, Palette.INK, false);
                graphics.drawString(font, Component.literal(count).withStyle(ChatFormatting.BOLD), cx + columnW - 2 - font.width(count), cy + 2, color, false);
                i++;
            }
            y += ((i + 2) / 3) * 13 + 6;
            Ui.label(graphics, font, "ACTIVITÉ", x, y);
            y += 12;
        }
        final List<Line> lines = lines();
        if (lines.isEmpty())
        {
            graphics.drawString(font, period == 3 ? "Rien n'a encore été compté ici." : "Rien sur cette période.", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        for (int i = 0; i < lines.size(); i++)
        {
            final Line line = lines.get(i);
            if (i % 2 == 0)
            {
                graphics.fill(x, y - 1, x + width, y + 11, Palette.PAPER_LIGHT);
            }
            final String number = String.valueOf(line.value());
            final Component bold = Component.literal(number).withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, bold, x + 40 - font.width(bold), y + 1, line.value() == 0 ? Palette.MUTED : Palette.INK, false);
            // The sentence without its leading number, which is already in the column
            String text = sentence(line).trim();
            if (text.startsWith(number))
            {
                text = text.substring(number.length()).trim();
            }
            graphics.drawString(font, fit(text, width - 52), x + 48, y + 1, Palette.INK, false);
            if (Ui.inside(mouseX, mouseY, x, y - 1, width, 12) && font.width(text) > width - 52)
            {
                hint = sentence(line);
            }
            y += 12;
        }
        return y;
    }
}
