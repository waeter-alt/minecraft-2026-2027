package fr.kodistan.interregne.mixin;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.core.placementhandlers.main.SurvivalHandler;
import fr.kodistan.interregne.colony.BuildNow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a hut is placed with the build tool, start its construction if the player chose "Construire maintenant".
 */
@Mixin(value = SurvivalHandler.class, remap = false)
public abstract class SurvivalHandlerMixin
{
    @Inject(method = "handle", at = @At("RETURN"))
    private void interregne$buildNow(
      final Blueprint blueprint,
      final String packName,
      final String blueprintPath,
      final boolean clientPack,
      final Level world,
      final Player player,
      final BlockPos blockPos,
      final RotationMirror rotMir,
      final CallbackInfo ci)
    {
        BuildNow.afterPlacement(world, player, blockPos);
    }
}
