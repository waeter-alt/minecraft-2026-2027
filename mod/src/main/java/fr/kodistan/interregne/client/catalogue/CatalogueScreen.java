package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.CrateRows;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.ScrollGrid;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.mixin.client.AbstractBlueprintManipulationWindowAccessor;
import fr.kodistan.interregne.mixin.client.WindowExtendedBuildToolAccessor;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import fr.kodistan.interregne.region.Crate;
import fr.kodistan.interregne.region.CrateRequirements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The build catalogue: what to build, sorted by need, with its price and what it brings, on one page.
 * Opens in place of the build tool's folder menus; "Placer" hands the blueprint to the build tool for placement.
 */
public final class CatalogueScreen extends ParchmentScreen
{
    private static final int LEFT_W = 104;
    private static final int RIGHT_W = 138;
    private static final int CARD_H = 32;
    private static final int CARD_MIN_W = 100;
    private static final int GAP = 4;
    private static final int ROW = 17;

    private static CatalogueIndex.Need lastNeed = CatalogueIndex.Need.FOOD;

    private final WindowExtendedBuildTool buildTool;
    private final BlockPos anchor;
    private CompletableFuture<List<CatalogueIndex.Entry>> loading;
    private boolean loaded;
    private int ticks;
    private List<CatalogueIndex.Entry> entries = List.of();
    private CatalogueIndex.Need need = lastNeed;
    @Nullable
    private CatalogueIndex.Entry selected;
    private int variant;
    private final ScrollGrid grid = new ScrollGrid(2, GAP);
    private final Map<CatalogueIndex.Entry, List<MutableComponent>> requirements = new HashMap<>();
    private final List<int[]> variantSquares = new ArrayList<>();
    private final List<Ui.Slot> flowSlots = new ArrayList<>();

    private CatalogueScreen(final WindowExtendedBuildTool buildTool, final BlockPos anchor)
    {
        super(Component.literal("Que voulez-vous construire ?"), null);
        this.buildTool = buildTool;
        this.anchor = anchor;
    }

    /**
     * Open the catalogue for this build tool window, at the position the player clicked.
     */
    public static void open(final WindowExtendedBuildTool buildTool, final BlockPos anchor)
    {
        net.minecraft.client.Minecraft.getInstance().setScreen(new CatalogueScreen(buildTool, anchor));
    }

    /**
     * Opens the style gallery directly, skipping the catalogue click, for the UiShot dev capture tool.
     */
    public static void openStyleGalleryForUiShot(final WindowExtendedBuildTool buildTool, final BlockPos anchor)
    {
        net.minecraft.client.Minecraft.getInstance().setScreen(new StyleScreen(new CatalogueScreen(buildTool, anchor)));
    }

    @Override
    protected void init()
    {
        super.init();
        if (loading == null)
        {
            reload();
        }
    }

    void reload()
    {
        StructurePacks.ensureSelectedPack();
        final StructurePackMeta pack = StructurePacks.getSelectedPack();
        loaded = false;
        loading = pack == null || minecraft.level == null ? CompletableFuture.completedFuture(List.of())
          : CatalogueIndex.of(pack.getName(), minecraft.level.registryAccess());
        CatalogueIndex.preloadAll();
        tick();
    }

    @Override
    public void tick()
    {
        // The colony's research reaches the client after a while: check the locks again every second
        if (++ticks % 20 == 0)
        {
            requirements.clear();
        }
        if (!loaded && loading != null && loading.isDone())
        {
            loaded = true;
            final String previous = selected == null ? null : selected.key();
            entries = loading.getNow(List.of());
            requirements.clear();
            selected = null;
            variant = 0;
            visible().stream().filter(e -> e.key().equals(previous)).findFirst()
              .or(() -> visible().stream().findFirst()).ifPresent(this::select);
        }
    }

    private List<CatalogueIndex.Entry> visible()
    {
        return entries.stream().filter(e -> e.need() == need)
          .sorted(Comparator.comparing(this::locked).thenComparingInt(CatalogueIndex::rank)).toList();
    }

    private void select(final CatalogueIndex.Entry entry)
    {
        selected = entry;
        variant = 0;
    }

