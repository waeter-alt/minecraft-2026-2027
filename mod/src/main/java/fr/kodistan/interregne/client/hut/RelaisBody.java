package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ClientCrateStock;
import fr.kodistan.interregne.client.commerce.KingdomMapScreen;
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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Relais commercial's body in its hut window: the contracts of the colony (to answer, to withdraw, caravans on their
 * way) and, on its own page, the form for a new offer. The level and its upgrade are the hut window's.
 */
final class RelaisBody implements HutBody
{
    private static final String OFFER = "offre";
    private static final String[] CRATES = {"Bois", "Brique", "Blé", "Pierre", "Minerai"};

    @Nullable
    private static RelaisStatePayload state;
    /** False for the UiShot preview: its fake data must not be replaced by the real server's answer. */
    private static boolean live = true;
    private static int ticks;
    private static int partner;
    private static int crate;
    private static int quantity = 10;
    private static long price = 10;
    private static int escort;

    static void receive(final RelaisStatePayload payload)
    {
        state = payload;
    }

    /**
     * Fake data that is never refreshed (UiShot).
     */
    static void preview(final RelaisStatePayload payload, final int escortTier)
    {
        state = payload;
        live = false;
        escort = escortTier;
    }

    @Override
    public List<Tab> tabs(final IBuildingView building)
    {
        return List.of(new Tab(OFFER, "Nouvelle offre", Icon.of(Crate.values()[0].stack(1))));
    }

    @Override
    public void tick(final IBuildingView building)
    {
        // The caravans move: ask again twice a second
        if (live && ++ticks % 10 == 0)
        {
            send(building.getColony().getID(), RelaisActionPayload.OPEN, 0, 0, 0, 0, 0, 0);
        }
    }

    private static void send(final int colony, final int action, final int other, final long contract, final int crate, final int quantity, final long price,
      final int escort)
    {
        PacketDistributor.sendToServer(new RelaisActionPayload(action, colony, other, contract, crate, quantity, price, escort));
    }

    private static List<RelaisStatePayload.Partner> ready(final RelaisStatePayload relais)
    {
        final List<RelaisStatePayload.Partner> list = new ArrayList<>();
        for (final RelaisStatePayload.Partner candidate : relais.partners())
        {
            if (candidate.ready())
            {
                list.add(candidate);
            }
        }
        return list;
    }

    @Override
    public int render(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final String tab, final IBuildingView building, final int x,
      int y, final int width)
    {
        final RelaisStatePayload relais = state;
        if (relais == null || relais.colony() != building.getColony().getID())
        {
            return HutBodies.loading(kit, graphics, x, y, width, building);
        }
        if (relais.level() < 1)
        {
            return y + Ui.banner(graphics, kit.font(), x, y, width, Ui.State.WAIT, "En construction", "le Relais commercial doit être terminé") + 21;
        }
        if (!relais.notice().isEmpty())
        {
            y += Ui.banner(graphics, kit.font(), x, y, width, Ui.State.BAD, "Refusé", relais.notice());
            y += 25;
        }
        return OFFER.equals(tab) ? form(kit, graphics, mouseX, mouseY, relais, x, y, width) : contracts(kit, graphics, mouseX, mouseY, relais, x, y, width);
    }

    private static int contracts(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final RelaisStatePayload relais, final int x, int y,
      final int width)
    {
        y = kit.hero(graphics, x, y, width, Crate.values()[0].stack(1), "CONTRATS EN COURS", relais.contracts().size() + " / " + relais.capacity(),
          "Chaque niveau du Relais ouvre deux contrats de plus.");
        Ui.label(graphics, kit.font(), "CONTRATS", x, y);
        final String map = "Carte du royaume";
        kit.button(graphics, mouseX, mouseY, x + width - kit.buttonWidth(map), y - 3, map, Ui.ButtonStyle.SECONDARY, true, "Voir les routes et les caravanes en route (touche M)",
          () -> Minecraft.getInstance().setScreen(new KingdomMapScreen(Minecraft.getInstance().screen)));
        y += 12;
        if (relais.contracts().isEmpty())
        {
            graphics.drawString(kit.font(), "Aucun contrat pour l'instant.", x + 2, y, Palette.MUTED, false);
            return y + 14;
        }
        for (final RelaisStatePayload.Row row : relais.contracts())
        {
            y = row(kit, graphics, mouseX, mouseY, relais, x, y, width, row);
        }
        return y;
    }

