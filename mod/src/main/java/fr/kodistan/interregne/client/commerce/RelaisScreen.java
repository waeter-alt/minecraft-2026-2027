package fr.kodistan.interregne.client.commerce;

import fr.kodistan.interregne.client.ClientCrateStock;
import fr.kodistan.interregne.client.blocks.CounterScreen;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.commerce.Contract;
import fr.kodistan.interregne.commerce.Contracts;
import fr.kodistan.interregne.commerce.RelaisActionPayload;
import fr.kodistan.interregne.commerce.RelaisStatePayload;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A colony's Relais commercial: first how many contracts it holds, then the form for a new offer (to whom, which
 * crate, how many, at what price), then every contract of the colony with the answer or the caravan's progress.
 */
public final class RelaisScreen extends CounterScreen
{
    private static final String[] CRATES = {"Bois", "Brique", "Blé", "Pierre", "Minerai"};

    private RelaisStatePayload state;
    private int ticks;
    /** False for the UiShot preview: its fake data must not be replaced by the real server's answer. */
    private boolean live = true;
    private int partner;
    private int crate;
    private int quantity = 10;
    private long price = 10;

    private RelaisScreen(final RelaisStatePayload state)
    {
        super("Relais commercial", 380, 330);
        this.state = state;
    }

    /**
     * Opens the window, or refreshes it when it is already showing this colony.
     */
    public static void open(final RelaisStatePayload state)
    {
        if (Minecraft.getInstance().screen instanceof RelaisScreen screen && screen.state.colony() == state.colony())
        {
            screen.state = state;
            return;
        }
        Minecraft.getInstance().setScreen(new RelaisScreen(state));
    }

    /**
     * Shows fake data that is never refreshed (UiShot).
     */
    public static void preview(final RelaisStatePayload state)
    {
        final RelaisScreen screen = new RelaisScreen(state);
        screen.live = false;
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected String crumb()
    {
        return state.colonyName();
    }

    @Override
    public void tick()
    {
        // The caravans move: ask again twice a second
        if (live && ++ticks % 10 == 0)
        {
            send(RelaisActionPayload.OPEN, 0, 0, 0, 0, 0);
        }
    }

    private void send(final int action, final int other, final long contract, final int crate, final int quantity, final long price)
    {
        PacketDistributor.sendToServer(new RelaisActionPayload(action, state.colony(), other, contract, crate, quantity, price));
    }

    private List<RelaisStatePayload.Partner> ready()
    {
        final List<RelaisStatePayload.Partner> list = new ArrayList<>();
        for (final RelaisStatePayload.Partner candidate : state.partners())
        {
            if (candidate.ready())
            {
                list.add(candidate);
            }
        }
        return list;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (state.level() < 1)
        {
            y += Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "En construction", "le Relais commercial doit être terminé");
            return y + 21;
        }
        final int top = y;
        y = hero(graphics, x, y, width, Crate.values()[0].stack(1), "CONTRATS EN COURS", state.contracts().size() + " / " + state.capacity(),
          "Chaque niveau du Relais ouvre deux contrats de plus.");
        final String badge = "Niv. " + state.level();
        Ui.levelBadge(graphics, font, x + width - 8 - font.width(badge) - 8, top + 6, badge, state.level());
        if (!state.notice().isEmpty())
        {
            y += Ui.banner(graphics, font, x, y, width, Ui.State.BAD, "Refusé", state.notice());
            y += 25;
        }
        return y;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (state.level() < 1)
        {
            return y;
        }
        y = renderForm(graphics, mouseX, mouseY, x, y, width);
        Ui.label(graphics, font, "CONTRATS", x, y);
        y += 12;
        if (state.contracts().isEmpty())
        {
            graphics.drawString(font, "Aucun contrat pour l'instant.", x + 2, y, Palette.MUTED, false);
            return y + 14;
        }
        for (final RelaisStatePayload.Row row : state.contracts())
        {
            y = renderRow(graphics, mouseX, mouseY, x, y, width, row);
        }
        return y;
    }

