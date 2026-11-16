package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.network.messages.server.GetColonyInfoMessage;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Opening the founding window of a town hall: a creative player is never sent to the "destroy your old colony"
 * warning, so they can found several colonies (see {@link CreateColonyMessageMixin}).
 */
@Mixin(value = GetColonyInfoMessage.class, remap = false)
public abstract class GetColonyInfoMessageMixin
{
    @Redirect(method = "onExecute", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"))
    private IColony interregne$creativeMayFoundSeveral(final IColonyManager manager, final Level world, final Player sender)
    {
        return sender.isCreative() ? null : manager.getIColonyByOwner(world, sender);
    }
}
