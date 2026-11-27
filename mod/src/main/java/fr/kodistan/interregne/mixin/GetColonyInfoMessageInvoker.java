package fr.kodistan.interregne.mixin;

import com.minecolonies.core.network.messages.server.GetColonyInfoMessage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Runs the town hall click's server side without the click (see {@link TownHallPlacementMixin}). */
@Mixin(value = GetColonyInfoMessage.class, remap = false)
public interface GetColonyInfoMessageInvoker
{
    @Invoker("onExecute")
    void interregne$execute(IPayloadContext ctx, ServerPlayer sender);
}
