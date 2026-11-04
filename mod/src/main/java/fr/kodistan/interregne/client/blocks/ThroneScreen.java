package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.ThroneActionPayload;
import fr.kodistan.interregne.politics.Empire;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Trône impérial. The Empire tab answers what a player comes for: does my colony owe a tithe, what is missing,
 * when is it due; then delivering it in one click; the Intendants' edicts last. The treasury tab shows the imperial
 * treasury first, then paying in, then (Intendants) taking out.
 */
public final class ThroneScreen extends CounterScreen
{
    private static final long[] TREASURY_AMOUNTS = {100, 1_000, 10_000};

    private OpenThronePayload state;
    private int tab;
    private int influence = 5;
    private int ticks;
    private long secondsLeft;

    private ThroneScreen(final OpenThronePayload state)
    {
        super("Trône impérial", 400, 250);
        show(state);
    }

    /**
     * Opens the throne, or refreshes it when it is already showing.
     */
    public static void open(final OpenThronePayload state)
    {
        if (Minecraft.getInstance().screen instanceof ThroneScreen screen && screen.state.throne().equals(state.throne()))
        {
            screen.show(state);
            return;
        }
        Minecraft.getInstance().setScreen(new ThroneScreen(state));
    }

    private void show(final OpenThronePayload state)
    {
        this.state = state;
        this.secondsLeft = state.secondsLeft();
    }

    @Override
    public void tick()
    {
        super.tick();
        if (++ticks % 20 == 0 && secondsLeft > 0)
        {
            secondsLeft--;
        }
    }

    @Override
    protected void init()
    {
        super.init();
        // The field is a player's name for the edicts, an amount for the treasury
        if (tab == 0)
        {
            if (state.intendant())
            {
                addField(0, 0, 100, "Nom du joueur");
            }
        }
        else
        {
            amountField();
        }
    }

    private void send(final int action, final String target, final int amount)
    {
        PacketDistributor.sendToServer(new ThroneActionPayload(state.throne(), action, target, amount));
    }

