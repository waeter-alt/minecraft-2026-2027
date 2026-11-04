package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.core.client.gui.WindowInteraction;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import fr.kodistan.interregne.client.catalogue.CatalogueIndex;
import fr.kodistan.interregne.client.ui.Icon;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The citizen window: who they are, where they work and live, their health, hunger, mood and skills; what makes them
 * happy or not; what they ask for (and handing it over); their family.
 */
public final class CitizenScreen extends ParchmentScreen
{
    private static final int NAV_W = 112;

    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation HEART_EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");

    private static final String SKILL_KEY = "com.minecolonies.coremod.gui.citizen.skills.";

    private enum Page
    {
        SHEET("Fiche", "info"),
        HAPPINESS("Bonheur", "happiness"),
        REQUESTS("Demandes", "requests"),
        FAMILY("Famille", "family");

        private final String label;
        private final String icon;

        Page(final String label, final String icon)
        {
            this.label = label;
            this.icon = icon;
        }
    }

    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private ICitizenDataView citizen;
    private final IColonyView colony;
    private Page page = Page.SHEET;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    private String hint;
    private int scroll;
    private int contentHeight;

    private CitizenScreen(final ICitizenDataView citizen, final IColonyView colony, @Nullable final Screen parent)
    {
        super(Component.literal(citizen.getName()), parent);
        this.citizen = citizen;
        this.colony = colony;
    }

