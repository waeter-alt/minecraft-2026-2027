package fr.kodistan.interregne.mixin.client;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.containers.WindowField;
import fr.kodistan.interregne.client.WindowCropChoice;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A scarecrow field is sown from our crop list (the colony's farm crops first, the others greyed out) instead of
 * MineColonies' list of every seed.
 */
@Mixin(value = WindowField.class, remap = false)
public abstract class WindowFieldMixin
{
    @Shadow
    private void setSeed(final ItemStack stack)
    {
        throw new AssertionError();
    }

    @Inject(method = "selectSeed", at = @At("HEAD"), cancellable = true)
    private void interregne$farmCrops(final CallbackInfo ci)
    {
        ci.cancel();
        WindowCropChoice.openForField((BOWindow) (Object) this, this::setSeed);
    }
}
