package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ClientIntendance;
import net.minecraft.ChatFormatting;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.hud.Grievances;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.client.war.ClientWarRuins;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import fr.kodistan.interregne.network.WarRebuildPayload;
import fr.kodistan.interregne.network.WarRuinsSyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.client.CrateRows;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.colony.ColonyMaps;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue d'ensemble : les tuiles d'état de la colonie et le siège (hôtel de ville).
 * Une page du hub de colonie ({@link ColonyHubScreen}) : elle dessine dans le cadre du hub et lui emprunte son état.
 */
final class HubOverviewPage
{
    private final ColonyHubScreen hub;

    HubOverviewPage(final ColonyHubScreen hub)
    {
        this.hub = hub;
    }

    // --- Overview ---

    int renderOverview(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IBuildingView> buildings = hub.colony.getBuildings();
        final List<ICitizenDataView> citizens = new ArrayList<>(hub.colony.getCitizens().values());
        int blocked = 0;
        int sites = 0;
        for (final IBuildingView building : buildings)
        {
            final ColonyHubScreen.BuildingState state = hub.stateOf(building);
            if (state.state() == Ui.State.BAD)
            {
                blocked++;
            }
            if (building.isBuilding() || building.isRepairing() || building.isDeconstructing())
            {
                sites++;
            }
        }
        int grievances = 0;
        int troubled = 0;
        for (final ICitizenDataView citizen : citizens)
        {
            final int count = Grievances.count(citizen);
            grievances += count;
            if (count > 0)
            {
                troubled++;
            }
        }
        // What the Intendance would settle, as its page lists it
        final IntendanceQuotePayload intendance = ClientIntendance.of(hub.colony.getID());
        final int requests = intendance == null ? -1 : intendance.care().size() + intendance.supplies().size();

        final WarRuinsSyncPayload ruins = ClientWarRuins.of(hub.colony);
        if (ruins != null && (ruins.count() > 0 || ruins.wallDamaged()))
        {
            final String ruinPart = ruins.count() <= 0 ? null
              : ruins.count() + (ruins.count() == 1 ? " bâtiment en ruines" : " bâtiments en ruines");
            final String wallPart = ruins.wallDamaged() ? "muraille percée" : null;
            final String text = ruinPart == null ? wallPart : wallPart == null ? ruinPart : ruinPart + ", " + wallPart;
            // banner() returns only the height ADDED past its base 14px (0 on a one-line banner) — the base row
            // itself is never reflected in the return value, so it has to be added here too.
            y += 14 + Ui.banner(graphics, hub.font(), x, y, width, Ui.State.BAD, "Guerre", text);
            y += 4;
            y = renderRebuildButton(graphics, mouseX, mouseY, x, y, width, ruins) + 6;
        }
        if (blocked > 0)
        {
            y += Ui.banner(graphics, hub.font(), x, y, width, Ui.State.BAD, "À régler", blocked + (blocked == 1 ? " bâtiment bloqué" : " bâtiments bloqués"));
            hub.addHit(x, y, width, 14, () -> hub.show(ColonyHubScreen.Page.BUILDINGS));
        }
        else if (troubled > 0)
        {
            y += Ui.banner(graphics, hub.font(), x, y, width, Ui.State.WAIT, "À surveiller", troubled + (troubled == 1 ? " citoyen a" : " citoyens ont") + " des doléances");
            hub.addHit(x, y, width, 14, () -> hub.show(ColonyHubScreen.Page.CITIZENS));
        }
        else
        {
            y += Ui.banner(graphics, hub.font(), x, y, width, Ui.State.OK, "Tout va bien", "aucun bâtiment bloqué, aucune doléance");
        }
        y += 20;
        y = renderSeat(graphics, mouseX, mouseY, x, y, width) + 2;

        // Tiles: the colony's vital signs, each opening the page that explains it
        // Four tiles across, two when the page is narrow
        final int columns = width >= 360 ? 4 : 2;
        final int tileW = (width - (columns - 1) * 4) / columns;
        final double happiness = hub.colony.getOverallHappiness();
        y = tileRow(graphics, mouseX, mouseY, x, y, tileW, columns, List.of(
          new Tile("Citoyens", citizens.size() + " / " + hub.colony.getCitizenCountLimit(), Icon.module("workers"), Ui.State.OK, () -> hub.show(ColonyHubScreen.Page.CITIZENS)),
          new Tile("Bonheur", ColonyHubScreen.format(happiness) + " / 10", Icon.module("happiness"), happiness < 4 ? Ui.State.BAD : happiness < 7 ? Ui.State.WAIT : Ui.State.OK,
            () -> hub.show(ColonyHubScreen.Page.CITIZENS)),
          new Tile("Doléances", Integer.toString(grievances), Icon.of(Items.WRITABLE_BOOK), grievances > 0 ? Ui.State.WAIT : Ui.State.OK, () -> hub.show(ColonyHubScreen.Page.CITIZENS)),
          new Tile("Intendance", requests < 0 ? "…" : Integer.toString(requests), Icon.of(Items.GOLD_INGOT), requests > 0 ? Ui.State.WAIT : Ui.State.OK, () -> hub.show(ColonyHubScreen.Page.INTENDANCE))));
        y = tileRow(graphics, mouseX, mouseY, x, y + 4, tileW, columns, List.of(
          new Tile("Bâtiments", Integer.toString(buildings.size()), Icon.of(Items.BRICKS), blocked > 0 ? Ui.State.BAD : Ui.State.OK, () -> hub.show(ColonyHubScreen.Page.BUILDINGS)),
          new Tile("Chantiers", Integer.toString(sites), Icon.of(Items.IRON_PICKAXE), Ui.State.OK, () -> hub.show(ColonyHubScreen.Page.BUILDINGS)),
          mercenaries()));

        return y;
    }

