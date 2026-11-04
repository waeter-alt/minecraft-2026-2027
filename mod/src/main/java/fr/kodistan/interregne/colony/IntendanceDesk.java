package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.util.constant.CitizenConstants;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.IntendanceActionPayload;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import fr.kodistan.interregne.politics.Influence;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The Intendance's counter at the town hall: a colony member settles, in one click, either all the citizens it can
 * tend (cure the sick, feed the hungry) or all the supplies its workers wait on. Each costs Couronnes, from the colony
 * treasury then the player's purse, and the player's Influence. Dearer than building the right hut, on purpose.
 */
public final class IntendanceDesk
{
    private static final double FULL_SATURATION = 20.0;

    private IntendanceDesk()
    {
    }

    public static void onAction(final IntendanceActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(payload.colony(), player.level());
        if (colony == null || !colony.getPermissions().isColonyMember(player))
        {
            return;
        }
        switch (payload.action())
        {
            case IntendanceActionPayload.CURE, IntendanceActionPayload.FEED -> settleCitizen(player, colony, payload.target(),
              payload.action() == IntendanceActionPayload.CURE);
            case IntendanceActionPayload.SUPPLY -> settleSupply(player, colony, payload.target());
            default -> settle(player, colony, payload.action());
        }
        PacketDistributor.sendToPlayer(player, quote(colony));
    }

    /**
     * Settle a whole card for this player (no membership check: the caller does it).
     */
    public static void settle(final ServerPlayer player, final IColony colony, final int action)
    {
        switch (action)
        {
            case IntendanceActionPayload.SETTLE_GRIEVANCES -> settleGrievances(player, colony);
            case IntendanceActionPayload.SETTLE_SUPPLIES -> settleSupplies(player, colony);
            default -> {
            }
        }
    }

    public static IntendanceQuotePayload quote(final IColony colony)
    {
        final List<ICitizenData> sick = sick(colony);
        final List<ICitizenData> hungry = hungry(colony);
        final List<IntendanceQuotePayload.Care> care = new ArrayList<>();
        sick.forEach(citizen -> care.add(new IntendanceQuotePayload.Care(citizen.getId(), citizen.getName(), true, Config.INTENDANCE_CURE_COST.get())));
        hungry.forEach(citizen -> care.add(new IntendanceQuotePayload.Care(citizen.getId(), citizen.getName(), false, Config.INTENDANCE_MEAL_COST.get())));
        final long careGold = careCost(sick, hungry);

        final Intendance.Supplies supplies = Intendance.supplies(colony);
        final IRequestManager manager = colony.getRequestManager();
        final List<IntendanceQuotePayload.Supply> rows = new ArrayList<>();
        supplies.buyable().forEach(purchase -> rows.add(new IntendanceQuotePayload.Supply(purchase.stack(), whoAsks(manager, purchase), (int) rowCost(purchase),
          key(purchase))));
        supplies.rare().forEach(purchase -> rows.add(new IntendanceQuotePayload.Supply(purchase.stack(), whoAsks(manager, purchase), -1, key(purchase))));
        final long supplyGold = supplyCost(supplies);
        return new IntendanceQuotePayload(colony.getID(), care, careGold, influenceFor(careGold), rows, supplyGold, influenceFor(supplyGold));
    }

    private static Component whoAsks(final IRequestManager manager, final Intendance.Purchase purchase)
    {
        final IRequest<?> request = manager.getRequestForToken(purchase.token());
        return request == null ? Component.literal("?") : request.getRequester().getRequesterDisplayName(manager, request);
    }

    private static int key(final Intendance.Purchase purchase)
    {
        return purchase.token().getIdentifier().hashCode();
    }

    /**
     * One row of the grievances list: cure this sick citizen, or feed this hungry one, at its row price.
     */
    public static void settleCitizen(final ServerPlayer player, final IColony colony, final int id, final boolean cure)
    {
        final ICitizenData citizen = colony.getCitizenManager().getCivilian(id);
        if (citizen == null || (cure ? !citizen.getCitizenDiseaseHandler().isSick() : citizen.getSaturation() >= CitizenConstants.LOW_SATURATION))
        {
            return;
        }
        final long gold = cure ? Config.INTENDANCE_CURE_COST.get() : Config.INTENDANCE_MEAL_COST.get();
        if (!charge(player, colony, gold))
        {
            return;
        }
        if (cure)
        {
            citizen.getCitizenDiseaseHandler().cure();
        }
        else
        {
            citizen.setSaturation(FULL_SATURATION);
        }
        colony.getCitizenManager().markDirty();
        report(colony, player, citizen.getName() + (cure ? " soigné" : " nourri"), gold);
    }

