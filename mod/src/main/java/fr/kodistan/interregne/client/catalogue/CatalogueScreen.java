package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.CrateRows;
import fr.kodistan.interregne.client.hud.InterregneHud;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.mixin.client.WindowExtendedBuildToolAccessor;
import fr.kodistan.interregne.region.Crate;
import fr.kodistan.interregne.region.CrateRequirements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
public final class CatalogueScreen extends Screen
{
    private static final ResourceLocation COIN = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/hud_coin.png");

    private static final int PAPER       = 0xFFE9D8A6;
    private static final int PAPER_DARK  = 0xFFDCC48E;
    private static final int PAPER_LIGHT = 0xFFF8EED6;
    private static final int EDGE        = 0xFF4A2E16;
    private static final int RULE        = 0xFFB9976A;
    private static final int LEATHER     = 0xFF5C3A21;
    private static final int LEATHER_LIGHT = 0xFF7A5232;
    private static final int GOLD_TEXT   = 0xFFFFE3A3;
    private static final int INK         = 0xFF4A2A10;
    private static final int MUTED       = 0xFF7A5232;
    private static final int RED         = 0xFFA32D2D;
    private static final int GREEN       = 0xFF3B6D11;
    private static final int GREEN_TEXT  = 0xFFEAF3DE;
    private static final int SELECTED    = 0xFFC08A1E;

    private static final int LEFT_W = 104;
    private static final int RIGHT_W = 138;
    private static final int CARD_H = 32;
    private static final int GAP = 4;
    private static final int ROW = 17;

    private static CatalogueIndex.Need lastNeed = CatalogueIndex.Need.FOOD;

    private final WindowExtendedBuildTool buildTool;
    private final BlockPos anchor;
    private CompletableFuture<List<CatalogueIndex.Entry>> loading;
    private List<CatalogueIndex.Entry> entries = List.of();
    private CatalogueIndex.Need need = lastNeed;
    @Nullable
    private CatalogueIndex.Entry selected;
    private int variant;
    private int scroll;
    private final Map<CatalogueIndex.Entry, List<MutableComponent>> requirements = new HashMap<>();
    private final List<int[]> variantSquares = new ArrayList<>();

    private int x0;
    private int y0;
    private int w;
    private int h;

