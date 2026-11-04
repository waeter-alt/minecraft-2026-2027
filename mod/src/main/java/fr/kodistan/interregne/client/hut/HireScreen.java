package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.util.BuildingUtils;
import com.minecolonies.core.colony.buildings.moduleviews.PupilBuildingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.network.messages.server.colony.citizen.PauseCitizenMessage;
import com.minecolonies.core.network.messages.server.colony.citizen.RestartCitizenMessage;
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
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Who works in a building: the people on the job with pause, restart and dismiss, then the candidates with the two
 * skills the job uses, best first, each hired in one click. A building with several jobs shows one tab per job.
 * Replaces MineColonies' hiring window.
 */
public final class HireScreen extends ParchmentScreen
{
    private static final int WIDTH = 400;
    private static final int ROW_H = 26;
    private static final String SKILL_KEY = "com.minecolonies.coremod.gui.citizen.skills.";

    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private IBuildingView building;
    private final List<Hit> hits = new ArrayList<>();
    @Nullable
    private String hint;
    private int scroll;
    private int contentHeight;
    private int selected;
    /**
     * Also list people who already have a job elsewhere (hiring them moves them here).
     */
    private static boolean showEmployed;

    private HireScreen(final IBuildingView building, @Nullable final Screen parent)
    {
        super(Component.literal("Embauche"), parent);
        this.building = building;
    }

