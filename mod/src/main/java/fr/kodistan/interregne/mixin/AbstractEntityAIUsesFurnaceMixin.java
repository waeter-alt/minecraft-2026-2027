package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkCook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The restaurant cooks without fuel (Kitchen cooks its raw food on the spot): its cook never asks for any.
 * Smelters and bakers keep needing fuel.
 */
@Mixin(value = AbstractEntityAIUsesFurnace.class, remap = false)
public abstract class AbstractEntityAIUsesFurnaceMixin
{
    @Redirect(method = "startWorking", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/colony/ICitizenData;createRequestAsync(Lcom/minecolonies/api/colony/requestsystem/requestable/IRequestable;)Lcom/minecolonies/api/colony/requestsystem/token/IToken;"))
    private IToken<?> interregne$noFuelForCooks(final ICitizenData citizen, final IRequestable requested)
    {
        if ((Object) this instanceof EntityAIWorkCook)
        {
            return null;
        }
        return citizen.createRequestAsync(requested);
    }
}
