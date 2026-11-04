package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.tileentities.TileEntityRack;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.network.TerminalDepositPayload;
import fr.kodistan.interregne.network.TerminalWithdrawPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The warehouse register: the whole stock in one searchable list, one item or a stack taken with a click, and
 * everything carried stored in one go (the hotbar kept). Replaces the « all inventory » view for warehouses.
 */
public final class TerminalScreen extends ModuleScreen
{
    private final List<ItemStorage> stock = new ArrayList<>();
    private int refresh;

    private TerminalScreen(final IBuildingView building, @Nullable final Screen parent)
    {
        super("Registre de l'entrepôt", building, parent, 400);
        navCurrent = "Tout le stock";
        count();
    }

    public static void open(final IBuildingView building, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new TerminalScreen(building, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        addSearch(25);
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

    /**
     * The racks as this client sees them, merged by item.
     */
    private void count()
    {
        stock.clear();
        stock.addAll(stockOf(building));
    }

    /**
     * A warehouse's racks as this client sees them, merged by item, by name.
     */
    static List<ItemStorage> stockOf(final IBuildingView building)
    {
        final List<ItemStorage> stock = new ArrayList<>();
        final Map<ItemStorage, Integer> counted = new HashMap<>();
        final List<BlockPos> racks = new ArrayList<>(building.getContainerList());
        racks.add(building.getID());
        for (final BlockPos pos : racks)
        {
            if (Minecraft.getInstance().level.getBlockEntity(pos) instanceof TileEntityRack rack)
            {
                rack.getAllContent().forEach((storage, amount) -> counted.merge(storage, amount, Integer::sum));
            }
        }
        counted.forEach((storage, amount) -> {
            final ItemStorage entry = new ItemStorage(storage.getItemStack().copy());
            entry.setAmount(amount);
            stock.add(entry);
        });
        stock.sort(Comparator.comparing(storage -> storage.getItemStack().getHoverName().getString()));
        return stock;
    }

    private void take(final ItemStorage storage, final int amount)
    {
        PacketDistributor.sendToServer(new TerminalWithdrawPayload(building.getID(), storage.getItemStack().copyWithCount(1), amount));
        refresh = 5;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final String deposit = "Tout ranger";
        button(graphics, mouseX, mouseY, x, y, deposit, Ui.ButtonStyle.PRIMARY, true, "Range tout ce que tu portes, sauf ta barre d'action.", () -> {
            PacketDistributor.sendToServer(new TerminalDepositPayload(building.getID()));
            refresh = 5;
        });
        graphics.drawString(font, stock.size() + " sortes d'objets", x + buttonWidth(deposit) + 8, y + 3, Palette.MUTED, false);
        return y + 20;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final String query = query();
        final List<ItemStorage> visible = stock.stream()
          .filter(s -> query.isEmpty() || s.getItemStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
          .toList();
        if (visible.isEmpty())
        {
            graphics.drawString(font, stock.isEmpty() ? "L'entrepôt est vide." : "Rien ne correspond.", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        for (final ItemStorage storage : visible)
        {
            final ItemStack icon = storage.getItemStack().copyWithCount(1);
            Ui.card(graphics, x, y, width, 20, false, false, false);
            Icon.of(icon).draw(graphics, x + 2, y + 2, 16);
            addSlot(x + 2, y + 2, 16, icon, mouseY);
            final String count = storage.getAmount() >= 10_000 ? storage.getAmount() / 1000 + "k" : String.valueOf(storage.getAmount());
            graphics.drawString(font, Component.literal(count).withStyle(ChatFormatting.BOLD), x + 22, y + 6, Palette.INK, false);
            graphics.drawString(font, fit(icon.getHoverName().getString(), width - 160), x + 56, y + 6, Palette.INK, false);
            int bx = x + width - 4 - buttonWidth("Un stack");
            button(graphics, mouseX, mouseY, bx, y + 3, "Un stack", Ui.ButtonStyle.SECONDARY, true, null, () -> take(storage, 64));
            bx -= buttonWidth("Un") + 3;
            button(graphics, mouseX, mouseY, bx, y + 3, "Un", Ui.ButtonStyle.SECONDARY, true, null, () -> take(storage, 1));
            y += 22;
        }
        return y;
    }
}
