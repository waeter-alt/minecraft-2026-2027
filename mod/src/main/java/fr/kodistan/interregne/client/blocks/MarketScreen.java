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
 * The imperial market: one card per crate with the imperial stock, what the player carries, and the price to buy or
 * sell one, prices following the stock. Shift trades several at once.
 */
public final class MarketScreen extends CounterScreen
{
    private static final int SHIFT_BATCH = 4;

    private OpenMarketPayload state;

    private MarketScreen(final OpenMarketPayload state)
    {
        super("Marché impérial", 380, 260);
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
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        Ui.banner(graphics, font, x, y, width, Ui.State.OK, couronnes(state.purse()) + " sur toi", "prix selon le stock impérial · Maj : par " + SHIFT_BATCH);
        return y + 21;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final Crate[] crates = Crate.values();
        for (int i = 0; i < crates.length; i++)
        {
            final Crate crate = crates[i];
            final int index = i;
            Ui.card(graphics, x, y, width, 24, false, false, false);
            Icon.of(crate.stack(1)).draw(graphics, x + 4, y + 4, 16);
            addSlot(x + 4, y + 4, 16, crate.stack(1), mouseY);
            graphics.drawString(font, Component.literal(fit(crate.displayName(), 120)).withStyle(ChatFormatting.BOLD), x + 24, y + 4, Palette.INK, false);
            graphics.drawString(font, "Stock " + state.stock()[i] + " · sur toi " + state.carried()[i], x + 24, y + 13, Palette.MUTED, false);

            final String sell = "Vendre · " + Wallet.format(state.sell()[i]);
            int bx = x + width - 4 - buttonWidth(sell);
            button(graphics, mouseX, mouseY, bx, y + 5, sell, Ui.ButtonStyle.SECONDARY, state.carried()[i] > 0,
              state.carried()[i] > 0 ? "Vend une caisse de ton inventaire." : "Tu n'en portes pas.", () -> trade(RegionActionPayload.MARKET_SELL, index));
            final String buy = "Acheter · " + Wallet.format(state.buy()[i]);
            bx -= buttonWidth(buy) + 4;
            final String why = state.stock()[i] <= 0 ? "Plus aucune en stock." : state.purse() < state.buy()[i] ? "Pas assez de couronnes sur toi." : null;
            button(graphics, mouseX, mouseY, bx, y + 5, buy, Ui.ButtonStyle.PRIMARY, why == null, why, () -> trade(RegionActionPayload.MARKET_BUY, index));
            y += 26;
        }
        return y;
    }
}
