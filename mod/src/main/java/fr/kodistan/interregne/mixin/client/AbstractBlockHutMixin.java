package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.core.blocks.huts.BlockHutTownHall;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A hut no longer requires its hut block in the inventory: the catalogue and the build tool stop asking for it.
 * The other requirements (being in a colony, research) stay.
 */
@Mixin(value = {AbstractBlockHut.class, BlockHutTownHall.class}, remap = false)
public abstract class AbstractBlockHutMixin
{
    @Redirect(method = "getRequirements", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/util/InventoryUtils;findFirstSlotInItemHandlerWith(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/level/block/Block;)I"))
    private int interregne$noHutBlockNeeded(final IItemHandler handler, final Block block)
    {
        return 0;
    }
}
