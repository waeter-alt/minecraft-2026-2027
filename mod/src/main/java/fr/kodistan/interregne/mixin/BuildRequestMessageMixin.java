package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.network.messages.server.colony.building.BuildRequestMessage;
import fr.kodistan.interregne.colony.BuildContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remember who clicked "build / upgrade / repair" in a hut window, so the instant builder knows who pays.
 */
@Mixin(value = BuildRequestMessage.class, remap = false)
public abstract class BuildRequestMessageMixin
{
    @Inject(method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;Lcom/minecolonies/api/colony/IColony;Lcom/minecolonies/api/colony/buildings/IBuilding;)V", at = @At("HEAD"))
    private void interregne$begin(final IPayloadContext ctx, final ServerPlayer player, final IColony colony, final IBuilding building, final CallbackInfo ci)
    {
        BuildContext.begin(player);
    }

    @Inject(method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;Lcom/minecolonies/api/colony/IColony;Lcom/minecolonies/api/colony/buildings/IBuilding;)V", at = @At("RETURN"))
    private void interregne$end(final IPayloadContext ctx, final ServerPlayer player, final IColony colony, final IBuilding building, final CallbackInfo ci)
    {
        BuildContext.end();
    }
}
