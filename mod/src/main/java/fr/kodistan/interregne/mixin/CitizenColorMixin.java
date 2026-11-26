package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import fr.kodistan.interregne.colony.ColonyColors;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Citizens (bought guards included) show their colony's RGB color in their name and glow, instead of MineColonies'
 * own colony color, which is limited to the 16 chat colors (see {@link ColonyColors}).
 */
@Mixin(value = AbstractEntityCitizen.class, remap = false)
public abstract class CitizenColorMixin
{
    @Unique
    private Integer interregne$color()
    {
        final AbstractEntityCitizen self = (AbstractEntityCitizen) (Object) this;
        return self.getCitizenColonyHandler() == null ? null
                 : ColonyColors.ofColony(self.getCitizenColonyHandler().getColonyId(), self.level().isClientSide);
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$teamColor(final CallbackInfoReturnable<Integer> cir)
    {
        final Integer color = interregne$color();
        if (color != null)
        {
            cir.setReturnValue(color);
        }
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, remap = false)
    private void interregne$displayName(final CallbackInfoReturnable<Component> cir)
    {
        final Integer color = interregne$color();
        if (color != null)
        {
            cir.setReturnValue(cir.getReturnValue().copy().withStyle(style -> style.withColor(color)));
        }
    }
}
