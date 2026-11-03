package fr.kodistan.interregne.mixin;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFisherman;
import fr.kodistan.interregne.colony.Yields;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The fisherman's bonus loot (treasure from the fishery's loot table) scales with the level like the main catch.
 */
@Mixin(value = EntityAIWorkFisherman.class, remap = false)
public abstract class EntityAIWorkFishermanMixin
{
    @Redirect(method = "generateBonusLoot", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> interregne$boostBonus(final LootTable table, final LootParams params)
    {
        final AbstractEntityAIBasic<?, ?> ai = (AbstractEntityAIBasic<?, ?>) (Object) this;
        return new ObjectArrayList<>(Yields.boost(ai.building, table.getRandomItems(params), ai.building.getColony().getWorld().getRandom()));
    }
}
