package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.ImperialBank;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Comptoir régional: one per colony, placed by a member inside the colony. It produces the colony's crate on its own
 * and files it in the warehouse racks, or keeps it inside when there is no warehouse. Upgraded with Couronnes.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public class ComptoirBlock extends Block
{
    public static final int MAX_LEVEL = 5;

    private static final double REACH_SQ = 8 * 8;

    public ComptoirBlock(final Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            final IColony colony = ownerOf(serverPlayer.serverLevel(), pos);
            if (colony == null)
            {
                serverPlayer.sendSystemMessage(Component.literal("Ce comptoir n'est rattaché à aucune colonie : casse-le et repose-le dans ta colonie.")
                                                 .withStyle(ChatFormatting.RED));
            }
            else if (!colony.getPermissions().hasPermission(serverPlayer, Action.ACCESS_HUTS))
            {
                final Crate crate = RegionData.get(serverPlayer.server).regionOf(colony);
                serverPlayer.sendSystemMessage(Component.literal("Comptoir de " + colony.getName() + (crate == null ? "." : ", " + crate.regionName() + "."))
                                                 .withStyle(ChatFormatting.GRAY));
            }
            else
            {
                sendWindow(serverPlayer, colony, pos);
                level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.1F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel)
        {
            final IColony colony = ownerOf(serverLevel, pos);
            if (colony != null)
            {
                RegionData.get(serverLevel.getServer()).comptoir(colony).pos = null;
                RegionData.get(serverLevel.getServer()).touch();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Only a colony member may place a Comptoir, inside their colony, and only one per colony.
     */
    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getPlacedBlock().getBlock() instanceof ComptoirBlock)
              || !(event.getEntity() instanceof ServerPlayer player)
              || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        final BlockPos pos = event.getPos();
        final IColony colony = ImperialBank.colonyOf(player);
        final String refusal = refusal(level, pos, colony);
        if (refusal != null)
        {
            player.displayClientMessage(Component.literal(refusal).withStyle(ChatFormatting.RED), false);
            event.setCanceled(true);
            return;
        }
        final RegionData data = RegionData.get(level.getServer());
        final RegionData.Comptoir comptoir = data.comptoir(colony);
        comptoir.pos = pos.immutable();
        // No catch-up for the time it was not standing
        comptoir.lastTime = level.getGameTime();
        data.touch();
        final Crate crate = data.regionOf(colony);
        player.sendSystemMessage(Component.literal("Comptoir régional de " + colony.getName() + " installé"
                                                     + (crate == null ? "." : " : il produit des " + crate.pluralName() + "."))
                                   .withStyle(ChatFormatting.GOLD));
    }

    @Nullable
    private static String refusal(final ServerLevel level, final BlockPos pos, @Nullable final IColony colony)
    {
        if (colony == null)
        {
            return "Il faut appartenir à une colonie pour installer un Comptoir régional.";
        }
        if (colony.getDimension() != level.dimension() || !colony.isCoordInColony(level, pos))
        {
            return "Le Comptoir régional s'installe à l'intérieur de ta colonie, " + colony.getName() + ".";
        }
        final RegionData.Comptoir existing = RegionData.get(level.getServer()).existingComptoir(colony);
        if (existing != null && existing.pos != null && !existing.pos.equals(pos)
              && (!level.isLoaded(existing.pos) || level.getBlockState(existing.pos).getBlock() instanceof ComptoirBlock))
        {
            return colony.getName() + " a déjà un Comptoir régional (en " + existing.pos.toShortString() + ").";
        }
        return null;
    }

    /**
     * The colony whose Comptoir stands here.
     */
    @Nullable
    public static IColony ownerOf(final ServerLevel level, final BlockPos pos)
    {
        final RegionData data = RegionData.get(level.getServer());
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            final RegionData.Comptoir comptoir = data.existingComptoir(colony);
            if (comptoir != null && pos.equals(comptoir.pos))
            {
                return colony;
            }
        }
        return null;
    }

    public static void onAction(final RegionActionPayload payload, final ServerPlayer player)
    {
        final ServerLevel level = player.serverLevel();
        if (!(level.getBlockState(payload.block()).getBlock() instanceof ComptoirBlock) || player.distanceToSqr(payload.block().getCenter()) > REACH_SQ)
        {
            return;
        }
        final IColony colony = ownerOf(level, payload.block());
        if (colony == null || !colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            return;
        }
        final RegionData data = RegionData.get(player.server);
        final RegionData.Comptoir comptoir = data.comptoir(colony);
        final IItemHandler purse = new PlayerMainInvWrapper(player.getInventory());

        if (payload.action() == RegionActionPayload.COMPTOIR_UPGRADE && comptoir.level < MAX_LEVEL)
        {
            final long cost = Regions.upgradeCost(comptoir.level + 1);
            if (player.isCreative() || Wallet.pay(purse, cost, stack -> player.drop(stack, false)))
            {
                comptoir.level++;
                data.touch();
                player.sendSystemMessage(Component.literal("Comptoir régional amélioré au niveau " + comptoir.level + " : une caisse toutes les "
                                                             + BuildCosts.formatSeconds(Regions.secondsPerCrate(comptoir.level)) + ".").withStyle(ChatFormatting.GOLD));
                level.playSound(null, payload.block(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.2F);
            }
            else
            {
                player.sendSystemMessage(Component.literal("Il te faut " + Wallet.couronnes(cost, null) + " pour cette amélioration (tu en as "
                                                             + Wallet.format(Wallet.value(purse)) + ").").withStyle(ChatFormatting.RED));
            }
        }
        else if (payload.action() == RegionActionPayload.COMPTOIR_TAKE && comptoir.stock > 0)
        {
            final Crate crate = data.regionOf(colony);
            if (crate != null)
            {
                int given = 0;
                while (given < comptoir.stock)
                {
                    final ItemStack stack = crate.stack(Math.min(Crates.STACK_SIZE, comptoir.stock - given));
                    final int offered = stack.getCount();
                    final int placed = offered - ItemHandlerHelper.insertItemStacked(purse, stack, false).getCount();
                    given += placed;
                    if (placed < offered)
                    {
                        break;
                    }
                }
                comptoir.stock -= given;
                data.touch();
                if (given == 0)
                {
                    player.sendSystemMessage(Component.literal("Ton inventaire est plein.").withStyle(ChatFormatting.RED));
                }
            }
        }
        sendWindow(player, colony, payload.block());
    }

    private static void sendWindow(final ServerPlayer player, final IColony colony, final BlockPos pos)
    {
        final RegionData data = RegionData.get(player.server);
        final RegionData.Comptoir comptoir = data.comptoir(colony);
        final Crate crate = data.regionOf(colony);
        final boolean top = comptoir.level >= MAX_LEVEL;
        final int interval = Regions.ticksPerCrate(comptoir.level);
        final int secondsLeft = (int) Math.max(0, (interval - Math.min(comptoir.progress, interval)) / 20);
        PacketDistributor.sendToPlayer(player, new OpenComptoirPayload(pos, colony.getName(), crate == null ? "" : crate.id(), comptoir.level,
          Regions.secondsPerCrate(comptoir.level), top ? 0 : Regions.secondsPerCrate(comptoir.level + 1), top ? -1 : Regions.upgradeCost(comptoir.level + 1),
          comptoir.stock, Config.COMPTOIR_STORAGE.get(), CrateStores.warehouse(colony) != null, secondsLeft,
          Wallet.value(new PlayerMainInvWrapper(player.getInventory()))));
    }
}
