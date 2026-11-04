package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
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
            case IntendanceActionPayload.SETTLE_GRIEVANCES -> settleGrievances(player, colony);
            case IntendanceActionPayload.SETTLE_SUPPLIES -> settleSupplies(player, colony);
            default -> {
            }
        }
        PacketDistributor.sendToPlayer(player, quote(colony));
    }

    public static IntendanceQuotePayload quote(final IColony colony)
    {
        final List<ICitizenData> sick = sick(colony);
        final List<ICitizenData> hungry = hungry(colony);
        final long care = careCost(sick, hungry);
        final Intendance.Supplies supplies = Intendance.supplies(colony);
        final long supply = supplyCost(supplies);
        return new IntendanceQuotePayload(colony.getID(), sick.size(), hungry.size(), care, influenceFor(care), supplies.buyable().size(), supplies.strategic(),
          supply, influenceFor(supply));
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

    private static long supplyCost(final Intendance.Supplies supplies)
    {
        return (long) Math.ceil(supplies.cost() * Config.INTENDANCE_MARKUP.get());
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

    private static void settleSupplies(final ServerPlayer player, final IColony colony)
    {
        final Intendance.Supplies supplies = Intendance.supplies(colony);
        final long gold = supplyCost(supplies);
        if (supplies.buyable().isEmpty() || !charge(player, colony, gold))
        {
            return;
        }
        int delivered = 0;
        for (final Intendance.Purchase purchase : supplies.buyable())
        {
            delivered += Intendance.deliver(colony, purchase) ? 1 : 0;
        }
        report(colony, player, delivered + (delivered == 1 ? " fourniture livrée" : " fournitures livrées"), gold);
        if (delivered < supplies.buyable().size())
        {
            player.sendSystemMessage(Component.literal("Certains ateliers sont pleins : vide-les, le reste sera livré au prochain règlement.")
                                       .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Take the Couronnes and the Influence, or nothing at all with the reason in the chat.
     */
    private static boolean charge(final ServerPlayer player, final IColony colony, final long gold)
    {
        final long influence = influenceFor(gold);
        if (Influence.get(player.server, player.getUUID()) < influence)
        {
            player.sendSystemMessage(Component.literal("Il te faut " + influence + " Influence pour régler cela.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (TreasuryPayment.pay(player, colony, gold) == null)
        {
            player.sendSystemMessage(Component.literal("Trésor et bourse réunis n'y suffisent pas : il faut " + Wallet.format(gold) + " Couronnes.")
                                       .withStyle(ChatFormatting.RED));
            return false;
        }
        Influence.add(player.server, player.getUUID(), -influence, "Intendance");
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
