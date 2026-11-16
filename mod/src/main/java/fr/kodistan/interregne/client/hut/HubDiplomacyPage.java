package fr.kodistan.interregne.client.hut;

import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.network.DiplomacyActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Diplomatie : les autres colonies du serveur et le lien avec chacune (proposer, accepter, refuser, rompre une alliance).
 * Une page du hub de colonie ({@link ColonyHubScreen}) : elle dessine dans le cadre du hub et lui emprunte son état.
 */
final class HubDiplomacyPage
{
    private final ColonyHubScreen hub;
    /**
     * Whether the colonies list was asked from the server, and the ally whose « Rompre » was clicked once (a second
     * click confirms).
     */
    boolean asked;
    private int confirmBreak = -1;

    HubDiplomacyPage(final ColonyHubScreen hub)
    {
        this.hub = hub;
    }

    /**
     * Every other colony of the server and the tie to each: propose, accept or refuse an alliance, break one. The list
     * is asked from the server the first time the page draws, then kept fresh by the server's own broadcasts.
     */
    int renderDiplomacy(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        if (!asked)
        {
            asked = true;
            ClientDiplomacy.refresh(hub.colony.getID());
        }
        final fr.kodistan.interregne.network.DiplomacySyncPayload sync = ClientDiplomacy.of(hub.colony.getID());
        if (sync == null)
        {
            y += Ui.banner(graphics, hub.font(), x, y, width, Ui.State.WAIT, "Diplomatie", "chargement…");
            return y + 20;
        }
        final boolean mayAct = hub.colony.getPermissions().hasPermission(hub.minecraft().player, com.minecolonies.api.colony.permissions.Action.EDIT_PERMISSIONS);
        int allies = 0;
        int received = 0;
        for (final var entry : sync.entries())
        {
            allies += entry.status() == fr.kodistan.interregne.politics.Alliances.ALLIED ? 1 : 0;
            received += entry.status() == fr.kodistan.interregne.politics.Alliances.RECEIVED ? 1 : 0;
        }
        y += Ui.banner(graphics, hub.font(), x, y, width, received > 0 ? Ui.State.WAIT : Ui.State.OK, allies + (allies > 1 ? " alliés" : " allié"),
          received > 0 ? received + (received > 1 ? " propositions à examiner" : " proposition à examiner") : sync.entries().size() + " autres colonies");
        y += 20;
        if (!mayAct)
        {
            y = Ui.wrapped(graphics, hub.font(), Component.literal("Votre rang ne permet pas de conclure ni de rompre une alliance."), x + 2, y, width - 4, Palette.MUTED, 10) + 4;
        }
        if (sync.entries().isEmpty())
        {
            return Ui.wrapped(graphics, hub.font(), Component.literal("Aucune autre colonie sur le serveur pour l'instant."), x + 2, y, width - 4, Palette.MUTED, 10) + 6;
        }
        final String[] titles = { "PROPOSITIONS REÇUES", "ALLIÉS", "PROPOSITIONS ENVOYÉES", "AUTRES COLONIES" };
        final int[] order = { fr.kodistan.interregne.politics.Alliances.RECEIVED, fr.kodistan.interregne.politics.Alliances.ALLIED,
          fr.kodistan.interregne.politics.Alliances.SENT, fr.kodistan.interregne.politics.Alliances.NONE };
        for (int section = 0; section < order.length; section++)
        {
            boolean titled = false;
            for (final var entry : sync.entries())
            {
                if (entry.status() != order[section])
                {
                    continue;
                }
                if (!titled)
                {
                    titled = true;
                    Ui.label(graphics, hub.font(), titles[section], x, y);
                    y += 11;
                }
                y = diplomacyRow(graphics, mouseX, mouseY, x, y, width, entry, mayAct);
            }
            if (titled)
            {
                y += 4;
            }
        }
        return y;
    }

    private int diplomacyRow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final fr.kodistan.interregne.network.DiplomacySyncPayload.Entry entry, final boolean mayAct)
    {
        final int rowH = 26;
        final int status = entry.status();
        Ui.card(graphics, x, y, width, rowH, false, false, false);
        graphics.fill(x + 1, y + 1, x + 4, y + rowH - 1, switch (status)
        {
            case fr.kodistan.interregne.politics.Alliances.ALLIED -> Palette.GREEN;
            case fr.kodistan.interregne.politics.Alliances.NONE -> Palette.MUTED;
            default -> Palette.AMBER;
        });

        final int id = entry.id();
        final int mine = hub.colony.getID();
        final String primary;
        final String secondary;
        final int primaryAction;
        final int secondaryAction;
        switch (status)
        {
            case fr.kodistan.interregne.politics.Alliances.ALLIED ->
            {
                primary = confirmBreak == id ? "Confirmer la rupture" : "Rompre";
                primaryAction = DiplomacyActionPayload.BREAK;
                secondary = null;
                secondaryAction = 0;
            }
            case fr.kodistan.interregne.politics.Alliances.SENT ->
            {
                primary = "Annuler";
                primaryAction = DiplomacyActionPayload.WITHDRAW;
                secondary = null;
                secondaryAction = 0;
            }
            case fr.kodistan.interregne.politics.Alliances.RECEIVED ->
            {
                primary = "Accepter";
                primaryAction = DiplomacyActionPayload.ACCEPT;
                secondary = "Refuser";
                secondaryAction = DiplomacyActionPayload.WITHDRAW;
            }
            default ->
            {
                primary = "Proposer";
                primaryAction = DiplomacyActionPayload.PROPOSE;
                secondary = null;
                secondaryAction = 0;
            }
        }
        final boolean breaking = status == fr.kodistan.interregne.politics.Alliances.ALLIED;
        final Ui.ButtonStyle primaryStyle = breaking ? Ui.ButtonStyle.DANGER : status == fr.kodistan.interregne.politics.Alliances.SENT ? Ui.ButtonStyle.SECONDARY
                                              : Ui.ButtonStyle.PRIMARY;
        final int by = y + (rowH - Ui.BUTTON_H) / 2;
        int bx = x + width - 4;
        int reserved = 0;
        if (secondary != null)
        {
            final int bw = hub.font().width(secondary) + 16;
            bx -= bw;
            reserved += bw + 4;
            hub.seatButton(graphics, mouseX, mouseY, bx, by, bw, secondary, Ui.ButtonStyle.SECONDARY, mayAct, () -> ClientDiplomacy.act(mine, secondaryAction, id));
            bx -= 4;
        }
        final int pw = hub.font().width(primary) + 16;
        bx -= pw;
        reserved += pw;
        hub.seatButton(graphics, mouseX, mouseY, bx, by, pw, primary, primaryStyle, mayAct, () -> {
            if (breaking && confirmBreak != id)
            {
                confirmBreak = id;
                return;
            }
            confirmBreak = -1;
            ClientDiplomacy.act(mine, primaryAction, id);
        });

        final int textRoom = width - reserved - 18;
        graphics.drawString(hub.font(), Component.literal(Ui.fit(hub.font(), entry.name(), textRoom)).withStyle(ChatFormatting.BOLD), x + 9, y + 5, Palette.INK, false);
        final double dx = entry.x() - hub.colony.getCenter().getX();
        final double dz = entry.z() - hub.colony.getCenter().getZ();
        final String detail = entry.owner() + " · " + entry.citizens() + " hab. · à " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocs";
        graphics.drawString(hub.font(), Ui.fit(hub.font(), detail, textRoom), x + 9, y + 15, Palette.MUTED, false);
        return y + rowH + 2;
    }
}
