package fr.kodistan.interregne.client.hut;

import net.minecraft.ChatFormatting;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.economy.Wallet;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Murailles : l'état de l'enceinte de la colonie et ses actions (construire, monter de niveau).
 * Une page du hub de colonie ({@link ColonyHubScreen}) : elle dessine dans le cadre du hub et lui emprunte son état.
 */
final class HubWallsPage
{
    private final ColonyHubScreen hub;

    HubWallsPage(final ColonyHubScreen hub)
    {
        this.hub = hub;
    }

    /**
     * The walls: none yet, and the button that opens the aerial planner.
     */
    int renderWalls(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final fr.kodistan.interregne.network.WallSyncPayload wall = fr.kodistan.interregne.client.walls.ClientWalls.of(hub.colony);
        if (wall != null)
        {
            final String look = fr.kodistan.interregne.walls.WallLevels.of(wall.level()).name();
            y += Ui.banner(graphics, hub.font(), x, y, width, wall.building() ? Ui.State.WAIT : Ui.State.OK, "Muraille",
              wall.building() ? "en construction" : "niveau " + wall.level() + " · " + look);
            final int towers = wall.points().size() / 2;
            final int gates = wall.gates().size() / 2;
            y = Ui.wrapped(graphics, hub.font(), Component.literal((wall.closed() ? "Enceinte fermée" : "Muraille ouverte") + ", " + towers + " tours, "
              + gates + (gates > 1 ? " portes." : " porte.")), x + 2, y + 20, width - 4, Palette.MUTED, 10) + 6;
            return wallActions(graphics, mouseX, mouseY, x, y, width, wall);
        }
        y += Ui.banner(graphics, hub.font(), x, y, width, Ui.State.WAIT, "Murailles", "aucune muraille");
        y = Ui.wrapped(graphics, hub.font(), Component.literal("Tracez l'enceinte de la colonie vue du ciel : posez des points au sol, la muraille "
          + "suit le relief entre eux. Elle peut rester ouverte, une tour se dresse sur chaque point, et il lui faut au moins une porte (la première est offerte)."), x + 2, y + 20, width - 4, Palette.MUTED, 10) + 6;
        final int buttonW = Math.min(width, Math.max(140, hub.font().width("Tracer la muraille") + 24));
        hub.seatButton(graphics, mouseX, mouseY, x, y, buttonW, "Tracer la muraille", Ui.ButtonStyle.PRIMARY, true,
          () -> hub.minecraft().setScreen(new fr.kodistan.interregne.client.walls.WallPlannerScreen(hub.townHall)));
        return y + Ui.BUTTON_H + 6;
    }

    /**
     * The built wall's two actions: raise it to the next level (price inside, capped by the town hall's level) and
     * retrace it in the planner. Side by side when they fit, stacked otherwise.
     */
    int wallActions(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width,
      final fr.kodistan.interregne.network.WallSyncPayload wall)
    {
        final int next = wall.level() + 1;
        final boolean max = next > fr.kodistan.interregne.walls.WallLevels.MAX;
        if (!max)
        {
            final fr.kodistan.interregne.walls.WallLevels.Level look = fr.kodistan.interregne.walls.WallLevels.of(next);
            y = Ui.wrapped(graphics, hub.font(), Component.literal("Niveau " + next + " · " + look.name() + " : " + look.height() + " blocs de haut. "
              + "La muraille garde sa largeur, son tracé et ses portes ; elle est rebâtie dans son nouvel aspect."), x + 2, y, width - 4, Palette.MUTED, 10) + 6;
        }
        final boolean creative = hub.minecraft().player != null && hub.minecraft().player.isCreative();
        final long cost = max || creative ? 0 : wallCost(wall, next);
        final long money = ClientTreasury.of(hub.colony) + (hub.minecraft().player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(hub.minecraft().player.getInventory())));
        final String refusal = max ? "Niveau maximal" : wall.building() ? "Chantier en cours"
                                 : hub.townHall.getBuildingLevel() < next ? "Hôtel de ville niv. " + next + " requis" : null;
        final boolean payable = refusal == null && money >= cost;

