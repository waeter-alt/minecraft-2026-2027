package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import fr.kodistan.interregne.colony.IntendanceDesk;
import fr.kodistan.interregne.client.ClientIntendance;
import net.minecraft.ChatFormatting;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.hud.InterregneHud;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.IntendanceActionPayload;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/**
 * Intendance : doléances et approvisionnement, réglables en or depuis l'hôtel de ville.
 * Une page du hub de colonie ({@link ColonyHubScreen}) : elle dessine dans le cadre du hub et lui emprunte son état.
 */
final class HubIntendancePage
{
    private final ColonyHubScreen hub;

    HubIntendancePage(final ColonyHubScreen hub)
    {
        this.hub = hub;
    }

    /**
     * The Intendance: two cards, what it would do for the citizens and what it would deliver to the workers, each
     * showing its price first and settled in one click. Rows come from the server and are exactly what it would settle:
     * what the colony already handles never shows here.
     */
    int renderIntendance(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IntendanceQuotePayload quote = ClientIntendance.of(hub.colony.getID());
        y = Ui.wrapped(graphics, hub.font(), Component.literal("Construire le bon bâtiment revient toujours moins cher que de payer l'Intendance."),
          x, y, width, Palette.MUTED, 10) + 6;

        final List<IntendanceQuotePayload.Care> care = quote == null ? List.of() : quote.care();
        final long sick = care.stream().filter(IntendanceQuotePayload.Care::sick).count();
        final long hungry = care.size() - sick;
        final List<String> careParts = new ArrayList<>();
        if (sick > 0)
        {
            careParts.add(sick + (sick == 1 ? " malade à soigner" : " malades à soigner"));
        }
        if (hungry > 0)
        {
            careParts.add(hungry + (hungry == 1 ? " affamé à nourrir" : " affamés à nourrir"));
        }
        final int careTop = y;
        final String careText = quote == null ? "…" : care.isEmpty() ? "Aucun malade ni affamé." : String.join(", ", careParts) + ".";
        y = desk(graphics, mouseX, mouseY, x, y, width, Icon.of(Items.WRITABLE_BOOK), "DOLÉANCES", careText, quote == null ? -1 : quote.careGold(),
          quote == null ? 0 : quote.careInfluence(), !care.isEmpty(), 1, IntendanceActionPayload.SETTLE_GRIEVANCES);
        if (hub.intendanceList == 1)
        {
            y = renderCareRows(graphics, mouseX, mouseY, x, y, width, care) + 4;
            reveal(careTop, y);
        }
        y += 6;

        final List<IntendanceQuotePayload.Supply> supplies = quote == null ? List.of() : quote.supplies();
        final long rare = supplies.stream().filter(IntendanceQuotePayload.Supply::rare).count();
        String supplyText = quote == null ? "…" : supplies.isEmpty() ? "Aucune fourniture ne manque." : supplies.size() + (supplies.size() == 1 ? " fourniture manque." : " fournitures manquent.");
        if (rare > 0)
        {
            supplyText += rare == supplies.size() ? (rare == 1 ? " Objet rare : à fournir toi-même." : " Objets rares : à fournir toi-même.")
                            : " Dont " + rare + (rare == 1 ? " rare" : " rares") + ", à fournir toi-même.";
        }
        final int supplyTop = y;
        y = desk(graphics, mouseX, mouseY, x, y, width, Icon.module("requests"), "FOURNITURES DES TRAVAILLEURS", supplyText, quote == null ? -1 : quote.supplyGold(),
          quote == null ? 0 : quote.supplyInfluence(), !supplies.isEmpty(), 2, IntendanceActionPayload.SETTLE_SUPPLIES);
        if (hub.intendanceList == 2)
        {
            y = renderSupplyRows(graphics, mouseX, mouseY, x, y, width, supplies) + 4;
            reveal(supplyTop, y);
        }
        return y;
    }

    /**
     * Once, after a list was unfolded: scroll just enough for the list's end to show, never past its card's top.
     */
    void reveal(final int cardTop, final int listEnd)
    {
        if (hub.revealList)
        {
            hub.revealList = false;
            hub.scroll += Math.max(0, Math.min(listEnd - hub.viewBottom, cardTop - hub.viewTop));
        }
    }

