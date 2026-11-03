package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.core.colony.buildings.modules.EnchanterStationsModule;
import fr.kodistan.interregne.colony.AutoEnchanter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

/**
 * The enchanter drains every worker hut of its colony on its own: no list of buildings to pick by hand.
 */
@Mixin(value = EnchanterStationsModule.class, remap = false)
public abstract class EnchanterStationsModuleMixin
{
    @Shadow
    private Map<BlockPos, Boolean> buildingToGatherFrom;

    @Inject(method = "getBuildingsToGatherFrom", at = @At("HEAD"))
    private void interregne$fillBeforeList(final CallbackInfoReturnable<Set<BlockPos>> cir)
    {
        interregne$fill();
    }

    @Inject(method = "getRandomBuildingToDrainFrom", at = @At("HEAD"))
    private void interregne$fillBeforePick(final CallbackInfoReturnable<BlockPos> cir)
    {
        interregne$fill();
    }

    private void interregne$fill()
    {
        final IBuilding enchanter = ((IBuildingModule) (Object) this).getBuilding();
        if (enchanter != null)
        {
            AutoEnchanter.addAllWorkerHuts(enchanter, buildingToGatherFrom);
        }
    }
}
