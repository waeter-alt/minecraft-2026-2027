package fr.kodistan.interregne.heraldry;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.eventbus.events.colony.ColonyFlagChangedModEvent;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * L'Interrègne heraldry: our own banner patterns (data-driven, in {@code data/interregne/banner_pattern}) and
 * helpers to edit a colony flag on the server.
 * <p>
 * A colony flag is a {@link BannerPatternLayers} whose first layer is {@code minecraft:base} (the field colour),
 * followed by at most {@link #MAX_LAYERS} charges, as MineColonies stores it.
 */
public final class Heraldry
{
    /** Charges a flag can carry on top of its field, like the loom. */
    public static final int MAX_LAYERS = 6;

    /** Region resources, in the order of the event's five building materials. */
    public static final List<String> REGIONS = List.of("bois", "brique", "ble", "pierre", "minerai");

    /** Default colour of each region emblem. */
    private static final Map<String, DyeColor> REGION_COLORS = Map.of(
      "bois", DyeColor.GREEN,
      "brique", DyeColor.RED,
      "ble", DyeColor.YELLOW,
      "pierre", DyeColor.LIGHT_GRAY,
      "minerai", DyeColor.CYAN);

    /** Prefix of region emblem pattern ids. */
    private static final String REGION_PREFIX = "region_";

    private Heraldry()
    {
    }

    /**
     * @param id a pattern id in our namespace, e.g. {@code "couronne"}
     * @return the registry key of that banner pattern
     */
    public static ResourceKey<BannerPattern> pattern(final String id)
    {
        return ResourceKey.create(Registries.BANNER_PATTERN, ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, id));
    }

    /**
     * @param resource a region resource, accents and case ignored ({@code "blé"} works)
     * @return the normalised resource id among {@link #REGIONS}, or null if unknown
     */
    @Nullable
    public static String normaliseRegion(final String resource)
    {
        if (resource == null)
        {
            return null;
        }
        final String plain = Normalizer.normalize(resource.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return REGIONS.contains(plain) ? plain : null;
    }

    /**
     * @return whether this pattern is one of our region emblems
     */
    public static boolean isRegionEmblem(final Holder<BannerPattern> pattern)
    {
        return pattern.unwrapKey()
                 .map(key -> key.location().getNamespace().equals(InterregneMod.MODID) && key.location().getPath().startsWith(REGION_PREFIX))
                 .orElse(false);
    }

    /**
     * Adds the region emblem as the top layer of the colony flag, or replaces a previous region emblem.
     * Uses the region's default colour, or a contrasting one if it matches the field.
     *
     * @param colony   the server-side colony
     * @param resource {@code "bois"}, {@code "brique"}, {@code "ble"}, {@code "pierre"} or {@code "minerai"}
     * @return true if the flag was changed, false for an unknown resource
     */
    public static boolean applyRegionEmblem(final IColony colony, final String resource)
    {
        return applyRegionEmblem(colony, resource, null);
    }

    /**
     * Same as {@link #applyRegionEmblem(IColony, String)} with an explicit emblem colour.
     *
     * @param color the emblem colour, or null for the region default
     */
    public static boolean applyRegionEmblem(final IColony colony, final String resource, @Nullable final DyeColor color)
    {
        final String region = normaliseRegion(resource);
        if (region == null)
        {
            InterregneMod.LOGGER.warn("L'Interregne: unknown region resource '{}' for a colony flag", resource);
            return false;
        }

        final HolderLookup.RegistryLookup<BannerPattern> lookup = colony.getWorld().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        final Holder<BannerPattern> emblem = lookup.get(pattern(REGION_PREFIX + region)).orElse(null);
        final Holder<BannerPattern> base = lookup.get(BannerPatterns.BASE).orElse(null);
        if (emblem == null || base == null)
        {
            InterregneMod.LOGGER.error("L'Interregne: banner pattern for region '{}' is not registered", region);
            return false;
        }

        // Split the current flag into its field colour and its charges
        final List<BannerPatternLayers.Layer> current = colony.getColonyFlag() == null ? List.of() : colony.getColonyFlag().layers();
        DyeColor field = DyeColor.WHITE;
        final List<BannerPatternLayers.Layer> charges = new ArrayList<>();
        for (int i = 0; i < current.size(); i++)
        {
            final BannerPatternLayers.Layer layer = current.get(i);
            if (i == 0 && layer.pattern().is(BannerPatterns.BASE))
            {
                field = layer.color();
            }
            else if (!isRegionEmblem(layer.pattern()))
            {
                charges.add(layer);
            }
        }
        while (charges.size() >= MAX_LAYERS)
        {
            charges.remove(charges.size() - 1);
        }

        DyeColor tint = color != null ? color : REGION_COLORS.get(region);
        if (tint == field)
        {
            tint = contrast(field);
        }

        final BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder().add(base, field);
        charges.forEach(builder::add);
        builder.add(emblem, tint);
        colony.setColonyFlag(builder.build());
        IMinecoloniesAPI.getInstance().getEventBus().post(new ColonyFlagChangedModEvent(colony));
        return true;
    }

    /**
     * @return black or white, whichever reads better on the given colour
     */
    public static DyeColor contrast(final DyeColor color)
    {
        final int rgb = color.getTextureDiffuseColor();
        final int luma = ((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114;
        return luma > 128_000 ? DyeColor.BLACK : DyeColor.WHITE;
    }
}
