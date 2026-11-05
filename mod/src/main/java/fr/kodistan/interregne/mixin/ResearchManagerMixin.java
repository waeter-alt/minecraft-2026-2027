package fr.kodistan.interregne.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The research layer is gone from La Couronne Vacante (no University): hut-unlock research effects must never gate
 * placing or upgrading a building. An id no research has makes every hasResearchEffect check quietly false, so the
 * whole gate opens at once — buildings will unlock by town hall level instead.
 */
@Mixin(value = com.minecolonies.core.colony.managers.ResearchManager.class, remap = false)
public abstract class ResearchManagerMixin
{
    @Inject(method = "getResearchEffectIdFrom", at = @At("HEAD"), cancellable = true)
    private void interregne$noResearchGates(final Block block, final CallbackInfoReturnable<ResourceLocation> cir)
    {
        cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("interregne", "no_research"));
    }
}