    /**
     * One card of the Intendance: title, one sentence, then the big settle button carrying its own price (coin and
     * figure, star and figure). Green when it can be paid, red when it cannot (the hover says what is missing), grey
     * when there is nothing to settle. The list button sits beside it when wide, under it when narrow.
     */
    int desk(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final Icon icon, final String title,
      final String text, final long gold, final long influence, final boolean hasList, final int list, final int action)
    {
        final int pad = 6;
        final int inner = width - 2 * pad;
        final boolean wide = width >= 300;
        final List<FormattedCharSequence> lines = hub.font().split(Component.literal(text), inner);
        final int buttonsH = wide ? 22 : 22 + 4 + 18;
        final int height = 24 + lines.size() * 10 + 4 + buttonsH + pad;

        Ui.card(graphics, x, y, width, height, false, false, false);
        icon.draw(graphics, x + pad, y + 5, 16);
        Ui.label(graphics, hub.font(), hub.fitText(title, inner - 22), x + pad + 22, y + 9);
        int ty = y + 24;
        for (final FormattedCharSequence line : lines)
        {
            graphics.drawString(hub.font(), line, x + pad, ty, Palette.INK, false);
            ty += 10;
        }
        ty += 4;

        final long money = money();
        final long ownInfluence = InterregneHud.influence();
        final boolean priced = gold > 0;
        final List<String> lacks = new ArrayList<>();
        if (priced && money < gold)
        {
            lacks.add(Wallet.format(gold - money) + " Couronnes (trésor de la colonie et ta bourse réunis)");
        }
        if (priced && ownInfluence < influence)
        {
            lacks.add((influence - ownInfluence) + " Influence");
        }
        final String missing = lacks.isEmpty() ? null : "Il te manque " + String.join(" et ", lacks);

        final int listW = hub.font().width("Masquer la liste") + 16;
        final int settleW = wide ? inner - listW - 6 : inner;
        final boolean over = Ui.inside(mouseX, mouseY, x + pad, ty, settleW, 22);
        if (!priced)
        {
            Ui.button(graphics, hub.font(), x + pad, ty, settleW, 22, hub.fitText(gold < 0 ? "Calcul du prix…" : "Rien à régler", settleW - 8), Ui.ButtonStyle.PRIMARY, false, over);
        }
        else
        {
            priceButton(graphics, x + pad, ty, settleW, 22, "Tout régler", gold, money, influence, ownInfluence, over);
            if (missing == null)
            {
                hub.addHit(x + pad, ty, settleW, 22, () -> ClientIntendance.settle(hub.colony.getID(), action));
            }
            if (over)
            {
                hub.hint = missing != null ? missing + "." : "Payé avec le trésor de la colonie, complété par les couronnes sur toi ; l'Influence est la tienne.";
            }
        }

        final String listLabel = hub.intendanceList == list ? "Masquer la liste" : "Voir la liste";
        final int listX = wide ? x + width - pad - listW : x + pad;
        final int listY = wide ? ty : ty + 26;
        final int listH = wide ? 22 : 18;
        final int listWidth = wide ? listW : inner;
        final boolean overList = Ui.inside(mouseX, mouseY, listX, listY, listWidth, listH);
        Ui.button(graphics, hub.font(), listX, listY, listWidth, listH, listLabel, Ui.ButtonStyle.SECONDARY, hasList, overList);
        if (hasList)
        {
            hub.addHit(listX, listY, listWidth, listH, () -> {
                hub.intendanceList = hub.intendanceList == list ? 0 : list;
                hub.revealList = hub.intendanceList != 0;
            });
        }
        return y + height + 4;
    }

