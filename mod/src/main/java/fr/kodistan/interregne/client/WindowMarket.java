package fr.kodistan.interregne.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenMarketPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * A Marché impérial's window: every crate type with the Empire's stock, the price to buy one, the price paid for one,
 * and how many the player carries. Shift-click trades four at once.
 */
public class WindowMarket extends AbstractWindowSkeleton
{
    private static final int SHIFT_BATCH = 4;

    private final BlockPos      market;
    private final ScrollingList list;

    private OpenMarketPayload state;

    private WindowMarket(final OpenMarketPayload state)
    {
        super("interregne:gui/market.xml");
        this.market = state.market();
        this.state = state;
        this.list = findPaneOfTypeByID("crates", ScrollingList.class);
        registerButton("buy", button -> trade(button, RegionActionPayload.MARKET_BUY));
        registerButton("sell", button -> trade(button, RegionActionPayload.MARKET_SELL));
        registerButton("close", this::close);
        findPaneOfTypeByID("hint", Text.class).setText(Component.literal("Prix selon le stock impérial · Maj+clic : par " + SHIFT_BATCH));

        list.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return Crate.values().length;
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final Crate crate = Crate.values()[index];
                final OpenMarketPayload shown = WindowMarket.this.state;
                rowPane.findPaneOfTypeByID("icon", ItemIcon.class).setItem(crate.stack(1));
                rowPane.findPaneOfTypeByID("name", Text.class).setText(Component.literal(crate.displayName()));
                rowPane.findPaneOfTypeByID("stock", Text.class).setText(Component.literal(Integer.toString(shown.stock()[index])));
                rowPane.findPaneOfTypeByID("carried", Text.class).setText(Component.literal(Integer.toString(shown.carried()[index])));
                final Button buy = rowPane.findPaneOfTypeByID("buy", Button.class);
                buy.setText(Component.literal(Integer.toString(shown.buy()[index])));
                buy.setEnabled(shown.stock()[index] > 0 && shown.purse() >= shown.buy()[index]);
                final Button sell = rowPane.findPaneOfTypeByID("sell", Button.class);
                sell.setText(Component.literal(Integer.toString(shown.sell()[index])));
                sell.setEnabled(shown.carried()[index] > 0);
            }
        });
        show(state);
    }

    /**
     * Open the window, or refresh it when it is already showing this market.
     */
    public static void open(final OpenMarketPayload state)
    {
        if (Minecraft.getInstance().screen instanceof BOScreen screen && screen.getWindow() instanceof WindowMarket window && window.market.equals(state.market()))
        {
            window.show(state);
            return;
        }
        new WindowMarket(state).open();
    }

    private void show(final OpenMarketPayload state)
    {
        this.state = state;
        findPaneOfTypeByID("purse", Text.class).setText(Component.literal("Sur toi : " + Wallet.couronnes(state.purse(), null)));
        list.refreshElementPanes();
    }

    private void trade(final Button button, final int action)
    {
        final int row = list.getListElementIndexByPane(button);
        if (row >= 0 && row < Crate.values().length)
        {
            PacketDistributor.sendToServer(new RegionActionPayload(market, action, row, Screen.hasShiftDown() ? SHIFT_BATCH : 1));
        }
    }
}
