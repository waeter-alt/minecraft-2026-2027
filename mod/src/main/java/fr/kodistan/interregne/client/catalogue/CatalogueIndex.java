package fr.kodistan.interregne.client.catalogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.AbstractBlockHut;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
 * huts by the building they hold (the same in every style), the rest by what they are.
 * Entries only keep where each blueprint lives; the blueprint itself is read again when placed.
 */
public final class CatalogueIndex
{
    /**
     * The player's needs, in the order of the catalogue's left column.
     */
    public enum Need
    {
        FOOD("Nourriture", Items.COOKED_CHICKEN, "Ce qui nourrit vos citoyens et remplit l'entrepôt : élevages, ferme, pêche, restaurant."),
        PLANTATION("Plantations", Items.SUGAR_CANE, "La Plantation cultive bambou, canne à sucre, cactus, cacao, lianes… Posez ses champs à côté : ses ouvriers y récoltent ce qui sert au papier, au sucre et aux teintures."),
        WOOD_STONE("Bois et pierre", Items.IRON_AXE, "Les matières premières : bûcheron, mine, carrières."),
        HOUSING("Logement", Items.RED_BED, "Loger, soigner et rassembler vos citoyens."),
        CRAFT("Artisanat", Items.ANVIL, "Les ateliers qui transforment les matières premières."),
        LOGISTICS("Logistique", Items.CHEST, "Stocker et livrer."),
        DEFENSE("Défense", Items.SHIELD, "Gardes, casernes et entraînement."),
        KNOWLEDGE("Savoir", Items.BOOK, "Éducation, recherche et enchantements."),
        GROUNDS("Aménagements", Items.DIRT_PATH, "Routes, voies ferrées, ponts, murs et canaux, payés à la taille."),
        DECORATION("Décoration", Items.FLOWER_POT, "Pour embellir la ville, payé à la taille.");

        private final String label;
        private final Item icon;
        private final String desc;

        Need(final String label, final Item icon, final String desc)
        {
            this.label = label;
            this.icon = icon;
            this.desc = desc;
        }

        public String label()
        {
            return label;
        }

        public ItemStack icon()
        {
            return new ItemStack(icon);
        }

        public String desc()
        {
            return desc;
        }
    }

    /**
     * Where a blueprint lives, to read it again when the player places it.
     */
    public record Ref(String pack, Path file)
    {
        @Nullable
        public Blueprint load(final HolderLookup.Provider provider)
        {
            return StructurePacks.getBlueprint(pack, file, provider);
        }
    }

    /**
     * One card: a building (all its variants in this style, at their lowest level) or one decoration.
     */
    public record Entry(Need need, @Nullable AbstractBlockHut<?> hut, String buildingId, String name, ItemStack icon, String folder,
                        List<Ref> variants, long blocks, @Nullable String about)
    {
        /**
         * The same card in another style: same building, or same blueprint name.
         */
        public String key()
        {
            return hut != null ? "hut:" + buildingId : "deco:" + name;
        }
    }

    private static final Map<String, Need> NEEDS = new HashMap<>();

    static
    {
        put(Need.FOOD, "chickenherder", "cowboy", "shepherd", "swineherder", "rabbithutch", "fisherman", "farmer", "cook", "baker", "beekeeper", "kitchen");
        put(Need.PLANTATION, "plantation");
        put(Need.WOOD_STONE, "lumberjack", "miner", "simplequarry", "mediumquarry", "largequarry", "sifter", "crusher");
        put(Need.HOUSING, "townhall", "residence", "tavern", "hospital", "graveyard");
        put(Need.LOGISTICS, "warehouse", "postbox", "stash");
        put(Need.DEFENSE, "archery", "barracks", "barrackstower", "combatacademy", "guardtower");
        put(Need.KNOWLEDGE, "library", "school", "university", "enchanter", "mysticalsite");
    }

    /**
     * Huts the modpack no longer uses: our timed sites replace the builders, the magic network the couriers.
     */
    private static final Set<String> HIDDEN = Set.of("builder", "deliveryman");

    private static final Map<String, CompletableFuture<List<Entry>>> CACHE = new ConcurrentHashMap<>();
    private static Map<String, JsonObject> names;
    private static boolean preloading;

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
     * The style's entries, read once in the background.
     */
    public static CompletableFuture<List<Entry>> of(final String pack, final HolderLookup.Provider provider)
    {
        return CACHE.computeIfAbsent(pack, name -> CompletableFuture.supplyAsync(() -> build(name, provider)));
    }

    /**
     * Read every style one after the other in the background, so switching style in the catalogue is instant.
     */
    public static void preloadAll()
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (preloading || minecraft.level == null)
        {
            return;
        }
        preloading = true;
        final HolderLookup.Provider provider = minecraft.level.registryAccess();
        final Thread thread = new Thread(() -> {
            StructurePacks.waitUntilFinishedLoading();
            final List<StructurePackMeta> packs = new ArrayList<>(StructurePacks.getPackMetas());
            final StructurePackMeta selected = StructurePacks.getSelectedPack();
            packs.sort(Comparator.comparing((StructurePackMeta p) -> p != selected).thenComparing(StructurePackMeta::getName));
            for (final StructurePackMeta pack : packs)
            {
                if (!pack.isDisabled())
                {
                    of(pack.getName(), provider).join();
                }
            }
        }, "Interregne catalogue preload");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public static void clear()
    {
        CACHE.clear();
        preloading = false;
    }

