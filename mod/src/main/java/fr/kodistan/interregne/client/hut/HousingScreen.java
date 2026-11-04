package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.core.colony.buildings.moduleviews.LivingBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.minecolonies.core.network.messages.server.colony.building.home.AssignUnassignMessage;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Who lives in a house: its residents, then everyone who could move in (homeless first, then those who would live
 * closer to their work), each moved in or out in one click when housing is in manual mode. Replaces MineColonies'
 * housing window.
 */
public final class HousingScreen extends ParchmentScreen
{
    private static final int WIDTH = 380;
    private static final int ROW_H = 26;
    /**
     * Beyond this walk to work, a home is too far (MineColonies' own threshold).
     */
    private static final int FAR = 300;

    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private LivingBuildingView building;
    private final List<Hit> hits = new ArrayList<>();
    @Nullable
    private String hint;
    private int scroll;
    private int contentHeight;

    private HousingScreen(final LivingBuildingView building, @Nullable final Screen parent)
    {
        super(Component.literal("Logement"), parent);
        this.building = building;
    }

    public static void open(final LivingBuildingView building, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new HousingScreen(building, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        w = Math.min(width - 16, WIDTH);
        x0 = (width - w) / 2;
    }

    @Override
    public void tick()
    {
        if (building.getColony().getBuilding(building.getID()) instanceof LivingBuildingView fresh)
        {
            building = fresh;
        }
    }

    @Override
    @Nullable
    protected String crumb()
    {
        return RequestScreen.nameOf(building);
    }

    @Override
    public void onClose()
    {
        if (parent == null)
        {
            building.openGui(false);
            return;
        }
        super.onClose();
    }

    private IColonyView colony()
    {
        return building.getColony();
    }

    /**
     * Whether the player chooses who lives here, or the colony does.
     */
    private boolean manual()
    {
        final HiringMode mode = building.getHiringMode();
        return mode == HiringMode.MANUAL || (mode == HiringMode.DEFAULT && colony().isManualHousing());
    }

    private List<ICitizenDataView> residents()
    {
        final List<ICitizenDataView> residents = new ArrayList<>();
        for (final int id : building.getModuleViewByType(LivingBuildingModuleView.class).getAssignedCitizens())
        {
            final ICitizenDataView citizen = colony().getCitizen(id);
            if (citizen != null)
            {
                residents.add(citizen);
            }
        }
        return residents;
    }

    /**
     * Everyone not living here, except those who live at their workplace; homeless first, then by the walk this home
     * would give them.
     */
    private List<ICitizenDataView> candidates()
    {
        return colony().getCitizens().values().stream()
          .filter(c -> (c.getHomeBuilding() == null || !Objects.equals(c.getHomeBuilding(), c.getWorkBuilding())) && !building.getPosition().equals(c.getHomeBuilding()))
          .sorted(Comparator.comparing((ICitizenDataView c) -> c.getHomeBuilding() != null)
            .thenComparingInt(c -> c.getWorkBuilding() == null ? Integer.MAX_VALUE : distance(c.getWorkBuilding(), building.getPosition()))
            .thenComparing(ICitizenDataView::getName))
          .toList();
    }

    private static int distance(final BlockPos a, final BlockPos b)
    {
        return (int) Math.sqrt(a.distSqr(b));
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        hits.clear();
        hint = null;
        final int x = x0 + 10;
        final int width = w - 24;
        int top = y0 + HEADER + 6;

        final List<ICitizenDataView> residents = residents();
        final int max = building.getMax();
        final String places = residents.size() + " / " + max + (max > 1 ? " lits" : " lit");
        final boolean manual = manual();
        if (building.getHiringMode() == HiringMode.LOCKED)
        {
            Ui.banner(graphics, font, x, top, width, Ui.State.WAIT, places, "maison verrouillée : personne n'emménage");
        }
        else if (residents.size() >= max)
        {
            Ui.banner(graphics, font, x, top, width, Ui.State.OK, places, "maison pleine");
        }
        else
        {
            Ui.banner(graphics, font, x, top, width, Ui.State.WAIT, places, manual ? "choisis qui emménage ci-dessous" : "la colonie la remplira d'elle-même");
        }
        top += 19;

        final String mode = "Attribution : " + HireScreen.modeName(building.getHiringMode()) + " ⟳";
        final boolean overMode = Ui.inside(mouseX, mouseY, x - 2, top - 1, font.width(mode) + 4, 11);
        graphics.drawString(font, mode, x, top, overMode ? Palette.SELECTED : Palette.INK, false);
        hits.add(new Hit(x - 2, top - 1, font.width(mode) + 4, 11, this::nextMode));
        if (overMode)
        {
            hint = modeHint();
        }
        if (!manual)
        {
            final String note = "passe en manuelle pour choisir";
            graphics.drawString(font, note, x + width - font.width(note), top, Palette.MUTED, false);
        }
        top += 14;

        final int bottom = y0 + h - 5;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;

        Ui.label(graphics, font, "HABITENT ICI", x, y);
        y += 11;
        if (residents.isEmpty())
        {
            graphics.drawString(font, "Personne pour l'instant.", x + 2, y, Palette.MUTED, false);
            y += 13;
        }
        for (final ICitizenDataView citizen : residents)
        {
            y = resident(graphics, mouseX, my, x, y, width, citizen, manual, top, bottom);
        }

        y = Ui.separator(graphics, x, y, width);
        final List<ICitizenDataView> candidates = candidates();
        Ui.label(graphics, font, "POURRAIENT EMMÉNAGER", x, y);
        graphics.drawString(font, String.valueOf(candidates.size()), x + font.width("POURRAIENT EMMÉNAGER") + 6, y, Palette.INK, false);
        y += 11;
        if (candidates.isEmpty())
        {
            graphics.drawString(font, "Tout le monde est déjà logé.", x + 2, y, Palette.MUTED, false);
            y += 13;
        }
        for (final ICitizenDataView citizen : candidates)
        {
            y = candidate(graphics, mouseX, my, x, y, width, citizen, manual, residents.size() >= max, top, bottom);
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
        if (hint != null)
        {
            graphics.renderTooltip(font, font.split(Component.literal(hint), 220), mouseX, mouseY);
        }
    }

    @Nullable
    private static String job(final ICitizenDataView citizen)
    {
        return citizen.getJob() == null || citizen.getJob().isEmpty() ? null : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());
    }

    private int resident(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final ICitizenDataView citizen, final boolean manual, final int top, final int bottom)
    {
        Ui.card(graphics, x, y, width, ROW_H - 2, false, false, false);
        CitizenBits.face(graphics, citizen, x + 4, y + 4, 16);

        final String action = "Faire partir";
        final int actionW = font.width(action) + 10;
        final int ax = x + width - 4 - actionW;
        final boolean travelling = colony().getTravelingManager().isTravelling(citizen);
        final boolean enabled = manual && !travelling;
        final boolean over = Ui.inside(mouseX, mouseY, ax, y + 5, actionW, 14);
        Ui.button(graphics, font, ax, y + 5, actionW, 14, action, Ui.ButtonStyle.DANGER, enabled, over);
        if (enabled)
        {
            addHit(ax, y + 5, actionW, 14, top, bottom, () -> moveOut(citizen));
        }
        if (over)
        {
            hint = !manual ? "L'attribution est automatique : passe-la en manuelle pour choisir."
                     : travelling ? citizen.getName() + " est en voyage : attends son retour."
                         : citizen.getName() + " quitte la maison et se retrouve sans logement.";
        }

        int textW = ax - x - 30;
        graphics.drawString(font, Component.literal(fit(citizen.getName(), textW)).withStyle(ChatFormatting.BOLD), x + 24, y + 3, Palette.INK, false);
        final String job = job(citizen);
        final BlockPos work = citizen.getWorkBuilding();
        if (job == null || work == null)
        {
            graphics.drawString(font, "Sans emploi", x + 24, y + 13, Palette.MUTED, false);
        }
        else
        {
            final int walk = distance(work, building.getPosition());
            graphics.drawString(font, fit(job + " · travail à " + walk + " blocs" + (walk > FAR ? ", trop loin" : ""), textW), x + 24, y + 13,
              walk > FAR ? Palette.RED : Palette.MUTED, false);
        }
        return y + ROW_H;
    }

    private int candidate(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final ICitizenDataView citizen, final boolean manual, final boolean full, final int top, final int bottom)
    {
        Ui.card(graphics, x, y, width, ROW_H - 2, false, false, false);
        CitizenBits.face(graphics, citizen, x + 4, y + 4, 16);

        final String action = "Loger ici";
        final int actionW = font.width(action) + 12;
        final int ax = x + width - 4 - actionW;
        final boolean enabled = manual && !full;
        final boolean over = Ui.inside(mouseX, mouseY, ax, y + 5, actionW, 14);
        Ui.button(graphics, font, ax, y + 5, actionW, 14, action, Ui.ButtonStyle.PRIMARY, enabled, over);
        if (enabled)
        {
            addHit(ax, y + 5, actionW, 14, top, bottom, () -> moveIn(citizen));
        }

        // The point of moving: off the street, or a shorter walk to work
        final BlockPos home = citizen.getHomeBuilding();
        final BlockPos work = citizen.getWorkBuilding();
        final int here = work == null ? -1 : distance(work, building.getPosition());
        final int now = work == null || home == null ? -1 : distance(work, home);
        int cx = ax - 4;
        if (home == null)
        {
            cx -= font.width("Sans logement") + 8;
            Ui.chip(graphics, font, cx, y + 7, "Sans logement", Ui.State.BAD);
        }
        else if (here >= 0 && now >= 0 && here < now)
        {
            final String text = "Plus près : " + now + " → " + here;
            cx -= font.width(text) + 8;
            Ui.chip(graphics, font, cx, y + 7, text, Ui.State.OK);
        }
        if (over)
        {
            hint = !manual ? "L'attribution est automatique : passe-la en manuelle pour choisir."
                     : full ? "La maison est pleine : fais partir quelqu'un ou améliore-la."
                         : citizen.getName() + (home == null ? " emménage ici." : " déménage ici et libère son lit actuel.");
        }

        final int textW = cx - x - 30;
        graphics.drawString(font, Component.literal(fit(citizen.getName(), textW)).withStyle(ChatFormatting.BOLD), x + 24, y + 3, Palette.INK, false);
        final String job = job(citizen);
        final String line = (job == null ? "Sans emploi" : job) + (here >= 0 ? " · son travail serait à " + here + " blocs" : "");
        graphics.drawString(font, fit(line, textW), x + 24, y + 13, Palette.MUTED, false);
        return y + ROW_H;
    }

    private void moveIn(final ICitizenDataView citizen)
    {
        if (building.getResidents().size() >= building.getMax())
        {
            return;
        }
        building.addResident(citizen.getId());
        citizen.setHomeBuilding(building.getPosition());
        new AssignUnassignMessage(building, true, citizen.getId(), null).sendToServer();
    }

    private void moveOut(final ICitizenDataView citizen)
    {
        building.removeResident(citizen.getId());
        citizen.setHomeBuilding(null);
        new AssignUnassignMessage(building, false, citizen.getId(), null).sendToServer();
    }

    private String modeHint()
    {
        return switch (building.getHiringMode())
        {
            case DEFAULT -> "Suit le réglage de la colonie (" + (colony().isManualHousing() ? "manuelle" : "automatique") + "). Clique pour changer.";
            case AUTO -> "La colonie loge ici d'elle-même ses citoyens sans toit. Clique pour changer.";
            case MANUAL -> "Toi seul choisis qui habite ici. Clique pour changer.";
            case LOCKED -> "Plus personne n'emménage ; les habitants restent. Clique pour changer.";
        };
    }

    private void nextMode()
    {
        building.setHiringMode(HiringMode.values()[(building.getHiringMode().ordinal() + 1) % HiringMode.values().length]);
    }

    private void addHit(final int x, final int y, final int width, final int height, final int top, final int bottom, final Runnable action)
    {
        final int hitTop = Math.max(y, top);
        final int hitBottom = Math.min(y + height, bottom);
        if (hitBottom > hitTop)
        {
            hits.add(new Hit(x, hitTop, width, hitBottom - hitTop, action));
        }
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
