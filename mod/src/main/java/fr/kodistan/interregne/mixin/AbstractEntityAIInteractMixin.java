package fr.kodistan.interregne.mixin;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import fr.kodistan.interregne.colony.Yields;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Miners, quarriers, lumberjacks, farmers, planters and florists all collect their drops through mineBlock:
 * multiply what they gather by their building's level.
 */
@Mixin(value = AbstractEntityAIInteract.class, remap = false)
public abstract class AbstractEntityAIInteractMixin
{
    @Shadow
    protected abstract List<ItemStack> increaseBlockDrops(List<ItemStack> drops);

    @Redirect(
      method = "mineBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;ZZLjava/lang/Runnable;)Z",
      at = @At(value = "INVOKE", target = "Lcom/minecolonies/core/entity/ai/workers/AbstractEntityAIInteract;increaseBlockDrops(Ljava/util/List;)Ljava/util/List;"))
    private List<ItemStack> interregne$boostDrops(final AbstractEntityAIInteract<?, ?> self, final List<ItemStack> drops)
    {
        final AbstractEntityAIBasic<?, ?> ai = (AbstractEntityAIBasic<?, ?>) (Object) this;
        return Yields.boost(ai.building, increaseBlockDrops(drops), ai.building.getColony().getWorld().getRandom());
    }
}