    private static List<Entry> build(final String pack, final HolderLookup.Provider provider)
    {
        final StructurePackMeta meta = StructurePacks.getStructurePack(pack);
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
            final String key = blueprint.getFilePath() + "/" + base;
            if (!lowest.containsKey(key) || level < lowestLevel.get(key))
            {
                lowest.put(key, blueprint);
                lowestLevel.put(key, level);
            }
        }

        final Map<String, List<Ref>> byBuilding = new LinkedHashMap<>();
        final Map<String, AbstractBlockHut<?>> huts = new HashMap<>();
        final Map<String, String> hutFolders = new HashMap<>();
        final List<Entry> entries = new ArrayList<>();
        for (final Blueprint blueprint : lowest.values())
        {
            final Ref ref = new Ref(pack, blueprint.getFilePath().resolve(blueprint.getFileName() + ".blueprint"));
            final String folder = folderOf(meta, blueprint);
            final Block anchor = anchorOf(blueprint);
            if (anchor instanceof AbstractBlockHut<?> hut)
            {
                final String id = hut.getBuildingEntry().getRegistryName().getPath();
                if (HIDDEN.contains(id))
                {
                    continue;
                }
                byBuilding.computeIfAbsent(id, k -> new ArrayList<>()).add(ref);
                huts.put(id, hut);
                hutFolders.putIfAbsent(id, folder);
                continue;
            }
            final String base = blueprint.getFileName().replaceAll("\\d+$", "").toLowerCase(Locale.ROOT);
            final JsonObject named = names().get(base);
            final String name = named != null ? named.get("name").getAsString() : pretty(base);
            final ItemStack icon = named != null ? item(named.get("icon").getAsString()) : ItemStack.EMPTY;
            final String anchorId = anchor == null ? "" : BuiltInRegistries.BLOCK.getKey(anchor).getPath();
            final Need need = anchorId.contains("plantationfield") ? Need.PLANTATION : needOfFolder(folder);
            final long blocks = blueprint.getBlockInfoAsList().stream().filter(info -> !info.getState().isAir()).count();
            entries.add(new Entry(need, null, "", name, icon.isEmpty() ? need.icon() : icon, folder, List.of(ref), blocks, aboutDeco(need, anchorId, name)));
        }
        byBuilding.forEach((id, variants) -> {
            final AbstractBlockHut<?> hut = huts.get(id);
            entries.add(new Entry(NEEDS.getOrDefault(id, Need.CRAFT), hut, id, hutName(hut), new ItemStack(hut), hutFolders.get(id), List.copyOf(variants), 0, null));
        });
        entries.sort(Comparator.comparing((Entry e) -> e.hut() == null).thenComparing(Entry::folder).thenComparing(Entry::name));
        return List.copyOf(entries);
    }

    @Nullable
    private static String aboutDeco(final Need need, final String anchorId, final String name)
    {
        if (need == Need.PLANTATION)
        {
            return "Champ de la Plantation : ses ouvriers y cultivent et récoltent. Il faut une Plantation pour qu'il serve.";
        }
        if (anchorId.contains("field"))
        {
            return "Champ décoratif : jamais récolté, il renforce la ferme qui cultive la même plante.";
        }
        return need == Need.GROUNDS ? "Aménagement, payé à la taille." : "Décoration, payée à la taille.";
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
    private static Block anchorOf(final Blueprint blueprint)
    {
        try
        {
            return blueprint.getBlockState(blueprint.getPrimaryBlockOffset()).getBlock();
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    private static String folderOf(@Nullable final StructurePackMeta meta, final Blueprint blueprint)
    {
        final Path parent = blueprint.getFilePath();
        if (parent == null)
        {
            return "";
        }
        try
        {
            return meta == null ? parent.toString() : meta.getSubPath(parent).replace('\\', '/').toLowerCase(Locale.ROOT);
        }
        catch (final RuntimeException e)
        {
            return parent.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        }
    }

    private static Need needOfFolder(final String folder)
    {
        if (folder.contains("field"))
        {
            return Need.FOOD;
        }
        for (final String grounds : List.of("infrastructure", "road", "mineshaft", "rail", "wall", "bridge", "canal"))
        {
            if (folder.contains(grounds))
            {
                return Need.GROUNDS;
            }
        }
        return Need.DECORATION;
    }

    private static synchronized Map<String, JsonObject> names()
    {
        if (names == null)
        {
            names = new HashMap<>();
            try (Reader reader = new InputStreamReader(CatalogueIndex.class.getResourceAsStream("/assets/" + InterregneMod.MODID + "/catalogue_names.json"), StandardCharsets.UTF_8))
            {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsJsonObject()));
            }
            catch (final Exception e)
            {
                InterregneMod.LOGGER.warn("L'Interregne: catalogue names unreadable", e);
            }
        }
        return names;
    }

    private static ItemStack item(final String id)
    {
        if (id.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        final Item item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
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

    private static String pretty(final String base)
    {
        final String text = base.replace('_', ' ').replace('-', ' ').trim();
        return capitalize(text.isEmpty() ? base : text);
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
        final int perMinute = Math.max(1, 60 / Math.max(1, Config.PRODUCTION_SECONDS.get())) * Config.PRODUCTION_PER_LEVEL.get();
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
            case "plantation" -> Component.literal("Récolte les champs de plantation posés à côté : bambou, canne à sucre, cactus…");
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
            case "plantation" -> new ItemStack(Items.SUGAR_CANE);
            default -> null;
        };
    }
}
