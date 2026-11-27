package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.network.messages.server.CreateColonyMessage;
import fr.kodistan.interregne.colony.BuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MineColonies lets a player own a single colony. A creative player (admin / test) may found as many as they like,
 * to link colonies together and test the routes between them.
 * <p>
 * Founding also orders the town hall's first level, as if the player had clicked "Build" in its window, so the
 * colony starts without a trip back to the hut block.
 */
@Mixin(value = CreateColonyMessage.class, remap = false)
public abstract class CreateColonyMessageMixin
{
    @Shadow
    @Final
    private BlockPos townHall;

    @Redirect(method = "onExecute", at = @At(value = "INVOKE",
      target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"))
    private IColony interregne$creativeMayFoundSeveral(final IColonyManager manager, final Level world, final Player sender)
    {
        return sender.isCreative() ? null : manager.getIColonyByOwner(world, sender);
    }

    /** Right before the town hall window opens, so it already shows the work under way. */
    @Inject(method = "onExecute", at = @At(value = "NEW", target = "com/minecolonies/core/network/messages/client/colony/OpenBuildingUIMessage"))
    private void interregne$buildTownHall(final IPayloadContext ctx, final ServerPlayer sender, final CallbackInfo ci)
    {
        final IBuilding building = IColonyManager.getInstance().getBuilding(sender.level(), townHall);
        if (building == null || building.getBuildingLevel() > 0 || building.hasWorkOrder())
        {
            return;
        }
        BuildContext.begin(sender);
        try
        {
            building.requestUpgrade(sender, BlockPos.ZERO);
        }
        finally
        {
            BuildContext.end();
        }
    }
}
