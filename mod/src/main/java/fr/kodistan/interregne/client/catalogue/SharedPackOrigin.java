package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers which pack the blueprint being placed comes from. The build tool always places from the selected style, which
 * is wrong for the shared pack (the Relais commercial belongs to no style): its placement must name the shared pack.
 * Only the exact blueprint the catalogue loaded is remembered, so any other placement keeps the selected style.
 */
public final class SharedPackOrigin
{
    private static Blueprint blueprint;
    private static String pack;

    private SharedPackOrigin()
    {
    }

    public static void remember(final Blueprint placed, final String originPack)
    {
        blueprint = placed;
        pack = originPack;
    }

    /**
     * The pack to send with the placement: the remembered one when the previewed blueprint is the remembered one, else {@code current}.
     */
    public static String packFor(final String current)
    {
        final Blueprint previewed = RenderingCache.getOrCreateBlueprintPreviewData("blueprint").getBlueprint();
        return previewed != null && previewed == blueprint && pack != null ? pack : current;
    }
}