    private static int form(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final RelaisStatePayload relais, final int x, int y,
      final int width)
    {
        final var font = kit.font();
        final List<RelaisStatePayload.Partner> partners = ready(relais);
        if (partners.isEmpty())
        {
            for (final FormattedCharSequence line : font.split(Component.literal(
              "Aucun allié relié par une route : concluez une alliance depuis la Diplomatie, puis tracez la route avec le poste commercial."), width - 4))
            {
                graphics.drawString(font, line, x + 2, y, Palette.MUTED, false);
                y += 10;
            }
            return y + 8;
        }
        // Labels take the room they need, the controls start after the longest one
        final int labelW = Math.max(font.width("Quantité"), Math.max(font.width("Prix pièce"), font.width("Escorte"))) + 8;
        final int cx = x + labelW;
        partner = Math.clamp(partner, 0, partners.size() - 1);
        graphics.drawString(font, "Acheteur", x + 2, y + 3, Palette.INK, false);
        final int count = partners.size();
        int px = cx;
        px += kit.button(graphics, mouseX, mouseY, px, y, "<", Ui.ButtonStyle.SECONDARY, count > 1, null, () -> partner = (partner + count - 1) % count) + 4;
        final String chosenName = kit.fit(partners.get(partner).name(), Math.max(30, width - labelW - 40));
        graphics.drawString(font, chosenName, px, y + 3, Palette.INK, false);
        px += font.width(chosenName) + 6;
        kit.button(graphics, mouseX, mouseY, px, y, ">", Ui.ButtonStyle.SECONDARY, count > 1, null, () -> partner = (partner + 1) % count);
        y += 18;
        graphics.drawString(font, "Caisses", x + 2, y + 3, Palette.INK, false);
        y = kit.segmented(graphics, mouseX, mouseY, cx, x + width, y, List.of(CRATES), crate, i -> crate = i) + 1;

        final Crate chosen = Crate.values()[crate];
        final int stock = ClientCrateStock.get(chosen);
        graphics.drawString(font, "Quantité", x + 2, y + 3, Palette.INK, false);
        stepper(kit, graphics, mouseX, mouseY, cx, y, String.valueOf(quantity), quantity > 1, quantity < Contracts.MAX_QUANTITY,
          d -> quantity = Math.clamp((long) quantity + d, 1, Contracts.MAX_QUANTITY));
        y += 14;
        y = kit.note(graphics, x + 2, y + 1, width - 4, "à l'entrepôt : " + stock, stock >= quantity ? Palette.MUTED : Palette.RED) + 5;

        graphics.drawString(font, "Prix pièce", x + 2, y + 3, Palette.INK, false);
        stepper(kit, graphics, mouseX, mouseY, cx, y, String.valueOf(price), price > 1, true, d -> price = Math.max(1, price + d));
        y += 14;
        y = kit.note(graphics, x + 2, y + 1, width - 4, "total " + price * quantity, Palette.MUTED) + 6;

        final int maxEscort = Math.min(relais.level(), relais.tiers().size());
        escort = Math.clamp(escort, 0, maxEscort);
        graphics.drawString(font, "Escorte", x + 2, y + 3, Palette.INK, false);
        int ex = cx;
        ex += kit.button(graphics, mouseX, mouseY, ex, y, "<", Ui.ButtonStyle.SECONDARY, escort > 0, null, () -> escort--) + 4;
        graphics.drawString(font, escort == 0 ? "Aucune" : "Palier " + escort, ex, y + 3, Palette.INK, false);
        ex += Math.max(font.width("Palier 5"), font.width("Aucune")) + 6;
        kit.button(graphics, mouseX, mouseY, ex, y, ">", Ui.ButtonStyle.SECONDARY, escort < maxEscort,
          escort < maxEscort ? null : "Chaque niveau du Relais ouvre un palier d'escorte de plus.", () -> escort++);
        y += 15;
        final long escortCost = escort == 0 ? 0 : relais.tiers().get(escort - 1).cost();
        final String escortLine = escort == 0 ? "Sans escorte, la caravane ne se défend pas seule."
          : relais.tiers().get(escort - 1).swordsmen() + " épéistes et " + relais.tiers().get(escort - 1).archers() + " archers · " + escortCost + " à chaque contrat";
        y = kit.note(graphics, x + 2, y, width - 4, escortLine, escort > 0 && relais.available() < escortCost ? Palette.RED : Palette.MUTED) + 6;

        final boolean ok = stock >= quantity && relais.contracts().size() < relais.capacity() && relais.available() >= escortCost;
        final String why = stock < quantity ? "Pas assez de caisses à l'entrepôt." : relais.contracts().size() >= relais.capacity() ? "Trop de contrats : améliorez le Relais."
          : relais.available() < escortCost ? "Il vous faut " + escortCost + " pour l'escorte (trésor régional et couronnes sur vous)." : null;
        final RelaisStatePayload.Partner target = partners.get(partner);
        y = kit.bigButton(graphics, mouseX, mouseY, x, y, width, "Proposer à " + target.name(), ok, why,
          () -> send(relais.colony(), RelaisActionPayload.OFFER, target.id(), 0, crate, quantity, price, escort));
        return y + 4;
    }

