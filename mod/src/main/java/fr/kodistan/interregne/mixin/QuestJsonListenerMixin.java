package fr.kodistan.interregne.mixin;

import com.minecolonies.api.quests.IQuestManager;
import com.minecolonies.core.datalistener.QuestJsonListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops MineColonies' tutorial quests ("tutorial/*") once the quest data is loaded. Colonies from older saves
 * that already hold "tutorial/welcome" lose it too: MineColonies discards any quest whose template is gone.
 * The "guides/*" quests only start after a tutorial quest is finished, so they never fire either.
 */
@Mixin(value = QuestJsonListener.class, remap = false)
public abstract class QuestJsonListenerMixin
{
    @Inject(method = "apply(Lnet/minecraft/core/HolderLookup$Provider;Ljava/util/Map;)V", at = @At("TAIL"), remap = false)
    private static void interregne$dropTutorial(final CallbackInfo ci)
    {
        IQuestManager.GLOBAL_SERVER_QUESTS.keySet().removeIf(id -> id.getPath().startsWith("tutorial/"));
    }
}
