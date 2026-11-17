package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.RegionalBankActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The bank's body in its hut window: the treasury against what the bank holds, paying in everything carried, and
 * withdrawing (managers only). The level and its upgrade are the hut window's.
 */
final class BankBody implements HutBody
{
    private static final String MANAGERS = "Réservé au propriétaire et aux officiers de la colonie.";

    @Nullable
    private static OpenRegionalBankPayload state;

    static void receive(final OpenRegionalBankPayload payload)
    {
        state = payload;
        ClientTreasury.set(payload.colonyKey(), payload.balance());
    }

    private static void send(final OpenRegionalBankPayload bank, final int action, final long amount)
    {
        PacketDistributor.sendToServer(new RegionalBankActionPayload(bank.bank(), action, amount));
    }

    @Override
    public boolean wantsField()
    {
        return true;
    }

    @Override
    public int render(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final String tab, final IBuildingView building, final int x,
      int y, final int width)
    {
        final OpenRegionalBankPayload bank = state;
        if (bank == null || !bank.bank().equals(building.getPosition()))
        {
            return HutBodies.loading(kit, graphics, x, y, width, building);
        }
        final boolean full = bank.balance() >= bank.capacity();
        final long room = Math.max(0, bank.capacity() - bank.balance());
        final int top = y;
        y = kit.hero(graphics, x, y, width, null, "TRÉSOR DE LA COLONIE", BodyKit.couronnes(bank.balance()),
          full ? "Plein : améliore la banque pour y mettre plus." : "Place pour " + BodyKit.couronnes(room) + " de plus, sur " + Wallet.format(bank.capacity()));
        kit.heroGauge(graphics, x, top, width, bank.balance(), bank.capacity());

        final long purse = kit.purse();
        final String depositWhy = !bank.member() ? "Réservé aux membres de la colonie." : full ? "Le trésor est plein : améliore la banque."
          : purse <= 0 ? "Tu ne portes aucune couronne." : null;
        y = kit.bigButton(graphics, mouseX, mouseY, x, y, width, purse > 0 ? "Verser mes " + BodyKit.couronnes(Math.min(purse, room)) + " au trésor" : "Rien à verser",
          depositWhy == null, depositWhy == null ? (purse > room ? "Le trésor ne prend que " + BodyKit.couronnes(room) + " de plus." : null) : depositWhy,
          () -> send(bank, RegionalBankActionPayload.DEPOSIT, 0));

        y = kit.panel(graphics, mouseX, mouseY, x, y, width, "Retirer", bank.manager() ? null : "officiers seulement", BodyKit.AMOUNTS,
          amount -> !bank.manager() ? MANAGERS : BodyKit.notMore(amount, bank.balance(), "dans le trésor"),
          amount -> send(bank, RegionalBankActionPayload.WITHDRAW, amount), true, amount -> "il restera " + BodyKit.couronnes(bank.balance() - amount));

        if (bank.upgradeCost() >= 0)
        {
            y = kit.note(graphics, x + 2, y + 2, width - 4, "Niveau " + (bank.level() + 1) + " : contient jusqu'à " + BodyKit.couronnes(bank.nextCapacity()), Palette.MUTED) + 4;
        }
        return y;
    }
}
