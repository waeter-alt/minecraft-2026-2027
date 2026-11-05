package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import fr.kodistan.interregne.colony.ConstructionManager;
import fr.kodistan.interregne.walls.Walls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Our sites never enter MineColonies' work order queue, so a building's view would never know it is being built:
 * report a running site as its work order.
 */
@Mixin(value = AbstractBuilding.class, remap = false)
public abstract class AbstractBuildingMixin
{
    @Inject(method = "getCurrentWorkOrderLevel", at = @At("HEAD"), cancellable = true)
    private void interregne$ourSite(final CallbackInfoReturnable<Integer> cir)
    {
        final Integer level = ConstructionManager.siteLevel((IBuilding) this);
        if (level != null)
        {
            cir.setReturnValue(level);
        }
    }

    /**
     * MineColonies hugs its border to the buildings (1 chunk, 2 at levels 4-5): no room left to trace a wall
     * between the houses and the limit. Every building claims Walls.BUILDING_CLAIM_BONUS chunks further out.
     */
    @Inject(method = "getClaimRadius", at = @At("HEAD"), cancellable = true)
    private void interregne$roomierBorder(final int newLevel, final CallbackInfoReturnable<Integer> cir)
    {
        cir.setReturnValue(newLevel <= 0 ? 0 : newLevel >= 4 ? Walls.BUILDING_CLAIM_BONUS + 2 : Walls.BUILDING_CLAIM_BONUS + 1);
    }
}
