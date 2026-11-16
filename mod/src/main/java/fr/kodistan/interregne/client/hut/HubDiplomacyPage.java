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
    /** The colony whose « Guerre » was clicked once (a second click declares it). */
    private int confirmWar = -1;

    private static final int A_NONE = 0;
    private static final int A_ALLIED = 1;
    private static final int A_SENT = 2;
    private static final int A_RECEIVED = 3;
    private static final int A_WAR_OUT_NOTICE = 4;
    private static final int A_WAR_OUT = 5;
    private static final int A_WAR_IN_NOTICE = 6;
    private static final int A_WAR_IN = 7;
    /** Influence a declaration costs, as the server's config says (mirrored here for the button; the server enforces it). */
    private static final long WAR_COST_SHOWN = 20;

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
        int wars = 0;
        for (final var entry : sync.entries())
        {
            wars += entry.status() >= A_WAR_OUT_NOTICE ? 1 : 0;
        }
        y += Ui.banner(graphics, hub.font(), x, y, width, wars > 0 ? Ui.State.BAD : received > 0 ? Ui.State.WAIT : Ui.State.OK, allies + (allies > 1 ? " alliés" : " allié"),
          wars > 0 ? wars + (wars > 1 ? " guerres" : " guerre") : received > 0 ? received + (received > 1 ? " propositions à examiner" : " proposition à examiner")
            : sync.entries().size() + " autres colonies");
        y += 20;
        if (!mayAct)
        {
            y = Ui.wrapped(graphics, hub.font(), Component.literal("Votre rang ne permet pas de conclure ni de rompre une alliance."), x + 2, y, width - 4, Palette.MUTED, 10) + 4;
        }
        if (sync.entries().isEmpty())
        {
            return Ui.wrapped(graphics, hub.font(), Component.literal("Aucune autre colonie sur le serveur pour l'instant."), x + 2, y, width - 4, Palette.MUTED, 10) + 6;
        }
        final String[] titles = { "PROPOSITIONS REÇUES", "ALLIÉS", "PROPOSITIONS ENVOYÉES", "EN GUERRE", "AUTRES COLONIES" };
        final int[][] order = { { A_RECEIVED }, { A_ALLIED }, { A_SENT }, { A_WAR_IN_NOTICE, A_WAR_IN, A_WAR_OUT_NOTICE, A_WAR_OUT }, { A_NONE } };
        for (int section = 0; section < order.length; section++)
        {
            boolean titled = false;
            for (final var entry : sync.entries())
            {
                boolean inSection = false;
                for (final int status : order[section])
                {
                    inSection |= entry.status() == status;
                }
                if (!inSection)
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
        final boolean war = status >= A_WAR_OUT_NOTICE;
        Ui.card(graphics, x, y, width, rowH, false, false, false);
        graphics.fill(x + 1, y + 1, x + 4, y + rowH - 1, war ? Palette.RED : switch (status)
        {
            case A_ALLIED -> Palette.GREEN;
            case A_NONE -> Palette.MUTED;
            default -> Palette.AMBER;
        });

        final int id = entry.id();
        final int mine = hub.colony.getID();
        String primary = null;
        int primaryAction = 0;
        String secondary = null;
        int secondaryAction = 0;
        Ui.ButtonStyle primaryStyle = Ui.ButtonStyle.PRIMARY;
        Ui.ButtonStyle secondaryStyle = Ui.ButtonStyle.SECONDARY;
        boolean secondaryEnabled = mayAct;
        switch (status)
        {
            case A_ALLIED ->
            {
                primary = confirmBreak == id ? "Confirmer la rupture" : "Rompre";
                primaryAction = DiplomacyActionPayload.BREAK;
                primaryStyle = Ui.ButtonStyle.DANGER;
            }
            case A_SENT ->
            {
                primary = "Annuler";
                primaryAction = DiplomacyActionPayload.WITHDRAW;
                primaryStyle = Ui.ButtonStyle.SECONDARY;
            }
            case A_RECEIVED ->
            {
                primary = "Accepter";
                primaryAction = DiplomacyActionPayload.ACCEPT;
                secondary = "Refuser";
                secondaryAction = DiplomacyActionPayload.WITHDRAW;
            }
            case A_WAR_OUT_NOTICE, A_WAR_OUT ->
            {
                primary = "Faire la paix";
                primaryAction = DiplomacyActionPayload.MAKE_PEACE;
                primaryStyle = Ui.ButtonStyle.SECONDARY;
            }
            case A_NONE ->
            {
                primary = "Proposer";
                primaryAction = DiplomacyActionPayload.PROPOSE;
                secondary = confirmWar == id ? "Confirmer · " + WAR_COST_SHOWN + " ★" : "Guerre · " + WAR_COST_SHOWN + " ★";
                secondaryAction = DiplomacyActionPayload.DECLARE_WAR;
                secondaryStyle = Ui.ButtonStyle.DANGER;
                secondaryEnabled = mayAct && fr.kodistan.interregne.client.hud.InterregneHud.influence() >= WAR_COST_SHOWN;
            }
            default ->
            {
            }
        }
        final int by = y + (rowH - Ui.BUTTON_H) / 2;
        int bx = x + width - 4;
        int reserved = 0;
        if (primary != null)
        {
            final int pw = hub.font().width(primary) + 16;
            bx -= pw;
            reserved += pw;
            final boolean breaking = status == A_ALLIED;
            final int action = primaryAction;
            hub.seatButton(graphics, mouseX, mouseY, bx, by, pw, primary, primaryStyle, mayAct, () -> {
                if (breaking && confirmBreak != id)
                {
                    confirmBreak = id;
                    return;
                }
                confirmBreak = -1;
                ClientDiplomacy.act(mine, action, id);
            });
        }
        if (secondary != null)
        {
            final int bw = hub.font().width(secondary) + 16;
            bx -= bw + 4;
            reserved += bw + 4;
            final int action = secondaryAction;
            final boolean declaring = action == DiplomacyActionPayload.DECLARE_WAR;
            hub.seatButton(graphics, mouseX, mouseY, bx, by, bw, secondary, secondaryStyle, secondaryEnabled, () -> {
                if (declaring && confirmWar != id)
                {
                    confirmWar = id;
                    return;
                }
                confirmWar = -1;
                ClientDiplomacy.act(mine, action, id);
            });
        }

        final int textRoom = width - reserved - 18;
        graphics.drawString(hub.font(), Component.literal(Ui.fit(hub.font(), entry.name(), textRoom)).withStyle(ChatFormatting.BOLD), x + 9, y + 5, Palette.INK, false);
        final double dx = entry.x() - hub.colony.getCenter().getX();
        final double dz = entry.z() - hub.colony.getCenter().getZ();
        String detail = entry.owner() + " · " + entry.citizens() + " hab. · à " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocs";
        int detailColor = Palette.MUTED;
        if (war)
        {
            final boolean attacker = status == A_WAR_OUT_NOTICE || status == A_WAR_OUT;
            final int left = Math.max(0, entry.noticeSeconds() - ClientDiplomacy.secondsSince(mine));
            final boolean notice = (status == A_WAR_OUT_NOTICE || status == A_WAR_IN_NOTICE) && left > 0;
            detail = notice ? (attacker ? "Guerre déclarée · ouverte dans " : "Vous déclare la guerre · ouverte dans ") + left / 60 + ":" + String.format("%02d", left % 60)
                            : attacker ? "Guerre ouverte : vous pouvez l'assaillir" : "Guerre ouverte : elle peut vous assaillir";
            detailColor = Palette.RED;
        }
        graphics.drawString(hub.font(), Ui.fit(hub.font(), detail, textRoom), x + 9, y + 15, detailColor, false);
        return y + rowH + 2;
    }
}
