package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.core.entity.mobs.aitasks.RaiderWalkAI;
import fr.kodistan.interregne.war.AssaultTroop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The stock march hauls raiders toward a getRandomBuilding() pick — during an assault that fights our per-troop
 * assignments: {@link fr.kodistan.interregne.war.AssaultGoal}/{@link fr.kodistan.interregne.war.AssaultRangedGoal}
 * batters the assigned building while this AI drags the troop elsewhere, so pillagers visibly charge one building
 * while another crumbles. Our troops march under their own goal instead; walk() is switched off for them.
 */
@Mixin(value = RaiderWalkAI.class, remap = false)
public abstract class RaiderWalkAiMixin
{
    @Shadow(remap = false)
    private AbstractEntityMinecoloniesRaider raider;

    @Inject(method = "walk", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$assignedMarch(final CallbackInfoReturnable<Boolean> cir)
    {
        if (raider instanceof AssaultTroop)
        {
            // One troop, one building: AssaultGoal/AssaultRangedGoal owns both the march and the battering.
            cir.setReturnValue(false);
        }
    }
}