    public static void open(final ICitizenDataView citizen, @Nullable final Screen parent)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        final IColonyView colony = com.minecolonies.api.colony.IColonyManager.getInstance().getColonyView(citizen.getColonyId(), minecraft.level.dimension());
        if (colony != null)
        {
            minecraft.setScreen(new CitizenScreen(citizen, colony, parent));
        }
    }

    @Override
    protected String crumb()
    {
        return parent instanceof ColonyHubScreen ? "Colonie" : null;
    }

    @Override
    public void tick()
    {
        final ICitizenDataView fresh = colony.getCitizen(citizen.getId());
        if (fresh != null)
        {
            citizen = fresh;
        }
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        hits.clear();
        slots.clear();
        hint = null;
        renderNav(graphics, mouseX, mouseY);

        final int x = contentLeft();
        final int width = contentWidth();
        final int top = y0 + HEADER + 4;
        final int bottom = y0 + h - 5;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (bottom - top)));
        final int my = Ui.inside(mouseX, mouseY, x, top, width, bottom - top) ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(x, top, x + width, bottom);
        int y = top - scroll;
        y = switch (page)
        {
            case SHEET -> renderSheet(graphics, mouseX, my, x, y, width);
            case HAPPINESS -> renderHappiness(graphics, x, y, width);
            case REQUESTS -> renderRequests(graphics, mouseX, my, x, y, width);
            case FAMILY -> renderFamily(graphics, mouseX, my, x, y, width);
        };
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

    private int contentLeft()
    {
        return x0 + NAV_W + 7;
    }

    private int contentWidth()
    {
        return w - NAV_W - 16;
    }

    /**
     * A click target in the scrolling content: only its visible part counts.
     */
    private void addHit(final int x, final int y, final int width, final int height, final Runnable action)
    {
        final int top = Math.max(y, y0 + HEADER + 4);
        final int bottom = Math.min(y + height, y0 + h - 5);
        if (bottom > top)
        {
            hits.add(new Hit(x, top, width, bottom - top, action));
        }
    }

    // --- Left navigation ---

    private void renderNav(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int x = x0 + 5;
        final int width = NAV_W - 8;
        verticalRule(graphics, x0 + NAV_W);
        Ui.label(graphics, font, "CITOYEN", x + 2, y0 + 25);
        int y = y0 + 36;
        for (final Page p : Page.values())
        {
            String label = p.label;
            if (p == Page.REQUESTS)
            {
                final int count = requests().size();
                label = count > 0 ? label + " (" + count + ")" : label;
            }
            Ui.navItem(graphics, font, x, y, width, Icon.module(p.icon), label, p == page, Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW));
            hits.add(new Hit(x, y, width, Ui.NAV_ROW, () ->
            {
                page = p;
                scroll = 0;
            }));
            y += Ui.NAV_ROW + 1;
        }

        final boolean talk = citizen.hasVisibleInteractions();
        y = y0 + h - 6 - (talk ? 2 : 1) * (Ui.NAV_ROW + 1);
        graphics.fill(x + 2, y - 3, x + width - 2, y - 2, Palette.RULE);
        Ui.navItem(graphics, font, x, y, width, Icon.module("inventory"), "Inventaire", false, Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW));
        hits.add(new Hit(x, y, width, Ui.NAV_ROW, () ->
        {
            final net.minecraft.world.entity.LivingEntity entity = CitizenBits.entity(citizen);
            new OpenInventoryMessage(colony, citizen.getName(), entity != null ? entity.getId() : citizen.getEntityId()).sendToServer();
        }));
        if (talk)
        {
            y += Ui.NAV_ROW + 1;
            Ui.navItem(graphics, font, x, y, width, Icon.of(Items.WRITABLE_BOOK), "Lui parler", false, Ui.inside(mouseX, mouseY, x, y, width, Ui.NAV_ROW));
            hits.add(new Hit(x, y, width, Ui.NAV_ROW, () -> new WindowInteraction(citizen).open()));
        }
    }

    // --- Sheet ---

    private int renderSheet(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int bodyW = 70;
        final int top = y;
        Ui.plate(graphics, x, y, x + bodyW, y + 104, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        CitizenBits.body(graphics, citizen, x + 2, y + 2, x + bodyW - 2, y + 102, mouseX, mouseY == Integer.MIN_VALUE ? y + 30 : mouseY);

        final int rx = x + bodyW + 8;
        final int rw = width - bodyW - 8;
        final IBuildingView work = citizen.getWorkBuilding() == null ? null : colony.getBuilding(citizen.getWorkBuilding());
        final String job = citizen.getJob() == null || citizen.getJob().isEmpty() ? null : CitizenBits.jobName(citizen.getJob(), citizen.isFemale());
        if (job == null && citizen.isChild())
        {
            y = line(graphics, rx, y, rw, "Enfant", Palette.INK);
        }
        else if (job == null || work == null)
        {
            y = line(graphics, rx, y, rw, "Sans emploi", Palette.RED);
        }
        else
        {
            graphics.drawString(font, Component.literal(job).withStyle(ChatFormatting.BOLD), rx, y, Palette.INK, false);
            y += 12;
            y = link(graphics, mouseX, mouseY, rx, y, rw, "Travaille à : ", buildingName(work), () -> openBuilding(work));
        }
        final IBuildingView home = citizen.getHomeBuilding() == null ? null : colony.getBuilding(citizen.getHomeBuilding());
        if (home == null)
        {
            y = line(graphics, rx, y, rw, "Sans logis", Palette.RED);
        }
        else
        {
            y = link(graphics, mouseX, mouseY, rx, y, rw, "Habite : ", buildingName(home), () -> openBuilding(home));
        }
        y = line(graphics, rx, y, rw, "En ce moment, " + (citizen.isSick() ? "est malade" : CitizenBits.activity(citizen)), Palette.MUTED);

        int chipX = rx;
        final List<CitizenBits.Chip> chips = CitizenBits.chips(citizen);
        for (final CitizenBits.Chip chip : chips)
        {
            final int chipW = font.width(chip.text()) + 8;
            final boolean over = chip.action() != null && Ui.inside(mouseX, mouseY, chipX, y, chipW, 11);
            Ui.chip(graphics, font, chipX, y, chip.text(), chip.soft(), chip.ink(), chip.action() != null, over);
            if (chip.action() != null)
            {
                addHit(chipX, y, chipW, 11, chip.action());
                if (over)
                {
                    hint = "Cliquez pour régler son problème";
                }
            }
            chipX += chipW + 3;
        }
        if (!chips.isEmpty())
        {
            y += 15;
        }

        y = bar(graphics, rx, y, "Santé", citizen.getHealth(), citizen.getMaxHealth(), HEART_FULL, HEART_HALF, HEART_EMPTY);
        y = bar(graphics, rx, y, "Faim", citizen.getSaturation(), 20, FOOD_FULL, FOOD_HALF, FOOD_EMPTY);
        final double happiness = citizen.getHappiness();
        graphics.drawString(font, "Bonheur", rx, y + 1, Palette.INK, false);
        final Ui.State mood = happiness < 4 ? Ui.State.BAD : happiness < 7 ? Ui.State.WAIT : Ui.State.OK;
        Ui.progressBar(graphics, rx + 48, y + 2, 81, 6, (float) (happiness / 10), mood);
        graphics.drawString(font, format(happiness) + " / 10", rx + 134, y + 1, Palette.INK, false);
        y += 13;

        y = Math.max(y, top + 108);
        y = Ui.separator(graphics, x, y - 4, width);
        return renderSkills(graphics, x, y, width, work);
    }

    private int line(final GuiGraphics graphics, final int x, final int y, final int width, final String text,
      final int color)
    {
        graphics.drawString(font, fit(text, width), x, y, color, false);
        return y + 12;
    }

    /**
     * « prefix » then an underlined link that opens something.
     */
    private int link(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String prefix,
      final String target, final Runnable action)
    {
        graphics.drawString(font, prefix, x, y, Palette.INK, false);
        final int lx = x + font.width(prefix);
        final String shown = fit(target + " ›", width - font.width(prefix));
        final boolean over = Ui.inside(mouseX, mouseY, lx, y - 1, font.width(shown), 10);
        graphics.drawString(font, Component.literal(shown).withStyle(ChatFormatting.UNDERLINE), lx, y, over ? Palette.SELECTED : Palette.LEATHER, false);
        addHit(lx, y - 1, font.width(shown), 10, action);
        return y + 12;
    }

    /**
     * Ten icons for a value out of max (hearts, drumsticks), as the game shows them.
     */
    private int bar(final GuiGraphics graphics, final int x, final int y, final String label, final double value, final double max,
      final ResourceLocation full, final ResourceLocation half, final ResourceLocation empty)
    {
        graphics.drawString(font, label, x, y + 1, Palette.INK, false);
        final double points = value / Math.max(1, max) * 20;
        for (int i = 0; i < 10; i++)
        {
            final int ix = x + 48 + i * 8;
            graphics.blitSprite(empty, ix, y, 9, 9);
            if (points >= i * 2 + 2)
            {
                graphics.blitSprite(full, ix, y, 9, 9);
            }
            else if (points >= i * 2 + 1)
            {
                graphics.blitSprite(half, ix, y, 9, 9);
            }
        }
        return y + 12;
    }

    private int renderSkills(final GuiGraphics graphics, final int x, int y, final int width, @Nullable final IBuildingView work)
    {
        Skill primary = null;
        Skill secondary = null;
        if (work != null)
        {
            for (final WorkerBuildingModuleView module : work.getModuleViews(WorkerBuildingModuleView.class))
            {
                if (module.getAssignedCitizens().contains(citizen.getId()))
                {
                    primary = module.getPrimarySkill();
                    secondary = module.getSecondarySkill();
                }
            }
        }
        Ui.label(graphics, font, "COMPÉTENCES", x, y);
        if (primary != null)
        {
            graphics.drawString(font, "★ utiles à son métier", x + font.width("COMPÉTENCES") + 8, y, Palette.SELECTED, false);
        }
        y += 12;
        final List<Skill> skills = new ArrayList<>(List.of(Skill.values()));
        // The job's two skills first
        if (secondary != null)
        {
            skills.remove(secondary);
            skills.add(0, secondary);
        }
        if (primary != null)
        {
            skills.remove(primary);
            skills.add(0, primary);
        }
        final int columnW = (width - 8) / 2;
        for (int i = 0; i < skills.size(); i++)
        {
            final Skill skill = skills.get(i);
            final int cx = x + (i % 2) * (columnW + 8);
            final int cy = y + (i / 2) * 11;
            final boolean useful = skill == primary || skill == secondary;
            final String name = (useful ? "★ " : "") + Component.translatable(SKILL_KEY + skill.name().toLowerCase(Locale.US)).getString();
            final int level = citizen.getCitizenSkillHandler().getSkills().get(skill).getLevel();
            graphics.drawString(font, name, cx, cy, useful ? Palette.SELECTED : Palette.INK, false);
            final String value = Integer.toString(level);
            graphics.drawString(font, value, cx + columnW - font.width(value), cy, Palette.INK, false);
            graphics.fill(cx, cy + 9, cx + columnW, cy + 10, Palette.PAPER_DARK);
            graphics.fill(cx, cy + 9, cx + Math.round(columnW * Math.min(1, level / 99F)), cy + 10, useful ? Palette.SELECTED : Palette.RULE);
        }
        return y + (skills.size() + 1) / 2 * 11 + 4;
    }

    // --- Happiness ---

    /**
     * One factor of happiness, as the page shows it.
     */
    private record Factor(HappinessFactors.Reading reading, double factor, double weight)
    {
    }

    private int renderHappiness(final GuiGraphics graphics, final int x, int y, final int width)
    {
        final double happiness = citizen.getHappiness();
        final Ui.State mood = happiness < 4 ? Ui.State.BAD : happiness < 7 ? Ui.State.WAIT : Ui.State.OK;
        final String title = happiness < 4 ? "Malheureux" : happiness < 7 ? "Mitigé" : "Heureux";
        Ui.banner(graphics, font, x, y, width, mood, title, format(happiness) + " / 10");
        y += 20;

        final List<Factor> bad = new ArrayList<>();
        final List<Factor> good = new ArrayList<>();
        for (final String id : citizen.getHappinessHandler().getModifiers())
        {
            final IHappinessModifier modifier = citizen.getHappinessHandler().getModifier(id);
            final double factor = modifier.getFactor(null);
            if (factor == 1.0)
            {
                continue;
            }
            final Factor entry = new Factor(HappinessFactors.read(id, factor > 1.0, citizen, colony), factor, modifier.getWeight());
            (factor > 1.0 ? good : bad).add(entry);
        }
        // Worst first: what weighs most and pulls hardest
        bad.sort((a, b) -> Double.compare(a.weight() * (a.factor() - 1), b.weight() * (b.factor() - 1)));
        good.sort((a, b) -> Double.compare(b.weight() * (b.factor() - 1), a.weight() * (a.factor() - 1)));

        if (bad.isEmpty() && good.isEmpty())
        {
            graphics.drawString(font, "Rien ne pèse sur son humeur en ce moment.", x, y, Palette.MUTED, false);
            return y + 12;
        }
        if (!bad.isEmpty())
        {
            Ui.label(graphics, font, "CE QUI LE REND MALHEUREUX", x, y);
            y += 12;
            for (final Factor factor : bad)
            {
                y = factorCard(graphics, x, y, width, factor, false);
            }
            y += 4;
        }
        if (!good.isEmpty())
        {
            Ui.label(graphics, font, "CE QUI LE REND HEUREUX", x, y);
            y += 12;
            for (final Factor factor : good)
            {
                y = factorCard(graphics, x, y, width, factor, true);
            }
        }
        return y;
    }

    /**
     * A card: coloured edge, the factor's name and how much it weighs, why, and what to do about it.
     */
    private int factorCard(final GuiGraphics graphics, final int x, final int y, final int width, final Factor factor, final boolean good)
    {
        final HappinessFactors.Reading reading = factor.reading();
        final int textX = x + 20;
        final int textW = width - 26;
        final List<net.minecraft.util.FormattedCharSequence> why = font.split(Component.literal(reading.why()), textW);
        final List<net.minecraft.util.FormattedCharSequence> fix = reading.fix() == null ? List.of() : font.split(Component.literal("→ " + reading.fix()), textW);
        final int height = 16 + why.size() * 9 + fix.size() * 9;

        final int soft = good ? Palette.GREEN_SOFT : factor.factor() > 0.75 ? Palette.AMBER_SOFT : Palette.RED_SOFT;
        final int strong = good ? Palette.GREEN : factor.factor() > 0.75 ? Palette.AMBER : Palette.RED;
        Ui.plate(graphics, x, y, x + width, y + height, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        graphics.fill(x + 1, y + 1, x + 4, y + height - 1, strong);
        final String icon = good ? "happy_icon" : factor.factor() > 0.75 ? "unsatisfied_icon" : "unhappy_icon";
        Icon.texture(ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/" + icon + ".png"), 16).draw(graphics, x + 7, y + 4, 10);
        graphics.drawString(font, Component.literal(reading.name()).withStyle(ChatFormatting.BOLD), textX, y + 5, Palette.INK, false);

        // How much this factor counts in the mood, from its weight
        final String impact = factor.weight() >= 3 ? "compte beaucoup" : factor.weight() >= 2 ? "compte" : "compte peu";
        final int impactW = font.width(impact) + 8;
        Ui.chip(graphics, font, x + width - impactW - 4, y + 3, impact, soft, good ? Palette.GREEN : factor.factor() > 0.75 ? Palette.AMBER_TEXT : Palette.RED,
          false, false);

        int ty = y + 16;
        for (final net.minecraft.util.FormattedCharSequence line : why)
        {
            graphics.drawString(font, line, textX, ty, Palette.INK, false);
            ty += 9;
        }
        for (final net.minecraft.util.FormattedCharSequence line : fix)
        {
            graphics.drawString(font, line, textX, ty, Palette.LEATHER, false);
            ty += 9;
        }
        return y + height + 3;
    }

    // --- Requests ---

    private List<IRequest<?>> requests()
    {
        final Map<Object, IRequest<?>> found = new LinkedHashMap<>();
        try
        {
            for (final BlockPos pos : new BlockPos[] {citizen.getWorkBuilding(), citizen.getHomeBuilding()})
            {
                final IBuildingView building = pos == null ? null : colony.getBuilding(pos);
                if (building != null)
                {
                    building.getOpenRequests(citizen).forEach(r -> found.put(r.getId(), r));
                }
            }
        }
        catch (final RuntimeException e)
        {
            // The colony's request system is not synced yet: nothing to show
        }
        return new ArrayList<>(found.values());
    }

    private int renderRequests(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IRequest<?>> requests = requests();
        if (requests.isEmpty())
        {
            graphics.drawString(font, citizen.getName() + " ne demande rien.", x, y, Palette.MUTED, false);
            return y + 12;
        }
        Ui.label(graphics, font, "CE QU'IL ATTEND", x, y);
        y += 12;
        final boolean creative = minecraft.player.isCreative();
        for (final IRequest<?> request : requests)
        {
            Ui.card(graphics, x, y, width, 22, false, Ui.inside(mouseX, mouseY, x, y, width, 22), false);
            final List<ItemStack> stacks = request.getDisplayStacks();
            final ItemStack stack = stacks.isEmpty() ? new ItemStack(Items.PAPER) : stacks.get(0);
            Icon.of(stack).draw(graphics, x + 3, y + 3, 16);
            slots.add(new Ui.Slot(x + 3, y + 3, 16, stack));

            final int carried = CitizenBits.carried(request);
            final boolean canGive = creative || carried > 0;
            final int giveW = font.width("Donner") + 12;
            final int giveX = x + width - giveW - 3;
            final String state = CitizenBits.requestStateText(request);
            final int stateW = font.width(state) + 8;
            final int stateX = giveX - stateW - 4;
            Ui.chip(graphics, font, stateX, y + 6, state, CitizenBits.requestState(request));
            graphics.drawString(font, fit(request.getShortDisplayString().getString(), stateX - x - 26), x + 23, y + 7, Palette.INK, false);

            final boolean over = Ui.inside(mouseX, mouseY, giveX, y + 3, giveW, 16);
            Ui.button(graphics, font, giveX, y + 3, giveW, 16, "Donner", Ui.ButtonStyle.PRIMARY, canGive, over);
            if (canGive)
            {
                addHit(giveX, y + 3, giveW, 16, () ->
                {
                    if (!CitizenBits.give(colony, citizen, request))
                    {
                        minecraft.player.displayClientMessage(Component.literal("Ce que tu portes en armure ou en main gauche ne peut pas être donné."), true);
                    }
                });
            }
            else if (over)
            {
                hint = "Tu n'as pas cet objet sur toi.";
            }
            // The rest of the card opens the request in full; the button above wins, being added first
            addHit(x, y, width, 22, () -> RequestScreen.open(request, colony, citizen, this));
            y += 24;
        }
        return y;
    }

    // --- Family ---

    private int renderFamily(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        Ui.label(graphics, font, "PARENTS", x, y);
        y += 12;
        final String parentA = citizen.getParents().getA();
        final String parentB = citizen.getParents().getB();
        y = line(graphics, x + 4, y, width, parentA.isEmpty() && parentB.isEmpty() ? "Inconnus"
                                              : (parentA.isEmpty() ? "?" : parentA) + " et " + (parentB.isEmpty() ? "?" : parentB), Palette.INK);
        y = familySection(graphics, mouseX, mouseY, x, y + 4, width, "PARTENAIRE",
          citizen.getPartner() == null || citizen.getPartner() == 0 ? List.of() : List.of(citizen.getPartner()), "Célibataire");
        y = familySection(graphics, mouseX, mouseY, x, y, width, "ENFANTS", citizen.getChildren(), "Aucun");
        return familySection(graphics, mouseX, mouseY, x, y, width, "FRÈRES ET SŒURS", citizen.getSiblings(), "Aucun");
    }

    private int familySection(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final String title,
      final List<Integer> ids, final String none)
    {
        Ui.label(graphics, font, title, x, y);
        y += 12;
        boolean any = false;
        for (final int id : ids)
        {
            final ICitizenDataView other = colony.getCitizen(id);
            if (other == null)
            {
                continue;
            }
            any = true;
            final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 20);
            Ui.card(graphics, x, y, width, 20, false, over, false);
            CitizenBits.face(graphics, other, x + 2, y + 2, 16);
            graphics.drawString(font, Component.literal(other.getName()).withStyle(ChatFormatting.BOLD), x + 22, y + 6, Palette.INK, false);
            graphics.drawString(font, "›", x + width - 10, y + 6, Palette.MUTED, false);
            addHit(x, y, width, 20, () -> open(other, this));
            y += 22;
        }
        if (!any)
        {
            y = line(graphics, x + 4, y, width, none, Palette.MUTED);
        }
        return y + 4;
    }

    // --- Shared ---

    private static String buildingName(final IBuildingView building)
    {
        final String custom = building.getCustomName();
        if (custom != null && !custom.isEmpty())
        {
            return custom;
        }
        return CatalogueIndex.buildingName(building.getBuildingType().getRegistryName().getPath(),
          Component.translatable(building.getBuildingDisplayName()).getString());
    }

    private void openBuilding(final IBuildingView building)
    {
        HutScreen.openFrom(building, this);
    }

    private static String format(final double value)
    {
        return String.format(Locale.FRANCE, "%.1f", value).replace(",0", "");
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
