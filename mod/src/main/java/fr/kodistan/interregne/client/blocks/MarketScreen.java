package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenMarketPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The imperial market. First the player's purse, what every trade is measured against; then one row per crate: the
 * crate, the imperial stock (amber when it runs low, prices then climb), what the player carries, and the two prices
 * as buttons, buying in green. Shift trades several at once.
 */
public final class MarketScreen extends CounterScreen
{
    private static final int SHIFT_BATCH = 4;
    private static final int LOW_STOCK = 8;

    private OpenMarketPayload state;

    private MarketScreen(final OpenMarketPayload state)
    {
        super("Marché impérial", 390, 250);
        this.state = state;
    }

    /**
     * Opens the market, or refreshes it when it is already showing.
     */
    public static void open(final OpenMarketPayload state)
    {
        if (Minecraft.getInstance().screen instanceof MarketScreen screen && screen.state.market().equals(state.market()))
        {
            screen.state = state;
            return;
        }
        Minecraft.getInstance().setScreen(new MarketScreen(state));
    }

    private void trade(final int action, final int crate)
    {
        PacketDistributor.sendToServer(new RegionActionPayload(state.market(), action, crate, Screen.hasShiftDown() ? SHIFT_BATCH : 1));
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        int crates = 0;
        for (final int carried : state.carried())
        {
            crates += carried;
        }
        y = hero(graphics, x, y, width, null, "TA BOURSE", couronnes(state.purse()),
          (crates > 0 ? crates + (crates > 1 ? " caisses à vendre sur toi" : " caisse à vendre sur toi") : "Aucune caisse sur toi")
            + " · Maj : par " + SHIFT_BATCH);
        // Column heads over the rows
        graphics.drawString(font, "CAISSE", x + 6, y, Palette.MUTED, false);
        graphics.drawString(font, "STOCK", x + 150, y, Palette.MUTED, false);
        graphics.drawString(font, "SUR TOI", x + 192, y, Palette.MUTED, false);
        return y + 11;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final Crate[] crates = Crate.values();
        for (int i = 0; i < crates.length; i++)
        {
            final Crate crate = crates[i];
            final int index = i;
            final int stock = state.stock()[i];
            final int carried = state.carried()[i];
            Ui.card(graphics, x, y, width, 24, false, false, false);
            Icon.of(crate.stack(1)).draw(graphics, x + 4, y + 4, 16);
            addSlot(x + 4, y + 4, 16, crate.stack(1), mouseY);
            graphics.drawString(font, Component.literal(fit(crate.displayName(), 118)).withStyle(ChatFormatting.BOLD), x + 24, y + 8, Palette.INK, false);
            final String stockText = String.valueOf(stock);
            graphics.drawString(font, Component.literal(stockText).withStyle(ChatFormatting.BOLD), x + 150, y + 8,
              stock <= 0 ? Palette.RED : stock < LOW_STOCK ? Palette.AMBER_TEXT : Palette.INK, false);
            if (Ui.inside(mouseX, mouseY, x + 146, y, 40, 24))
            {
                hint = stock <= 0 ? "Plus aucune dans l'Empire." : stock < LOW_STOCK ? "Stock bas : le prix d'achat grimpe." : "Caisses en vente dans tout l'Empire.";
            }
            graphics.drawString(font, String.valueOf(carried), x + 192, y + 8, carried > 0 ? Palette.INK : Palette.MUTED, false);

            final String sell = "Vendre " + Wallet.format(state.sell()[i]);
            int bx = x + width - 5 - buttonWidth(sell);
            button(graphics, mouseX, mouseY, bx, y + 5, sell, Ui.ButtonStyle.SECONDARY, carried > 0,
              carried > 0 ? "Tu reçois " + couronnes(state.sell()[i]) + " par caisse." : "Tu n'en portes pas.", () -> trade(RegionActionPayload.MARKET_SELL, index));
            final String buy = "Acheter " + Wallet.format(state.buy()[i]);
            bx -= buttonWidth(buy) + 3;
            final String why = stock <= 0 ? "Plus aucune en stock." : state.purse() < state.buy()[i] ? "Il te faut " + couronnes(state.buy()[i]) + " sur toi." : null;
            button(graphics, mouseX, mouseY, bx, y + 5, buy, Ui.ButtonStyle.PRIMARY, why == null, why, () -> trade(RegionActionPayload.MARKET_BUY, index));
            y += 26;
        }
        return y;
    }
}
