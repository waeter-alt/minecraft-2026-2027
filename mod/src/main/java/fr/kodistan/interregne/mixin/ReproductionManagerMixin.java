package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.managers.interfaces.ICitizenManager;
import com.minecolonies.core.colony.managers.ReproductionManager;
import fr.kodistan.interregne.garrison.GarrisonRoster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bought guards live in the towers, outside the population: they do not fill the room children need to be born.
 */
@Mixin(value = ReproductionManager.class, remap = false)
public abstract class ReproductionManagerMixin
{
    @Shadow
    @Final
    private com.minecolonies.core.colony.Colony colony;

    @Redirect(method = "trySpawnChild",
      at = @At(value = "INVOKE", target = "Lcom/minecolonies/api/colony/managers/interfaces/ICitizenManager;getCurrentCitizenCount()I"))
    private int interregne$withoutGarrison(final ICitizenManager manager)
    {
        return Math.max(0, manager.getCurrentCitizenCount() - GarrisonRoster.countIn(colony));
    }
}