    /**
     * "-10 -1 value +1 +10": one row to adjust a number.
     */
    private static void stepper(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final String value,
      final boolean canLower, final boolean canRaise, final java.util.function.IntConsumer change)
    {
        int bx = x;
        bx += kit.button(graphics, mouseX, mouseY, bx, y, "-10", Ui.ButtonStyle.SECONDARY, canLower, null, () -> change.accept(-10)) + 2;
        bx += kit.button(graphics, mouseX, mouseY, bx, y, "-1", Ui.ButtonStyle.SECONDARY, canLower, null, () -> change.accept(-1)) + 4;
        graphics.drawString(kit.font(), value, bx, y + 3, Palette.INK, false);
        bx += Math.max(24, kit.font().width(value) + 6);
        bx += kit.button(graphics, mouseX, mouseY, bx, y, "+1", Ui.ButtonStyle.SECONDARY, canRaise, null, () -> change.accept(1)) + 2;
        kit.button(graphics, mouseX, mouseY, bx, y, "+10", Ui.ButtonStyle.SECONDARY, canRaise, null, () -> change.accept(10));
    }

    private static int row(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final RelaisStatePayload relais, final int x, final int y,
      final int width, final RelaisStatePayload.Row row)
    {
        final boolean selling = row.seller() == relais.colony();
        final Crate what = Crate.values()[Math.clamp(row.crate(), 0, Crate.values().length - 1)];
        final String title = (selling ? "Vente à " + row.buyerName() : "Achat à " + row.sellerName()) + " · " + row.quantity() + " × " + what.displayName().toLowerCase();
        if (row.state() == Contract.OFFER)
        {
            final String status = row.unitPrice() + " pièce, " + row.unitPrice() * row.quantity() + " au total · " + (selling ? "en attente de réponse" : "vous devez répondre")
                                  + (row.escort() > 0 ? " · escorte palier " + row.escort() + " (" + row.soldiers() + " soldats)" : "");
            final Runnable action = selling ? () -> send(relais.colony(), RelaisActionPayload.CANCEL, 0, row.id(), 0, 0, 0, 0)
                                            : () -> send(relais.colony(), RelaisActionPayload.ACCEPT, 0, row.id(), 0, 0, 0, 0);
            return kit.actionCard(graphics, mouseX, mouseY, x, y, width, Icon.of(what.stack(1)), title, status, selling ? "Retirer" : "Accepter", true, null, action);
        }
        final String status = row.state() == Contract.WAITING ? "arrivée : en attente de place à l'entrepôt" : "caravane en route : " + row.percent() + " %"
                                                                                                           + (row.escort() > 0 ? " · escorte " + row.soldiers() + " soldat"
                                                                                                                                 + (row.soldiers() > 1 ? "s" : "") : "");
        // The title and the status wrap in the room the icon leaves; the card grows to hold them and the progress bar
        final int textW = width - 36;
        final int titleLines = kit.font().split(Component.literal(title), textW).size();
        final int statusLines = kit.font().split(Component.literal(status), textW).size();
        final int height = 14 + titleLines * 10 + statusLines * 10 + 8;
        Ui.card(graphics, x, y, width, height, false, false, false);
        Icon.of(what.stack(1)).draw(graphics, x + 5, y + (height - 16) / 2, 16);
        int ty = Ui.wrapped(graphics, kit.font(), Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD), x + 26, y + 6, textW, Palette.INK, 10);
        ty = Ui.wrapped(graphics, kit.font(), Component.literal(status), x + 26, ty + 1, textW, Palette.MUTED, 10);
        final int barY = y + height - 8;
        graphics.fill(x + 26, barY, x + 26 + textW, barY + 2, Palette.RULE);
        graphics.fill(x + 26, barY, x + 26 + textW * row.percent() / 100, barY + 2, Palette.GREEN);
        return y + height + 4;
    }
}
