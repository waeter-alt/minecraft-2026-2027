package fr.kodistan.interregne.mixin;

import com.minecolonies.core.colony.managers.CitizenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The first citizen no longer carries MineColonies' tutorial quest (the yellow "!" above their head):
 * our own dialogue replaces it, and the vanilla one would stay there forever.
 * See also {@link QuestJsonListenerMixin}, which removes the tutorial quests themselves.
 */
@Mixin(value = CitizenManager.class, remap = false)
public abstract class CitizenManagerMixin
{
    // firstCitizen is only read to inject "tutorial/welcome"; forcing it to false skips that and nothing else.
    @ModifyVariable(method = "onColonyTick", at = @At(value = "STORE"), ordinal = 0, remap = false)
    private boolean interregne$noTutorialQuest(final boolean firstCitizen)
    {
        return false;
    }
}
