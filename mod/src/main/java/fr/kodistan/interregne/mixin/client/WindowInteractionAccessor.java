package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.core.client.gui.WindowInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The citizen of MineColonies' dialogue window, so talking to a citizen opens their sheet instead.
 */
@Mixin(value = WindowInteraction.class, remap = false)
public interface WindowInteractionAccessor
{
    @Accessor("citizen")
    ICitizenDataView interregne$citizen();
}
