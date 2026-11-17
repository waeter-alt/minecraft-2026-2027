package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ClientCrateStock;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Dépôt Régional's body in its hut window: the reserve of the region's crate with the countdown to the next one and
 * taking it, then a page with the crates the colony holds in its warehouse. The level and its upgrade are the hut window's.
 */
final class DepotBody implements HutBody
{
    private static final String CRATES = "caisses";

    @Nullable
    private static OpenComptoirPayload state;
    private static int secondsLeft;
    private static int ticks;

    static void receive(final OpenComptoirPayload payload)
    {
        state = payload;
        secondsLeft = payload.secondsLeft();
    }

    @Override
    public List<Tab> tabs(final IBuildingView building)
    {
        return List.of(new Tab(CRATES, "Caisses", Icon.of(Crate.values()[0].stack(1))));
    }

    @Override
    public void tick(final IBuildingView building)
    {
        if (++ticks % 20 == 0 && secondsLeft > 0)
        {
            secondsLeft--;
        }
    }

    @Override
    public int render(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final String tab, final IBuildingView building, final int x,
      final int y, final int width)
    {
        final OpenComptoirPayload depot = state;
        if (depot == null || !depot.comptoir().equals(building.getPosition()))
        {
            return HutBodies.loading(kit, graphics, x, y, width, building);
        }
        return CRATES.equals(tab) ? crates(kit, graphics, x, y, width) : reserve(kit, graphics, mouseX, mouseY, depot, x, y, width);
    }

    private static int reserve(final BodyKit kit, final GuiGraphics graphics, final int mouseX, final int mouseY, final OpenComptoirPayload depot, final int x, int y,
      final int width)
    {
        final Crate crate = Crate.byId(depot.region());
        if (crate == null)
        {
            return y + Ui.banner(graphics, kit.font(), x, y, width, Ui.State.WAIT, "En attente", "la région n'est pas encore attribuée") + 21;
        }
        final String region = crate.regionName().isEmpty() ? "" : crate.regionName().toUpperCase();
        final int top = y;
        final boolean full = depot.stock() >= depot.storage();
        final String where = depot.warehouse() ? " · elle part à l'entrepôt" : full ? " · réserve pleine, la production s'arrête" : "";
        y = kit.hero(graphics, x, y, width, crate.stack(1), "RÉSERVE · " + region, depot.stock() + " / " + depot.storage() + (depot.storage() > 1 ? " caisses" : " caisse"),
          "Prochaine dans " + BuildCosts.formatSeconds(secondsLeft) + where);
        kit.heroGauge(graphics, x, top, width, depot.stock(), depot.storage());
        y = kit.bigButton(graphics, mouseX, mouseY, x, y, width, depot.stock() > 0 ? "Prendre " + depot.stock() + (depot.stock() > 1 ? " caisses" : " caisse") : "Réserve vide",
          depot.stock() > 0, depot.stock() > 0 ? "Les caisses vont dans ton inventaire." : "Une caisse toutes les " + BuildCosts.formatSeconds(depot.seconds()) + ".",
          () -> PacketDistributor.sendToServer(new RegionActionPayload(depot.comptoir(), RegionActionPayload.COMPTOIR_TAKE, 0, 0)));
        final String rhythm = depot.upgradeCost() < 0 ? "Niveau maximal · une caisse toutes les " + BuildCosts.formatSeconds(depot.seconds())
          : "Niveau " + (depot.level() + 1) + " : une caisse toutes les " + BuildCosts.formatSeconds(depot.nextSeconds()) + " (au lieu de " + BuildCosts.formatSeconds(depot.seconds()) + ")";
        return kit.note(graphics, x + 2, y + 2, width - 4, rhythm, Palette.MUTED) + 4;
    }

    private static int crates(final BodyKit kit, final GuiGraphics graphics, final int x, int y, final int width)
    {
        Ui.label(graphics, kit.font(), "CAISSES À L'ENTREPÔT DE LA COLONIE", x, y);
        y += 11;
        final Crate[] all = Crate.values();
        // One column when two would cut the names
        final int columns = width >= 250 ? 2 : 1;
        final int columnW = (width - 3) / columns;
        for (int i = 0; i < all.length; i++)
        {
            final int cx = x + (i % columns) * (columnW + 3);
            final int cy = y + (i / columns) * 20;
            Icon.of(all[i].stack(1)).draw(graphics, cx, cy, 16);
            kit.slot(cx, cy, 16, all[i].stack(1));
            graphics.drawString(kit.font(), kit.fit(all[i].displayName(), columnW - 50), cx + 20, cy + 4, Palette.INK, false);
            final String count = String.valueOf(ClientCrateStock.get(all[i]));
            graphics.drawString(kit.font(), Component.literal(count).withStyle(ChatFormatting.BOLD), cx + columnW - 4 - kit.font().width(count), cy + 4, Palette.INK, false);
        }
        return y + ((all.length + columns - 1) / columns) * 20;
    }
}
