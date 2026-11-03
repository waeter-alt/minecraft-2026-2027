package fr.kodistan.interregne.warehouse;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.core.tileentities.TileEntityWareHouse;
import fr.kodistan.interregne.economy.CoinItem;
import fr.kodistan.interregne.network.TerminalDepositPayload;
import fr.kodistan.interregne.network.TerminalWithdrawPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the warehouse register: withdraw and deposit from inside the warehouse, for colony members only.
 * The warehouse stays the place to go: the register only works for a player standing inside it.
 */
public final class Terminal
{
    /**
     * Main inventory slots, the hotbar (0-8) is never emptied.
     */
    private static final int FIRST_MAIN_SLOT = 9;
    private static final int LAST_MAIN_SLOT  = 35;

    private Terminal()
    {
    }

    public static void onWithdraw(final TerminalWithdrawPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final BuildingWareHouse warehouse = access(player, payload.warehouse());
        final IItemHandler storage = warehouse == null ? null : warehouse.getItemHandlerCap();
        if (storage == null)
        {
            return;
        }

        int left = Math.clamp(payload.amount(), 1, payload.item().getMaxStackSize());
        int taken = 0;
        for (int slot = 0; slot < storage.getSlots() && left > 0; slot++)
        {
            if (!ItemStack.isSameItemSameComponents(storage.getStackInSlot(slot), payload.item()))
            {
                continue;
            }
            final ItemStack out = storage.extractItem(slot, left, false);
            left -= out.getCount();
            taken += out.getCount();
            if (!player.getInventory().add(out))
            {
                player.drop(out, false);
            }
        }
        if (taken > 0)
        {
            player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.2F);
        }
    }

    public static void onDeposit(final TerminalDepositPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final BuildingWareHouse warehouse = access(player, payload.warehouse());
        if (warehouse == null || !(warehouse.getTileEntity() instanceof TileEntityWareHouse tile))
        {
            return;
        }

        final Inventory inventory = player.getInventory();
        int stored = 0;
        boolean full = false;
        for (int slot = FIRST_MAIN_SLOT; slot <= LAST_MAIN_SLOT; slot++)
        {
            final ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() instanceof CoinItem)
            {
                // Couronnes go to the Imperial Bank, not the warehouse
                continue;
            }
            final AbstractTileEntityRack rack = tile.getRackForStack(stack);
            if (rack == null)
            {
                full = true;
                break;
            }
            final ItemStack rest = ItemHandlerHelper.insertItemStacked(rack.getItemHandlerCap(), stack.copy(), false);
            stored += stack.getCount() - rest.getCount();
            inventory.setItem(slot, rest);
        }
        inventory.setChanged();

        player.sendSystemMessage(Component.literal(stored + " objets rangés dans l'entrepôt" + (full ? " — il est plein !" : "."))
                                   .withStyle(full ? ChatFormatting.RED : ChatFormatting.GREEN));
        if (stored > 0)
        {
            player.playNotifySound(SoundEvents.BARREL_CLOSE, SoundSource.PLAYERS, 0.6F, 1.1F);
        }
    }

    /**
     * The warehouse at this position, if the player is a member of its colony standing inside it.
     */
    @Nullable
    private static BuildingWareHouse access(final ServerPlayer player, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), pos);
        if (colony == null || !(colony.getBuildingManager().getBuilding(pos) instanceof BuildingWareHouse warehouse))
        {
            return null;
        }
        if (!colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            player.sendSystemMessage(Component.literal("Ce registre appartient à une autre colonie.").withStyle(ChatFormatting.RED));
            return null;
        }
        final AABB inside = AABB.encapsulatingFullBlocks(warehouse.getCorners().getA(), warehouse.getCorners().getB()).inflate(1);
        if (!inside.contains(player.position()))
        {
            player.sendSystemMessage(Component.literal("Il faut être dans l'entrepôt pour utiliser son registre.").withStyle(ChatFormatting.RED));
            return null;
        }
        return warehouse;
    }
}
