package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.AbstractBlockHut;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every blueprint of a style, sorted by what the player needs instead of by the pack's folders:
 * huts by the building they hold (the same in every style), the rest by the folder they sit in.
 */
public final class CatalogueIndex
{
    /**
     * The player's needs, in the order of the catalogue's left column.
     */
    public enum Need
    {
        FOOD("Nourriture", Items.COOKED_CHICKEN),
        WOOD_STONE("Bois et pierre", Items.IRON_AXE),
        HOUSING("Logement", Items.RED_BED),
        CRAFT("Artisanat", Items.ANVIL),
        LOGISTICS("Logistique", Items.CHEST),
        DEFENSE("Défense", Items.SHIELD),
        KNOWLEDGE("Savoir", Items.BOOK),
        GROUNDS("Aménagements", Items.RAIL),
        DECORATION("Décoration", Items.POPPY);

        private final String label;
        private final Item icon;

        Need(final String label, final Item icon)
        {
            this.label = label;
            this.icon = icon;
        }

        public String label()
        {
            return label;
        }

        public ItemStack icon()
        {
            return new ItemStack(icon);
        }
    }

    /**
     * One card: a building (all its styles' variants, at their lowest level) or one decoration.
     */
    public record Entry(Need need, @Nullable AbstractBlockHut<?> hut, String buildingId, String name, String folder, List<Blueprint> variants, long blocks)
    {
        public ItemStack icon()
        {
            return hut != null ? new ItemStack(hut) : need.icon();
        }
    }

    private static final Map<String, Need> NEEDS = new HashMap<>();

    static
    {
        put(Need.FOOD, "chickenherder", "cowboy", "shepherd", "swineherder", "rabbithutch", "fisherman", "farmer", "cook", "baker", "beekeeper", "kitchen");
        put(Need.WOOD_STONE, "lumberjack", "miner", "simplequarry", "mediumquarry", "largequarry", "sifter", "crusher");
        put(Need.HOUSING, "townhall", "residence", "tavern", "hospital", "graveyard");
        put(Need.LOGISTICS, "builder", "warehouse", "deliveryman", "postbox", "stash");
        put(Need.DEFENSE, "archery", "barracks", "barrackstower", "combatacademy", "guardtower");
        put(Need.KNOWLEDGE, "library", "school", "university", "enchanter", "mysticalsite");
    }

    private static final Map<String, CompletableFuture<List<Entry>>> CACHE = new ConcurrentHashMap<>();

    private CatalogueIndex()
    {
    }

    private static void put(final Need need, final String... ids)
    {
        for (final String id : ids)
        {
            NEEDS.put(id, need);
        }
    }

    /**
     * The style's entries, loaded once in the background.
     */
    public static CompletableFuture<List<Entry>> of(final String pack, final HolderLookup.Provider provider)
    {
        return CACHE.computeIfAbsent(pack, name -> CompletableFuture.supplyAsync(() -> build(name, provider)));
    }

    public static void clear()
    {
        CACHE.clear();
    }

    private static List<Entry> build(final String pack, final HolderLookup.Provider provider)
    {
        final List<Blueprint> all = new ArrayList<>();
        walk(pack, "", provider, all, 0);

        // Lowest level of each variant: "residence1".."residence5" and "altresidence1".. are two variants of one building
        final Map<String, Blueprint> lowest = new LinkedHashMap<>();
        final Map<String, Integer> lowestLevel = new HashMap<>();
        for (final Blueprint blueprint : all)
        {
            final String file = blueprint.getFileName();
            final String base = file.replaceAll("\\d+$", "");
            final int level = base.length() == file.length() ? 1 : parse(file.substring(base.length()));
            final String key = folderOf(blueprint) + "/" + base;
            if (!lowest.containsKey(key) || level < lowestLevel.get(key))
            {
                lowest.put(key, blueprint);
                lowestLevel.put(key, level);
            }
        }

        final Map<String, List<Blueprint>> byBuilding = new LinkedHashMap<>();
        final Map<String, AbstractBlockHut<?>> huts = new HashMap<>();
        final List<Entry> entries = new ArrayList<>();
        for (final Blueprint blueprint : lowest.values())
        {
            final AbstractBlockHut<?> hut = hutOf(blueprint);
            if (hut != null)
            {
                final String id = hut.getBuildingEntry().getRegistryName().getPath();
                byBuilding.computeIfAbsent(id, k -> new ArrayList<>()).add(blueprint);
                huts.put(id, hut);
            }
            else
            {
                final String folder = folderOf(blueprint);
                entries.add(new Entry(needOfFolder(folder), null, "", pretty(blueprint.getFileName()), folder, List.of(blueprint), solidBlocks(blueprint)));
            }
        }
        byBuilding.forEach((id, variants) -> {
            final AbstractBlockHut<?> hut = huts.get(id);
            entries.add(new Entry(NEEDS.getOrDefault(id, Need.CRAFT), hut, id, hutName(hut), folderOf(variants.get(0)), List.copyOf(variants), 0));
        });
        entries.sort(Comparator.comparing((Entry e) -> e.hut() == null).thenComparing(Entry::folder).thenComparing(Entry::name));
        return List.copyOf(entries);
    }

