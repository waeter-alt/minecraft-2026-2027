package fr.kodistan.interregne.mixin.client;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.core.client.gui.WindowRequestDetail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * What MineColonies' request detail window shows, so our request screen can open in its place.
 */
@Mixin(value = WindowRequestDetail.class, remap = false)
public interface WindowRequestDetailAccessor
{
    @Accessor("request")
    IRequest<?> interregne$request();

    @Accessor("colonyId")
    int interregne$colonyId();

    @Accessor("prevWindow")
    BOWindow interregne$previous();
}
