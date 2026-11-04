package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ClientCrateStock;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A Comptoir régional. First its reserve of the region's crate with the countdown to the next one; then taking the
 * reserve; then the next level and what it changes; last the colony's crates in the warehouse.
 */
public final class ComptoirScreen extends CounterScreen
{
    private OpenComptoirPayload state;
    private int secondsLeft;
    private int ticks;

    private ComptoirScreen(final OpenComptoirPayload state)
    {
        super("Comptoir régional", 360, 250);
        show(state);
    }

    /**
     * Opens the comptoir, or refreshes it when it is already showing.
     */
    public static void open(final OpenComptoirPayload state)
    {
        if (Minecraft.getInstance().screen instanceof ComptoirScreen screen && screen.state.comptoir().equals(state.comptoir()))
        {
            screen.show(state);
            return;
        }
        Minecraft.getInstance().setScreen(new ComptoirScreen(state));
    }

    private void show(final OpenComptoirPayload state)
    {
        this.state = state;
        this.secondsLeft = state.secondsLeft();
    }

    @Override
    protected String crumb()
    {
        return state.colonyName();
    }

    @Override
    public void tick()
    {
        if (++ticks % 20 == 0 && secondsLeft > 0)
        {
            secondsLeft--;
        }
    }

    private void send(final int action)
    {
        PacketDistributor.sendToServer(new RegionActionPayload(state.comptoir(), action, 0, 0));
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final Crate crate = Crate.byId(state.region());
        if (crate == null)
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, "En attente", "la région n'est pas encore attribuée");
            return y + 21;
        }
        final String region = crate.regionName().isEmpty() ? "" : crate.regionName().toUpperCase();
        final int top = y;
        final boolean full = state.stock() >= state.storage();
        final String where = state.warehouse() ? " · elle part à l'entrepôt" : full ? " · réserve pleine, la production s'arrête" : "";
        y = hero(graphics, x, y, width, crate.stack(1), "RÉSERVE · " + region, state.stock() + " / " + state.storage() + (state.storage() > 1 ? " caisses" : " caisse"),
          "Prochaine dans " + BuildCosts.formatSeconds(secondsLeft) + where);
        heroGauge(graphics, x, top, width, state.stock(), state.storage());
        final String badge = "Niv. " + state.level();
        Ui.levelBadge(graphics, font, x + width - 8 - font.width(badge) - 8, top + 6, badge, state.level());

        y = bigButton(graphics, mouseX, mouseY, x, y, width,
          state.stock() > 0 ? "Prendre " + state.stock() + (state.stock() > 1 ? " caisses" : " caisse") : "Réserve vide", state.stock() > 0,
          state.stock() > 0 ? "Les caisses vont dans ton inventaire." : "Une caisse toutes les " + BuildCosts.formatSeconds(state.seconds()) + ".",
          () -> send(RegionActionPayload.COMPTOIR_TAKE));

        // The next level
        if (state.upgradeCost() < 0)
        {
            graphics.drawString(font, Component.literal("Niveau maximal · une caisse toutes les " + BuildCosts.formatSeconds(state.seconds())).withStyle(ChatFormatting.BOLD),
              x + 2, y + 3, Palette.MUTED, false);
            return y + 16;
        }
        final int next = state.level() + 1;
        Ui.plate(graphics, x, y, x + width, y + 30, Palette.levelSoft(next), Palette.levelEdge(next), Palette.levelSoft(next));
        graphics.drawString(font, Component.literal("Niveau " + next).withStyle(ChatFormatting.BOLD), x + 6, y + 5, Palette.INK, false);
        graphics.drawString(font, "une caisse toutes les " + BuildCosts.formatSeconds(state.nextSeconds()) + " (au lieu de " + BuildCosts.formatSeconds(state.seconds()) + ")",
          x + 6, y + 17, Palette.MUTED, false);
        final String upgrade = "Améliorer · " + Wallet.format(state.upgradeCost());
        final boolean can = state.purse() >= state.upgradeCost() || minecraft.player.isCreative();
        final int bw = Ui.levelButtonWidth(font, upgrade);
        final int bx = x + width - 6 - bw;
        final boolean over = Ui.inside(mouseX, mouseY, bx, y + 8, bw, 14);
        Ui.levelButton(graphics, font, bx, y + 8, bw, 14, upgrade, next, can, over);
        if (can)
        {
            addHit(bx, y + 8, bw, 14, () -> send(RegionActionPayload.COMPTOIR_UPGRADE));
        }
        if (over)
        {
            hint = can ? "Payé avec les couronnes sur toi." : "Il te faut " + couronnes(state.upgradeCost()) + " sur toi.";
        }
        return y + 35;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        Ui.label(graphics, font, "CAISSES À L'ENTREPÔT DE LA COLONIE", x, y);
        y += 11;
        final Crate[] crates = Crate.values();
        final int columnW = (width - 3) / 2;
        for (int i = 0; i < crates.length; i++)
        {
            final int cx = x + (i % 2) * (columnW + 3);
            final int cy = y + (i / 2) * 20;
            Icon.of(crates[i].stack(1)).draw(graphics, cx, cy, 16);
            addSlot(cx, cy, 16, crates[i].stack(1), mouseY);
            graphics.drawString(font, fit(crates[i].displayName(), columnW - 50), cx + 20, cy + 4, Palette.INK, false);
            final String count = String.valueOf(ClientCrateStock.get(crates[i]));
            graphics.drawString(font, Component.literal(count).withStyle(ChatFormatting.BOLD), cx + columnW - 4 - font.width(count), cy + 4, Palette.INK, false);
        }
        return y + ((crates.length + 1) / 2) * 20;
    }
}