    /**
     * The settle button with its price inside, the way games show a cost: icons and figures, no unit names.
     * Payable: green, white figures. Not payable: the button turns grey like any unavailable action, and only the
     * figure that falls short turns red, written « what you have / what it costs », so the eye goes straight to what
     * is missing; a figure that is covered stays plain. When narrow, « have / » goes first, then the words shorten.
     */
    void priceButton(final GuiGraphics graphics, final int x, int y, final int width, final int height, final String fullVerb, final long gold,
      final long money, final long influence, final long ownInfluence, final boolean hover)
    {
        final boolean goldOk = money >= gold;
        final boolean influenceOk = ownInfluence >= influence;
        final boolean payable = goldOk && influenceOk;
        if (payable)
        {
            Ui.plate(graphics, x, y, x + width, y + height, hover ? Palette.GREEN_HOVER : Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
        }
        else
        {
            Ui.plate(graphics, x, y, x + width, y + height, Palette.PAPER_DARK, Palette.EDGE, Palette.PAPER_DARK);
        }
        final int plain = payable ? Palette.GREEN_TEXT : Palette.INK;
        final int verbColor = payable ? Palette.GREEN_TEXT : Palette.MUTED;

        boolean showHave = !payable;
        String verb = fullVerb;
        List<Component> parts = priceParts(gold, money, goldOk, influence, ownInfluence, influenceOk, showHave);
        // The verb is drawn bold: measure it bold, or the button overflows by a pixel per letter
        while (hub.font().width(Component.literal(verb).withStyle(ChatFormatting.BOLD)) + (verb.isEmpty() ? 0 : 12) + priceWidth(parts) > width - 12)
        {
            if (showHave)
            {
                showHave = false;
            }
            else if (verb.startsWith("Tout "))
            {
                verb = "Régler";
            }
            else if (!verb.isEmpty())
            {
                verb = "";
            }
            else
            {
                break;
            }
            parts = priceParts(gold, money, goldOk, influence, ownInfluence, influenceOk, showHave);
        }

        final Component verbText = Component.literal(verb).withStyle(ChatFormatting.BOLD);
        final int total = (verb.isEmpty() ? 0 : hub.font().width(verbText) + 12) + priceWidth(parts);
        int cx = x + (width - total) / 2;
        final int textY = y + (height - 8) / 2;
        final int iconY = y + (height - ColonyHubScreen.PRICE_ICON) / 2;
        if (!verb.isEmpty())
        {
            graphics.drawString(hub.font(), verbText, cx, textY, verbColor, false);
            cx += hub.font().width(verbText) + 12;
        }
        Ui.coinTight(graphics, cx, iconY, ColonyHubScreen.PRICE_ICON);
        cx += ColonyHubScreen.PRICE_ICON + 3;
        graphics.drawString(hub.font(), parts.get(0), cx, textY, goldOk ? plain : Palette.RED, false);
        cx += hub.font().width(parts.get(0)) + 10;
        Ui.influence(graphics, cx, iconY, ColonyHubScreen.PRICE_ICON);
        cx += ColonyHubScreen.PRICE_ICON + 2;
        graphics.drawString(hub.font(), parts.get(1), cx, textY, influenceOk ? plain : Palette.RED, false);
    }

    /**
     * Each figure of the price: just the cost when covered, « have / cost » when short (if asked).
     */
    static List<Component> priceParts(final long gold, final long money, final boolean goldOk, final long influence, final long ownInfluence,
      final boolean influenceOk, final boolean showHave)
    {
        return List.of(figure(gold, money, goldOk, showHave), figure(influence, ownInfluence, influenceOk, showHave));
    }

    static Component figure(final long cost, final long have, final boolean ok, final boolean showHave)
    {
        final String text = !ok && showHave ? Wallet.format(have) + "/" + Wallet.format(cost) : Wallet.format(cost);
        return Component.literal(text).withStyle(ChatFormatting.BOLD);
    }

    int priceWidth(final List<Component> parts)
    {
        return ColonyHubScreen.PRICE_ICON + 3 + hub.font().width(parts.get(0)) + 10 + ColonyHubScreen.PRICE_ICON + 2 + hub.font().width(parts.get(1));
    }

    /**
     * What can pay the Intendance: the colony treasury, then the Couronnes on the player.
     */
    long money()
    {
        final long purse = fr.kodistan.interregne.client.dev.DevOverrides.purse != null ? fr.kodistan.interregne.client.dev.DevOverrides.purse
                             : hub.minecraft().player == null ? 0 : Wallet.value(new PlayerMainInvWrapper(hub.minecraft().player.getInventory()));
        return ClientTreasury.of(hub.colony) + purse;
    }

    /**
     * A row's own pay button at its right edge, its price inside like the card's: settles that row alone.
     *
     * @return its width.
     */
    int rowPay(final GuiGraphics graphics, final int mouseX, final int mouseY, final int right, final int y, final int width, final int cost,
      final String what, final Runnable pay)
    {
        final long influence = IntendanceDesk.influenceFor(cost);
        final long money = money();
        final long ownInfluence = InterregneHud.influence();
        final int x = right - width;
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 18);
        priceButton(graphics, x, y, width, 18, "Régler", cost, money, influence, ownInfluence, over);
        final boolean payable = money >= cost && ownInfluence >= influence;
        if (payable)
        {
            hub.addHit(x, y, width, 18, pay);
        }
        if (over)
        {
            hub.hint = payable ? "Régler seulement " + what + "." : "Il te manque de quoi régler " + what + ".";
        }
        return width;
    }

