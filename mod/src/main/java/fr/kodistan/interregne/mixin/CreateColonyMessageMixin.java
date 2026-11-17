package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.network.messages.server.CreateColonyMessage;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MineColonies lets a player own a single colony. A creative player (admin / test) may found as many as they like,
 * to link colonies together and test the routes between them.
 */
@Mixin(value = CreateColonyMessage.class, remap = false)
public abstract class CreateColonyMessageMixin
{
    @Redirect(method = "onExecute", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"))
    private IColony interregne$creativeMayFoundSeveral(final IColonyManager manager, final Level world, final Player sender)
    {
        return sender.isCreative() ? null : manager.getIColonyByOwner(world, sender);
    }
}
