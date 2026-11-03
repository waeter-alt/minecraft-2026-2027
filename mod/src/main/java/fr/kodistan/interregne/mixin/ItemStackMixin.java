package fr.kodistan.interregne.mixin;

import fr.kodistan.interregne.colony.CitizenEquipment;
import fr.kodistan.interregne.gear.DamagedGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Every durability loss of a living entity's item ends up here, MineColonies' own helpers included:
 * skip it for citizens so their equipment never wears out, and stop players' gear at 1 durability instead of breaking it.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin
{
    /**
     * Whether the stack was already abîmé before this durability loss, to warn the player only once.
     */
    @Unique
    private boolean interregne$wasDamaged;

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void interregne$unbreakableForCitizens(final int amount, final ServerLevel level, final LivingEntity entity, final Consumer<Item> onBreak, final CallbackInfo ci)
    {
        final ItemStack self = (ItemStack) (Object) this;
        if (CitizenEquipment.isUnbreakable(entity, self))
        {
            ci.cancel();
            return;
        }
        interregne$wasDamaged = DamagedGear.isDamaged(self);
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), cancellable = true)
    private void interregne$neverBreakForPlayers(final int amount, final ServerLevel level, final LivingEntity entity, final Consumer<Item> onBreak, final CallbackInfo ci)
    {
        final ItemStack self = (ItemStack) (Object) this;
        if (DamagedGear.keepsFromBreaking(entity, self))
        {
            DamagedGear.wearOut(self, entity, interregne$wasDamaged);
            ci.cancel();
        }
    }
}