    public static void open(final IBuildingView building, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new HireScreen(building, parent));
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
        final IBuildingView fresh = building.getColony().getBuilding(building.getID());
        if (fresh != null)
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
            // Opened from one of MineColonies' windows: back to the building, as its cancel button did
            building.openGui(false);
            return;
        }
        super.onClose();
    }

    /**
     * The jobs this building can take, as MineColonies offers them: some place, and allowed here (or already filled).
     */
    private List<IAssignmentModuleView> jobs()
    {
        final Predicate<JobEntry> allowed = BuildingUtils.getAllowedJobs(building.getColony().getWorld(), building.getPosition());
        return building.getModuleViews(IAssignmentModuleView.class).stream()
          .filter(m -> m.getMaxInhabitants() > 0 && (!m.getAssignedCitizens().isEmpty() || allowed.test(m.getJobEntry())))
          .toList();
    }

    private static String jobName(final IAssignmentModuleView module)
    {
        return CitizenBits.jobName(module.getJobEntry().getTranslationKey(), false);
    }

    private static String skillName(final Skill skill)
    {
        return Component.translatable(SKILL_KEY + skill.name().toLowerCase(Locale.US)).getString();
    }

    @Nullable
    private static Skill primary(final IAssignmentModuleView module)
    {
        return module instanceof WorkerBuildingModuleView worker ? worker.getPrimarySkill() : null;
    }

    @Nullable
    private static Skill secondary(final IAssignmentModuleView module)
    {
        return module instanceof WorkerBuildingModuleView worker ? worker.getSecondarySkill() : null;
    }

    private static int level(final ICitizenDataView citizen, @Nullable final Skill skill)
    {
        return skill == null ? 0 : citizen.getCitizenSkillHandler().getSkills().get(skill).getLevel();
    }

    private static boolean employedToggleAllowed(final IAssignmentModuleView module)
    {
        return module instanceof WorkerBuildingModuleView && !(module instanceof PupilBuildingModuleView);
    }

    private List<ICitizenDataView> candidates(final IAssignmentModuleView module)
    {
        final Skill primary = primary(module);
        final Skill secondary = secondary(module);
        final boolean employed = showEmployed && employedToggleAllowed(module);
        return building.getColony().getCitizens().values().stream()
          .filter(c -> !module.getAssignedCitizens().contains(c.getId()))
          .filter(c -> !building.getPosition().equals(c.getWorkBuilding()))
          .filter(c -> (employed && !c.isChild()) || module.canAssign(c))
          // Free hands first, then the best at what the job needs
          .sorted(Comparator.comparing((ICitizenDataView c) -> c.getWorkBuilding() != null)
            .thenComparing(c -> -(2 * level(c, primary) + level(c, secondary)))
            .thenComparing(ICitizenDataView::getName))
          .toList();
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        hits.clear();
        hint = null;
        final int x = x0 + 10;
        final int width = w - 24;
        int top = y0 + HEADER + 6;

        final List<IAssignmentModuleView> jobs = jobs();
        if (jobs.isEmpty())
        {
            top += Ui.banner(graphics, font, x, top, width, Ui.State.BAD, "Aucun poste", "ce bâtiment n'embauche personne");
            return;
        }
        selected = Math.min(selected, jobs.size() - 1);
        final IAssignmentModuleView module = jobs.get(selected);

        // One tab per job, when there are several
        if (jobs.size() > 1)
        {
            int tx = x;
            for (int i = 0; i < jobs.size(); i++)
            {
                final IAssignmentModuleView job = jobs.get(i);
                final String label = jobName(job) + " " + job.getAssignedCitizens().size() + "/" + job.getMaxInhabitants();
                final int tw = font.width(label) + 12;
                final boolean over = Ui.inside(mouseX, mouseY, tx, top, tw, 14);
                Ui.card(graphics, tx, top, tw, 14, i == selected, over && i != selected, false);
                graphics.drawString(font, label, tx + 6, top + 3, i == selected ? Palette.INK : Palette.MUTED, false);
                final int index = i;
                hits.add(new Hit(tx, top, tw, 14, () -> {
                    selected = index;
                    scroll = 0;
                }));
                tx += tw + 3;
            }
            top += 18;
        }

        // How many places are taken
        final int taken = module.getAssignedCitizens().size();
        final int max = module.getMaxInhabitants();
        final String places = taken + " / " + max + (max > 1 ? " postes" : " poste") + " de " + jobName(module).toLowerCase(Locale.FRENCH);
        if (taken >= max)
        {
            top += Ui.banner(graphics, font, x, top, width, Ui.State.OK, places, "complet");
        }
        else if (taken == 0)
        {
            top += Ui.banner(graphics, font, x, top, width, Ui.State.BAD, places, "personne ne travaille ici : embauche un candidat");
        }
        else
        {
            top += Ui.banner(graphics, font, x, top, width, Ui.State.WAIT, places, "il reste de la place");
        }
        top += 19;

        // Hiring mode on the left, the « employed too » toggle on the right
        final String mode = "Embauche : " + modeName(module.getHiringMode()) + " ⟳";
        final boolean overMode = Ui.inside(mouseX, mouseY, x - 2, top - 1, font.width(mode) + 4, 11);
        graphics.drawString(font, mode, x, top, overMode ? Palette.SELECTED : Palette.INK, false);
        hits.add(new Hit(x - 2, top - 1, font.width(mode) + 4, 11, () -> nextMode(module)));
        if (overMode)
        {
            hint = modeHint(module.getHiringMode());
        }
        if (employedToggleAllowed(module))
        {
            final String toggle = (showEmployed ? "☑" : "☐") + " Montrer ceux qui ont un emploi";
            final int toggleX = x + width - font.width(toggle);
            final boolean overToggle = Ui.inside(mouseX, mouseY, toggleX - 2, top - 1, font.width(toggle) + 4, 11);
            graphics.drawString(font, toggle, toggleX, top, overToggle ? Palette.SELECTED : Palette.INK, false);
            hits.add(new Hit(toggleX - 2, top - 1, font.width(toggle) + 4, 11, () -> showEmployed = !showEmployed));
            if (overToggle)
            {
                hint = "L'embaucher ici lui fera quitter son poste actuel.";
            }
        }
        top += 14;

        final int bottom = y0 + h - 5;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;

        Ui.label(graphics, font, "EN POSTE", x, y);
        y += 11;
        if (taken == 0)
        {
            graphics.drawString(font, fit("Personne pour l'instant.", width - (2)), x + 2, y, Palette.MUTED, false);
            y += 13;
        }
        for (final int id : module.getAssignedCitizens())
        {
            final ICitizenDataView citizen = building.getColony().getCitizen(id);
            if (citizen != null)
            {
                y = worker(graphics, mouseX, my, x, y, width, module, citizen, top, bottom);
            }
        }

        y = Ui.separator(graphics, x, y, width);
        final List<ICitizenDataView> candidates = candidates(module);
        Ui.label(graphics, font, "CANDIDATS", x, y);
        graphics.drawString(font, String.valueOf(candidates.size()), x + font.width("CANDIDATS") + 6, y, Palette.INK, false);
        if (primary(module) != null)
        {
            final String legend = "★ " + skillName(primary(module)) + " compte double";
            graphics.drawString(font, legend, x + width - font.width(legend), y, Palette.MUTED, false);
        }
        y += 11;
        if (candidates.isEmpty())
        {
            graphics.drawString(font, showEmployed || !employedToggleAllowed(module) ? "Personne ne peut prendre ce poste."
                                        : "Tout le monde a un emploi : coche la case pour les voir.", x + 2, y, Palette.MUTED, false);
            y += 13;
        }
        for (final ICitizenDataView citizen : candidates)
        {
            y = candidate(graphics, mouseX, my, x, y, width, module, citizen, taken >= max, top, bottom);
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

    /**
     * The two skills the job uses, as « ★ Force 12 · Agilité 8 ».
     */
    private String skillLine(final IAssignmentModuleView module, final ICitizenDataView citizen)
    {
        final Skill primary = primary(module);
        final Skill secondary = secondary(module);
        if (primary == null)
        {
            return "";
        }
        return "★ " + skillName(primary) + " " + level(citizen, primary) + (secondary == null ? "" : " · " + skillName(secondary) + " " + level(citizen, secondary));
    }

    private int worker(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final IAssignmentModuleView module, final ICitizenDataView citizen, int top, final int bottom)
    {
        Ui.card(graphics, x, y, width, ROW_H - 2, false, false, false);
        CitizenBits.face(graphics, citizen, x + 4, y + 4, 16);
        final Component name = Component.literal(citizen.getName()).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, name, x + 24, y + 3, Palette.INK, false);
        int nameEnd = x + 24 + font.width(name) + 4;
        if (citizen.isPaused())
        {
            nameEnd += Ui.chip(graphics, font, nameEnd, y + 2, "En pause", Ui.State.WAIT) + 3;
        }

        // Buttons from the right: dismiss, pause / resume, restart when paused
        int bx = x + width - 4;
        final boolean travelling = building.getColony().getTravelingManager().isTravelling(citizen);
        final String fire = "Renvoyer";
        final int fireW = font.width(fire) + 10;
        bx -= fireW;
        final boolean overFire = Ui.inside(mouseX, mouseY, bx, y + 5, fireW, 14);
        Ui.button(graphics, font, bx, y + 5, fireW, 14, fire, Ui.ButtonStyle.DANGER, !travelling, overFire);
        if (travelling)
        {
            if (overFire)
            {
                hint = citizen.getName() + " est en voyage : attends son retour.";
            }
        }
        else
        {
            addHit(bx, y + 5, fireW, 14, top, bottom, () -> module.removeCitizen(citizen));
            if (overFire)
            {
                hint = citizen.getName() + " quitte son poste et redevient sans emploi.";
            }
        }
        if (module instanceof WorkerBuildingModuleView && building instanceof AbstractBuildingView view)
        {
            final String pause = citizen.isPaused() ? "Reprendre" : "Pause";
            final int pauseW = font.width(pause) + 10;
            bx -= pauseW + 3;
            final boolean overPause = Ui.inside(mouseX, mouseY, bx, y + 5, pauseW, 14);
            Ui.button(graphics, font, bx, y + 5, pauseW, 14, pause, citizen.isPaused() ? Ui.ButtonStyle.PRIMARY : Ui.ButtonStyle.SECONDARY, true, overPause);
            addHit(bx, y + 5, pauseW, 14, top, bottom, () -> {
                new PauseCitizenMessage(view, citizen.getId()).sendToServer();
                citizen.setPaused(!citizen.isPaused());
            });
            if (overPause)
            {
                hint = citizen.isPaused() ? citizen.getName() + " reprend le travail." : citizen.getName() + " arrête de travailler et reste au bâtiment.";
            }
            if (citizen.isPaused())
            {
                final String restart = "Relancer";
                final int restartW = font.width(restart) + 10;
                bx -= restartW + 3;
                final boolean overRestart = Ui.inside(mouseX, mouseY, bx, y + 5, restartW, 14);
                Ui.button(graphics, font, bx, y + 5, restartW, 14, restart, Ui.ButtonStyle.SECONDARY, true, overRestart);
                addHit(bx, y + 5, restartW, 14, top, bottom, () -> new RestartCitizenMessage(view, citizen.getId()).sendToServer());
                if (overRestart)
                {
                    hint = "Remet " + citizen.getName() + " à zéro : sa tâche en cours est oubliée et tout reprend du début. Utile en cas de blocage.";
                }
            }
        }
        graphics.drawString(font, fit(skillLine(module, citizen), bx - x - 30), x + 24, y + 13, Palette.MUTED, false);
        return y + ROW_H;
    }

    private int candidate(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final IAssignmentModuleView module, final ICitizenDataView citizen, final boolean full, int top, final int bottom)
    {
        final boolean canTake = module.canAssign(citizen) || (showEmployed && !citizen.isChild());
        Ui.card(graphics, x, y, width, ROW_H - 2, false, false, false);
        CitizenBits.face(graphics, citizen, x + 4, y + 4, 16);

        final String action = "Embaucher";
        final int actionW = font.width(action) + 12;
        final int ax = x + width - 4 - actionW;
        final boolean over = Ui.inside(mouseX, mouseY, ax, y + 5, actionW, 14);
        final boolean enabled = !full && canTake;
        Ui.button(graphics, font, ax, y + 5, actionW, 14, action, Ui.ButtonStyle.PRIMARY, enabled, over);
        final String current = citizen.getJob() == null || citizen.getJob().isEmpty() ? null : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());
        if (enabled)
        {
            addHit(ax, y + 5, actionW, 14, top, bottom, () -> hire(module, citizen));
            if (over)
            {
                hint = current == null ? citizen.getName() + " prend le poste." : citizen.getName() + " quitte son poste de " + current.toLowerCase(Locale.FRENCH) + " pour celui-ci.";
            }
        }
        else if (over)
        {
            hint = full ? "Tous les postes sont pris : renvoie quelqu'un ou améliore le bâtiment." : citizen.getName() + " ne peut pas prendre ce poste.";
        }

        // Skills the job uses, as chips just before the button: the main one in green
        int cx = ax - 4;
        final Skill secondary = secondary(module);
        if (secondary != null)
        {
            final String text = skillName(secondary) + " " + level(citizen, secondary);
            cx -= font.width(text) + 8;
            Ui.chip(graphics, font, cx, y + 7, text, Palette.PAPER_DARK, Palette.INK, false, false);
            cx -= 3;
        }
        final Skill primary = primary(module);
        if (primary != null)
        {
            final String text = "★ " + skillName(primary) + " " + level(citizen, primary);
            cx -= font.width(text) + 8;
            Ui.chip(graphics, font, cx, y + 7, text, Ui.State.OK);
        }

        final int textW = cx - x - 30;
        graphics.drawString(font, Component.literal(fit(citizen.getName(), textW)).withStyle(ChatFormatting.BOLD), x + 24, y + 3, Palette.INK, false);
        graphics.drawString(font, fit((current == null ? "Sans emploi" : current) + " · " + home(citizen), textW), x + 24, y + 13,
          current == null ? Palette.GREEN : Palette.MUTED, false);
        return y + ROW_H;
    }

    /**
     * Where the citizen lives, measured from this building: the walk to work.
     */
    private String home(final ICitizenDataView citizen)
    {
        final BlockPos home = citizen.getHomeBuilding();
        if (home == null)
        {
            return "sans logement";
        }
        if (home.equals(building.getPosition()))
        {
            return "habite ici";
        }
        final int distance = (int) Math.sqrt(home.distSqr(building.getPosition()));
        return "habite à " + distance + (distance > 1 ? " blocs" : " bloc");
    }

    private void hire(final IAssignmentModuleView module, final ICitizenDataView citizen)
    {
        // Someone with a job leaves it first, as MineColonies does
        if (citizen.getWorkBuilding() != null && module instanceof WorkerBuildingModuleView && citizen.getJobView() != null)
        {
            final IBuildingView old = building.getColony().getBuilding(citizen.getWorkBuilding());
            if (old != null)
            {
                final IAssignmentModuleView oldModule = old.getModuleViewMatching(IAssignmentModuleView.class,
                  m -> m.getJobEntry() == citizen.getJobView().getEntry());
                if (oldModule != null)
                {
                    oldModule.removeCitizen(citizen);
                }
            }
        }
        module.addCitizen(citizen);
    }

    // --- Hiring mode: workplaces cannot be locked ---

    static String modeName(final HiringMode mode)
    {
        return switch (mode)
        {
            case DEFAULT -> "selon la colonie";
            case AUTO -> "automatique";
            case MANUAL -> "manuelle";
            case LOCKED -> "verrouillée";
        };
    }

    private String modeHint(final HiringMode mode)
    {
        final boolean colonyManual = building.getColony().isManualHiring();
        return switch (mode)
        {
            case DEFAULT -> "Suit le réglage de la colonie (" + (colonyManual ? "manuelle" : "automatique") + "). Clique pour changer.";
            case AUTO -> "La colonie place d'elle-même ses citoyens sans emploi ici. Clique pour changer.";
            case MANUAL -> "Toi seul choisis qui travaille ici. Clique pour changer.";
            case LOCKED -> "Personne n'est placé ici. Clique pour changer.";
        };
    }

    private void nextMode(final IAssignmentModuleView module)
    {
        int index = module.getHiringMode().ordinal() + 1;
        if (index == HiringMode.LOCKED.ordinal())
        {
            index++;
        }
        module.setHiringMode(HiringMode.values()[index % HiringMode.values().length]);
    }

    private void addHit(final int x, final int y, final int width, final int height, int top, final int bottom, final Runnable action)
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
