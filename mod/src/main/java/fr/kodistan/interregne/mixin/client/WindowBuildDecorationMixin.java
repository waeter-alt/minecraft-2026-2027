package fr.kodistan.interregne.mixin.client;

import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.client.gui.WindowBuildDecoration;
import fr.kodistan.interregne.client.BuildWindowPricing;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Future;

/**
 * Replace the decoration window's builder material list with its price by size (Couronnes and crates).
 */
@Mixin(value = WindowBuildDecoration.class, remap = false)
public abstract class WindowBuildDecorationMixin
{
    @Shadow
    private Future<Blueprint> blueprintFuture;

    @Shadow
    @Final
    private BlockPos structurePos;

    @Inject(method = "updateResourceList", at = @At("HEAD"), cancellable = true)
    private void interregne$showPrice(final CallbackInfo ci)
    {
        if (!BuildWindowPricing.enabled() || blueprintFuture == null || !blueprintFuture.isDone())
        {
            return;
        }
        try
        {
            final Blueprint blueprint = blueprintFuture.get();
            if (blueprint == null)
            {
                return;
            }
            final long blocks = blueprint.getBlockInfoAsList().stream().filter(info -> !info.getState().isAir()).count();
            ci.cancel();
            BuildWindowPricing.applyDecoration((BOWindow) (Object) this, blocks,
              IColonyManager.getInstance().getClosestColonyView(Minecraft.getInstance().level, structurePos));
        }
        catch (final Exception e)
        {
            // Keep MineColonies' own list
        }
    }
}
