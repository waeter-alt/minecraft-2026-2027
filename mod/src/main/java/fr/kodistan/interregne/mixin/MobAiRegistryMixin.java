package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.entity.mobs.registry.MobAIRegistry;
import fr.kodistan.interregne.war.AssaultTroop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every MineColonies raider gets its whole combat kit from MobAIRegistry in the raider constructor: RaiderMeleeAI
 * / RaiderRangedAI (proactive citizen scan + threat table), RaiderWalkAI, door breaking, fence toggling. Our own
 * troops ({@link AssaultTroop}: the légionnaire and the archer) run on plain vanilla goals instead: nothing is
 * attached to them here. Other raiders are untouched.
 */
@Mixin(value = MobAIRegistry.class, remap = false)
public abstract class MobAiRegistryMixin
{
    @Inject(method = "applyToMob", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$skipAssaultTroops(final AbstractEntityMinecoloniesMonster mob, final CallbackInfo ci)
    {
        if (mob instanceof AssaultTroop)
        {
            ci.cancel();
        }
    }
}
