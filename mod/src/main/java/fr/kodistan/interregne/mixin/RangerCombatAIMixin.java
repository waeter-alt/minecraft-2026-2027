package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.guard.RangerCombatAI;
import fr.kodistan.interregne.garrison.Garrison;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An archère always shoots, with no bow, arrows of the fixed damage of its troop level.
 */
@Mixin(value = RangerCombatAI.class, remap = false)
public abstract class RangerCombatAIMixin
{
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void interregne$noBow(final CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(true);
    }

    @Inject(method = "calculateDamage", at = @At("HEAD"), cancellable = true)
    private void interregne$troopDamage(final AbstractArrow arrow, final CallbackInfoReturnable<Double> cir)
    {
        final ICitizenData citizen = ((AbstractEntityCitizen) ((TargetAIAccessor) this).interregne$user()).getCitizenData();
        if (citizen != null)
        {
            cir.setReturnValue(Garrison.arrowDamage(Garrison.levelOf(citizen.getColony(), Garrison.ARCHERE)));
        }
    }
}
