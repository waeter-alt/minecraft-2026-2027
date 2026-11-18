package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import net.minecraft.ChatFormatting;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Citoyens : la liste des habitants de la colonie.
 * Une page du hub de colonie ({@link ColonyHubScreen}) : elle dessine dans le cadre du hub et lui emprunte son état.
 */
final class HubCitizensPage
{
    private final ColonyHubScreen hub;

    HubCitizensPage(final ColonyHubScreen hub)
    {
        this.hub = hub;
    }

    // --- Citizens ---

    int renderCitizens(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<ICitizenDataView> citizens = new ArrayList<>(hub.colony.getCitizens().values());
        final Map<ICitizenDataView, List<CitizenBits.Chip>> chips = new LinkedHashMap<>();
        citizens.forEach(c -> chips.put(c, CitizenBits.chips(c)));
        // Those with something to settle first
        citizens.sort(Comparator.<ICitizenDataView>comparingInt(c -> -(int) chips.get(c).stream().filter(ch -> ch.action() != null).count())
          .thenComparing(ICitizenDataView::getName));
        Ui.label(graphics, hub.font(), citizens.size() + " / " + hub.colony.getCitizenCountLimit() + " CITOYENS", x, y);
        y += 12;
        for (final ICitizenDataView citizen : citizens)
        {
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 22);
            Ui.card(graphics, x, y, width, 22, false, over, false);
            CitizenBits.face(graphics, citizen, x + 3, y + 3, 16);
            final String job = citizen.getJob() == null || citizen.getJob().isEmpty() ? (citizen.isChild() ? "Enfant" : "Sans emploi")
                                 : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());

            // Chips right-aligned; a click on one settles the problem, elsewhere opens the sheet
            int chipX = x + width - 12;
            final List<CitizenBits.Chip> list = chips.get(citizen);
            int chipsW = 0;
            for (final CitizenBits.Chip chip : list)
            {
                chipsW += hub.font().width(chip.text()) + 8 + 3;
            }
            // Name and job keep clear of the chips
            final int textW = width - 12 - chipsW - 23 - 8;
            final String name = citizen.getName();
            final String shownName = hub.font().width(Component.literal(name).withStyle(ChatFormatting.BOLD)) <= textW ? name : hub.fitText(name, textW - 6);
            graphics.drawString(hub.font(), Component.literal(shownName).withStyle(ChatFormatting.BOLD), x + 23, y + 3, Palette.INK, false);
            graphics.drawString(hub.font(), hub.fitText(job, textW), x + 23, y + 12, Palette.MUTED, false);
            for (int i = list.size() - 1; i >= 0; i--)
            {
                final CitizenBits.Chip chip = list.get(i);
                final int chipW = hub.font().width(chip.text()) + 8;
                chipX -= chipW;
                final boolean overChip = chip.action() != null && Ui.inside(mouseX, mouseY, chipX, y + 6, chipW, 11);
                if (Ui.inside(mouseX, mouseY, chipX, y + 6, chipW, 11) && chip.hover() != null)
                {
                    hub.hint = chip.hover();
                }
                Ui.chip(graphics, hub.font(), chipX, y + 6, chip.text(), chip.soft(), chip.ink(), chip.action() != null, overChip);
                if (chip.action() != null)
                {
                    hub.addHit(chipX, y + 6, chipW, 11, chip.action());
                }
                chipX -= 3;
            }
            graphics.drawString(hub.font(), "›", x + width - 9, y + 7, Palette.MUTED, false);
            hub.addHit(x, y, width, 22, () -> CitizenScreen.open(citizen, hub));
            y += 24;
        }
        return y;
    }
}