    /**
     * "Tout reconstruire" : pays une fois pour chaque bâtiment en ruines et, s'il y en a une, pour la brèche de
     * la muraille — un seul prix combiné (price inside the button, the same convention as the wall's upgrade
     * button — green and payable, or grey with the shortfall in red).
     */
    int renderRebuildButton(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final WarRuinsSyncPayload ruins)
    {
        final long totalPrice = ruins.price() + ruins.wallPrice();
        final boolean creative = hub.minecraft().player != null && hub.minecraft().player.isCreative();
        final long money = ClientTreasury.of(hub.colony) + (hub.minecraft().player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(hub.minecraft().player.getInventory())));
        final boolean payable = creative || money >= totalPrice;
        final int h = Ui.BUTTON_H;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, h);
        Ui.plate(graphics, x, y, x + width, y + h, payable ? (over ? Palette.GREEN_HOVER : Palette.GREEN) : Palette.PAPER_DARK, Palette.EDGE,
          payable ? Palette.GREEN_LIGHT : Palette.PAPER_DARK);

        final int textY = y + (h - 8) / 2;
        final Component verbText = Component.literal("Tout reconstruire").withStyle(ChatFormatting.BOLD);
        final Component price = Component.literal(creative ? "gratuit" : money >= totalPrice ? Wallet.format(totalPrice) : Wallet.format(money) + "/" + Wallet.format(totalPrice))
                                  .withStyle(ChatFormatting.BOLD);
        final int priceW = ColonyHubScreen.PRICE_ICON + 3 + hub.font().width(price);
        int cx = x + (width - (hub.font().width(verbText) + 12 + priceW)) / 2;
        graphics.drawString(hub.font(), verbText, cx, textY, payable ? Palette.GREEN_TEXT : Palette.MUTED, false);
        cx += hub.font().width(verbText) + 12;
        Ui.coinTight(graphics, cx, y + (h - ColonyHubScreen.PRICE_ICON) / 2, ColonyHubScreen.PRICE_ICON);
        graphics.drawString(hub.font(), price, cx + ColonyHubScreen.PRICE_ICON + 3, textY, money >= totalPrice ? (payable ? Palette.GREEN_TEXT : Palette.INK) : Palette.RED, false);

        if (payable)
        {
            hub.addHit(x, y, width, h, () ->
            {
                PacketDistributor.sendToServer(new WarRebuildPayload(hub.colony.getID()));
                hub.onClose();
            });
        }
        if (over && !payable)
        {
            hub.hint = "Il manque " + Wallet.format(totalPrice - money) + " Couronnes (trésor régional et bourse comptés).";
        }
        return y + h;
    }

    /**
     * The mercenaries' tile: ready, or resting for so many minutes.
     */
    Tile mercenaries()
    {
        final long used = hub.colony.getMercenaryUseTime();
        final long wait = used == 0 ? 0 : Math.max(0, com.minecolonies.api.util.constant.Constants.TICKS_FOURTY_MIN - (hub.colony.getWorld().getGameTime() - used));
        return new Tile("Mercenaires", wait == 0 ? "Prêts" : (wait / 1200 + 1) + " min", Icon.of(Items.IRON_SWORD), wait == 0 ? Ui.State.OK : Ui.State.WAIT,
          () -> MercenaryScreen.open(hub.townHall, hub));
    }

    /**
     * The town hall's next level, as a hut shows it but laid out across the page: what it unlocks, what it costs and
     * the buttons; or its site while one is running.
     */
    int renderSeat(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int level = hub.townHall.getBuildingLevel();
        final boolean site = hub.townHall.isBuilding() || hub.townHall.isRepairing() || hub.townHall.isDeconstructing();
        final boolean max = level >= hub.townHall.getBuildingMaxLevel();
        final int tint = hub.townHall.isBuilding() || !site && !max ? level + 1 : level;
        final UpgradeOffer offer = site || max ? null : UpgradeOffer.of(hub.townHall);
        final int band = 18;
        final int pad = 8;
        // Three columns side by side when there is room, one under the other otherwise
        final boolean wide = width >= 380;
        final int gap = 12;
        final int buttonW = wide ? 112 : (width - 2 * pad - 4) / 2;
        final int textW = wide ? (width - 2 * pad - 2 * gap - buttonW) / 3 : width - 2 * pad;
        final String unlock = offer == null ? "" : "+ Bâtiments jusqu'au niveau " + offer.target();
        final boolean map = offer != null && offer.target() == ColonyMaps.UNLOCK_LEVEL;
        final int unlockH = offer == null ? 0 : (hub.font().split(Component.literal(unlock), textW).size() + (map ? 1 : 0)) * 10;
        final int priceH = offer == null ? 0 : 11 * (1 + offer.crates().size());
        final int height;
        if (site || offer == null)
        {
            height = 84;
        }
        else if (wide)
        {
            height = Math.max(84, band + pad + 13 + Math.max(unlockH, priceH) + pad);
        }
        else
        {
            height = band + pad + 13 + unlockH + 6 + 13 + priceH + 6 + Ui.BUTTON_H + pad;
        }

        graphics.fill(x, y, x + width, y + height, Palette.levelSoft(tint));
        graphics.renderOutline(x, y, width, height, Palette.levelEdge(tint));
        Ui.plate(graphics, x, y, x + width, y + band, Palette.levelFill(tint), Palette.levelEdge(tint), Palette.levelFill(tint));
        Icon.of(hub.townHall.getBuildingType().getBuildingBlock()).draw(graphics, x + 5, y + 1, 16);
        final String heading = hub.townHall.isDeconstructing() ? "Démolition en cours"
                                 : hub.townHall.isRepairing() ? "Réparation en cours"
                                     : hub.townHall.isBuilding() ? "Amélioration en cours"
                                         : max ? "Niveau maximum" : "Passer au niveau " + (level + 1);
        final String open = width >= 300 ? "Voir l'hôtel de ville ›" : "Voir ›";
        final int openX = x + width - hub.font().width(open) - 8;
        graphics.drawString(hub.font(), Component.literal(hub.fitText(heading, openX - x - 33)).withStyle(ChatFormatting.BOLD), x + 25, y + 5, Palette.levelText(tint), false);
        final boolean overOpen = Ui.inside(mouseX, mouseY, openX - 2, y, hub.font().width(open) + 4, band);
        graphics.drawString(hub.font(), Component.literal(open).withStyle(overOpen ? ChatFormatting.UNDERLINE : ChatFormatting.RESET), openX, y + 5,
          Palette.levelText(tint), false);
        hub.addHit(openX - 2, y, hub.font().width(open) + 4, band, () -> HutScreen.openWith(hub.townHall, hub));

        final int bodyY = y + band + pad;
        final int c1 = x + pad;
        final int buttonY = y + height - pad - Ui.BUTTON_H;
        final int ruleColor = Palette.levelEdge(tint) & 0x00FFFFFF | 0x50000000;

        if (site)
        {
            Ui.label(graphics, hub.font(), "AVANCEMENT", c1, bodyY);
            final Float progress = UpgradeOffer.siteProgress(hub.townHall, "Hôtel de ville");
            final int barW = width - 2 * pad;
            if (progress == null)
            {
                Ui.wrapped(graphics, hub.font(), Component.literal("Le chantier avance. Approchez-vous pour suivre sa barre."), c1, bodyY + 14, barW, Palette.MUTED, 10);
            }
            else
            {
                if (hub.townHall.isDeconstructing())
                {
                    Ui.progressBar(graphics, c1, bodyY + 15, barW, 8, progress, Ui.State.BAD);
                }
                else
                {
                    Ui.progressBar(graphics, c1, bodyY + 15, barW, 8, progress, Palette.levelFill(tint), Palette.levelEdge(tint));
                }
                final int seconds = hub.townHall.isBuilding() ? BuildCosts.secondsForLevel(level + 1) : BuildCosts.secondsForFlat();
                final int left = Math.max(1, Math.round((1 - progress) * seconds));
                graphics.drawString(hub.font(), hub.fitText(Math.round(progress * 100) + " % · encore " + BuildCosts.formatSeconds(left) + " environ", barW), c1, bodyY + 30,
                  Palette.INK, false);
            }
            return y + height + 6;
        }

        if (offer == null)
        {
            final int bw = wide ? buttonW : width - 2 * pad;
            final int bx = x + width - pad - bw;
            Ui.wrapped(graphics, hub.font(), Component.literal("L'hôtel de ville est au plus haut. Tous les bâtiments peuvent monter jusqu'au niveau " + level + "."),
              c1, bodyY, wide ? bx - c1 - gap : width - 2 * pad, Palette.MUTED, 10);
            hub.seatButton(graphics, mouseX, mouseY, bx, buttonY, bw, "Réparer, démolir…", Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
            return y + height + 6;
        }

        // What the next level opens, what it costs, then the buttons
        final int c2 = wide ? c1 + textW + gap : c1;
        final int priceW = wide ? x + width - pad - buttonW - gap - c2 : width - 2 * pad;
        Ui.label(graphics, hub.font(), "DÉBLOQUE", c1, bodyY);
        int ly = Ui.wrapped(graphics, hub.font(), Component.literal(unlock), c1, bodyY + 13, textW, Palette.GREEN, 10);
        if (map)
        {
            ly = Ui.wrapped(graphics, hub.font(), Component.literal("+ Carte de la colonie"), c1, ly, textW, Palette.GREEN, 10);
        }
        final int priceY = wide ? bodyY : ly + 6;
        if (wide)
        {
            graphics.fill(c2 - gap / 2, bodyY, c2 - gap / 2 + 1, y + height - pad, ruleColor);
        }
        Ui.label(graphics, hub.font(), "PRIX", c2, priceY);
        int py = Ui.priceLine(graphics, hub.font(), c2, priceY + 13, priceW, offer.price(), offer.affordable());
        for (final CrateRows.Row row : offer.crates())
        {
            py = Ui.crateLine(graphics, hub.font(), c2, py, priceW, row);
        }

        final int c3 = x + width - pad - buttonW;
        if (wide)
        {
            graphics.fill(c3 - gap / 2, bodyY, c3 - gap / 2 + 1, y + height - pad, ruleColor);
            hub.seatButton(graphics, mouseX, mouseY, c3, buttonY - Ui.BUTTON_H - 5, buttonW, "Réparer, démolir…", Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
        }
        else
        {
            hub.seatButton(graphics, mouseX, mouseY, c1, buttonY, buttonW, "Réparer, démolir…", Ui.ButtonStyle.SECONDARY, true, this::openBuildWindow);
        }
        // Green when it can be done now, grey with the reason otherwise
        hub.seatButton(graphics, mouseX, mouseY, c3, buttonY, buttonW, offer.action(hub.font(), buttonW - 6), Ui.ButtonStyle.PRIMARY, offer.canBuy(), () ->
        {
            offer.buy(hub.townHall);
            hub.onClose();
        });
        return y + height + 6;
    }

    void openBuildWindow()
    {
        new WindowBuildBuilding(hub.colony, hub.townHall).open();
    }

    record Tile(String label, String value, Icon icon, Ui.State state, Runnable action)
    {
    }

    int tileRow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int tileW, final int columns,
      final List<Tile> tiles)
    {
        for (int i = 0; i < tiles.size(); i++)
        {
            final Tile tile = tiles.get(i);
            final int tx = x + (i % columns) * (tileW + 4);
            final int ty = y + (i / columns) * 34;
            final boolean over = Ui.inside(mouseX, mouseY, tx, ty, tileW, 30);
            Ui.card(graphics, tx, ty, tileW, 30, false, over, false);
            graphics.fill(tx + 1, ty + 1, tx + 3, ty + 29, switch (tile.state())
            {
                case OK -> Palette.GREEN;
                case WAIT -> Palette.AMBER;
                case BAD -> Palette.RED;
            });
            tile.icon().draw(graphics, tx + 6, ty + 4, 12);
            graphics.drawString(hub.font(), hub.fitText(tile.label(), tileW - 24), tx + 21, ty + 5, Palette.MUTED, false);
            graphics.drawString(hub.font(), Component.literal(hub.fitText(tile.value(), tileW - 10)).withStyle(ChatFormatting.BOLD), tx + 7, ty + 18, Palette.INK, false);
            hub.addHit(tx, ty, tileW, 30, tile.action());
        }
        return y + ((tiles.size() + columns - 1) / columns) * 34 - 4;
    }
}