    private static void walk(final String pack, final String path, final HolderLookup.Provider provider, final List<Blueprint> out, final int depth)
    {
        if (depth > 6)
        {
            return;
        }
        try
        {
            out.addAll(StructurePacks.getBlueprints(pack, path, provider));
        }
        catch (final RuntimeException e)
        {
            // An unreadable folder only hides its own blueprints
        }
        for (final StructurePacks.Category category : StructurePacks.getCategories(pack, path))
        {
            walk(pack, category.subPath, provider, out, depth + 1);
        }
    }

    @Nullable
    private static AbstractBlockHut<?> hutOf(final Blueprint blueprint)
    {
        try
        {
            return blueprint.getBlockState(blueprint.getPrimaryBlockOffset()).getBlock() instanceof AbstractBlockHut<?> hut ? hut : null;
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    private static String folderOf(final Blueprint blueprint)
    {
        final StringBuilder folder = new StringBuilder();
        final java.nio.file.Path parent = blueprint.getFilePath();
        if (parent != null)
        {
            for (final java.nio.file.Path part : parent)
            {
                if (!folder.isEmpty())
                {
                    folder.append('/');
                }
                folder.append(part.toString().toLowerCase(Locale.ROOT));
            }
        }
        return folder.toString();
    }

    private static Need needOfFolder(final String folder)
    {
        if (folder.contains("field"))
        {
            return Need.FOOD;
        }
        if (folder.contains("infrastructure") || folder.contains("road") || folder.contains("mineshaft") || folder.contains("rail"))
        {
            return Need.GROUNDS;
        }
        return Need.DECORATION;
    }

    private static long solidBlocks(final Blueprint blueprint)
    {
        return blueprint.getBlockInfoAsList().stream().filter(info -> !info.getState().isAir()).count();
    }

    /**
     * "Hutte du poulailler" reads "Poulailler" on a card.
     */
    private static String hutName(final AbstractBlockHut<?> hut)
    {
        String name = hut.getName().getString();
        for (final String prefix : Set.of("Hutte du ", "Hutte de la ", "Hutte de l'", "Hutte de l’", "Hutte des ", "Hutte d'", "Hutte de "))
        {
            if (name.startsWith(prefix))
            {
                name = name.substring(prefix.length());
                break;
            }
        }
        return capitalize(name);
    }

    private static String pretty(final String file)
    {
        final String base = file.replaceAll("\\d+$", "").replace('_', ' ').replace('-', ' ')
          .replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return capitalize(base.isEmpty() ? file : base.toLowerCase(Locale.ROOT));
    }

    private static String capitalize(final String text)
    {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int parse(final String digits)
    {
        try
        {
            return Integer.parseInt(digits);
        }
        catch (final NumberFormatException e)
        {
            return 1;
        }
    }

    /**
     * What a production hut yields, for the detail panel; null for the others.
     */
    @Nullable
    public static Component production(final String buildingId)
    {
        final int perMinute = Math.max(1, 60 / Math.max(1, fr.kodistan.interregne.Config.PRODUCTION_SECONDS.get())) * fr.kodistan.interregne.Config.PRODUCTION_PER_LEVEL.get();
        return switch (buildingId)
        {
            case "chickenherder" -> rate(Items.CHICKEN, perMinute);
            case "cowboy" -> rate(Items.BEEF, perMinute);
            case "shepherd" -> rate(Items.MUTTON, perMinute);
            case "swineherder" -> rate(Items.PORKCHOP, perMinute);
            case "rabbithutch" -> rate(Items.RABBIT, perMinute);
            case "fisherman" -> rate(Items.COD, perMinute);
            case "farmer" -> Component.literal("La culture choisie : " + perMinute + " par minute au niveau 1, plus à chaque niveau.");
            case "cook" -> Component.literal("Cuisine sans combustible et remplit l'entrepôt de plats.");
            case "lumberjack" -> Component.literal("Plante des arbres autour de lui, qui poussent vite.");
            default -> null;
        };
    }

    private static Component rate(final Item item, final int perMinute)
    {
        return Component.literal(new ItemStack(item).getHoverName().getString() + " : " + perMinute + " par minute au niveau 1, plus à chaque niveau.");
    }

    @Nullable
    public static ItemStack productIcon(final String buildingId)
    {
        return switch (buildingId)
        {
            case "chickenherder" -> new ItemStack(Items.CHICKEN);
            case "cowboy" -> new ItemStack(Items.BEEF);
            case "shepherd" -> new ItemStack(Items.MUTTON);
            case "swineherder" -> new ItemStack(Items.PORKCHOP);
            case "rabbithutch" -> new ItemStack(Items.RABBIT);
            case "fisherman" -> new ItemStack(Items.COD);
            case "farmer" -> new ItemStack(Items.WHEAT);
            case "cook" -> new ItemStack(Items.COOKED_CHICKEN);
            case "lumberjack" -> new ItemStack(Items.OAK_SAPLING);
            default -> null;
        };
    }
}
