package fr.kodistan.interregne.mixin;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.core.placementhandlers.main.SurvivalHandler;
import fr.kodistan.interregne.colony.BuildNow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Huts placed with the build tool need no hut block: the catalogue's buildings are paid in Couronnes. MineColonies'
 * inventory check passes as in creative and nothing is taken from the inventory. After the placement, start the
 * construction if the player chose "Construire maintenant".
 */
@Mixin(value = SurvivalHandler.class, remap = false)
public abstract class SurvivalHandlerMixin
{
    @Redirect(method = "handle", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/util/InventoryUtils;findFirstSlotInItemHandlerWith(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/level/block/Block;)I"))
    private int interregne$noHutBlockSlot(final IItemHandler handler, final Block block)
    {
        return -1;
    }

    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isCreative()Z"))
    private boolean interregne$noHutBlockNeeded(final Player player)
    {
        return true;
    }

    @Redirect(method = "handle", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/util/InventoryUtils;reduceStackInItemHandler(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void interregne$keepInventory(final IItemHandler handler, final ItemStack stack, final int quantity)
    {
        // No hut block is consumed
    }

    @Inject(method = "handle", at = @At("RETURN"))
    private void interregne$buildNow(
      final Blueprint blueprint,
      final String packName,
      final String blueprintPath,
      final boolean clientPack,
      final Level world,
      final Player player,
      final BlockPos blockPos,
      final RotationMirror rotMir,
      final CallbackInfo ci)
    {
        BuildNow.afterPlacement(world, player, blockPos);
    }
}
