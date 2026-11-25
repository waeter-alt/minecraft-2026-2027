package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.managers.interfaces.ICitizenManager;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import fr.kodistan.interregne.garrison.GarrisonRoster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bought guards live in the towers, outside the population: they do not take a tavern visitor's place.
 */
@Mixin(value = RecruitmentInteraction.class, remap = false)
public abstract class RecruitmentInteractionMixin
{
    @Redirect(method = "onServerResponseTriggered",
      at = @At(value = "INVOKE", target = "Lcom/minecolonies/api/colony/managers/interfaces/ICitizenManager;getCurrentCitizenCount()I"))
    private int interregne$withoutGarrison(final ICitizenManager manager, final int responseId, final Player player, final ICitizenData data)
    {
        return Math.max(0, manager.getCurrentCitizenCount() - GarrisonRoster.countIn(data.getColony()));
    }
}
