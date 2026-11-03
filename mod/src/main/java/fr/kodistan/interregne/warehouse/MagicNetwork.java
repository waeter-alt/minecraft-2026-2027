package fr.kodistan.interregne.warehouse;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Pickup;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.tileentities.TileEntityWareHouse;
import fr.kodistan.interregne.economy.CoinItem;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The warehouse's magic network: instead of a courier walking it over, a delivery or pickup lands after a short
 * delay, straight from rack to hut or hut to rack, with a little sparkle at both ends.
 * Mirrors what MineColonies' courier does (gather, force into the hut, dump into the racks) minus the walk.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class MagicNetwork
{
    /**
     * Retries while one end sits in an unloaded chunk, before giving the request back as failed.
     */
    private static final int MAX_ATTEMPTS = 20;
    private static final int RETRY_TICKS  = 100;

    /**
     * A transfer waiting to land.
     */
    private static final class Transfer
    {
        private final IColony   colony;
        private final IToken<?> token;
        private final BlockPos  warehouse;
        private long            dueAt;
        private int             attempts;

        private Transfer(final IColony colony, final IToken<?> token, final BlockPos warehouse, final long dueAt)
        {
            this.colony = colony;
            this.token = token;
            this.warehouse = warehouse;
            this.dueAt = dueAt;
        }
    }

    private static final List<Transfer> QUEUE = new ArrayList<>();

    private static long now;

    private MagicNetwork()
    {
    }

    public static boolean enabled()
    {
        try
        {
            return Config.MAGIC_NETWORK.get();
        }
        catch (final IllegalStateException e)
        {
            return false;
        }
    }

    /**
     * Take over a delivery or pickup the warehouse would have queued for its couriers.
     */
    public static void schedule(final IRequestManager manager, final IRequest<?> request, final BlockPos warehouse)
    {
        QUEUE.add(new Transfer(manager.getColony(), request.getId(), warehouse, now + Config.TRANSFER_TICKS.get()));
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        now++;
        final int sweepSeconds = enabled() ? Config.SWEEP_SECONDS.get() : 0;
        if (sweepSeconds > 0 && now % (sweepSeconds * 20L) == 0)
        {
            try
            {
                sweep();
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.error("Magic network sweep failed", e);
            }
        }
        if (QUEUE.isEmpty())
        {
            return;
        }
        for (final Transfer transfer : new ArrayList<>(QUEUE))
        {
            if (transfer.dueAt > now)
            {
                continue;
            }
            boolean done = true;
            try
            {
                done = land(transfer);
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.error("Magic network transfer failed", e);
                finish(transfer, false);
            }
            if (done)
            {
                QUEUE.remove(transfer);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        // Unfinished transfers are still IN_PROGRESS requests: hand them back so they get resolved again next start
        for (final Iterator<Transfer> it = QUEUE.iterator(); it.hasNext(); )
        {
            finish(it.next(), false);
            it.remove();
        }
    }

    /**
     * @return true once the transfer is settled (done or given up), false to retry later.
     */
    private static boolean land(final Transfer transfer)
    {
        final IRequestManager manager = transfer.colony.getRequestManager();
        final IRequest<?> request = manager.getRequestForToken(transfer.token);
        if (request == null || request.getState() != RequestState.IN_PROGRESS)
        {
            // Cancelled or settled elsewhere meanwhile
            return true;
        }

        final Boolean success;
        if (request.getRequest() instanceof Delivery delivery)
        {
            success = deliver(transfer, delivery);
        }
        else if (request.getRequest() instanceof Pickup)
        {
            success = pickup(transfer, request.getRequester().getLocation().getInDimensionLocation());
        }
        else
        {
            success = false;
        }

        if (success == null)
        {
            if (++transfer.attempts >= MAX_ATTEMPTS)
            {
                finish(transfer, false);
                return true;
            }
            transfer.dueAt = now + RETRY_TICKS;
            return false;
        }
        finish(transfer, success);
        return true;
    }

    private static void finish(final Transfer transfer, final boolean success)
    {
        final IRequestManager manager = transfer.colony.getRequestManager();
        final IRequest<?> request = manager.getRequestForToken(transfer.token);
        if (request != null && request.getState() == RequestState.IN_PROGRESS)
        {
            manager.updateRequestState(transfer.token, success ? RequestState.RESOLVED : RequestState.FAILED);
        }
    }

    /**
     * Rack (or hut) to hut. Null when one end is not loaded.
     */
    @Nullable
    private static Boolean deliver(final Transfer transfer, final Delivery delivery)
    {
        final ServerLevel level = (ServerLevel) transfer.colony.getWorld();
        final BlockPos startPos = delivery.getStart().getInDimensionLocation();
        final BlockPos targetPos = delivery.getTarget().getInDimensionLocation();
        if (!level.isLoaded(startPos) || !level.isLoaded(targetPos) || !level.isLoaded(transfer.warehouse))
        {
            return null;
        }

        final IItemHandler source = handlerAt(level, startPos);
        final IBuilding targetBuilding = transfer.colony.getBuildingManager().getBuilding(targetPos);
        final IItemHandler target = targetBuilding != null ? targetBuilding.getItemHandlerCap() : handlerAt(level, targetPos);
        if (target == null)
        {
            return false;
        }

        final ItemStack wanted = delivery.getStack();
        ItemStack moved = source == null ? ItemStack.EMPTY : extractMatching(source, wanted, wanted.getCount());
        if (moved.getCount() < wanted.getCount())
        {
            // The racks sort themselves, so the item may have moved: look through the whole warehouse
            final IBuilding warehouse = transfer.colony.getBuildingManager().getBuilding(transfer.warehouse);
            final IItemHandler all = warehouse == null ? null : warehouse.getItemHandlerCap();
            if (all != null)
            {
                final ItemStack rest = extractMatching(all, wanted, wanted.getCount() - moved.getCount());
                if (moved.isEmpty())
                {
                    moved = rest;
                }
                else
                {
                    moved.grow(rest.getCount());
                }
            }
        }
        if (moved.isEmpty())
        {
            return false;
        }

        final ItemStack leftover = InventoryUtils.forceItemStackToItemHandler(target, moved,
          targetBuilding != null ? targetBuilding::isItemStackInRequest : stack -> true);
        if (!leftover.isEmpty())
        {
            giveBack(transfer, source, leftover);
            if (ItemStack.matches(leftover, moved))
            {
                // Nothing fit: the hut is full
                return false;
            }
        }

        sparkle(level, startPos, false);
        trail(level, startPos, targetPos);
        sparkle(level, targetPos, true);
        return true;
    }

    /**
     * Hut to warehouse racks, taking only what the hut does not need to keep. Null when one end is not loaded.
     */
    @Nullable
    private static Boolean pickup(final Transfer transfer, final BlockPos hutPos)
    {
        final ServerLevel level = (ServerLevel) transfer.colony.getWorld();
        if (!level.isLoaded(hutPos) || !level.isLoaded(transfer.warehouse))
        {
            return null;
        }
        final IBuilding hut = transfer.colony.getBuildingManager().getBuilding(hutPos);
        final IBuilding warehouse = transfer.colony.getBuildingManager().getBuilding(transfer.warehouse);
        if (hut == null || warehouse == null || !(warehouse.getTileEntity() instanceof TileEntityWareHouse storage))
        {
            return false;
        }
        moveSurplus(level, hut, storage, transfer.warehouse);
        return true;
    }

    /**
     * Move what a hut does not need to keep into the warehouse racks; Couronnes stay where they are.
     *
     * @return true if anything moved.
     */
    private static boolean moveSurplus(final ServerLevel level, final IBuilding hut, final TileEntityWareHouse storage, final BlockPos warehousePos)
    {
        final IItemHandler handler = hut.getItemHandlerCap();
        if (handler == null)
        {
            return false;
        }
        final List<ItemStorage> kept = new ArrayList<>();
        boolean movedAny = false;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() instanceof CoinItem)
            {
                continue;
            }
            final int amount = hut.buildingRequiresCertainAmountOfItem(stack, kept, false);
            if (amount <= 0)
            {
                continue;
            }
            final AbstractTileEntityRack rack = storage.getRackForStack(stack);
            if (rack == null)
            {
                // Warehouse full
                break;
            }
            final ItemStack taken = handler.extractItem(slot, amount, false);
            final ItemStack rest = ItemHandlerHelper.insertItemStacked(rack.getItemHandlerCap(), taken, false);
            if (!rest.isEmpty())
            {
                handler.insertItem(slot, rest, false);
            }
            movedAny |= rest.getCount() < taken.getCount();
        }
        if (movedAny)
        {
            hut.markDirty();
            sparkle(level, hut.getPosition(), false);
            trail(level, hut.getPosition(), warehousePos);
            sparkle(level, warehousePos, true);
        }
        return movedAny;
    }

    /**
     * MineColonies only schedules pickups days apart: sweep every worker hut's surplus into the warehouse regularly,
     * so production actually reaches the chest room.
     */
    private static void sweep()
    {
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (!(colony.getWorld() instanceof ServerLevel level))
            {
                continue;
            }
            IBuilding warehouse = null;
            for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
            {
                if (building instanceof IWareHouse)
                {
                    warehouse = building;
                    break;
                }
            }
            if (warehouse == null || !level.isLoaded(warehouse.getPosition()) || !(warehouse.getTileEntity() instanceof TileEntityWareHouse storage))
            {
                continue;
            }
            for (final IBuilding hut : colony.getBuildingManager().getBuildings().values())
            {
                if (hut != warehouse && hut.hasModule(WorkerBuildingModule.class) && level.isLoaded(hut.getPosition()) && hut.getTileEntity() != null)
                {
                    moveSurplus(level, hut, storage, warehouse.getPosition());
                }
            }
        }
    }

    @Nullable
    private static IItemHandler handlerAt(final ServerLevel level, final BlockPos pos)
    {
        final BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof IItemHandlerCapProvider provider ? provider.getItemHandlerCap() : null;
    }

    private static ItemStack extractMatching(final IItemHandler handler, final ItemStack template, final int amount)
    {
        ItemStack collected = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots() && collected.getCount() < amount; slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStackUtils.compareItemStacksIgnoreStackSize(template, stack, true, true))
            {
                continue;
            }
            final ItemStack taken = handler.extractItem(slot, amount - collected.getCount(), false);
            if (collected.isEmpty())
            {
                collected = taken;
            }
            else
            {
                collected.grow(taken.getCount());
            }
        }
        return collected;
    }

    /**
     * Put back what could not be delivered: into its source, else anywhere in the warehouse.
     */
    private static void giveBack(final Transfer transfer, @Nullable final IItemHandler source, final ItemStack stack)
    {
        ItemStack rest = source == null ? stack : ItemHandlerHelper.insertItemStacked(source, stack, false);
        if (!rest.isEmpty())
        {
            final IBuilding warehouse = transfer.colony.getBuildingManager().getBuilding(transfer.warehouse);
            if (warehouse != null && warehouse.getItemHandlerCap() != null)
            {
                rest = ItemHandlerHelper.insertItemStacked(warehouse.getItemHandlerCap(), rest, false);
            }
        }
        if (!rest.isEmpty())
        {
            InterregneMod.LOGGER.warn("Magic network: {} could not be put back and was dropped at the warehouse", rest);
            net.minecraft.world.Containers.dropItemStack(transfer.colony.getWorld(), transfer.warehouse.getX(), transfer.warehouse.getY() + 1, transfer.warehouse.getZ(), rest);
        }
    }

    /**
     * A glowing arc from one building to the other, so players see goods travel along the network.
     */
    private static void trail(final ServerLevel level, final BlockPos from, final BlockPos to)
    {
        final double dx = to.getX() - from.getX();
        final double dy = to.getY() - from.getY();
        final double dz = to.getZ() - from.getZ();
        final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final int points = (int) Math.clamp(length / 1.2, 4, 160);
        final double lift = Math.min(8.0, 1.5 + length / 8);
        for (int i = 1; i < points; i++)
        {
            final double t = (double) i / points;
            // A parabola above the straight line, highest halfway
            final double arc = 4 * t * (1 - t) * lift;
            level.sendParticles(ParticleTypes.END_ROD,
              from.getX() + 0.5 + dx * t, from.getY() + 1.5 + dy * t + arc, from.getZ() + 0.5 + dz * t, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static void sparkle(final ServerLevel level, final BlockPos pos, final boolean arrival)
    {
        level.sendParticles(arrival ? ParticleTypes.END_ROD : ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, arrival ? 6 : 10, 0.3, 0.3, 0.3, 0.02);
        if (arrival)
        {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.4F);
        }
    }
}
