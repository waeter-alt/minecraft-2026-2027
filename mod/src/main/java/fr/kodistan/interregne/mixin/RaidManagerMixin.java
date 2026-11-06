package fr.kodistan.interregne.mixin;

import com.minecolonies.core.colony.events.raid.RaidManager;
import fr.kodistan.interregne.war.WarAssaults;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The stock raider march targets RANDOM buildings, re-picking often: troops piled onto the warehouse and looped
 * on it. During an assault, WarAssaults hands out buildings round-robin instead — spread, destroyed ones skipped,
 * so each blow lands somewhere new.
 */
@Mixin(value = RaidManager.class, remap = false)
public abstract class RaidManagerMixin
{
    @Shadow(remap = false)
    private com.minecolonies.core.colony.Colony colony;

    @Inject(method = "getRandomBuilding", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$spreadTargets(final CallbackInfoReturnable<BlockPos> cir)
    {
        final BlockPos assigned = WarAssaults.pickTarget(colony);
        if (assigned != null)
        {
            cir.setReturnValue(assigned);
        }
    }
}
