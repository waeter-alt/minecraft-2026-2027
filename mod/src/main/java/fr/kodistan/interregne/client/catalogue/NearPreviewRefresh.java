package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.storage.rendering.RenderingCache;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Tells MineColonies' "neighbours at their maximum level" preview when the blueprint in hand has moved. MineColonies only looks
 * for the buildings the preview touches again when the PLAYER has walked 12.5 blocks, and loads what it found once a second, so a
 * ghost shows up late or not at all while the player slides the blueprint around a building. See
 * {@code ColonyBlueprintRendererMixin}.
 */
public final class NearPreviewRefresh
{
    /** How far the preview must move before the neighbours are looked for again, in blocks. */
    private static final double MOVE = 1.5;

    @Nullable
    private static BlockPos last;

    private NearPreviewRefresh()
    {
    }

    /**
     * Whether the previewed blueprint moved since the last time this said yes.
     */
    public static boolean previewMoved()
    {
        if (!RenderingCache.hasBlueprint("blueprint"))
        {
            last = null;
            return false;
        }
        final BlockPos pos = RenderingCache.getOrCreateBlueprintPreviewData("blueprint").getPos();
        if (pos == null)
        {
            return false;
        }
        if (last == null || !last.closerThan(pos, MOVE))
        {
            last = pos;
            return true;
        }
        return false;
    }
}
