package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.render.worldevent.HighlightManager;
import com.minecolonies.core.client.render.worldevent.highlightmanager.TimedBoxRenderData;
import com.minecolonies.core.tileentities.TileEntityRack;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything stored in a building's racks, counted together; a click shows in the world which racks hold the item.
 * Replaces MineColonies' « all inventory » window.
 */
public final class StockScreen extends ModuleScreen
{
    private static final int CELL = 20;
    private static final String HIGHLIGHT = "inventoryHighlight";

    private final List<ItemStorage> stock = new ArrayList<>();
    private int sort;
    private int refresh;

    private StockScreen(final IBuildingView building, @Nullable final Screen parent)
    {
        super("Tout le stock", building, parent, 380);
        navCurrent = "Tout le stock";
        count();
    }

    public static void open(final IBuildingView building, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new StockScreen(building, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        addSearch(19);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (refresh-- <= 0)
        {
            refresh = 20;
            count();
        }
    }

    private Set<BlockPos> racks()
    {
        final Set<BlockPos> racks = new HashSet<>(building.getContainerList());
        racks.add(building.getPosition());
        return racks;
    }

    /**
     * Counts the racks as the client sees them.
     */
    private void count()
    {
        final Map<ItemStorage, Integer> counted = new HashMap<>();
        for (final BlockPos pos : racks())
        {
            if (Minecraft.getInstance().level.getBlockEntity(pos) instanceof TileEntityRack rack)
            {
                rack.getAllContent().forEach((storage, amount) -> counted.merge(storage, amount, Integer::sum));
            }
        }
        stock.clear();
        counted.forEach((storage, amount) -> {
            storage.setAmount(amount);
            stock.add(storage);
        });
    }

    private List<ItemStorage> visible()
    {
        final String query = query();
        final Comparator<ItemStorage> order = sort == 0 ? Comparator.comparingInt(ItemStorage::getAmount).reversed()
                                                : Comparator.comparing((ItemStorage s) -> s.getItemStack().getHoverName().getString());
        return stock.stream()
          .filter(s -> query.isEmpty() || s.getItemStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
          .sorted(order)
          .toList();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int total = stock.stream().mapToInt(ItemStorage::getAmount).sum();
        graphics.drawString(font, stock.size() + " sortes d'objets · " + total + " en tout", x + 2, y + 3, Palette.MUTED, false);
        y += 17;
        segmented(graphics, mouseX, mouseY, x, y, List.of("Les plus nombreux", "Par nom"), sort, index -> {
            sort = index;
            resetScroll();
        });
        return Ui.wrapped(graphics, font, Component.literal("clic : montrer où c'est rangé"), x + width - font.width("clic : montrer où c'est rangé") - 2, y + 3, width - (width - font.width("clic : montrer où c'est rangé") - 2), Palette.MUTED, 10) + 10;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final List<ItemStorage> visible = visible();
        if (visible.isEmpty())
        {
            graphics.drawString(font, stock.isEmpty() ? "Les étagères sont vides." : "Rien ne correspond.", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        final int columns = Math.max(1, width / CELL);
        for (int i = 0; i < visible.size(); i++)
        {
            final ItemStorage storage = visible.get(i);
            final int cx = x + (i % columns) * CELL;
            final int cy = y + (i / columns) * CELL;
            final ItemStack stack = storage.getItemStack().copy();
            stack.setCount(storage.getAmount());
            if (Ui.inside(mouseX, mouseY, cx, cy, CELL - 2, CELL - 2))
            {
                graphics.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, Palette.CARD_HOVER);
            }
            Icon.of(stack).draw(graphics, cx + 1, cy + 1, 16);
            graphics.renderItemDecorations(font, stack, cx + 1, cy + 1, storage.getAmount() > 999 ? (storage.getAmount() / 1000) + "k" : null);
            addHit(cx, cy, CELL - 2, CELL - 2, () -> locate(storage));
            addSlot(cx + 1, cy + 1, 16, stack, mouseY);
        }
        return y + ((visible.size() + columns - 1) / columns) * CELL;
    }

    /**
     * Outlines in the world, for a minute, each rack holding the item: red for a few, green for a stack or more.
     */
    private void locate(final ItemStorage storage)
    {
        HighlightManager.clearHighlightsForKey(HIGHLIGHT);
        for (final BlockPos pos : racks())
        {
            final BlockEntity entity = Minecraft.getInstance().level.getBlockEntity(pos);
            if (entity instanceof TileEntityRack rack)
            {
                final int count = rack.getCount(storage.getItemStack(), storage.ignoreDamageValue(), false);
                if (count > 0)
                {
                    final int color = 0x80000000 | (Mth.clamp((int) (0xff * (2.0f - count / 32.0f)), 0, 255) << 16) | (Mth.clamp((int) (0xff * count / 32.0f), 0, 255) << 8);
                    HighlightManager.addHighlight(HIGHLIGHT, pos.toString(), new TimedBoxRenderData(pos).setDuration(Duration.ofSeconds(60)).addText("" + count).setColor(color));
                }
            }
        }
        minecraft.player.displayClientMessage(Component.literal("Les étagères qui en contiennent sont entourées pendant une minute."), true);
        minecraft.setScreen(null);
    }
}