    /**
     * One width for every pay button of a list, the widest price's, so they line up in a column.
     */
    int payWidth(final java.util.stream.IntStream costs)
    {
        final int widest = costs.map(cost -> priceWidth(priceParts(cost, 0, true, IntendanceDesk.influenceFor(cost), 0, true, false))).max().orElse(0);
        return Math.min(hub.font().width(Component.literal("Régler").withStyle(ChatFormatting.BOLD)) + 12 + widest + 14, 150);
    }

    int renderCareRows(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width,
      final List<IntendanceQuotePayload.Care> care)
    {
        final int payW = payWidth(care.stream().mapToInt(IntendanceQuotePayload.Care::cost));
        for (final IntendanceQuotePayload.Care row : care)
        {
            final ICitizenDataView citizen = hub.colony.getCitizen(row.citizen());
            final boolean over = citizen != null && Ui.inside(mouseX, mouseY, x, y, width, ColonyHubScreen.ROW_H);
            Ui.card(graphics, x, y, width, ColonyHubScreen.ROW_H, false, over, false);
            Icon.of(row.sick() ? new ItemStack(Items.POTION) : new ItemStack(Items.BREAD)).draw(graphics, x + 3, y + 4, 16);
            // Its own button first, so a click on it pays rather than opening the sheet
            final int priceW = rowPay(graphics, mouseX, mouseY, x + width - 3, y + 3, payW, row.cost(), (row.sick() ? "les soins de " : "le repas de ") + row.name(),
              () -> ClientIntendance.settle(hub.colony.getID(), row.sick() ? IntendanceActionPayload.CURE : IntendanceActionPayload.FEED, row.citizen()));
            final int textW = width - 23 - priceW - 12;
            graphics.drawString(hub.font(), hub.fitText(row.name(), textW), x + 23, y + 4, Palette.INK, false);
            graphics.drawString(hub.font(), hub.fitText(row.sick() ? "malade, à soigner" : "affamé, à nourrir", textW), x + 23, y + 14, Palette.MUTED, false);
            if (citizen != null)
            {
                hub.addHit(x, y, width, ColonyHubScreen.ROW_H, () -> CitizenScreen.open(citizen, hub));
            }
            y += ColonyHubScreen.ROW_H + 2;
        }
        return y;
    }

    int renderSupplyRows(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width,
      final List<IntendanceQuotePayload.Supply> supplies)
    {
        final int payW = payWidth(supplies.stream().filter(row -> !row.rare()).mapToInt(IntendanceQuotePayload.Supply::cost));
        for (final IntendanceQuotePayload.Supply row : supplies)
        {
            Ui.card(graphics, x, y, width, ColonyHubScreen.ROW_H, false, false, false);
            Icon.of(row.stack()).draw(graphics, x + 3, y + 4, 16);
            hub.slots.add(new Ui.Slot(x + 3, y + 4, 16, row.stack()));
            final int rightW;
            if (row.rare())
            {
                // Never sold: the player brings it
                final String chip = "À fournir";
                rightW = hub.font().width(chip) + 8;
                Ui.chip(graphics, hub.font(), x + width - 6 - rightW, y + 7, chip, Ui.State.BAD);
            }
            else
            {
                rightW = rowPay(graphics, mouseX, mouseY, x + width - 3, y + 3, payW, row.cost(), "cette fourniture",
                  () -> ClientIntendance.settle(hub.colony.getID(), IntendanceActionPayload.SUPPLY, row.key()));
            }
            final int textW = width - 23 - rightW - 12;
            graphics.drawString(hub.font(), hub.fitText(row.stack().getCount() + " × " + row.stack().getHoverName().getString(), textW), x + 23, y + 4, Palette.INK, false);
            graphics.drawString(hub.font(), hub.fitText("pour " + row.forWhom().getString(), textW), x + 23, y + 14, Palette.MUTED, false);
            y += ColonyHubScreen.ROW_H + 2;
        }
        return y;
    }
}
