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
     * Whether the colonies list was asked from the server, and the ally whose « Rompre » (or the enemy whose
     * « Abandonner ») was clicked once (a second click confirms).
     */
    boolean asked;
    private int confirmBreak = -1;
    /** The colony whose « Guerre » was clicked once (a second click declares it). */
    private int confirmWar = -1;
    /** The attacker whose « Capituler » was clicked once (a second click pays the tribute). */
    private int confirmCapitulate = -1;
    /** The attacker whose « Exiger la reddition » was clicked once (a second click ends the war). */
    private int confirmDemand = -1;

    private static final int A_NONE = 0;
    private static final int A_ALLIED = 1;
    private static final int A_SENT = 2;
    private static final int A_RECEIVED = 3;
    private static final int A_WAR_OUT_NOTICE = 4;
    private static final int A_WAR_OUT = 5;
    private static final int A_WAR_IN_NOTICE = 6;
    private static final int A_WAR_IN = 7;
    /** Soldiers chosen for the next assault, by target colony: {legionnaires, archers}. */
    private final java.util.Map<Integer, int[]> chosen = new java.util.HashMap<>();

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
                y = diplomacyRow(graphics, mouseX, mouseY, x, y, width, sync, entry, mayAct);
            }
            if (titled)
            {
                y += 4;
            }
        }
        return y;
    }

    private int diplomacyRow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final fr.kodistan.interregne.network.DiplomacySyncPayload sync, final fr.kodistan.interregne.network.DiplomacySyncPayload.Entry entry, final boolean mayAct)
    {
        final int status = entry.status();
        final boolean war = status >= A_WAR_OUT_NOTICE;
        final boolean campaign = entry.campaign() != fr.kodistan.interregne.network.DiplomacySyncPayload.CAMPAIGN_NONE;
        // An open war of ours with no troops out: a second line to pick the soldiers and launch the assault.
        final boolean launcher = status == A_WAR_OUT && !campaign;
        // Troops of ours out: the recall button gets that second line, so the march news stays readable.
        final boolean recall = (status == A_WAR_OUT || status == A_WAR_OUT_NOTICE) && campaign;
        // At war against us (notice or open): the tribute that ends it gets that second line.
        final boolean surrender = status == A_WAR_IN_NOTICE || status == A_WAR_IN;
        // An ally: the contingent kept for it (Aide de clan) gets that second line.
        final boolean aid = status == A_ALLIED;
        // « Exiger la reddition » shares the line of « Capituler » when both fit, else it gets a line of its own
        final int surrenderRoom = width - 13;
        final int capitulateW = surrender ? capitulateWidth(sync, surrenderRoom, true) : 0;
        final boolean demandStacked = surrender && capitulateW + 4 + hub.font().width(DEMAND_SHORT) + 16 > surrenderRoom;
        final int rowH = (launcher || recall || surrender || aid ? 26 + Ui.BUTTON_H + 4 : 26) + (demandStacked ? Ui.BUTTON_H + 4 : 0);
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
                primary = confirmBreak == id ? "Confirmer l'abandon" : "Abandonner";
                primaryAction = DiplomacyActionPayload.MAKE_PEACE;
                primaryStyle = Ui.ButtonStyle.DANGER;
            }
            case A_NONE ->
            {
                primary = "Proposer";
                primaryAction = DiplomacyActionPayload.PROPOSE;
                secondary = confirmWar == id ? "Confirmer · " + sync.warCost() + " ★" : "Guerre · " + sync.warCost() + " ★";
                secondaryAction = DiplomacyActionPayload.DECLARE_WAR;
                secondaryStyle = Ui.ButtonStyle.DANGER;
                secondaryEnabled = mayAct && fr.kodistan.interregne.client.hud.InterregneHud.influence() >= sync.warCost();
            }
            default ->
            {
            }
        }
        final int by = y + (26 - Ui.BUTTON_H) / 2;
        int bx = x + width - 4;
        int reserved = 0;
        if (primary != null)
        {
            final int pw = hub.font().width(primary) + 16;
            bx -= pw;
            reserved += pw;
            final boolean twoClicks = status == A_ALLIED || status == A_WAR_OUT || status == A_WAR_OUT_NOTICE;
            final int action = primaryAction;
            hub.seatButton(graphics, mouseX, mouseY, bx, by, pw, primary, primaryStyle, mayAct, () -> {
                if (twoClicks && confirmBreak != id)
                {
                    confirmBreak = id;
                    return;
                }
                confirmBreak = -1;
                ClientDiplomacy.act(mine, action, id);
            });
            if (action == DiplomacyActionPayload.MAKE_PEACE && mouseX >= bx && mouseX < bx + pw && mouseY >= by && mouseY < by + Ui.BUTTON_H)
            {
                hub.hint = "Vos troupes rentrent et la guerre est perdue : l'adversaire gagne l'Influence de la victoire et un bouclier.";
            }
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
            if (entry.campaign() == fr.kodistan.interregne.network.DiplomacySyncPayload.CAMPAIGN_MARCHING)
            {
                detail = attacker ? "Vos troupes marchent sur elle" : "Ses troupes marchent sur vous";
            }
            else if (entry.campaign() == fr.kodistan.interregne.network.DiplomacySyncPayload.CAMPAIGN_ASSAULTING)
            {
                detail = attacker ? "Vos troupes l'assaillent" : "Ses troupes vous assaillent";
            }
            else if (attacker && entry.roadState() != fr.kodistan.interregne.network.DiplomacySyncPayload.ROAD_READY)
            {
                // Road news replaces the longer sentence: both together get cut at GUI scale 4.
                final String road = entry.roadState() == fr.kodistan.interregne.network.DiplomacySyncPayload.ROAD_BUILDING ? "route en chantier" : "sans route";
                detail = notice ? "Ouverte dans " + left / 60 + ":" + String.format("%02d", left % 60) + " · " + road
                                : "Ouverte · " + road;
            }
            detailColor = Palette.RED;
        }
        graphics.drawString(hub.font(), Ui.fit(hub.font(), detail, textRoom), x + 9, y + 15, detailColor, false);
        if (launcher)
        {
            assaultLine(graphics, mouseX, mouseY, x + 9, y + 26, width - 13, sync, entry, mayAct);
        }
        else if (recall)
        {
            final String label = "Rappeler les troupes";
            final int rw = hub.font().width(label) + 16;
            hub.seatButton(graphics, mouseX, mouseY, x + width - 4 - rw, y + 26, rw, label, Ui.ButtonStyle.SECONDARY, mayAct,
              () -> ClientDiplomacy.act(mine, DiplomacyActionPayload.RECALL_TROOPS, id));
        }
        else if (surrender)
        {
            final int capitulateX = surrenderButton(graphics, mouseX, mouseY, x + width - 4, y + 26, surrenderRoom, sync, id, mayAct);
            if (demandStacked)
            {
                demandButton(graphics, mouseX, mouseY, x + width - 4, y + 26 + Ui.BUTTON_H + 4, surrenderRoom, entry, mayAct);
            }
            else
            {
                demandButton(graphics, mouseX, mouseY, capitulateX - 4, y + 26, capitulateX - 4 - (x + 9), entry, mayAct);
            }
        }
        else if (aid)
        {
            aidLine(graphics, mouseX, mouseY, x + 9, x + width - 4, y + 26, sync, entry, mayAct);
        }
        return y + rowH + 2;
    }

    /**
     * Aide de clan, on an ally's row: the contingent this colony keeps for it ({@code n/max}, or how many still stand
     * while it fights there), the reinforcements that ally keeps for us, and « Remplir » with the price inside while
     * the contingent is home and not full.
     */
    private void aidLine(final GuiGraphics graphics, final int mouseX, final int mouseY, final int left, final int right, final int y,
      final fr.kodistan.interregne.network.DiplomacySyncPayload sync, final fr.kodistan.interregne.network.DiplomacySyncPayload.Entry entry, final boolean mayAct)
    {
        final int max = sync.aidMax();
        final boolean out = entry.engagedOut() >= 0;
        int buttonX = right;
        if (!out && entry.aidOut() < max)
        {
            final long cost = (long) (max - entry.aidOut()) * sync.aidPrice();
            buttonX = refillButton(graphics, mouseX, mouseY, right, y, right - left, cost, sync.treasury(), mayAct, entry);
        }
        // Longest wording that fits beside the button, so nothing is cut at GUI scale 4; the hint says it all anyway
        final String mine = out ? "Contingent engagé : " + entry.engagedOut() + " debout" : "Contingent : " + entry.aidOut() + "/" + max;
        final String mineShort = out ? "Engagé : " + entry.engagedOut() : entry.aidOut() + "/" + max;
        final boolean theirsHere = entry.engagedIn() >= 0;
        final int theirs = theirsHere ? entry.engagedIn() : entry.aidIn();
        final String[] wordings = theirsHere || theirs > 0
          ? new String[] {mine + " · " + (theirsHere ? "Leurs renforts ici : " : "Renforts réservés : ") + theirs, mine + " · Renforts : " + theirs,
                          mineShort + " · Renforts : " + theirs, mineShort + " · Renf. " + theirs}
          : new String[] {mine, mineShort};
        final int room = buttonX - left - 6;
        String text = wordings[wordings.length - 1];
        for (final String wording : wordings)
        {
            if (hub.font().width(wording) <= room)
            {
                text = wording;
                break;
            }
        }
        final String shown = Ui.fit(hub.font(), text, room);
        graphics.drawString(hub.font(), shown, left, y + (Ui.BUTTON_H - 8) / 2, out ? Palette.AMBER_TEXT : Palette.INK, false);
        if (Ui.inside(mouseX, mouseY, left, y, Math.min(room, hub.font().width(shown)), Ui.BUTTON_H))
        {
            hub.hint = (out ? "Votre contingent se bat chez " + entry.name() + " : les survivants reviennent dans la réserve à la fin de la guerre."
                            : "Votre contingent pour " + entry.name() + " : " + entry.aidOut() + "/" + max + " soldats, qui apparaissent chez elle au premier assaut qu'elle subit. Vos propres troupes ne bougent pas.")
                       + (theirsHere ? " Ses renforts chez vous : " + theirs + " debout." : theirs > 0 ? " Renforts qu'elle garde pour vous : " + theirs + "." : "");
        }
    }

    /**
     * « Remplir » with the price inside, right-aligned and ending at {@code right}: green when the town hall treasury
     * pays it, grey with have/cost in red when not. Returns the button's left edge.
     */
    private int refillButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int right, final int y, final int room, final long cost,
      final long money, final boolean mayAct, final fr.kodistan.interregne.network.DiplomacySyncPayload.Entry entry)
    {
        final boolean payable = mayAct && money >= cost;
        final Component price = Component.literal(money >= cost ? fr.kodistan.interregne.economy.Wallet.format(cost)
                                                                : fr.kodistan.interregne.economy.Wallet.format(money) + "/" + fr.kodistan.interregne.economy.Wallet.format(cost))
                                  .withStyle(ChatFormatting.BOLD);
        final int priceW = ColonyHubScreen.PRICE_ICON + 3 + hub.font().width(price);
        final Component verb = Component.literal("Remplir").withStyle(ChatFormatting.BOLD);
        final int w = Math.min(room, hub.font().width(verb) + 10 + priceW + 16);
        final int x = right - w;
        final int h = Ui.BUTTON_H;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, w, h);
        Ui.plate(graphics, x, y, x + w, y + h, payable ? (over ? Palette.GREEN_HOVER : Palette.GREEN) : Palette.PAPER_DARK, Palette.EDGE,
          payable ? Palette.GREEN_LIGHT : Palette.PAPER_DARK);
        final int textY = y + (h - 8) / 2;
        int cx = x + (w - (hub.font().width(verb) + 10 + priceW)) / 2;
        graphics.drawString(hub.font(), verb, cx, textY, payable ? Palette.GREEN_TEXT : Palette.MUTED, false);
        cx += hub.font().width(verb) + 10;
        Ui.coinTight(graphics, cx, y + (h - ColonyHubScreen.PRICE_ICON) / 2, ColonyHubScreen.PRICE_ICON);
        graphics.drawString(hub.font(), price, cx + ColonyHubScreen.PRICE_ICON + 3, textY, money >= cost ? (payable ? Palette.GREEN_TEXT : Palette.INK) : Palette.RED, false);
        if (payable)
        {
            final int mine = hub.colony.getID();
            final int id = entry.id();
            hub.addHit(x, y, w, h, () -> ClientDiplomacy.act(mine, DiplomacyActionPayload.REFILL_AID, id));
        }
        if (over)
        {
            hub.hint = money < cost ? "Il manque " + fr.kodistan.interregne.economy.Wallet.format(cost - money) + " Couronnes au trésor régional pour remplir le contingent."
                                    : "Remplit la réserve de soldats pour " + entry.name() + ", payée par le trésor de la colonie : ils apparaissent chez elle au premier assaut qu'elle subit.";
        }
        return x;
    }

    /**
     * « Capituler » with the tribute inside, right-aligned and ending at {@code right}: green when the regional treasury
     * pays it, grey with have/cost in red when not. One click arms it, a second one pays.
     */
    private int surrenderButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int right, final int y, final int room,
      final fr.kodistan.interregne.network.DiplomacySyncPayload sync, final int id, final boolean mayAct)
    {
        final long tribute = sync.tribute();
        final long money = sync.treasury();
        final boolean payable = mayAct && money >= tribute;
        final boolean armed = confirmCapitulate == id;
        final Component price = Component.literal(money >= tribute ? fr.kodistan.interregne.economy.Wallet.format(tribute)
                                                                   : fr.kodistan.interregne.economy.Wallet.format(money) + "/" + fr.kodistan.interregne.economy.Wallet.format(tribute))
                                  .withStyle(ChatFormatting.BOLD);
        final int priceW = ColonyHubScreen.PRICE_ICON + 3 + hub.font().width(price);
        String verb = armed ? "Confirmer le tribut" : "Capituler";
        if (hub.font().width(Component.literal(verb).withStyle(ChatFormatting.BOLD)) + 10 + priceW + 16 > room)
        {
            verb = armed ? "Confirmer" : "Capituler";
        }
        final Component verbText = Component.literal(verb).withStyle(ChatFormatting.BOLD);
        final int w = Math.min(room, hub.font().width(verbText) + 10 + priceW + 16);
        final int x = right - w;
        final int h = Ui.BUTTON_H;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, w, h);
        Ui.plate(graphics, x, y, x + w, y + h, payable ? (over ? Palette.GREEN_HOVER : Palette.GREEN) : Palette.PAPER_DARK, Palette.EDGE,
          payable ? Palette.GREEN_LIGHT : Palette.PAPER_DARK);
        final int textY = y + (h - 8) / 2;
        int cx = x + (w - (hub.font().width(verbText) + 10 + priceW)) / 2;
        graphics.drawString(hub.font(), verbText, cx, textY, payable ? Palette.GREEN_TEXT : Palette.MUTED, false);
        cx += hub.font().width(verbText) + 10;
        Ui.coinTight(graphics, cx, y + (h - ColonyHubScreen.PRICE_ICON) / 2, ColonyHubScreen.PRICE_ICON);
        graphics.drawString(hub.font(), price, cx + ColonyHubScreen.PRICE_ICON + 3, textY, money >= tribute ? (payable ? Palette.GREEN_TEXT : Palette.INK) : Palette.RED, false);
        if (payable)
        {
            final int mine = hub.colony.getID();
            hub.addHit(x, y, w, h, () -> {
                if (confirmCapitulate != id)
                {
                    confirmCapitulate = id;
                    return;
                }
                confirmCapitulate = -1;
                ClientDiplomacy.act(mine, DiplomacyActionPayload.CAPITULATE, id);
            });
        }
        if (over)
        {
            hub.hint = money < tribute ? "Il manque " + fr.kodistan.interregne.economy.Wallet.format(tribute - money) + " Couronnes au trésor régional pour le tribut."
                                       : "Verse le tribut au trésor de l'assaillant : la guerre s'arrête, ses troupes rentrent et vous gagnez un bouclier.";
        }
        return x;
    }

    private static final String DEMAND_FULL = "Exiger la reddition";
    private static final String DEMAND_SHORT = "Reddition";

    /**
     * Width of the « Capituler » button as {@link #surrenderButton} lays it out, to know whether another button fits beside it.
     */
    private int capitulateWidth(final fr.kodistan.interregne.network.DiplomacySyncPayload sync, final int room, final boolean armed)
    {
        final long tribute = sync.tribute();
        final long money = sync.treasury();
        final Component price = Component.literal(money >= tribute ? fr.kodistan.interregne.economy.Wallet.format(tribute)
                                                                   : fr.kodistan.interregne.economy.Wallet.format(money) + "/" + fr.kodistan.interregne.economy.Wallet.format(tribute))
                                  .withStyle(ChatFormatting.BOLD);
        final int priceW = ColonyHubScreen.PRICE_ICON + 3 + hub.font().width(price);
        String verb = armed ? "Confirmer le tribut" : "Capituler";
        if (hub.font().width(Component.literal(verb).withStyle(ChatFormatting.BOLD)) + 10 + priceW + 16 > room)
        {
            verb = armed ? "Confirmer" : "Capituler";
        }
        return Math.min(room, hub.font().width(Component.literal(verb).withStyle(ChatFormatting.BOLD)) + 10 + priceW + 16);
    }

    /**
     * « Exiger la reddition », right-aligned and ending at {@code right}: the defender may end the war once it repelled an
     * assault or the attacker stayed idle long enough. Grey otherwise, the hover saying what is missing and the time left.
     * One click arms it, a second one demands. The shorter wording is used when the full one does not fit {@code room}.
     */
    private void demandButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int right, final int y, final int room,
      final fr.kodistan.interregne.network.DiplomacySyncPayload.Entry entry, final boolean mayAct)
    {
        final int id = entry.id();
        final boolean armed = confirmDemand == id;
        final int mine = hub.colony.getID();
        final int wait = entry.surrenderSeconds() > 0 ? Math.max(0, entry.surrenderSeconds() - ClientDiplomacy.secondsSince(mine)) : entry.surrenderSeconds();
        final boolean ready = wait == 0;
        final boolean enabled = mayAct && ready;
        String label = armed ? "Confirmer" : DEMAND_FULL;
        if (hub.font().width(label) + 16 > room)
        {
            label = armed ? "Confirmer" : DEMAND_SHORT;
        }
        final int w = Math.min(room, hub.font().width(label) + 16);
        final int x = right - w;
        hub.seatButton(graphics, mouseX, mouseY, x, y, w, label, armed ? Ui.ButtonStyle.DANGER : Ui.ButtonStyle.PRIMARY, enabled, () -> {
            if (confirmDemand != id)
            {
                confirmDemand = id;
                return;
            }
            confirmDemand = -1;
            ClientDiplomacy.act(mine, DiplomacyActionPayload.DEMAND_SURRENDER, id);
        });
        if (Ui.inside(mouseX, mouseY, x, y, w, Ui.BUTTON_H))
        {
            hub.hint = ready ? "Impose la reddition de " + entry.name() + " : la guerre s'arrête, elle vous verse un tribut pris sur son trésor (sa réserve reste intacte), "
                                 + "vous gagnez l'Influence de la victoire et c'est elle qui reçoit le bouclier."
                 : wait > 0 ? "Pas encore : repoussez un assaut de " + entry.name() + ", ou attendez qu'elle reste sans attaquer encore " + waitText(wait) + "."
                 : "Pas pour l'instant : la guerre n'est pas encore ouverte, ou les troupes de " + entry.name() + " sont en campagne. Repoussez leur assaut, ou attendez qu'elles restent sans attaquer assez longtemps.";
        }
    }

    /**
     * « 5 h 12 min », « 12 min », « 45 s ».
     */
    private static String waitText(final int seconds)
    {
        final int hours = seconds / 3600;
        final int minutes = seconds % 3600 / 60;
        if (hours > 0)
        {
            return minutes > 0 ? hours + " h " + minutes + " min" : hours + " h";
        }
        return minutes > 0 ? minutes + " min" : seconds + " s";
    }

    /**
     * Soldiers to send ({@code -}/{@code +} per kind, at most what the garrison has at home) and the button that sends
     * them. Names shrink to abbreviations when the row is narrow, so the button always fits.
     */
    private void assaultLine(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final fr.kodistan.interregne.network.DiplomacySyncPayload sync, final fr.kodistan.interregne.network.DiplomacySyncPayload.Entry entry, final boolean mayAct)
    {
        final int id = entry.id();
        final int[] max = { sync.legionAvailable(), sync.archersAvailable() };
        final int[] pick = chosen.computeIfAbsent(id, k -> max.clone());
        pick[0] = Math.min(pick[0], max[0]);
        pick[1] = Math.min(pick[1], max[1]);
        final String launch = "⚔ Lancer l'assaut";
        final int lw = hub.font().width(launch) + 16;
        final int step = 14;
        final int countW = hub.font().width("00/00") + 4;
        final String[][] names = { { "Légionnaires", "Lég." }, { "Archères", "Arc." } };
        final int full = 2 * (2 * step + countW + 11) + hub.font().width(names[0][0]) + hub.font().width(names[1][0]);
        final boolean shortNames = full > width - lw - 8;
        final int ty = y + (Ui.BUTTON_H - 8) / 2;
        int cx = x;
        for (int kind = 0; kind < 2; kind++)
        {
            final int k = kind;
            final String name = names[k][shortNames ? 1 : 0];
            graphics.drawString(hub.font(), name, cx, ty, Palette.INK, false);
            cx += hub.font().width(name) + 3;
            hub.seatButton(graphics, mouseX, mouseY, cx, y, step, "-", Ui.ButtonStyle.SECONDARY, mayAct && pick[k] > 0, () -> pick[k]--);
            cx += step;
            final String count = pick[k] + "/" + max[k];
            graphics.drawString(hub.font(), count, cx + (countW - hub.font().width(count)) / 2, ty, max[k] == 0 ? Palette.MUTED : Palette.INK, false);
            cx += countW;
            hub.seatButton(graphics, mouseX, mouseY, cx, y, step, "+", Ui.ButtonStyle.SECONDARY, mayAct && pick[k] < max[k], () -> pick[k]++);
            cx += step + 8;
        }
        final int mine = hub.colony.getID();
        // Another colony has this one reserved for its assaults: the button stays grey and the hover says until when
        final int lockLeft = entry.lockSeconds() > 0 ? Math.max(0, entry.lockSeconds() - ClientDiplomacy.secondsSince(mine)) : 0;
        final boolean locked = !entry.lockHolder().isEmpty() && lockLeft > 0;
        hub.seatButton(graphics, mouseX, mouseY, x + width - lw - 4, y, lw, launch, Ui.ButtonStyle.DANGER, mayAct && pick[0] + pick[1] > 0 && !locked, () -> {
            ClientDiplomacy.act(mine, DiplomacyActionPayload.LAUNCH_ASSAULT, id, DiplomacyActionPayload.troops(pick[0], pick[1]));
            chosen.remove(id);
        });
        if (locked && Ui.inside(mouseX, mouseY, x + width - lw - 4, y, lw, Ui.BUTTON_H))
        {
            hub.hint = entry.name() + " est aux prises avec " + entry.lockHolder() + " : encore " + Math.max(1, (lockLeft + 59) / 60) + " min avant de pouvoir l'attaquer.";
        }
    }
}
