package fr.kodistan.interregne.mixin;

import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIFight;
import com.minecolonies.core.entity.ai.workers.guard.EntityAIKnight;
import com.minecolonies.core.entity.ai.workers.guard.EntityAIRanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Légionnaires and archères fight bare-handed at fixed stats: with empty weapon and gear lists they never ask for, wait
 * for or fetch equipment. The per-level gear lists stay in place (empty) because MineColonies indexes them by hut level.
 */
@Mixin(value = {EntityAIKnight.class, EntityAIRanger.class}, remap = false)
public abstract class GuardGearlessMixin
{
    @Inject(method = "<init>", at = @At("RETURN"))
    private void interregne$noGear(final CallbackInfo ci)
    {
        final AbstractEntityAIFight<?, ?> ai = (AbstractEntityAIFight<?, ?>) (Object) this;
        ai.toolsNeeded.clear();
        ai.itemsNeeded.forEach(List::clear);
    }
}