    private int carried(final Crate crate)
    {
        int count = 0;
        for (final ItemStack stack : minecraft.player.getInventory().items)
        {
            if (stack.is(crate.item()))
            {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        segmented(graphics, mouseX, mouseY, x, y, List.of("Dîme et édits", "Trésor impérial"), tab, index -> {
            tab = index;
            search = null;
            rebuildWidgets();
            resetScroll();
        });
        y += 20;
        return tab == 0 ? tithe(graphics, mouseX, mouseY, x, y, width) : treasury(graphics, mouseX, mouseY, x, y, width);
    }

    private int tithe(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final Crate[] crates = Crate.values();
        int owed = 0;
        int deliverable = 0;
        Crate first = null;
        for (final Crate crate : crates)
        {
            final int left = state.owed()[crate.ordinal()];
            owed += left;
            deliverable += Math.min(left, carried(crate));
            if (left > 0 && first == null)
            {
                first = crate;
            }
        }
        final String weight = Empire.TitheWeight.values()[Math.clamp(state.weight(), 0, Empire.TitheWeight.values().length - 1)].label();
        final String auto = state.autopilot() ? " · pilote automatique" : "";

        // The hero: what the colony owes
        if (!state.titheActive())
        {
            y = hero(graphics, x, y, width, new ItemStack(Items.GOLDEN_HELMET), "DÎME IMPÉRIALE", "Aucune dîme en cours",
              "Prochaine dîme au poids " + weight.toLowerCase() + auto);
        }
        else if (state.colonyName().isEmpty())
        {
            y = hero(graphics, x, y, width, new ItemStack(Items.GOLDEN_HELMET), "DÎME IMPÉRIALE", "Tu n'as pas de colonie",
              "Échéance dans " + BuildCosts.formatSeconds((int) secondsLeft) + " · poids " + weight.toLowerCase() + auto);
        }
        else
        {
            y = hero(graphics, x, y, width, first == null ? new ItemStack(Items.EMERALD) : first.stack(1), "DÎME DE " + state.colonyName().toUpperCase(),
              owed == 0 ? "En règle : tout est livré" : "Il manque " + owed + (owed > 1 ? " caisses" : " caisse"),
              "Échéance dans " + BuildCosts.formatSeconds((int) secondsLeft) + " · poids " + weight.toLowerCase() + auto);

            // What the tithe asks, crate by crate
            final int columnW = (width - 9) / 4;
            int i = 0;
            for (final Crate crate : crates)
            {
                final int asked = state.basket()[crate.ordinal()];
                if (asked <= 0)
                {
                    continue;
                }
                final int left = state.owed()[crate.ordinal()];
                final int cx = x + i * (columnW + 3);
                Ui.plate(graphics, cx, y, cx + columnW, y + 28, left == 0 ? Palette.GREEN_SOFT : Palette.PAPER_LIGHT, Palette.RULE,
                  left == 0 ? Palette.GREEN_SOFT : Palette.PAPER_LIGHT);
                Icon.of(crate.stack(1)).draw(graphics, cx + 4, y + 6, 16);
                addSlot(cx + 4, y + 6, 16, crate.stack(1), mouseY);
                final String done = (asked - left) + " / " + asked;
                graphics.drawString(font, Component.literal(done).withStyle(ChatFormatting.BOLD), cx + 24, y + 5, left == 0 ? Palette.GREEN : Palette.INK, false);
                final int carried = carried(crate);
                graphics.drawString(font, fit(left == 0 ? "livré" : "sur toi " + carried, columnW - 26), cx + 24, y + 16,
                  left == 0 ? Palette.GREEN : carried > 0 ? Palette.MUTED : Palette.RED, false);
                i++;
                if (i == 4)
                {
                    break;
                }
            }
            y += 33;
            final String why = owed == 0 ? "La dîme de ta colonie est complète." : deliverable == 0 ? "Tu ne portes aucune des caisses qui manquent." : null;
            y = bigButton(graphics, mouseX, mouseY, x, y, width,
              deliverable > 0 ? "Livrer " + deliverable + (deliverable > 1 ? " caisses" : " caisse") : "Livrer la dîme", why == null, why == null
                ? "Prend dans ton inventaire les caisses qui manquent." : why, () -> send(ThroneActionPayload.DELIVER, "", 0));
        }
        return state.intendant() ? edicts(graphics, mouseX, mouseY, x, y, width) : y;
    }

    private int edicts(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        Ui.plate(graphics, x, y, x + width, y + 46, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        graphics.drawString(font, Component.literal("Édits de l'Intendance").withStyle(ChatFormatting.BOLD), x + 6, y + 6, Palette.INK, false);
        graphics.drawString(font, "Poids de la dîme", x + 6, y + 19, Palette.MUTED, false);
        segmented(graphics, mouseX, mouseY, x + 96, y + 16, List.of("Clémente", "Ordinaire", "Écrasante"), state.weight(), index -> send(switch (index)
        {
            case 0 -> ThroneActionPayload.CLEMENTE;
            case 1 -> ThroneActionPayload.ORDINAIRE;
            default -> ThroneActionPayload.ECRASANTE;
        }, "", 0));

        // Favour or disgrace for a player
        final int ry = y + 31;
        graphics.drawString(font, "Influence", x + 6, ry + 3, Palette.MUTED, false);
        if (search != null)
        {
            search.setX(x + 60);
            search.setY(ry + 1);
        }
        int bx = x + 166;
        bx += button(graphics, mouseX, mouseY, bx, ry, "−", Ui.ButtonStyle.SECONDARY, influence > 1, "Maj : par 10", () -> influence = Math.max(1, influence - step())) + 2;
        final String amount = String.valueOf(influence);
        Ui.plate(graphics, bx, ry, bx + 26, ry + 14, Palette.PAPER, Palette.RULE, Palette.PAPER);
        graphics.drawString(font, amount, bx + 13 - font.width(amount) / 2, ry + 3, Palette.INK, false);
        bx += 28;
        bx += button(graphics, mouseX, mouseY, bx, ry, "+", Ui.ButtonStyle.SECONDARY, true, "Maj : par 10", () -> influence += step()) + 6;
        final String name = search == null ? "" : search.getValue().trim();
        final String why = name.isEmpty() ? "Écris le nom d'un joueur connecté." : null;
        bx += button(graphics, mouseX, mouseY, bx, ry, "Faveur", Ui.ButtonStyle.PRIMARY, why == null, why == null ? "Lui donne " + influence + " d'influence." : why,
          () -> send(ThroneActionPayload.FAVOR, name, influence)) + 3;
        button(graphics, mouseX, mouseY, bx, ry, "Disgrâce", Ui.ButtonStyle.DANGER, why == null, why == null ? "Lui retire " + influence + " d'influence." : why,
          () -> send(ThroneActionPayload.DISGRACE, name, influence));
        return y + 51;
    }

    private static int step()
    {
        return Screen.hasShiftDown() ? 10 : 1;
    }

    private int treasury(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final long purse = purse();
        y = hero(graphics, x, y, width, null, "TRÉSOR IMPÉRIAL", couronnes(state.treasury()), "Chacun y verse ; seuls les Intendants y prélèvent.");
        y = bigButton(graphics, mouseX, mouseY, x, y, width, purse > 0 ? "Verser mes " + couronnes(purse) : "Rien à verser", purse > 0,
          purse > 0 ? "Toutes les couronnes sur toi vont au trésor impérial." : "Tu ne portes aucune couronne.", () -> send(ThroneActionPayload.FUND, "", 0));
        y = panel(graphics, mouseX, mouseY, x, y, width, "Verser", "sur toi : " + couronnes(purse), TREASURY_AMOUNTS, amount -> notMore(amount, purse, "sur toi"),
          amount -> send(ThroneActionPayload.FUND, "", (int) Math.min(amount, Integer.MAX_VALUE)), true, amount -> "il te restera " + couronnes(purse - amount));
        if (state.intendant())
        {
            final int top = y;
            y = panel(graphics, mouseX, mouseY, x, y, width, "Prélever", null, TREASURY_AMOUNTS, amount -> notMore(amount, state.treasury(), "dans le trésor"),
              amount -> send(ThroneActionPayload.WITHDRAW, "", (int) amount), false, null);
            final String all = "Tout prélever";
            button(graphics, mouseX, mouseY, x + width - 6 - buttonWidth(all), top + 18, all, Ui.ButtonStyle.SECONDARY, state.treasury() > 0,
              state.treasury() > 0 ? null : "Le trésor est vide.", () -> send(ThroneActionPayload.WITHDRAW, "", 0));
        }
        return y;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }
}