    /**
     * One row of the supplies list, by its key, at its row price.
     */
    public static void settleSupply(final ServerPlayer player, final IColony colony, final int key)
    {
        final Intendance.Purchase purchase = Intendance.supplies(colony).buyable().stream().filter(p -> key(p) == key).findFirst().orElse(null);
        if (purchase == null)
        {
            return;
        }
        final long gold = rowCost(purchase);
        if (!Intendance.fits(purchase))
        {
            player.sendSystemMessage(Component.literal("L'atelier est plein : vide-le avant cette livraison.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!canPay(player, colony, gold) || !Intendance.deliver(colony, purchase) || !charge(player, colony, gold))
        {
            return;
        }
        report(colony, player, purchase.stack().getCount() + " × " + purchase.stack().getHoverName().getString() + " livrés", gold);
    }

    private static long rowCost(final Intendance.Purchase purchase)
    {
        return (long) Math.ceil(purchase.cost() * Config.INTENDANCE_MARKUP.get());
    }

    public static long influenceFor(final long gold)
    {
        final long per = Config.INTENDANCE_CROWNS_PER_INFLUENCE.get();
        return gold <= 0 ? 0 : Math.max(1, (gold + per - 1) / per);
    }

    private static long careCost(final List<ICitizenData> sick, final List<ICitizenData> hungry)
    {
        return (long) sick.size() * Config.INTENDANCE_CURE_COST.get() + (long) hungry.size() * Config.INTENDANCE_MEAL_COST.get();
    }

    /**
     * The sum of the rows the page shows, so the total always matches them.
     */
    private static long supplyCost(final Intendance.Supplies supplies)
    {
        return supplies.buyable().stream().mapToLong(IntendanceDesk::rowCost).sum();
    }

    private static void settleGrievances(final ServerPlayer player, final IColony colony)
    {
        final List<ICitizenData> sick = sick(colony);
        final List<ICitizenData> hungry = hungry(colony);
        final long gold = careCost(sick, hungry);
        if (sick.isEmpty() && hungry.isEmpty() || !charge(player, colony, gold))
        {
            return;
        }
        sick.forEach(citizen -> citizen.getCitizenDiseaseHandler().cure());
        hungry.forEach(citizen -> citizen.setSaturation(FULL_SATURATION));
        colony.getCitizenManager().markDirty();
        final List<String> done = new ArrayList<>();
        if (!sick.isEmpty())
        {
            done.add(sick.size() + (sick.size() == 1 ? " malade soigné" : " malades soignés"));
        }
        if (!hungry.isEmpty())
        {
            done.add(hungry.size() + (hungry.size() == 1 ? " citoyen nourri" : " citoyens nourris"));
        }
        report(colony, player, String.join(", ", done), gold);
    }

    /**
     * Everything listed, in one payment: the player must afford the whole list, then pays only for what was delivered
     * (a full hut keeps its row for later; a request closed along with another one costs nothing).
     */
    private static void settleSupplies(final ServerPlayer player, final IColony colony)
    {
        final Intendance.Supplies supplies = Intendance.supplies(colony);
        if (supplies.buyable().isEmpty() || !canPay(player, colony, supplyCost(supplies)))
        {
            return;
        }
        int delivered = 0;
        int full = 0;
        long gold = 0;
        for (final Intendance.Purchase purchase : supplies.buyable())
        {
            if (!Intendance.fits(purchase))
            {
                full++;
            }
            else if (Intendance.deliver(colony, purchase))
            {
                delivered++;
                gold += rowCost(purchase);
            }
        }
        if (gold > 0 && charge(player, colony, gold))
        {
            report(colony, player, delivered + (delivered == 1 ? " fourniture livrée" : " fournitures livrées"), gold);
        }
        if (full > 0)
        {
            player.sendSystemMessage(Component.literal(full + (full == 1 ? " atelier est plein" : " ateliers sont pleins")
                                                         + " : vide-les, le reste sera livré au prochain règlement, sans rien payer d'ici là.")
                                       .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Take the Couronnes and the Influence, or nothing at all with the reason in the chat.
     */
    private static boolean charge(final ServerPlayer player, final IColony colony, final long gold)
    {
        if (!canPay(player, colony, gold))
        {
            return false;
        }
        if (TreasuryPayment.pay(player, colony, gold) == null)
        {
            player.sendSystemMessage(Component.literal("Trésor et bourse réunis n'y suffisent pas : il faut " + Wallet.format(gold) + " Couronnes.")
                                       .withStyle(ChatFormatting.RED));
            return false;
        }
        Influence.add(player.server, player.getUUID(), -influenceFor(gold), "Intendance");
        return true;
    }

    /**
     * Whether the player could pay this now, with the reason in the chat when not.
     */
    private static boolean canPay(final ServerPlayer player, final IColony colony, final long gold)
    {
        final long influence = influenceFor(gold);
        if (Influence.get(player.server, player.getUUID()) < influence)
        {
            player.sendSystemMessage(Component.literal("Il te faut " + influence + " Influence pour régler cela.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (TreasuryPayment.available(player, colony) < gold)
        {
            player.sendSystemMessage(Component.literal("Trésor et bourse réunis n'y suffisent pas : il faut " + Wallet.format(gold) + " Couronnes.")
                                       .withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static void report(final IColony colony, final ServerPlayer payer, final String what, final long gold)
    {
        final Component line = Component.literal("⚖ Intendance : " + what + " pour " + Wallet.format(gold) + " Couronnes et " + influenceFor(gold)
                                                   + " Influence, réglés par " + payer.getName().getString() + ".").withStyle(ChatFormatting.GOLD);
        colony.getMessagePlayerEntities().forEach(player -> player.sendSystemMessage(line));
        payer.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5F, 0.8F);
    }

    private static List<ICitizenData> sick(final IColony colony)
    {
        return colony.getCitizenManager().getCitizens().stream().filter(citizen -> citizen.getCitizenDiseaseHandler().isSick()).toList();
    }

    private static List<ICitizenData> hungry(final IColony colony)
    {
        return colony.getCitizenManager().getCitizens().stream().filter(citizen -> citizen.getSaturation() < CitizenConstants.LOW_SATURATION).toList();
    }
}
