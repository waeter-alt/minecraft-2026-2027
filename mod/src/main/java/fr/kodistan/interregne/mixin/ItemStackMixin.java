package fr.kodistan.interregne.mixin;

import fr.kodistan.interregne.colony.CitizenEquipment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Every durability loss of a living entity's item ends up here, MineColonies' own helpers included:
 * skip it for citizens so their equipment never wears out.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin
{
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void interregne$unbreakableForCitizens(final int amount, final ServerLevel level, final LivingEntity entity, final Consumer<Item> onBreak, final CallbackInfo ci)
    {
        if (CitizenEquipment.isUnbreakable(entity, (ItemStack) (Object) this))
        {
            ci.cancel();
        }
    }
}
