package fr.kodistan.interregne.mixin;

import com.minecolonies.core.network.messages.server.DecorationBuildRequestMessage;
import fr.kodistan.interregne.colony.BuildContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remember who requested a decoration (walls, roads...), so the instant builder knows who pays.
 */
@Mixin(value = DecorationBuildRequestMessage.class, remap = false)
public abstract class DecorationBuildRequestMessageMixin
{
    @Inject(method = "onExecute", at = @At("HEAD"))
    private void interregne$begin(final IPayloadContext ctx, final ServerPlayer player, final CallbackInfo ci)
    {
        BuildContext.begin(player);
    }

    @Inject(method = "onExecute", at = @At("RETURN"))
    private void interregne$end(final IPayloadContext ctx, final ServerPlayer player, final CallbackInfo ci)
    {
        BuildContext.end();
    }
}
