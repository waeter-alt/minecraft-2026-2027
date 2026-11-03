package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.core.colony.requestsystem.resolvers.DeliverymenRequestResolver;
import fr.kodistan.interregne.warehouse.MagicNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every delivery and pickup goes through the warehouse's courier resolver: no courier needed, the magic network
 * carries it instead.
 */
@Mixin(value = DeliverymenRequestResolver.class, remap = false)
public abstract class DeliverymenRequestResolverMixin
{
    @Inject(method = "hasCouriers", at = @At("HEAD"), cancellable = true)
    private void interregne$noCouriersNeeded(final IRequestManager manager, final CallbackInfoReturnable<Boolean> cir)
    {
        if (MagicNetwork.enabled())
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(
      method = "resolveRequest(Lcom/minecolonies/api/colony/requestsystem/manager/IRequestManager;Lcom/minecolonies/api/colony/requestsystem/request/IRequest;)V",
      at = @At("HEAD"),
      cancellable = true)
    private void interregne$magicTransfer(final IRequestManager manager, final IRequest<?> request, final CallbackInfo ci)
    {
        if (MagicNetwork.enabled())
        {
            ci.cancel();
            MagicNetwork.schedule(manager, request, ((DeliverymenRequestResolver<?>) (Object) this).getLocation().getInDimensionLocation());
        }
    }
}
