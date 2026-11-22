package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.managers.GraveManager;
import fr.kodistan.interregne.garrison.BoughtGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No grave (hence no funeral by the undertaker): the guard's gear drops on the spot like any non-grave death. */
@Mixin(value = GraveManager.class, remap = false)
public abstract class GuardGraveMixin
{
    @Inject(method = "createCitizenGrave", at = @At("HEAD"), cancellable = true)
    private void interregne$noGrave(final Level level, final BlockPos pos, final ICitizenData data, final CallbackInfoReturnable<Boolean> cir)
    {
        if (BoughtGuards.is(data))
        {
            cir.setReturnValue(false);
        }
    }
}
