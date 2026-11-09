package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.entity.mobs.registry.MobAIRegistry;
import fr.kodistan.interregne.war.EntLegionnaire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every MineColonies raider gets its whole combat kit from MobAIRegistry in the raider constructor: RaiderMeleeAI
 * (proactive citizen scan + threat table), RaiderWalkAI, door breaking, fence toggling. Légionnaires run on plain
 * vanilla goals instead ({@link EntLegionnaire}): nothing is attached to them here. Other raiders are untouched.
 */
@Mixin(value = MobAIRegistry.class, remap = false)
public abstract class MobAiRegistryMixin
{
    @Inject(method = "applyToMob", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$skipLegionnaires(final AbstractEntityMinecoloniesMonster mob, final CallbackInfo ci)
    {
        if (mob instanceof EntLegionnaire)
        {
            ci.cancel();
        }
    }
}
