package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.guard.KnightCombatAI;
import fr.kodistan.interregne.garrison.Garrison;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A légionnaire always strikes, with no sword, for the fixed damage of its troop level.
 */
@Mixin(value = KnightCombatAI.class, remap = false)
public abstract class KnightCombatAIMixin
{
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void interregne$bareHanded(final CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(true);
    }

    @Inject(method = "getAttackDamage", at = @At("HEAD"), cancellable = true)
    private void interregne$troopDamage(final CallbackInfoReturnable<Double> cir)
    {
        final ICitizenData citizen = ((AbstractEntityCitizen) ((TargetAIAccessor) this).interregne$user()).getCitizenData();
        if (citizen != null)
        {
            cir.setReturnValue(Garrison.meleeDamage(Garrison.levelOf(citizen.getColony(), Garrison.LEGIONNAIRE)));
        }
    }
}
