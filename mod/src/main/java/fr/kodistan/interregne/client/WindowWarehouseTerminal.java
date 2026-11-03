package fr.kodistan.interregne.client;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.tileentities.TileEntityRack;
import fr.kodistan.interregne.network.TerminalDepositPayload;
import fr.kodistan.interregne.network.TerminalWithdrawPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The warehouse register: the whole stock in one searchable list, take one or a stack with a click,
 * and store everything you carry (hotbar kept) in one go. Replaces the "all inventory" view for warehouses.
 */
public class WindowWarehouseTerminal extends AbstractWindowSkeleton
{
    private static final int REFRESH_TICKS = 20;

    private final IBuildingView  building;
    private final BOWindow       previous;
    private final ScrollingList  list;
    private final List<ItemStorage> shown = new ArrayList<>();

    private String filter = "";
    private int    tick;

    public WindowWarehouseTerminal(final IBuildingView building, final BOWindow previous)
    {
        super("interregne:gui/warehouseterminal.xml");
        this.building = building;
        this.previous = previous;
        this.list = findPaneOfTypeByID("stock", ScrollingList.class);

        registerButton("takeOne", button -> withdraw(button, 1));
        registerButton("takeStack", button -> withdraw(button, 64));
        registerButton("deposit", this::deposit);
        registerButton("back", () -> previous.open());
        findPaneOfTypeByID("search", TextField.class).setHandler(input -> {
            if (!input.getText().equals(filter))
            {
                filter = input.getText();
                refresh();
            }
        });

        list.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return shown.size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final ItemStorage storage = shown.get(index);
                final ItemStack icon = storage.getItemStack().copyWithCount(1);
                rowPane.findPaneOfTypeByID("icon", ItemIcon.class).setItem(icon);
                rowPane.findPaneOfTypeByID("name", Text.class).setText(icon.getHoverName());
                rowPane.findPaneOfTypeByID("count", Text.class).setText(Component.literal(compact(storage.getAmount())));
            }
        });
        refresh();
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (++tick % REFRESH_TICKS == 0)
        {
            refresh();
        }
    }

    /**
     * Rebuild the list from the racks this client sees, merged by item, filtered and sorted by name.
     */
    private void refresh()
    {
        final Map<ItemStorage, Integer> stock = new HashMap<>();
        final List<BlockPos> containers = new ArrayList<>(building.getContainerList());
        containers.add(building.getID());
        for (final BlockPos pos : containers)
        {
            final BlockEntity entity = Minecraft.getInstance().level.getBlockEntity(pos);
            if (entity instanceof TileEntityRack rack)
            {
                rack.getAllContent().forEach((storage, amount) -> stock.merge(storage, amount, Integer::sum));
            }
        }

        final String needle = filter.toLowerCase(Locale.ROOT);
        shown.clear();
        stock.forEach((storage, amount) -> {
            if (needle.isEmpty() || storage.getItemStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle))
            {
                final ItemStorage entry = new ItemStorage(storage.getItemStack().copy());
                entry.setAmount(amount);
                shown.add(entry);
            }
        });
        shown.sort(Comparator.comparing(storage -> storage.getItemStack().getHoverName().getString()));
        list.refreshElementPanes();
    }

    private void withdraw(final Button button, final int amount)
    {
        final int row = list.getListElementIndexByPane(button);
        if (row >= 0 && row < shown.size())
        {
            PacketDistributor.sendToServer(new TerminalWithdrawPayload(building.getID(), shown.get(row).getItemStack().copyWithCount(1), amount));
            tick = REFRESH_TICKS - 5;
        }
    }

    private void deposit()
    {
        PacketDistributor.sendToServer(new TerminalDepositPayload(building.getID()));
        tick = REFRESH_TICKS - 5;
    }

    private static String compact(final int amount)
    {
        return amount >= 10_000 ? amount / 1000 + "k" : Integer.toString(amount);
    }
}
