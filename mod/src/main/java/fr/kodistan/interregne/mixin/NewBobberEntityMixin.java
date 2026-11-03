package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.other.NewBobberEntity;
import fr.kodistan.interregne.colony.Yields;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The fisherman's main catch is rolled when the bobber is reeled in: multiply it by the fishery's level.
 */
@Mixin(value = NewBobberEntity.class, remap = false)
public abstract class NewBobberEntityMixin
{
    @Redirect(method = "retrieve", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> interregne$boostCatch(final LootTable table, final LootParams params)
    {
        final ObjectArrayList<ItemStack> catches = table.getRandomItems(params);
        final NewBobberEntity bobber = (NewBobberEntity) (Object) this;
        if (!(bobber.getOwner() instanceof AbstractEntityCitizen citizen) || citizen.getCitizenData() == null)
        {
            return catches;
        }
        return new ObjectArrayList<>(Yields.boost(citizen.getCitizenData().getWorkBuilding(), catches, bobber.getRandom()));
    }
}
