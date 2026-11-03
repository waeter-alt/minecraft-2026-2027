package fr.kodistan.interregne.mixin;

import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import fr.kodistan.interregne.economy.CoinItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Couronnes belong in the Imperial Bank: MineColonies racks and hut inventories refuse them,
 * whether put in by hand, by a hopper or by the magic network.
 */
@Mixin(value = ItemStackHandler.class, remap = false)
public abstract class ItemStackHandlerMixin
{
    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void interregne$noCoinsInRacks(final int slot, final ItemStack stack, final CallbackInfoReturnable<Boolean> cir)
    {
        if (stack.getItem() instanceof CoinItem && (Object) this instanceof AbstractTileEntityRack.RackInventory)
        {
            cir.setReturnValue(false);
        }
    }
}
