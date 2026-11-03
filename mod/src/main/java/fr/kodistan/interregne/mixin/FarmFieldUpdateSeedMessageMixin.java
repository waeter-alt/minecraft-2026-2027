package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldUpdateSeedMessage;
import fr.kodistan.interregne.production.Farms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A scarecrow field can only be sown with the crop of one of the colony's farms, and is planted ripe at once.
 */
@Mixin(value = FarmFieldUpdateSeedMessage.class, remap = false)
public abstract class FarmFieldUpdateSeedMessageMixin
{
    @Shadow
    @Final
    private ItemStack newSeed;

    @Shadow
    @Final
    private BlockPos position;

    @Inject(method = "onExecute", at = @At("HEAD"), cancellable = true)
    private void interregne$onlyFarmCrops(final IPayloadContext context, final ServerPlayer player, final IColony colony, final CallbackInfo ci)
    {
        if (!Farms.allowSeed(player, colony, newSeed))
        {
            ci.cancel();
        }
    }

    @Inject(method = "onExecute", at = @At("TAIL"))
    private void interregne$plantRipe(final IPayloadContext context, final ServerPlayer player, final IColony colony, final CallbackInfo ci)
    {
        colony.getBuildingManager()
          .getMatchingBuildingExtension(extension -> extension instanceof FarmField && extension.getPosition().equals(position))
          .ifPresent(field -> Farms.plantField((ServerLevel) colony.getWorld(), (FarmField) field));
    }
}
