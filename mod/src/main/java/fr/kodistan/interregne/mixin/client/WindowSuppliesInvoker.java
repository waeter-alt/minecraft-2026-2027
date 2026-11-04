package fr.kodistan.interregne.mixin.client;

import com.minecolonies.core.client.gui.WindowSupplies;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The style choice of MineColonies' starting camp placement, kept by our placement panel.
 */
@Mixin(value = WindowSupplies.class, remap = false)
public interface WindowSuppliesInvoker
{
    @Invoker("switchPackClicked")
    void interregne$switchPack();
}