    private List<MutableComponent> requirementsOf(final CatalogueIndex.Entry entry)
    {
        if (entry.hut() == null || minecraft.player == null || minecraft.level == null)
        {
            return List.of();
        }
        return requirements.computeIfAbsent(entry, e -> {
            try
            {
                return e.hut().areRequirementsMet(minecraft.level, anchor, minecraft.player) ? List.<MutableComponent>of()
                  : readable(e.hut().getRequirements(minecraft.level, anchor, minecraft.player));
            }
            catch (final RuntimeException ex)
            {
                return List.of();
            }
        });
    }

    /**
     * MineColonies' research lines ("Le/La X nécessite une recherche" / "à l'Université pour construire") as one plain sentence.
     */
    private static List<MutableComponent> readable(final List<MutableComponent> requirements)
    {
        final List<MutableComponent> lines = new ArrayList<>();
        for (final MutableComponent requirement : requirements)
        {
            final String key = requirement.getContents() instanceof TranslatableContents t ? t.getKey() : "";
            if (key.equals("com.minecolonies.coremod.research.item1"))
            {
                lines.add(Component.literal("Se débloque par une recherche à l'Université."));
            }
            else if (!key.equals("com.minecolonies.coremod.research.item2"))
            {
                lines.add(requirement);
            }
        }
        return lines;
    }

    private boolean locked(final CatalogueIndex.Entry entry)
    {
        return !requirementsOf(entry).isEmpty();
    }

    private int cost(final CatalogueIndex.Entry entry)
    {
        return entry.hut() != null ? BuildCosts.forLevel(1) : BuildCosts.forDecoration(entry.blocks());
    }

    private Map<Crate, Integer> crates(final CatalogueIndex.Entry entry)
    {
        return entry.hut() != null ? CrateRequirements.forLevel(1) : CrateRequirements.forDecoration(entry.blocks());
    }

