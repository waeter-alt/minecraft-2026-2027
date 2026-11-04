package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenDiseaseHandler;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.workers.CitizenAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import fr.kodistan.interregne.colony.Hunger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Neglect slows a colony down instead of stopping it:
 * <ul>
 * <li>a hungry citizen with nowhere to eat keeps working instead of waiting for food;</li>
 * <li>a sick citizen keeps working and eating instead of wandering in search of a cure. Sickness still spreads and
 * costs speed ({@link fr.kodistan.interregne.colony.Malus}); only the Intendance cures it.</li>
 * </ul>
 */
@Mixin(value = CitizenAI.class, remap = false)
public abstract class CitizenAIMixin
{
    @Shadow
    @Final
    private EntityCitizen citizen;

    @Shadow
    private IState lastState;

    @Redirect(method = "calculateNextState", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/entity/citizen/citizenhandlers/ICitizenDiseaseHandler;isSick()Z"))
    private boolean interregne$workWhileSick(final ICitizenDiseaseHandler disease)
    {
        return false;
    }

    @Inject(method = "shouldEat", at = @At("RETURN"), cancellable = true)
    private void interregne$keepWorkingWhenNoFood(final CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValue() && lastState != CitizenAIState.EATING && Hunger.nowhereToEat(citizen))
        {
            cir.setReturnValue(false);
        }
    }
}
