package fr.kodistan.interregne.mixin;

import fr.kodistan.interregne.colony.ColonyColors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A colony member's glowing outline takes their colony's color. The outline reads {@code getTeamColor}, which only
 * knows the 16 vanilla team colors; colony colors are RGB (see {@link ColonyColors}).
 */
@Mixin(value = Entity.class, remap = false)
public abstract class PlayerTeamColorMixin
{
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$colonyColor(final CallbackInfoReturnable<Integer> cir)
    {
        if ((Object) this instanceof Player player)
        {
            final Integer color = ColonyColors.of(player);
            if (color != null)
            {
                cir.setReturnValue(color);
            }
        }
    }
}