        final int retraceW = Math.max(110, hub.font().width("Retoucher le tracé") + 20);
        final boolean side = width >= retraceW + 6 + 150;
        final int upW = side ? width - retraceW - 6 : width;
        final int h = 18;
        final boolean overUp = Ui.inside(mouseX, mouseY, x, y, upW, h);
        if (payable)
        {
            Ui.plate(graphics, x, y, x + upW, y + h, overUp ? Palette.GREEN_HOVER : Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
        }
        else
        {
            Ui.plate(graphics, x, y, x + upW, y + h, Palette.PAPER_DARK, Palette.EDGE, Palette.PAPER_DARK);
        }
        final int textY = y + (h - 8) / 2;
        if (refusal != null)
        {
            graphics.drawCenteredString(hub.font(), hub.fitText(refusal, upW - 8), x + upW / 2, textY, Palette.MUTED);
        }
        else
        {
            String verb = "Améliorer · niv. " + next;
            final Component price = Component.literal(creative ? "gratuit" : money >= cost ? Wallet.format(cost) : Wallet.format(money) + "/" + Wallet.format(cost))
                                      .withStyle(ChatFormatting.BOLD);
            final int priceW = ColonyHubScreen.PRICE_ICON + 3 + hub.font().width(price);
            if (hub.font().width(Component.literal(verb).withStyle(ChatFormatting.BOLD)) + 12 + priceW > upW - 12)
            {
                verb = "Améliorer";
            }
            final Component verbText = Component.literal(verb).withStyle(ChatFormatting.BOLD);
            int cx = x + (upW - (hub.font().width(verbText) + 12 + priceW)) / 2;
            graphics.drawString(hub.font(), verbText, cx, textY, payable ? Palette.GREEN_TEXT : Palette.MUTED, false);
            cx += hub.font().width(verbText) + 12;
            Ui.coinTight(graphics, cx, y + (h - ColonyHubScreen.PRICE_ICON) / 2, ColonyHubScreen.PRICE_ICON);
            graphics.drawString(hub.font(), price, cx + ColonyHubScreen.PRICE_ICON + 3, textY, money >= cost ? (payable ? Palette.GREEN_TEXT : Palette.INK) : Palette.RED, false);
        }
        if (payable)
        {
            hub.addHit(x, y, upW, h, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.openUpgrade(hub.townHall));
        }
        if (overUp)
        {
            hub.hint = refusal != null && refusal.startsWith("Hôtel") ? "Une muraille ne dépasse pas le niveau de l'hôtel de ville : améliorez-le d'abord."
                     : refusal == null && money < cost ? "Il manque " + Wallet.format(cost - money) + " Couronnes (trésor régional et bourse comptés)."
                         : refusal == null ? "Ouvre la vue aérienne sur la muraille au niveau " + next + " : vérifiez-la, puis améliorez-la de là-haut. Trésor régional d'abord, puis la bourse." : null;
        }

        final int rx = side ? x + upW + 6 : x;
        final int ry = side ? y : y + h + 4;
        hub.seatButton(graphics, mouseX, mouseY, rx, ry + (h - Ui.BUTTON_H) / 2, side ? retraceW : width, "Retoucher le tracé", Ui.ButtonStyle.SECONDARY, !wall.building(),
          () -> hub.minecraft().setScreen(new fr.kodistan.interregne.client.walls.WallPlannerScreen(hub.townHall)));
        return ry + h + 6;
    }

    /**
     * The wall's full price at a level: it depends only on its geometry, so flat ground stands in for the terrain.
     */
    long wallCost(final fr.kodistan.interregne.network.WallSyncPayload wall, final int wallLevel)
    {
        final fr.kodistan.interregne.walls.WallLevels.Level look = fr.kodistan.interregne.walls.WallLevels.of(wallLevel);
        final fr.kodistan.interregne.walls.Terrain.Probe flat = new fr.kodistan.interregne.walls.Terrain.Probe(64, false, false, true);
        return fr.kodistan.interregne.walls.WallLayout.of(fr.kodistan.interregne.walls.WallTrace.unflat(wall.points()), wall.closed(),
          fr.kodistan.interregne.walls.WallTrace.unflat(wall.gates()), look, (gx, gz) -> flat, (gx, gz) -> true).cost(look);
    }
}