    private int renderForm(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        Ui.label(graphics, font, "NOUVELLE OFFRE", x, y);
        y += 12;
        final List<RelaisStatePayload.Partner> partners = ready();
        if (partners.isEmpty())
        {
            for (final net.minecraft.util.FormattedCharSequence line : font.split(net.minecraft.network.chat.Component.literal(
              "Aucun allié relié par une route : concluez une alliance depuis la Diplomatie, puis tracez la route avec le poste commercial."), width - 4))
            {
                graphics.drawString(font, line, x + 2, y, Palette.MUTED, false);
                y += 10;
            }
            return y + 8;
        }
        partner = Math.clamp(partner, 0, partners.size() - 1);
        graphics.drawString(font, "Acheteur", x + 2, y + 3, Palette.INK, false);
        final int count = partners.size();
        int px = x + 70;
        px += button(graphics, mouseX, mouseY, px, y, "<", Ui.ButtonStyle.SECONDARY, count > 1, null, () -> partner = (partner + count - 1) % count) + 4;
        final String chosenName = Ui.fit(font, partners.get(partner).name(), width - 70 - 60);
        graphics.drawString(font, chosenName, px, y + 3, Palette.INK, false);
        px += font.width(chosenName) + 6;
        button(graphics, mouseX, mouseY, px, y, ">", Ui.ButtonStyle.SECONDARY, count > 1, null, () -> partner = (partner + 1) % count);
        y += 18;
        graphics.drawString(font, "Caisses", x + 2, y + 3, Palette.INK, false);
        segmented(graphics, mouseX, mouseY, x + 70, y, List.of(CRATES), crate, i -> crate = i);
        y += 18;

        final Crate chosen = Crate.values()[crate];
        final int stock = ClientCrateStock.get(chosen);
        graphics.drawString(font, "Quantité", x + 2, y + 3, Palette.INK, false);
        int bx = x + 70;
        bx += button(graphics, mouseX, mouseY, bx, y, "-10", Ui.ButtonStyle.SECONDARY, quantity > 1, null, () -> quantity = Math.max(1, quantity - 10)) + 2;
        bx += button(graphics, mouseX, mouseY, bx, y, "-1", Ui.ButtonStyle.SECONDARY, quantity > 1, null, () -> quantity = Math.max(1, quantity - 1)) + 4;
        final String q = String.valueOf(quantity);
        graphics.drawString(font, q, bx, y + 3, Palette.INK, false);
        bx += Math.max(24, font.width(q) + 6);
        bx += button(graphics, mouseX, mouseY, bx, y, "+1", Ui.ButtonStyle.SECONDARY, quantity < Contracts.MAX_QUANTITY, null, () -> quantity = Math.min(Contracts.MAX_QUANTITY, quantity + 1)) + 2;
        button(graphics, mouseX, mouseY, bx, y, "+10", Ui.ButtonStyle.SECONDARY, quantity < Contracts.MAX_QUANTITY, null, () -> quantity = Math.min(Contracts.MAX_QUANTITY, quantity + 10));
        final String have = "à l'entrepôt : " + stock;
        graphics.drawString(font, have, x + width - 4 - font.width(have), y + 3, stock >= quantity ? Palette.MUTED : Palette.RED, false);
        y += 18;

        graphics.drawString(font, "Prix pièce", x + 2, y + 3, Palette.INK, false);
        bx = x + 70;
        bx += button(graphics, mouseX, mouseY, bx, y, "-10", Ui.ButtonStyle.SECONDARY, price > 1, null, () -> price = Math.max(1, price - 10)) + 2;
        bx += button(graphics, mouseX, mouseY, bx, y, "-1", Ui.ButtonStyle.SECONDARY, price > 1, null, () -> price = Math.max(1, price - 1)) + 4;
        final String p = String.valueOf(price);
        graphics.drawString(font, p, bx, y + 3, Palette.INK, false);
        bx += Math.max(24, font.width(p) + 6);
        bx += button(graphics, mouseX, mouseY, bx, y, "+1", Ui.ButtonStyle.SECONDARY, true, null, () -> price += 1) + 2;
        button(graphics, mouseX, mouseY, bx, y, "+10", Ui.ButtonStyle.SECONDARY, true, null, () -> price += 10);
        final String total = "total " + price * quantity;
        graphics.drawString(font, total, x + width - 4 - font.width(total), y + 3, Palette.MUTED, false);
        y += 20;

        final boolean ok = stock >= quantity && state.contracts().size() < state.capacity();
        final String why = stock < quantity ? "Pas assez de caisses à l'entrepôt." : state.contracts().size() >= state.capacity() ? "Trop de contrats : améliorez le Relais." : null;
        final RelaisStatePayload.Partner target = partners.get(partner);
        y = bigButton(graphics, mouseX, mouseY, x, y, width, "Proposer à " + target.name(), ok, why,
          () -> send(RelaisActionPayload.OFFER, target.id(), 0, crate, quantity, price));
        return y + 4;
    }

    private int renderRow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final RelaisStatePayload.Row row)
    {
        final boolean selling = row.seller() == state.colony();
        final Crate what = Crate.values()[Math.clamp(row.crate(), 0, Crate.values().length - 1)];
        final String title = (selling ? "Vente à " + row.buyerName() : "Achat à " + row.sellerName()) + " · " + row.quantity() + " × " + what.displayName().toLowerCase();
        final String status;
        final String label;
        final Runnable action;
        if (row.state() == Contract.OFFER)
        {
            status = row.unitPrice() + " pièce, " + row.unitPrice() * row.quantity() + " au total · " + (selling ? "en attente de réponse" : "vous devez répondre");
            label = selling ? "Retirer" : "Accepter";
            action = selling ? () -> send(RelaisActionPayload.CANCEL, 0, row.id(), 0, 0, 0) : () -> send(RelaisActionPayload.ACCEPT, 0, row.id(), 0, 0, 0);
        }
        else
        {
            status = row.state() == Contract.WAITING ? "arrivée : en attente de place à l'entrepôt" : "caravane en route : " + row.percent() + " %";
            label = null;
            action = null;
        }
        if (label == null)
        {
            Ui.card(graphics, x, y, width, 34, false, false, false);
            Icon.of(what.stack(1)).draw(graphics, x + 5, y + 9, 16);
            graphics.drawString(font, Ui.fit(font, title, width - 36), x + 26, y + 6, Palette.INK, false);
            graphics.drawString(font, Ui.fit(font, status, width - 36), x + 26, y + 18, Palette.MUTED, false);
            final int barX = x + 26;
            final int barW = width - 36;
            graphics.fill(barX, y + 29, barX + barW, y + 31, Palette.RULE);
            graphics.fill(barX, y + 29, barX + barW * row.percent() / 100, y + 31, Palette.GREEN);
            return y + 38;
        }
        final boolean enabled = true;
        return actionCard(graphics, mouseX, mouseY, x, y, width, Icon.of(what.stack(1)), title, status, label, enabled, null, action);
    }
}
