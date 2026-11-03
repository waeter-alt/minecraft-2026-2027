package fr.kodistan.interregne.mixin;

import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkBeekeeper;
import com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkShepherd;
import fr.kodistan.interregne.colony.Yields;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Beekeepers (combs, honey) and shepherds (wool) put their harvest straight into their inventory:
 * multiply each harvested stack on the way in.
 */
@Mixin(value = {EntityAIWorkBeekeeper.class, EntityAIWorkShepherd.class}, remap = false)
public abstract class HarvestTransferMixin
{
    @Redirect(method = {"harvestHoney", "shearSheep"}, require = 1, at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/util/InventoryUtils;transferItemStackIntoNextBestSlotInItemHandler(Lnet/minecraft/world/item/ItemStack;Lnet/neoforged/neoforge/items/IItemHandler;)Z"))
    private boolean interregne$boostHarvest(final ItemStack stack, final IItemHandler inventory)
    {
        final AbstractEntityAIBasic<?, ?> ai = (AbstractEntityAIBasic<?, ?>) (Object) this;
        final List<ItemStack> boosted = Yields.boost(ai.building, List.of(stack), ai.building.getColony().getWorld().getRandom());
        boolean all = true;
        for (final ItemStack part : boosted)
        {
            all &= InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(part, inventory);
        }
        return all;
    }
}
