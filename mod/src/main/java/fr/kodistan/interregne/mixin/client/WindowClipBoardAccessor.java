package fr.kodistan.interregne.mixin.client;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.WindowClipBoard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The colony of MineColonies' clipboard window, so our clipboard can open in its place.
 */
@Mixin(value = WindowClipBoard.class, remap = false)
public interface WindowClipBoardAccessor
{
    @Accessor("colony")
    IColonyView interregne$colony();
}
