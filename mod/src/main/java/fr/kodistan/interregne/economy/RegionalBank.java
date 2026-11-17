package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.Rank;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.RegionalBankActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * A colony's Banque régionale: the one place where its members deposit into the colony treasury,
 * and its owner and officers withdraw or upgrade the vault. One per colony, inside its borders (checked in {@link RegionalBankEvents}).
 */
public final class RegionalBank
{
    /**
     * How far a player may stand from the bank while using its window.
     */
    private static final double REACH_SQ = 8 * 8;

    private RegionalBank()
    {
    }

    /**
     * The colony's built bank (the highest, should a second one exist), or null while there is none.
     */
    @Nullable
    public static BuildingBanqueRegionale of(final IColony colony)
    {
        BuildingBanqueRegionale best = null;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingBanqueRegionale bank && bank.getBuildingLevel() > 0
                  && (best == null || bank.getBuildingLevel() > best.getBuildingLevel()))
            {
                best = bank;
            }
        }
        return best;
    }

    /**
     * The colony whose bank stands here.
     */
    @Nullable
    static IColony colonyOf(final Level level, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getIColony(level, pos);
        if (colony == null)
        {
            return null;
        }
        final BuildingBanqueRegionale bank = of(colony);
        return bank != null && pos.equals(bank.getPosition()) ? colony : null;
    }

    /**
     * Opens the bank's window for a player standing at its hut.
     */
    static void open(final ServerPlayer player, final BlockPos pos)
    {
        final IColony colony = colonyOf(player.level(), pos);
        if (colony == null)
        {
            // A colony has one bank, the highest: another one standing here is a duplicate, not an unbuilt bank
            final IColony here = IColonyManager.getInstance().getIColony(player.level(), pos);
            final BuildingBanqueRegionale active = here == null ? null : of(here);
            player.sendSystemMessage(Component.literal(active == null
              ? "Cette banque n'est pas encore construite : le trésor y sera gardé dès que le bâtisseur aura fini."
              : "Une colonie n'a qu'une banque : celle de " + here.getName() + " est en " + active.getPosition().toShortString() + ". Celle-ci est en trop, tu peux la démolir.")
                                       .withStyle(ChatFormatting.GRAY));
            return;
        }
        sendWindow(player, pos, colony);
        player.level().playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    static boolean isMember(final IColony colony, final Player player)
    {
        return colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS);
    }

    /**
     * Owner and officers (MineColonies' "colony managers") hold the keys of the vault.
     */
    static boolean isManager(final IColony colony, final Player player)
    {
        final Rank rank = colony.getPermissions().getRank(player);
        return rank != null && rank.isColonyManager();
    }

    public static void onAction(final RegionalBankActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(payload.bank().getCenter()) > REACH_SQ)
        {
            return;
        }
        if (payload.action() == RegionalBankActionPayload.OPEN)
        {
            open(player, payload.bank());
            return;
        }
        final IColony colony = colonyOf(player.level(), payload.bank());
        if (colony == null)
        {
            return;
        }
        switch (payload.action())
        {
            case RegionalBankActionPayload.DEPOSIT -> deposit(player, colony);
            case RegionalBankActionPayload.WITHDRAW -> withdraw(player, colony, payload.amount());
            default -> {
                return;
            }
        }
        sendWindow(player, payload.bank(), colony);
    }

    private static void deposit(final ServerPlayer player, final IColony colony)
    {
        if (!isMember(colony, player))
        {
            player.sendSystemMessage(Component.literal("Seuls les membres de " + colony.getName() + " peuvent déposer ici.").withStyle(ChatFormatting.RED));
            return;
        }
        final RegionalTreasury treasury = RegionalTreasury.get(player.server);
        final IItemHandler purse = purse(player);
        final long carried = Wallet.value(purse);
        final long room = treasury.room(colony);
        if (carried <= 0)
        {
            player.sendSystemMessage(Component.literal("Tu n'as aucune couronne sur toi.").withStyle(ChatFormatting.RED));
            return;
        }
        if (room <= 0)
        {
            player.sendSystemMessage(Component.literal("Le coffre est plein (" + Wallet.format(treasury.capacity(colony)) + " couronnes) : "
                                                         + "porte tes couronnes à la Banque impériale, ou améliore la banque.")
                                       .withStyle(ChatFormatting.RED));
            return;
        }
        final long amount = Math.min(carried, room);
        if (!Wallet.pay(purse, amount, stack -> player.drop(stack, false)))
        {
            return;
        }
        treasury.credit(colony, amount);
        player.sendSystemMessage(Component.literal(Wallet.couronnes(amount, "déposée") + " au trésor de " + colony.getName() + ".").withStyle(ChatFormatting.GOLD));
        if (amount < carried)
        {
            player.sendSystemMessage(Component.literal("Le coffre est plein : " + Wallet.couronnes(carried - amount, "refusée")
                                                         + ". Porte le reste à la Banque impériale, ou améliore la banque.")
                                       .withStyle(ChatFormatting.RED));
        }
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
    }

    private static void withdraw(final ServerPlayer player, final IColony colony, final long amount)
    {
        if (!isManager(colony, player))
        {
            player.sendSystemMessage(Component.literal("Seuls le propriétaire et les officiers de " + colony.getName() + " peuvent retirer.")
                                       .withStyle(ChatFormatting.RED));
            return;
        }
        if (amount <= 0)
        {
            return;
        }
        if (!RegionalTreasury.get(player.server).spend(colony, amount))
        {
            player.sendSystemMessage(Component.literal("Trésor insuffisant pour retirer " + Wallet.couronnes(amount, null) + ".").withStyle(ChatFormatting.RED));
            return;
        }
        Wallet.give(purse(player), amount, stack -> player.drop(stack, false));
        player.playNotifySound(SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.BLOCKS, 0.6F, 1.0F);
        // Every member sees who takes from the common purse
        final Component notice = Component.literal("⚖ " + player.getGameProfile().getName() + " a retiré " + Wallet.couronnes(amount, null)
                                                     + " du trésor de " + colony.getName() + ".").withStyle(ChatFormatting.GOLD);
        colony.getMessagePlayerEntities().forEach(member -> member.sendSystemMessage(notice));
        if (!colony.getMessagePlayerEntities().contains(player))
        {
            player.sendSystemMessage(notice);
        }
    }

    private static void sendWindow(final ServerPlayer player, final BlockPos bank, final IColony colony)
    {
        final RegionalTreasury treasury = RegionalTreasury.get(player.server);
        final int level = treasury.level(colony);
        final boolean last = level >= RegionalTreasury.MAX_LEVEL;
        PacketDistributor.sendToPlayer(player, new OpenRegionalBankPayload(bank, RegionalTreasury.keyOf(colony), colony.getName(),
          treasury.balance(colony), treasury.capacity(colony), level,
          last ? -1 : -2, last ? 0 : RegionalTreasury.capacityFor(level + 1),
          isMember(colony, player), isManager(colony, player)));
    }

    private static IItemHandler purse(final ServerPlayer player)
    {
        return new PlayerMainInvWrapper(player.getInventory());
    }
}
