package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.network.BankActionPayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A counter of the Imperial Bank, placed by the Intendants in the citadel. Using it opens the player's account.
 */
public class BankCounterBlock extends Block
{
    /**
     * How far a player may stand from the counter while using its window.
     */
    private static final double REACH_SQ = 8 * 8;

    public BankCounterBlock(final Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            sendAccount(serverPlayer, pos);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void onAction(final BankActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player)
              || !(player.level().getBlockState(payload.counter()).getBlock() instanceof BankCounterBlock)
              || player.distanceToSqr(payload.counter().getCenter()) > REACH_SQ)
        {
            return;
        }
        final ImperialBank bank = ImperialBank.get(player.server);
        final PlayerMainInvWrapper purse = new PlayerMainInvWrapper(player.getInventory());

        switch (payload.action())
        {
            case BankActionPayload.DEPOSIT -> {
                final long carried = Wallet.value(purse);
                if (carried > 0 && Wallet.pay(purse, carried, stack -> player.drop(stack, false)))
                {
                    bank.credit(player.getUUID(), carried);
                    player.sendSystemMessage(Component.literal(Wallet.couronnes(carried, "déposée") + " à la Banque impériale.").withStyle(ChatFormatting.GOLD));
                    player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.6F);
                }
            }
            case BankActionPayload.WITHDRAW -> {
                final long fee = ImperialBank.feeFor(payload.amount());
                if (bank.withdraw(player.getUUID(), payload.amount()))
                {
                    Wallet.give(purse, payload.amount(), stack -> player.drop(stack, false));
                    player.sendSystemMessage(Component.literal(Wallet.couronnes(payload.amount(), "retirée") + (fee > 0 ? " (frais : " + Wallet.format(fee) + ")." : "."))
                                               .withStyle(ChatFormatting.GOLD));
                    player.playNotifySound(SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.BLOCKS, 0.6F, 1.0F);
                }
                else
                {
                    player.sendSystemMessage(Component.literal("Solde insuffisant : il faut " + Wallet.couronnes(payload.amount() + fee, null) + " avec les frais.")
                                               .withStyle(ChatFormatting.RED));
                }
            }
            case BankActionPayload.FUND_TREASURY -> {
                final IColony colony = ImperialBank.colonyOf(player);
                final long carried = Wallet.value(purse);
                if (colony == null)
                {
                    player.sendSystemMessage(Component.literal("Tu n'appartiens à aucune colonie.").withStyle(ChatFormatting.RED));
                }
                else if (carried > 0 && Wallet.pay(purse, carried, stack -> player.drop(stack, false)))
                {
                    bank.credit(ImperialBank.treasuryOf(colony), carried);
                    player.sendSystemMessage(Component.literal(Wallet.couronnes(carried, "versée") + " au trésor de " + colony.getName() + ".").withStyle(ChatFormatting.GOLD));
                    player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
                }
            }
            default -> {
                return;
            }
        }
        sendAccount(player, payload.counter());
    }

    private static void sendAccount(final ServerPlayer player, final BlockPos counter)
    {
        final ImperialBank bank = ImperialBank.get(player.server);
        final IColony colony = ImperialBank.colonyOf(player);
        PacketDistributor.sendToPlayer(player, new OpenBankPayload(counter, bank.balance(player.getUUID()), Config.WITHDRAWAL_FEE.get(),
          colony == null ? "" : colony.getName(), colony == null ? 0 : bank.balance(ImperialBank.treasuryOf(colony))));
    }
}
