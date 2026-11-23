package fr.kodistan.interregne.mixin;

import com.minecolonies.core.entity.ai.combat.TargetAI;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TargetAI.class, remap = false)
public interface TargetAIAccessor
{
    @Accessor("user")
    Mob interregne$user();
}
