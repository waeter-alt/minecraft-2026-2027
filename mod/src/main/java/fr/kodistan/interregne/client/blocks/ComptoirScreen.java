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
 * A Comptoir régional: the region's crate it produces and how often, the countdown to the next one, its reserve with a
 * button to take it, the next level, and the colony's crates in the warehouse.
 */
public final class ComptoirScreen extends CounterScreen
{
    private OpenComptoirPayload state;
    private int secondsLeft;
    private int ticks;

    private ComptoirScreen(final OpenComptoirPayload state)
    {
        super("Comptoir régional", 360, 240);
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
        Ui.banner(graphics, font, x, y, width, Ui.State.OK, "Niveau " + state.level() + " / 5",
          "une caisse toutes les " + BuildCosts.formatSeconds(state.seconds()) + " · prochaine dans " + BuildCosts.formatSeconds(secondsLeft));
        y += 21;

        // The region's crate and the reserve
        Ui.card(graphics, x, y, width, 40, false, false, false);
        Icon.of(crate.stack(1)).draw(graphics, x + 5, y + 4, 32);
        addSlot(x + 5, y + 4, 32, crate.stack(1), mouseY);
        final String region = state.region().isEmpty() ? "" : Character.toUpperCase(crate.regionName().charAt(0)) + crate.regionName().substring(1);
        graphics.drawString(font, Component.literal(region).withStyle(ChatFormatting.BOLD), x + 44, y + 5, Palette.INK, false);
        graphics.drawString(font, "Réserve : " + state.stock() + " / " + state.storage() + (state.stock() > 1 ? " caisses" : " caisse"), x + 44, y + 16, Palette.INK, false);
        graphics.drawString(font, fit(state.warehouse() ? "La production va à l'entrepôt de la colonie." : "Pas d'entrepôt : elle reste ici, jusqu'à " + state.storage() + ".",
          width - 150), x + 44, y + 27, Palette.MUTED, false);
        final String take = "Prendre la réserve";
        button(graphics, mouseX, mouseY, x + width - 6 - buttonWidth(take), y + 13, take, Ui.ButtonStyle.PRIMARY, state.stock() > 0,
          state.stock() > 0 ? "Les caisses vont dans ton inventaire." : "La réserve est vide.", () -> send(RegionActionPayload.COMPTOIR_TAKE));
        y += 44;

        // The next level
        if (state.upgradeCost() < 0)
        {
            graphics.drawString(font, Component.literal("Niveau maximal atteint").withStyle(ChatFormatting.BOLD), x + 2, y + 3, Palette.MUTED, false);
            return y + 16;
        }
        final int next = state.level() + 1;
        Ui.plate(graphics, x, y, x + width, y + 30, Palette.levelSoft(next), Palette.levelEdge(next), Palette.levelSoft(next));
        graphics.drawString(font, Component.literal("Niveau " + next).withStyle(ChatFormatting.BOLD), x + 6, y + 4, Palette.INK, false);
        graphics.drawString(font, "une caisse toutes les " + BuildCosts.formatSeconds(state.nextSeconds()), x + 6, y + 16, Palette.MUTED, false);
        final String upgrade = "Améliorer · " + Wallet.format(state.upgradeCost());
        final boolean can = state.purse() >= state.upgradeCost() || minecraft.player.isCreative();
        button(graphics, mouseX, mouseY, x + width - 6 - buttonWidth(upgrade), y + 8, upgrade, Ui.ButtonStyle.PRIMARY, can,
          can ? "Payé avec les couronnes sur toi." : "Il te faut " + couronnes(state.upgradeCost()) + " sur toi.", () -> send(RegionActionPayload.COMPTOIR_UPGRADE));
        return y + 34;
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
