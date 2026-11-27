package fr.kodistan.interregne.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.blocks.huts.BlockHutTownHall;
import com.minecolonies.core.network.messages.server.GetColonyInfoMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Placing a town hall with the build tool opens the founding window at once: the player no longer has to walk to the
 * hut block (sometimes buried in the blueprint) and click it. The same server checks as the click run (spawn
 * distance, other colonies, already owning one), so a refused spot still shows MineColonies' warning.
 * <p>
 * Creative placements go through {@code setup}, survival ones through {@code onBlockPlacedByBuildTool}: both are hooked.
 */
@Mixin(value = AbstractBlockHut.class, remap = false)
public abstract class TownHallPlacementMixin
{
    @Inject(method = "setup", at = @At("RETURN"))
    private void interregne$foundRightAway(final ServerPlayer player, final Level world, final BlockPos pos, final Blueprint blueprint,
      final RotationMirror rotationMirror, final boolean fancyPlacement, final String pack, final String path, final CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ())
        {
            interregne$openFounding(player, world, pos);
        }
    }

    @Inject(method = "onBlockPlacedByBuildTool", at = @At("RETURN"))
    private void interregne$foundRightAwaySurvival(final Level world, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack,
      final RotationMirror rotationMirror, final String pack, final String path, final CallbackInfo ci)
    {
        if (placer instanceof ServerPlayer player)
        {
            interregne$openFounding(player, world, pos);
        }
    }

    private static void interregne$openFounding(final ServerPlayer player, final Level world, final BlockPos pos)
    {
        if (world.isClientSide || !(world.getBlockState(pos).getBlock() instanceof BlockHutTownHall) || IColonyManager.getInstance().getBuilding(world, pos) != null)
        {
            return;
        }
        ((GetColonyInfoMessageInvoker) new GetColonyInfoMessage(pos)).interregne$execute(null, player);
    }
}