    private long owned()
    {
        if (minecraft.player == null)
        {
            return 0;
        }
        final IColonyView colony = minecraft.level == null ? null : IColonyManager.getInstance().getClosestColonyView(minecraft.level, anchor);
        return Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory())) + ClientTreasury.of(colony);
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        renderLeft(graphics, mouseX, mouseY);
        renderCards(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY);
    }

    private void renderLeft(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int x = x0 + 6;
        int y = y0 + 25;
        graphics.drawString(font, "STYLE", x, y, Palette.MUTED, false);
        y += 11;
        Ui.plate(graphics, x, y, x + LEFT_W - 8, y + 16, Palette.LEATHER, Palette.EDGE, Palette.LEATHER_LIGHT);
        final StructurePackMeta pack = StructurePacks.getSelectedPack();
        final String name = pack == null ? "—" : fit(StyleNames.name(pack), LEFT_W - 30);
        graphics.drawString(font, "◀", x + 4, y + 4, inside(mouseX, mouseY, x, y, 14, 16) ? Palette.WHITE : Palette.GOLD_TEXT, false);
        graphics.drawString(font, "▶", x + LEFT_W - 17, y + 4, inside(mouseX, mouseY, x + LEFT_W - 22, y, 14, 16) ? Palette.WHITE : Palette.GOLD_TEXT, false);
        final boolean overName = inside(mouseX, mouseY, x + 14, y, LEFT_W - 36, 16);
        graphics.drawCenteredString(font, name, x + (LEFT_W - 8) / 2, y + 4, overName ? Palette.WHITE : Palette.GOLD_TEXT);
        if (overName)
        {
            graphics.renderTooltip(font, Component.literal("Voir tous les styles"), (int) mouseX, (int) mouseY);
        }
        y += 24;
        graphics.drawString(font, "BESOINS", x, y, Palette.MUTED, false);
        y += 11;
        final CatalogueIndex.Need[] needs = CatalogueIndex.Need.values();
        for (int i = 0; i < needs.length; i++)
        {
            final int[] cell = needCell(i);
            Ui.navItem(graphics, font, cell[0], cell[1], cell[2], Icon.of(needs[i].icon()), needs[i].label(), needs[i] == need,
              inside(mouseX, mouseY, cell[0], cell[1], cell[2], ROW));
        }
        verticalRule(graphics, x0 + LEFT_W);
    }

    /**
     * Where the i-th need sits in the left column: one per row with its name when the window is tall enough, else a
     * grid of icons (names on hover) so the list never runs past the window's bottom. {x, y, width}.
     */
    private int[] needCell(final int index)
    {
        final int x = x0 + 6;
        final int top = y0 + 36 + 24 + 11;
        final int count = CatalogueIndex.Need.values().length;
        if (top + count * (ROW + 1) <= y0 + h - 4)
        {
            return new int[] {x, top + index * (ROW + 1), LEFT_W - 8};
        }
        final int columns = 3;
        final int cellW = (LEFT_W - 8 - (columns - 1) * 2) / columns;
        return new int[] {x + (index % columns) * (cellW + 2), top + (index / columns) * (ROW + 1), cellW};
    }

    private int centerLeft()
    {
        return x0 + LEFT_W + 6;
    }

    private int centerWidth()
    {
        return w - LEFT_W - RIGHT_W - 12;
    }

    private List<FormattedCharSequence> needDesc()
    {
        return font.split(Component.literal(need.desc()), centerWidth());
    }

    private int cardsTop()
    {
        return y0 + 25 + needDesc().size() * 9 + 5;
    }

    private ScrollGrid grid()
    {
        // Two columns only when each card still has room for a building's name
        grid.setColumns(centerWidth() >= 2 * CARD_MIN_W + GAP ? 2 : 1);
        grid.layout(centerLeft(), cardsTop(), centerWidth(), y0 + h - 6, CARD_H);
        return grid;
    }

    private void renderCards(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int left = centerLeft();
        int descY = y0 + 25;
        for (final FormattedCharSequence line : needDesc())
        {
            graphics.drawString(font, line, left, descY, Palette.MUTED, false);
            descY += 9;
        }
        final int top = cardsTop();
        if (entries.isEmpty() && !loaded)
        {
            graphics.drawCenteredString(font, "Chargement des plans…", left + centerWidth() / 2, top + 40, Palette.MUTED);
            return;
        }
        if (!loaded)
        {
            graphics.drawString(font, "⟳", left + centerWidth() - 6, y0 + 7, Palette.MUTED, false);
        }
        final List<CatalogueIndex.Entry> list = visible();
        if (list.isEmpty())
        {
            for (final FormattedCharSequence line : font.split(Component.literal("Rien dans cette catégorie pour ce style."), centerWidth() - 10))
            {
                graphics.drawString(font, line, left + 5, top + 40, Palette.MUTED, false);
            }
            return;
        }
        final ScrollGrid grid = grid();
        grid.clamp(list.size());
        final int cardW = grid.cardWidth();
        final long owned = owned();
        final boolean free = minecraft.player != null && minecraft.player.isCreative();
        for (int i = grid.first(); i < grid.end(list.size()); i++)
        {
            final CatalogueIndex.Entry entry = list.get(i);
            final int cx = grid.x(i);
            final int cy = grid.y(i);
            final boolean locked = locked(entry);
            Ui.card(graphics, cx, cy, cardW, CARD_H, entry == selected, inside(mouseX, mouseY, cx, cy, cardW, CARD_H), locked);
            if (entry.icon().isItem())
            {
                entry.icon().draw(graphics, cx + 5, cy + 8, 16);
            }
            else
            {
                entry.icon().draw(graphics, cx + 3, cy + 6, 20);
            }
            graphics.drawString(font, fit(entry.name(), cardW - 30), cx + 25, cy + 6, locked ? Palette.MUTED : Palette.INK, false);
            if (locked)
            {
                graphics.drawString(font, "Verrouillé", cx + 25, cy + 18, Palette.RED, false);
            }
            else
            {
                final int cost = cost(entry);
                Ui.coin(graphics, cx + 25, cy + 18, 8);
                graphics.drawString(font, free ? "gratuit" : Integer.toString(cost), cx + 35, cy + 18, free || owned >= cost ? Palette.INK : Palette.RED, false);
                final int crates = crates(entry).values().stream().mapToInt(Integer::intValue).sum();
                if (crates > 0)
                {
                    final String text = crates + (crates > 1 ? " caisses" : " caisse");
                    graphics.drawString(font, text, cx + cardW - 5 - font.width(text), cy + 18, Palette.MUTED, false);
                }
            }
        }
        grid.renderScrollbar(graphics, list.size(), 1);
    }

    private void renderDetail(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int left = detailPanel(graphics, RIGHT_W);
        variantSquares.clear();
        flowSlots.clear();
        if (selected == null)
        {
            return;
        }
        final int x = left + 6;
        final int width = RIGHT_W - 14;
        int y = y0 + 26;
        for (final FormattedCharSequence line : font.split(Component.literal(selected.name()).withStyle(ChatFormatting.BOLD), width))
        {
            graphics.drawString(font, line, x, y, Palette.INK, false);
            y += 10;
        }
        y += 2;
        if (selected.hut() != null && selected.about() != null)
        {
            y = Ui.wrapped(graphics, font, Component.literal(selected.about()), x, y, width, Palette.MUTED, 9);
        }
        else if (selected.hut() != null)
        {
            // The hut's first description line is often just its name again, already shown in bold above
            final List<MutableComponent> desc = selected.hut().getDesc().stream()
              .filter(line -> !line.getString().strip().equalsIgnoreCase(selected.name().strip())).toList();
            if (!desc.isEmpty())
            {
                y = Ui.wrapped(graphics, font, desc.get(0), x, y, width, Palette.MUTED, 9);
            }
        }
        else
        {
            final String about = (selected.about() == null ? "" : selected.about() + " ") + selected.blocks() + " blocs.";
            y = Ui.wrapped(graphics, font, Component.literal(about), x, y, width, Palette.MUTED, 9);
        }

        // What goes in and what comes out, drawn: icons and arrows, read at a glance
        final List<List<ItemStack>> flow = selected.hut() == null ? List.of() : CatalogueIndex.flow(selected.buildingId());
        final Component rate = selected.hut() == null ? null : CatalogueIndex.production(selected.buildingId());
        if (!flow.isEmpty() || rate != null)
        {
            y = Ui.separator(graphics, x, y, width);
            if (!flow.isEmpty())
            {
                y = Ui.flow(graphics, font, x, y, width, flow, flowSlots);
            }
            if (rate != null)
            {
                y = Ui.wrapped(graphics, font, rate, x, y + 2, width, Palette.INK, 9);
            }
        }

        y = Ui.separator(graphics, x, y, width);
        Ui.label(graphics, font, "PRIX", x, y);
        y += 11;
        final boolean free = minecraft.player != null && minecraft.player.isCreative();
        final int cost = cost(selected);
        y = Ui.priceLine(graphics, font, x, y, width, free ? "Gratuit (créatif)" : cost + " Couronnes", free || owned() >= cost);
        for (final CrateRows.Row row : CrateRows.forCrates(crates(selected)))
        {
            y = Ui.crateLine(graphics, font, x, y, width, row);
        }
        final int seconds = selected.hut() != null ? BuildCosts.secondsForLevel(1) : BuildCosts.secondsForFlat();
        graphics.drawString(font, "⌛ " + BuildCosts.formatSeconds(seconds), x, y + 1, Palette.INK, false);
        y += 11;

        if (selected.variants().size() > 1)
        {
            y = Ui.separator(graphics, x, y, width);
            Ui.label(graphics, font, "VARIANTES", x, y);
            y += 11;
            for (int i = 0; i < selected.variants().size() && i < 8; i++)
            {
                final int vx = x + i * 15;
                final boolean on = i == variant;
                variantSquares.add(new int[] {vx, y});
                Ui.plate(graphics, vx, y, vx + 13, y + 13, on ? Palette.LEATHER : Palette.PAPER_LIGHT, on ? Palette.SELECTED : Palette.RULE, on ? Palette.LEATHER : Palette.PAPER_LIGHT);
                graphics.drawCenteredString(font, String.valueOf((char) ('A' + i)), vx + 7, y + 3, on ? Palette.GOLD_TEXT : Palette.INK);
            }
            y += 18;
        }

        final List<MutableComponent> missing = requirementsOf(selected);
        final int buttonY = actionY();
        if (!missing.isEmpty())
        {
            y += 4;
        }
        for (final MutableComponent requirement : missing)
        {
            for (final FormattedCharSequence line : font.split(requirement, width))
            {
                if (y < buttonY - 10)
                {
                    graphics.drawString(font, line, x, y, Palette.RED, false);
                    y += 9;
                }
            }
        }

        final boolean canPlace = missing.isEmpty();
        final boolean hover = canPlace && inside(mouseX, mouseY, x, buttonY, width, 18);
        Ui.button(graphics, font, x, buttonY, width, Ui.BUTTON_H, canPlace ? "Placer" : "Verrouillé", Ui.ButtonStyle.PRIMARY, canPlace, hover);

        for (final Ui.Slot slot : flowSlots)
        {
            if (inside(mouseX, mouseY, slot.x(), slot.y(), slot.size(), slot.size()))
            {
                graphics.renderTooltip(font, slot.stack(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        final int lx = x0 + 6;
        final int styleY = y0 + 36;
        if (inside(mouseX, mouseY, lx, styleY, 14, 16))
        {
            cyclePack(-1);
            return true;
        }
        if (inside(mouseX, mouseY, lx + LEFT_W - 22, styleY, 14, 16))
        {
            cyclePack(1);
            return true;
        }
        if (inside(mouseX, mouseY, lx + 14, styleY, LEFT_W - 36, 16))
        {
            minecraft.setScreen(new StyleScreen(this));
            return true;
        }
        final CatalogueIndex.Need[] needs = CatalogueIndex.Need.values();
        for (int i = 0; i < needs.length; i++)
        {
            final CatalogueIndex.Need each = needs[i];
            final int[] cell = needCell(i);
            if (inside(mouseX, mouseY, cell[0], cell[1], cell[2], ROW))
            {
                need = each;
                lastNeed = each;
                grid.reset();
                visible().stream().findFirst().ifPresentOrElse(this::select, () -> selected = null);
                return true;
            }
        }

        final List<CatalogueIndex.Entry> list = visible();
        final int hit = grid().hit(mouseX, mouseY, list.size());
        if (hit >= 0)
        {
            select(list.get(hit));
            return true;
        }

        if (selected != null)
        {
            final int x = x0 + w - RIGHT_W + 6;
            final int width = RIGHT_W - 14;
            final int buttonY = actionY();
            if (inside(mouseX, mouseY, x, buttonY, width, 18) && requirementsOf(selected).isEmpty())
            {
                place();
                return true;
            }
            for (int i = 0; i < variantSquares.size(); i++)
            {
                if (inside(mouseX, mouseY, variantSquares.get(i)[0], variantSquares.get(i)[1], 13, 13))
                {
                    variant = i;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        grid.scroll(scrollY);
        return true;
    }

    private void cyclePack(final int step)
    {
        final List<StructurePackMeta> packs = new ArrayList<>(StructurePacks.getPackMetas().stream().filter(p -> !p.isDisabled()).toList());
        if (packs.isEmpty())
        {
            return;
        }
        packs.sort(Comparator.comparing(StructurePackMeta::getName));
        final StructurePackMeta current = StructurePacks.getSelectedPack();
        int index = current == null ? -1 : packs.indexOf(current);
        index = Math.floorMod(index + step, packs.size());
        StructurePacks.switchSelectedPack(packs.get(index));
        reload();
    }

    private void place()
    {
        final CatalogueIndex.Ref ref = selected.variants().get(Math.min(variant, selected.variants().size() - 1));
        final Blueprint blueprint = minecraft.level == null ? null : ref.load(minecraft.level.registryAccess());
        if (blueprint == null)
        {
            return;
        }
        ((WindowExtendedBuildToolAccessor) (Object) buildTool).interregne$setBlueprint(blueprint);
        final BlockPos pos = RenderingCache.getOrCreateBlueprintPreviewData("blueprint").getPos();
        final int groundstyle = ((AbstractBlueprintManipulationWindowAccessor) (Object) buildTool).interregne$groundstyle();
        new WindowExtendedBuildTool(pos != null ? pos : anchor, groundstyle, minecraft.level.registryAccess()).open();
    }

    private static boolean inside(final double mouseX, final double mouseY, final int x, final int y, final int width, final int height)
    {
        return Ui.inside(mouseX, mouseY, x, y, width, height);
    }
}
