package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.RegionalBankActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A colony's regional bank. First its treasury against what the bank holds, with the level; then paying in
 * everything carried; then withdrawing (managers only); last the next level and what it costs.
 */
public final class RegionalBankScreen extends CounterScreen
{
    private static final String MANAGERS = "Réservé au propriétaire et aux officiers de la colonie.";

    private OpenRegionalBankPayload state;

    private RegionalBankScreen(final OpenRegionalBankPayload state)
    {
        super("Banque régionale", 340, 218);
        this.state = state;
    }

    /**
     * Opens the window, or refreshes it when it already shows this bank.
     */
    public static void open(final OpenRegionalBankPayload state)
    {
        ClientTreasury.set(state.colonyKey(), state.balance());
        if (Minecraft.getInstance().screen instanceof RegionalBankScreen screen && screen.state.bank().equals(state.bank()))
        {
            screen.state = state;
            return;
        }
        Minecraft.getInstance().setScreen(new RegionalBankScreen(state));
    }

    @Override
    protected String crumb()
    {
        return state.colonyName();
    }

    @Override
    protected void init()
    {
        super.init();
        amountField();
    }

    private void send(final int action, final long amount)
    {
        PacketDistributor.sendToServer(new RegionalBankActionPayload(state.bank(), action, amount));
    }

    @Nullable
    private String cannotWithdraw(final long amount)
    {
        return !state.manager() ? MANAGERS : notMore(amount, state.balance(), "dans le trésor");
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final boolean full = state.balance() >= state.capacity();
        final long room = Math.max(0, state.capacity() - state.balance());
        final int top = y;
        y = hero(graphics, x, y, width, null, "TRÉSOR DE LA COLONIE", couronnes(state.balance()),
          full ? "Plein : améliore la banque pour y mettre plus." : "Place pour " + couronnes(room) + " de plus, sur " + Wallet.format(state.capacity()));
        heroGauge(graphics, x, top, width, state.balance(), state.capacity());
        final String badge = "Niv. " + state.level();
        Ui.levelBadge(graphics, font, x + width - 8 - font.width(badge) - 8, top + 6, badge, state.level());

        final long purse = purse();
        final String depositWhy = !state.member() ? "Réservé aux membres de la colonie." : full ? "Le trésor est plein : améliore la banque."
          : purse <= 0 ? "Tu ne portes aucune couronne." : null;
        y = bigButton(graphics, mouseX, mouseY, x, y, width, purse > 0 ? "Verser mes " + couronnes(Math.min(purse, room)) + " au trésor" : "Rien à verser",
          depositWhy == null, depositWhy == null ? (purse > room ? "Le trésor ne prend que " + couronnes(room) + " de plus." : null) : depositWhy,
          () -> send(RegionalBankActionPayload.DEPOSIT, 0));

        y = panel(graphics, mouseX, mouseY, x, y, width, "Retirer", state.manager() ? null : "officiers seulement", AMOUNTS, this::cannotWithdraw,
          amount -> send(RegionalBankActionPayload.WITHDRAW, amount), true, amount -> "il restera " + couronnes(state.balance() - amount));

        // The next level
        if (state.upgradeCost() < 0)
        {
            graphics.drawString(font, Component.literal("Banque au niveau maximal").withStyle(ChatFormatting.BOLD), x + 2, y + 3, Palette.MUTED, false);
            return y + 16;
        }
        final int next = state.level() + 1;
        Ui.plate(graphics, x, y, x + width, y + 30, Palette.levelSoft(next), Palette.levelEdge(next), Palette.levelSoft(next));
        graphics.drawString(font, Component.literal("Niveau " + next).withStyle(ChatFormatting.BOLD), x + 6, y + 5, Palette.INK, false);
        graphics.drawString(font, fit("contient jusqu'à " + couronnes(state.nextCapacity()), width - (6)), x + 6, y + 17, Palette.MUTED, false);
        final String label = "Améliorer · " + Wallet.format(state.upgradeCost());
        final int bw = buttonWidth(label);
        final boolean over = Ui.inside(mouseX, mouseY, x + width - 6 - bw, y + 8, bw, 14);
        Ui.levelButton(graphics, font, x + width - 6 - bw, y + 8, bw, 14, label, next, state.manager(), over);
        if (state.manager())
        {
            addHit(x + width - 6 - bw, y + 8, bw, 14, () -> send(RegionalBankActionPayload.UPGRADE, 0));
        }
        if (over)
        {
            hint = state.manager() ? "Payé avec le trésor, complété par les couronnes sur toi." : MANAGERS;
        }
        return y + 34;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }
}
