package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import fr.kodistan.interregne.colony.AutoTools;
import fr.kodistan.interregne.colony.Hunger;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import fr.kodistan.interregne.colony.Yields;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every worker paces its actions through setDelay: shorten the waits as the building levels up,
 * lengthen them when the worker is hungry. Tools are issued before the worker checks for them.
 */
@Mixin(value = AbstractEntityAIBasic.class, remap = false)
public abstract class AbstractEntityAIBasicMixin
{
    @Inject(method = "checkForToolOrWeapon(Lcom/minecolonies/api/equipment/registry/EquipmentTypeEntry;I)Z", at = @At("HEAD"))
    private void interregne$issueTool(final EquipmentTypeEntry toolType, final int minimalLevel, final CallbackInfoReturnable<Boolean> cir)
    {
        final AbstractEntityAIBasic<?, ?> ai = (AbstractEntityAIBasic<?, ?>) (Object) this;
        AutoTools.equip(((AbstractAISkeletonAccessor) ai).interregne$getWorker(), ai.building, toolType, minimalLevel);
    }

    @ModifyVariable(method = "setDelay", at = @At("HEAD"), argsOnly = true)
    private int interregne$workPace(final int timeout)
    {
        if (timeout <= 1)
        {
            return timeout;
        }
        final AbstractEntityAIBasic<?, ?> ai = (AbstractEntityAIBasic<?, ?>) (Object) this;
        final AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) ai).interregne$getWorker();
        final double pace = Yields.speedFor(ai.building) / Hunger.slowdown(worker == null ? null : worker.getCitizenData());
        return pace == 1.0 ? timeout : Math.max(1, (int) Math.round(timeout / pace));
    }
}
