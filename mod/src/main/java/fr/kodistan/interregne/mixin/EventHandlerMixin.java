package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.blocks.huts.BlockHutWareHouse;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.core.event.EventHandler;
import fr.kodistan.interregne.warehouse.MagicNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * One warehouse per colony: the Salle des Coffres is the colony's single store, and the magic network
 * serves from one place only (with several, deliveries could come up short).
 */
@Mixin(value = EventHandler.class, remap = false)
public abstract class EventHandlerMixin
{
    @Inject(method = "onBlockHutPlaced(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;)Z",
      at = @At("HEAD"), cancellable = true)
    private static void interregne$singleWarehouse(final Level world, final Player player, final Block block, final BlockPos pos, final CallbackInfoReturnable<Boolean> cir)
    {
        if (!(block instanceof BlockHutWareHouse) || !MagicNetwork.enabled())
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getIColony(world, pos);
        if (colony == null)
        {
            return;
        }
        final boolean another = colony.getBuildingManager().getBuildings().values().stream()
                                  .anyMatch(building -> building instanceof BuildingWareHouse && !building.getPosition().equals(pos));
        if (another)
        {
            player.sendSystemMessage(Component.literal("Ta colonie a déjà un entrepôt : il ne peut y en avoir qu'un.").withStyle(ChatFormatting.RED));
            cir.setReturnValue(false);
        }
    }
}
