package fr.kodistan.interregne.mixin;

import com.minecolonies.api.inventory.InventoryCitizen;
import fr.kodistan.interregne.colony.CitizenItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Citizens only carry items from allowed mods: ground pickup, deliveries, requests, automation and the citizen GUI
 * all end in insertItem, and the armor slots of the GUI write through forceArmorStackToSlot.
 */
@Mixin(value = InventoryCitizen.class, remap = false)
public abstract class InventoryCitizenMixin
{
    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void interregne$refuseForeignItems(final int slot, final ItemStack stack, final boolean simulate, final CallbackInfoReturnable<ItemStack> cir)
    {
        if (!CitizenItems.allowed(stack))
        {
            cir.setReturnValue(stack);
        }
    }

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void interregne$noForeignItemSlots(final int slot, final ItemStack stack, final CallbackInfoReturnable<Boolean> cir)
    {
        if (!CitizenItems.allowed(stack))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "forceArmorStackToSlot", at = @At("HEAD"), cancellable = true)
    private void interregne$noForeignArmor(final EquipmentSlot slot, final ItemStack stack, final CallbackInfo ci)
    {
        if (!CitizenItems.allowed(stack))
        {
            ci.cancel();
        }
    }
}
