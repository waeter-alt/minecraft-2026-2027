package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Guards are bought at the combat academy and the archery: towers no longer take jobless citizens or trainees.
 */
@Mixin(value = GuardBuildingModule.class, remap = false)
public abstract class GuardBuildingModuleMixin
{
    @Inject(method = "onColonyTick", at = @At("HEAD"), cancellable = true)
    private void interregne$noAutoHire(final IColony colony, final CallbackInfo ci)
    {
        ci.cancel();
    }

    /**
     * An ally's contingent takes no place: the colony can still buy its own guards while it is there.
     */
    @Inject(method = "isFull", at = @At("HEAD"), cancellable = true)
    private void interregne$guestsTakeNoPlace(final CallbackInfoReturnable<Boolean> cir)
    {
        final GuardBuildingModule module = (GuardBuildingModule) (Object) this;
        if (module.getBuilding() == null)
        {
            return;
        }
        int own = 0;
        for (final ICitizenData citizen : module.getBuilding().getAllAssignedCitizen())
        {
            own += fr.kodistan.interregne.politics.AllyAid.isGuest(citizen) ? 0 : 1;
        }
        cir.setReturnValue(own >= module.getModuleMax());
    }
}
