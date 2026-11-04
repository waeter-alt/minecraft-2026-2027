package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
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

import static com.minecolonies.api.util.constant.TranslationConstants.PARTIAL_STATS_MODIFIER_NAME;

/**
 * What a building did: one line per statistic with its number in bold, over yesterday, the last 7 days, the last 100
 * days or all time, biggest first; empty lines hidden on request. Replaces MineColonies' statistics page.
 */
public final class StatsScreen extends ModuleScreen
{
    private static final List<String> PERIODS = List.of("Hier", "7 jours", "100 jours", "Depuis le début");
    private static final int[] DAYS = {1, 7, 100, -1};

    private final int moduleId;
    private BuildingStatisticsModuleView module;
    private static int period;
    private static boolean hideZero = true;

    private StatsScreen(final IBuildingView building, final BuildingStatisticsModuleView module, @Nullable final Screen parent)
    {
        super("Statistiques", building, parent, 360);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    public static void open(final IBuildingView building, final BuildingStatisticsModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new StatsScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
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
        final IStatisticsManager stats = module.getBuildingStatisticsManager();
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
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        segmented(graphics, mouseX, mouseY, x, y, PERIODS, period, index -> {
            period = index;
            resetScroll();
        });
        final String label = "Masquer les zéros";
        checkbox(graphics, mouseX, mouseY, x + width - font.width("☐ " + label), y + 3, label, hideZero, () -> hideZero = !hideZero);
        return y + 20;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
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
