package fr.kodistan.interregne.mixin.client;

import com.minecolonies.core.client.render.worldevent.ColonyBlueprintRenderer;
import com.minecolonies.core.client.render.worldevent.WorldEventContext;
import fr.kodistan.interregne.client.catalogue.NearPreviewRefresh;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The ghost of a neighbouring building at its maximum level, drawn while a blueprint is previewed next to it, was slow: MineColonies
 * looked for the neighbours only when the player had walked 12.5 blocks and loaded what it found once a second. Look again as soon as
 * the preview moves, and load every other tick.
 */
@Mixin(value = ColonyBlueprintRenderer.class, remap = false)
public abstract class ColonyBlueprintRendererMixin
{
    @Shadow
    private static BlockPos lastCacheRebuild;

    @Inject(method = "renderBlueprints", at = @At("HEAD"))
    private static void interregne$lookAgainWhenThePreviewMoves(final WorldEventContext ctx, final CallbackInfo ci)
    {
        if (NearPreviewRefresh.previewMoved())
        {
            lastCacheRebuild = null;
        }
    }

    @ModifyConstant(method = "renderBlueprints", constant = @Constant(longValue = 20L))
    private static long interregne$loadPendingBlueprintsSooner(final long every)
    {
        return 2L;
    }
}
