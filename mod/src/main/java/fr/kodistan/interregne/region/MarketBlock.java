package fr.kodistan.interregne.region;

import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenMarketPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Marché impérial, placed by the Intendants in the imperial city: buy any crate dear, sell it cheap.
 * Prices follow the Empire's stock of each crate (see {@link ImperialMarket}).
 */
public class MarketBlock extends Block
{
    private static final double REACH_SQ = 8 * 8;

    /**
     * Most crates traded in one click.
     */
    private static final int MAX_BATCH = Crates.STACK_SIZE;

    public MarketBlock(final Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            sendWindow(serverPlayer, pos);
            level.playSound(null, pos, SoundEvents.VILLAGER_TRADE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void onAction(final RegionActionPayload payload, final ServerPlayer player)
    {
        if (!(player.level().getBlockState(payload.block()).getBlock() instanceof MarketBlock)
              || player.distanceToSqr(payload.block().getCenter()) > REACH_SQ
              || payload.crate() < 0 || payload.crate() >= Crate.values().length)
        {
            return;
        }
        final Crate crate = Crate.values()[payload.crate()];
        final int amount = Math.clamp(payload.amount(), 1, MAX_BATCH);
        final ImperialMarket market = ImperialMarket.get(player.server);
        final IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());

        if (payload.action() == RegionActionPayload.MARKET_BUY)
        {
            buy(player, market, inventory, crate, amount);
        }
        else if (payload.action() == RegionActionPayload.MARKET_SELL)
        {
            sell(player, market, inventory, crate, amount);
        }
        sendWindow(player, payload.block());
    }

    private static void buy(final ServerPlayer player, final ImperialMarket market, final IItemHandler inventory, final Crate crate, final int wanted)
    {
        final int amount = Math.min(wanted, market.stock(crate));
        if (amount <= 0)
        {
            player.sendSystemMessage(Component.literal("L'Empire n'a plus de " + crate.pluralName() + " à vendre.").withStyle(ChatFormatting.RED));
            return;
        }
        final long price = market.buyTotal(crate, amount);
        if (!Wallet.pay(inventory, price, stack -> player.drop(stack, false)))
        {
            player.sendSystemMessage(Component.literal("Il te faut " + Wallet.couronnes(price, null) + " pour " + crate.count(amount) + " (tu en as "
                                                         + Wallet.format(Wallet.value(inventory)) + ").").withStyle(ChatFormatting.RED));
            return;
        }
        market.setStock(crate, market.stock(crate) - amount);
        final ItemStack rest = ItemHandlerHelper.insertItemStacked(inventory, crate.stack(amount), false);
        if (!rest.isEmpty())
        {
            player.drop(rest, false);
        }
        player.sendSystemMessage(Component.literal("Acheté : " + crate.count(amount) + " pour " + Wallet.couronnes(price, null) + ".").withStyle(ChatFormatting.GOLD));
        player.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    private static void sell(final ServerPlayer player, final ImperialMarket market, final IItemHandler inventory, final Crate crate, final int wanted)
    {
        final int amount = Math.min(wanted, CrateStores.count(inventory, crate));
        if (amount <= 0)
        {
            player.sendSystemMessage(Component.literal("Tu n'as aucune " + crate.displayName().toLowerCase() + " sur toi.").withStyle(ChatFormatting.RED));
            return;
        }
        final long price = market.sellTotal(crate, amount);
        final int taken = CrateStores.extract(inventory, crate, amount);
        final long paid = taken == amount ? price : market.sellTotal(crate, taken);
        market.setStock(crate, market.stock(crate) + taken);
        Wallet.give(inventory, paid, stack -> player.drop(stack, false));
        player.sendSystemMessage(Component.literal("Vendu : " + crate.count(taken) + " pour " + Wallet.couronnes(paid, null) + ".").withStyle(ChatFormatting.GOLD));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
    }

    private static void sendWindow(final ServerPlayer player, final BlockPos pos)
    {
        final ImperialMarket market = ImperialMarket.get(player.server);
        final IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());
        final int types = Crate.values().length;
        final int[] buy = new int[types];
        final int[] sell = new int[types];
        final int[] stock = new int[types];
        final int[] carried = new int[types];
        for (final Crate crate : Crate.values())
        {
            buy[crate.ordinal()] = market.buyPrice(crate);
            sell[crate.ordinal()] = market.sellPrice(crate);
            stock[crate.ordinal()] = market.stock(crate);
            carried[crate.ordinal()] = CrateStores.count(inventory, crate);
        }
        PacketDistributor.sendToPlayer(player, new OpenMarketPayload(pos, buy, sell, stock, carried, Wallet.value(inventory)));
    }
}