    private CatalogueScreen(final WindowExtendedBuildTool buildTool, final BlockPos anchor)
    {
        super(Component.literal("Constructions"));
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

    @Override
    protected void init()
    {
        w = Math.min(width - 16, 480);
        h = Math.min(height - 16, 260);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        if (loading == null)
        {
            reload();
        }
    }

    private void reload()
    {
        StructurePacks.ensureSelectedPack();
        final StructurePackMeta pack = StructurePacks.getSelectedPack();
        entries = List.of();
        selected = null;
        requirements.clear();
        loading = pack == null || minecraft.level == null ? CompletableFuture.completedFuture(List.of())
          : CatalogueIndex.of(pack.getName(), minecraft.level.registryAccess());
    }

    @Override
    public void tick()
    {
        if (entries.isEmpty() && loading != null && loading.isDone())
        {
            entries = loading.getNow(List.of());
            if (selected == null)
            {
                visible().stream().findFirst().ifPresent(this::select);
            }
        }
    }

    private List<CatalogueIndex.Entry> visible()
    {
        return entries.stream().filter(e -> e.need() == need).toList();
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
                return e.hut().areRequirementsMet(minecraft.level, anchor, minecraft.player) ? List.of()
                  : e.hut().getRequirements(minecraft.level, anchor, minecraft.player);
            }
            catch (final RuntimeException ex)
            {
                return List.of();
            }
        });
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
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        graphics.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        InterregneHud.roundedPlate(graphics, x0, y0, x0 + w, y0 + h, PAPER, EDGE, PAPER_LIGHT);

        // Header
        graphics.drawString(font, "Que voulez-vous construire ?", x0 + 8, y0 + 7, INK, false);
        final boolean overClose = inside(mouseX, mouseY, x0 + w - 16, y0 + 4, 12, 12);
        graphics.drawString(font, "✕", x0 + w - 13, y0 + 6, overClose ? RED : MUTED, false);
        graphics.fill(x0 + 4, y0 + 19, x0 + w - 4, y0 + 20, RULE);

        renderLeft(graphics, mouseX, mouseY);
        renderCards(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY);
    }

    private void renderLeft(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int x = x0 + 6;
        int y = y0 + 25;
        graphics.drawString(font, "STYLE", x, y, MUTED, false);
        y += 11;
        InterregneHud.roundedPlate(graphics, x, y, x + LEFT_W - 8, y + 16, LEATHER, EDGE, LEATHER_LIGHT);
        final StructurePackMeta pack = StructurePacks.getSelectedPack();
        final String name = pack == null ? "—" : fit(pack.getName(), LEFT_W - 30);
        graphics.drawString(font, "◀", x + 4, y + 4, inside(mouseX, mouseY, x, y, 14, 16) ? 0xFFFFFFFF : GOLD_TEXT, false);
        graphics.drawString(font, "▶", x + LEFT_W - 17, y + 4, inside(mouseX, mouseY, x + LEFT_W - 22, y, 14, 16) ? 0xFFFFFFFF : GOLD_TEXT, false);
        graphics.drawCenteredString(font, name, x + (LEFT_W - 8) / 2, y + 4, GOLD_TEXT);
        y += 24;
        graphics.drawString(font, "BESOINS", x, y, MUTED, false);
        y += 11;
        for (final CatalogueIndex.Need each : CatalogueIndex.Need.values())
        {
            final boolean on = each == need;
            final boolean hover = inside(mouseX, mouseY, x, y, LEFT_W - 8, ROW);
            if (on)
            {
                InterregneHud.roundedPlate(graphics, x, y, x + LEFT_W - 8, y + ROW, LEATHER, EDGE, LEATHER_LIGHT);
            }
            else if (hover)
            {
                graphics.fill(x, y, x + LEFT_W - 8, y + ROW, PAPER_DARK);
            }
            graphics.renderItem(each.icon(), x + 1, y);
            graphics.drawString(font, each.label(), x + 20, y + 5, on ? GOLD_TEXT : INK, false);
            y += ROW + 1;
        }
        graphics.fill(x0 + LEFT_W, y0 + 22, x0 + LEFT_W + 1, y0 + h - 4, RULE);
    }

    private int centerLeft()
    {
        return x0 + LEFT_W + 6;
    }

    private int centerWidth()
    {
        return w - LEFT_W - RIGHT_W - 12;
    }

    private int cardWidth()
    {
        return (centerWidth() - GAP) / 2;
    }

    private int visibleRows()
    {
        return Math.max(1, (h - 30) / (CARD_H + GAP));
    }

    private void renderCards(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int left = centerLeft();
        final int top = y0 + 25;
        if (!loading.isDone())
        {
            graphics.drawCenteredString(font, "Chargement des plans…", left + centerWidth() / 2, top + 40, MUTED);
            return;
        }
        final List<CatalogueIndex.Entry> list = visible();
        if (list.isEmpty())
        {
            for (final FormattedCharSequence line : font.split(Component.literal("Rien dans cette catégorie pour ce style."), centerWidth() - 10))
            {
                graphics.drawString(font, line, left + 5, top + 40, MUTED, false);
            }
            return;
        }
        final int rows = (list.size() + 1) / 2;
        scroll = Math.max(0, Math.min(scroll, rows - visibleRows()));
        final long owned = owned();
        final boolean free = minecraft.player != null && minecraft.player.isCreative();
        for (int i = scroll * 2; i < list.size() && i < (scroll + visibleRows()) * 2; i++)
        {
            final CatalogueIndex.Entry entry = list.get(i);
            final int cx = left + (i % 2) * (cardWidth() + GAP);
            final int cy = top + (i / 2 - scroll) * (CARD_H + GAP);
            final boolean isSelected = entry == selected;
            final boolean hover = inside(mouseX, mouseY, cx, cy, cardWidth(), CARD_H);
            final boolean locked = locked(entry);
            final int fill = locked ? PAPER_DARK : hover ? 0xFFFFF6DD : PAPER_LIGHT;
            InterregneHud.roundedPlate(graphics, cx, cy, cx + cardWidth(), cy + CARD_H, fill, isSelected ? SELECTED : RULE, fill);
            if (isSelected)
            {
                graphics.renderOutline(cx + 1, cy + 1, cardWidth() - 2, CARD_H - 2, SELECTED);
            }
            graphics.renderItem(entry.icon(), cx + 5, cy + 8);
            graphics.drawString(font, fit(entry.name(), cardWidth() - 30), cx + 25, cy + 6, locked ? MUTED : INK, false);
            if (locked)
            {
                graphics.drawString(font, "Verrouillé", cx + 25, cy + 18, RED, false);
            }
            else
            {
                final int cost = cost(entry);
                graphics.blit(COIN, cx + 25, cy + 18, 8, 8, 0, 0, 16, 16, 16, 16);
                graphics.drawString(font, free ? "gratuit" : Integer.toString(cost), cx + 35, cy + 18, free || owned >= cost ? INK : RED, false);
                final int crates = crates(entry).values().stream().mapToInt(Integer::intValue).sum();
                if (crates > 0)
                {
                    final String text = crates + (crates > 1 ? " caisses" : " caisse");
                    graphics.drawString(font, text, cx + cardWidth() - 5 - font.width(text), cy + 18, MUTED, false);
                }
            }
        }
        if (rows > visibleRows())
        {
            final int trackTop = top;
            final int trackH = visibleRows() * (CARD_H + GAP) - GAP;
            final int thumbH = Math.max(10, trackH * visibleRows() / rows);
            final int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, rows - visibleRows());
            final int sx = left + centerWidth() + 1;
            graphics.fill(sx, trackTop, sx + 2, trackTop + trackH, PAPER_DARK);
            graphics.fill(sx, thumbY, sx + 2, thumbY + thumbH, LEATHER);
        }
    }

    private void renderDetail(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        final int left = x0 + w - RIGHT_W;
        graphics.fill(left - 1, y0 + 22, left, y0 + h - 4, RULE);
        graphics.fill(left, y0 + 20, x0 + w - 3, y0 + h - 3, 0xFFF1E3BD);
        variantSquares.clear();
        if (selected == null)
        {
            return;
        }
        final int x = left + 6;
        final int width = RIGHT_W - 14;
        int y = y0 + 26;
        for (final FormattedCharSequence line : font.split(Component.literal(selected.name()).withStyle(ChatFormatting.BOLD), width))
        {
            graphics.drawString(font, line, x, y, INK, false);
            y += 10;
        }
        if (selected.hut() != null)
        {
            final List<MutableComponent> desc = selected.hut().getDesc();
            if (!desc.isEmpty())
            {
                for (final FormattedCharSequence line : font.split(desc.get(0), width))
                {
                    graphics.drawString(font, line, x, y, MUTED, false);
                    y += 9;
                }
            }
        }
        else
        {
            graphics.drawString(font, selected.blocks() + " blocs", x, y, MUTED, false);
            y += 9;
        }
        y += 4;

        final Component production = selected.hut() == null ? null : CatalogueIndex.production(selected.buildingId());
        if (production != null)
        {
            graphics.drawString(font, "PRODUIT", x, y, MUTED, false);
            y += 10;
            final ItemStack product = CatalogueIndex.productIcon(selected.buildingId());
            if (product != null)
            {
                graphics.renderItem(product, x - 1, y - 2);
            }
            for (final FormattedCharSequence line : font.split(production, width - 18))
            {
                graphics.drawString(font, line, x + 17, y, INK, false);
                y += 9;
            }
            y += 5;
        }

        graphics.drawString(font, "PRIX", x, y, MUTED, false);
        y += 10;
        final boolean free = minecraft.player != null && minecraft.player.isCreative();
        final int cost = cost(selected);
        graphics.blit(COIN, x, y, 9, 9, 0, 0, 16, 16, 16, 16);
        graphics.drawString(font, free ? "Gratuit (créatif)" : cost + " Couronnes", x + 12, y + 1, free || owned() >= cost ? INK : RED, false);
        y += 11;
        for (final CrateRows.Row row : CrateRows.forCrates(crates(selected)))
        {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y - 1, 0);
            graphics.pose().scale(0.625F, 0.625F, 1);
            graphics.renderItem(row.icon(), 0, 0);
            graphics.pose().popPose();
            graphics.drawString(font, row.count() + " × " + row.label().getString(), x + 12, y + 1, row.available() ? INK : RED, false);
            y += 11;
        }
        final int seconds = selected.hut() != null ? BuildCosts.secondsForLevel(1) : BuildCosts.secondsForFlat();
        graphics.drawString(font, "⌛ " + BuildCosts.formatSeconds(seconds), x, y + 1, INK, false);
        y += 15;

        if (selected.variants().size() > 1)
        {
            graphics.drawString(font, "VARIANTES", x, y, MUTED, false);
            y += 10;
            for (int i = 0; i < selected.variants().size() && i < 8; i++)
            {
                final int vx = x + i * 15;
                final boolean on = i == variant;
                variantSquares.add(new int[] {vx, y});
                InterregneHud.roundedPlate(graphics, vx, y, vx + 13, y + 13, on ? LEATHER : PAPER_LIGHT, on ? SELECTED : RULE, on ? LEATHER : PAPER_LIGHT);
                graphics.drawCenteredString(font, String.valueOf((char) ('A' + i)), vx + 7, y + 3, on ? GOLD_TEXT : INK);
            }
            y += 18;
        }

        final List<MutableComponent> missing = requirementsOf(selected);
        final int buttonY = y0 + h - 26;
        for (final MutableComponent requirement : missing)
        {
            for (final FormattedCharSequence line : font.split(requirement, width))
            {
                if (y < buttonY - 10)
                {
                    graphics.drawString(font, line, x, y, RED, false);
                    y += 9;
                }
            }
        }

        final boolean canPlace = missing.isEmpty();
        final boolean hover = canPlace && inside(mouseX, mouseY, x, buttonY, width, 18);
        InterregneHud.roundedPlate(graphics, x, buttonY, x + width, buttonY + 18, canPlace ? (hover ? 0xFF4A8516 : GREEN) : PAPER_DARK, EDGE, canPlace ? 0xFF639922 : PAPER_DARK);
        graphics.drawCenteredString(font, canPlace ? "Placer" : "Verrouillé", x + width / 2, buttonY + 5, canPlace ? GREEN_TEXT : MUTED);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button != 0)
        {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mouseX, mouseY, x0 + w - 16, y0 + 4, 12, 12))
        {
            onClose();
            return true;
        }
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
        int y = styleY + 24 + 11;
        for (final CatalogueIndex.Need each : CatalogueIndex.Need.values())
        {
            if (inside(mouseX, mouseY, lx, y, LEFT_W - 8, ROW))
            {
                need = each;
                lastNeed = each;
                scroll = 0;
                visible().stream().findFirst().ifPresentOrElse(this::select, () -> selected = null);
                return true;
            }
            y += ROW + 1;
        }

        final List<CatalogueIndex.Entry> list = visible();
        for (int i = scroll * 2; i < list.size() && i < (scroll + visibleRows()) * 2; i++)
        {
            final int cx = centerLeft() + (i % 2) * (cardWidth() + GAP);
            final int cy = y0 + 25 + (i / 2 - scroll) * (CARD_H + GAP);
            if (inside(mouseX, mouseY, cx, cy, cardWidth(), CARD_H))
            {
                select(list.get(i));
                return true;
            }
        }

        if (selected != null)
        {
            final int x = x0 + w - RIGHT_W + 6;
            final int width = RIGHT_W - 14;
            final int buttonY = y0 + h - 26;
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        scroll -= (int) Math.signum(scrollY);
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
        final Blueprint blueprint = selected.variants().get(Math.min(variant, selected.variants().size() - 1));
        ((WindowExtendedBuildToolAccessor) (Object) buildTool).interregne$setBlueprint(blueprint);
        buildTool.open();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private String fit(final String text, final int width)
    {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private static boolean inside(final double mouseX, final double mouseY, final int x, final int y, final int width, final int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
