package fr.kodistan.interregne;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static
    {
        BUILDER.push("instantBuild");
    }

    public static final ModConfigSpec.BooleanValue INSTANT_BUILD = BUILDER
      .comment("Replace MineColonies builders: build and upgrade orders are paid and placed directly")
      .define("enabled", true);

    public static final ModConfigSpec.IntValue COST_PER_LEVEL = BUILDER
      .comment("Cost of a building order in Couronnes, multiplied by the target level")
      .defineInRange("costPerLevel", 8, 0, 4096);

    public static final ModConfigSpec.IntValue REPAIR_COST = BUILDER
      .comment("Flat cost of a repair order")
      .defineInRange("repairCost", 4, 0, 4096);

    public static final ModConfigSpec.IntValue DECORATION_COST = BUILDER
      .comment("Flat cost of a decoration order (walls, roads, ...)")
      .defineInRange("decorationCost", 4, 0, 4096);

    public static final ModConfigSpec.IntValue MIN_BUILD_SECONDS = BUILDER
      .comment("Construction time of a level 1 building, a repair or a decoration, whatever its size")
      .defineInRange("minBuildSeconds", 30, 1, 600);

    public static final ModConfigSpec.IntValue MAX_BUILD_SECONDS = BUILDER
      .comment("Construction time of a max level (5) upgrade; levels in between scale linearly")
      .defineInRange("maxBuildSeconds", 120, 1, 1800);

    public static final ModConfigSpec.IntValue FLYING_SHARE = BUILDER
      .comment("One placed block out of this many flies into place (0 disables flying blocks)")
      .defineInRange("flyingShare", 6, 0, 1000);

    public static final ModConfigSpec.IntValue MAX_FLYING = BUILDER
      .comment("Maximum flying blocks in the air at once, per construction site")
      .defineInRange("maxFlying", 24, 0, 256);

    public static final ModConfigSpec.IntValue DEMOLITION_COST = BUILDER
      .comment("Flat cost of demolishing a building")
      .defineInRange("demolitionCost", 0, 0, 4096);

    public static final ModConfigSpec.IntValue DEMOLITION_SECONDS = BUILDER
      .comment("How long a demolition takes, whatever the building's size")
      .defineInRange("demolitionSeconds", 20, 1, 600);

    static
    {
        BUILDER.pop();
        BUILDER.push("citizens");
    }

    public static final ModConfigSpec.BooleanValue UNBREAKABLE_TOOLS = BUILDER
      .comment("Citizens' tools and weapons never lose durability")
      .define("unbreakableTools", true);

    public static final ModConfigSpec.BooleanValue UNBREAKABLE_ARMOR = BUILDER
      .comment("Citizens' armor (guards) never loses durability")
      .define("unbreakableArmor", true);

    public static final ModConfigSpec.BooleanValue AUTO_TOOLS = BUILDER
      .comment("Workers receive the best tool their building allows when they need one, upgraded with the building")
      .define("autoTools", true);

    public static final ModConfigSpec.DoubleValue HUNGRY_SLOWDOWN = BUILDER
      .comment("Hungry citizens work this many times slower (they keep working when there is nowhere to eat)")
      .defineInRange("hungrySlowdown", 1.5, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue STARVING_SLOWDOWN = BUILDER
      .comment("Starving citizens (saturation 0) work this many times slower")
      .defineInRange("starvingSlowdown", 2.0, 1.0, 10.0);

    public static final ModConfigSpec.IntValue IMMIGRATION_SECONDS = BUILDER
      .comment("An adult settler moves into a colony with a free bed every this many seconds (0 disables; children are still born as usual)")
      .defineInRange("immigrationSeconds", 180, 0, 86400);

    static
    {
        BUILDER.pop();
        BUILDER.push("production");
    }

    public static final ModConfigSpec.DoubleValue OUTPUT_PER_LEVEL = BUILDER
      .comment("Raw producers' output multiplier gained per building level: 0.5 gives x1.5 at level 1 up to x3.5 at level 5")
      .defineInRange("outputPerLevel", 0.5, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue MAX_SPEED = BUILDER
      .comment("Work speed multiplier at level 5 (level 1 works at normal speed); applies to every worker except guards")
      .defineInRange("maxSpeed", 1.5, 1.0, 4.0);

    public static final ModConfigSpec.DoubleValue HAPPINESS_FOR_FULL_BONUS = BUILDER
      .comment("Workers at or above this happiness (0-10) get the full output bonus; below it the bonus shrinks, down to 30%")
      .defineInRange("happinessForFullBonus", 7.0, 0.0, 10.0);

    static
    {
        BUILDER.pop();
        BUILDER.push("intendance");
    }

    public static final ModConfigSpec.BooleanValue INTENDANCE = BUILDER
      .comment("Requests nobody in the colony can fill are bought automatically from the town hall treasury after a grace period (off: players settle them from the town hall's Intendance page)")
      .define("enabled", false);

    public static final ModConfigSpec.DoubleValue INTENDANCE_MARKUP = BUILDER
      .comment("The Intendance's supplies cost this many times the automatic purchase price: building the right hut stays cheaper")
      .defineInRange("markup", 2.0, 1.0, 100.0);

    public static final ModConfigSpec.IntValue INTENDANCE_CURE_COST = BUILDER
      .comment("Couronnes the Intendance charges to cure one sick citizen")
      .defineInRange("cureCost", 20, 0, 100_000);

    public static final ModConfigSpec.IntValue INTENDANCE_MEAL_COST = BUILDER
      .comment("Couronnes the Intendance charges to feed one hungry citizen")
      .defineInRange("mealCost", 3, 0, 100_000);

    public static final ModConfigSpec.IntValue INTENDANCE_CROWNS_PER_INFLUENCE = BUILDER
      .comment("Settling with the Intendance also costs the player one Influence per this many Couronnes (at least one)")
      .defineInRange("crownsPerInfluence", 10, 1, 100_000);

    public static final ModConfigSpec.IntValue GRACE_SECONDS = BUILDER
      .comment("Grace period before the Intendance buys a request, leaving players time to supply it themselves")
      .defineInRange("graceSeconds", 300, 0, 86400);

    public static final ModConfigSpec.IntValue ITEMS_PER_CROWN = BUILDER
      .comment("Items the Intendance buys for one Couronne; every purchase costs at least one")
      .defineInRange("itemsPerCrown", 16, 1, 4096);

    static
    {
        BUILDER.pop();
        BUILDER.push("warehouse");
    }

    public static final ModConfigSpec.BooleanValue MAGIC_NETWORK = BUILDER
      .comment("No couriers: deliveries and pickups move straight between the warehouse and the huts")
      .define("magicNetwork", true);

    public static final ModConfigSpec.IntValue TRANSFER_TICKS = BUILDER
      .comment("Delay before a delivery or pickup lands, in ticks (20 = 1 second)")
      .defineInRange("transferTicks", 30, 0, 1200);

    public static final ModConfigSpec.IntValue SWEEP_SECONDS = BUILDER
      .comment("Every worker hut's surplus is sent to the warehouse this often (0 disables; MineColonies alone waits days)")
      .defineInRange("sweepSeconds", 10, 0, 3600);

    public static final ModConfigSpec.IntValue COMET_VIEW_DISTANCE = BUILDER
      .comment("How far away players see the warehouse comets, in blocks (vanilla particles stop at 32)")
      .defineInRange("cometViewDistance", 384, 32, 512);

    public static final ModConfigSpec.IntValue SWEEP_ITEMS_PER_LEVEL = BUILDER
      .comment("Items a hut sends to the warehouse in one trip, per building level (level 3 hut: 3 x this)")
      .defineInRange("sweepItemsPerLevel", 16, 1, 4096);

    public static final ModConfigSpec.IntValue AUTO_SORT_SECONDS = BUILDER
      .comment("The warehouse racks sort themselves this often (0 disables)")
      .defineInRange("autoSortSeconds", 60, 0, 3600);

    static
    {
        BUILDER.pop();
        BUILDER.push("intendanceAlerts");
    }

    public static final ModConfigSpec.IntValue ALERT_SECONDS = BUILDER
      .comment("A request the Intendance will not buy (strategic items) is reported to the colony's players after this long")
      .defineInRange("alertSeconds", 120, 0, 86400);

    static
    {
        BUILDER.pop();
    }

    static
    {
        BUILDER.push("economy");
    }

    public static final ModConfigSpec.LongValue HEAVY_PURSE = BUILDER
      .comment("Couronnes carried from which a player is slowed down (Slowness I)")
      .defineInRange("heavyPurse", 10_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue DRAGON_PURSE = BUILDER
      .comment("Couronnes carried from which a player is slowed further (Slowness II) and glows through walls")
      .defineInRange("dragonPurse", 100_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue WITHDRAWAL_FEE = BUILDER
      .comment("Share the Imperial Bank keeps on every withdrawal (0.02 = 2%, at least one Couronne)")
      .defineInRange("withdrawalFee", 0.02, 0.0, 1.0);

    static
    {
        BUILDER.pop();
        BUILDER.push("regionalBank");
    }

    /**
     * Treasury capacity of a regional bank, by bank level (index 0 is level 1).
     */
    public static final List<ModConfigSpec.LongValue> BANK_CAPACITY = List.of(
      BUILDER.comment("Treasury capacity of a level 1 regional bank; deposits beyond it are refused").defineInRange("capacityLevel1", 10_000L, 0L, Long.MAX_VALUE),
      BUILDER.comment("Treasury capacity at level 2").defineInRange("capacityLevel2", 25_000L, 0L, Long.MAX_VALUE),
      BUILDER.comment("Treasury capacity at level 3").defineInRange("capacityLevel3", 50_000L, 0L, Long.MAX_VALUE),
      BUILDER.comment("Treasury capacity at level 4").defineInRange("capacityLevel4", 100_000L, 0L, Long.MAX_VALUE),
      BUILDER.comment("Treasury capacity at level 5").defineInRange("capacityLevel5", 250_000L, 0L, Long.MAX_VALUE));

    /**
     * Price of upgrading a regional bank to each level (index 0 is level 2).
     */
    public static final List<ModConfigSpec.LongValue> BANK_UPGRADE_COST = List.of(
      BUILDER.comment("Price of upgrading a regional bank to level 2, paid from the treasury first, then from the coins carried").defineInRange("upgradeCostLevel2", 1_000L, 0L, Long.MAX_VALUE),
      BUILDER.comment("Price of the upgrade to level 3").defineInRange("upgradeCostLevel3", 2_500L, 0L, Long.MAX_VALUE),
      BUILDER.comment("Price of the upgrade to level 4").defineInRange("upgradeCostLevel4", 5_000L, 0L, Long.MAX_VALUE),
      BUILDER.comment("Price of the upgrade to level 5").defineInRange("upgradeCostLevel5", 10_000L, 0L, Long.MAX_VALUE));

    public static final ModConfigSpec.BooleanValue VAULT_DECORATIONS = BUILDER
      .comment("The bank fills the 5x5 floor around itself with coin piles, ingots, chests and a hoard as the treasury grows")
      .define("vaultDecorations", true);

    static
    {
        BUILDER.pop();
    }

    static
    {
        BUILDER.push("death");
    }

    public static final ModConfigSpec.BooleanValue DEATH_RULE = BUILDER
      .comment("Players keep armor, hotbar and inventory on death, even with keepInventory off; items tagged interregne:drops_on_death (backpacks) always drop")
      .define("enabled", true);

    public static final ModConfigSpec.BooleanValue PVP_DROPS_COINS = BUILDER
      .comment("When killed by a player, every Couronne carried drops for the killer to loot")
      .define("pvpDropsCoins", true);

    public static final ModConfigSpec.LongValue PVP_INFLUENCE_LOSS = BUILDER
      .comment("Influence lost when killed by a player (never below zero)")
      .defineInRange("pvpInfluenceLoss", 5L, 0L, Long.MAX_VALUE);

    static
    {
        BUILDER.pop();
        BUILDER.push("gear");
    }

    public static final ModConfigSpec.BooleanValue UNBREAKABLE_PLAYER_GEAR = BUILDER
      .comment("Players' tools, weapons and armor never break: they stop at 1 durability and become damaged (abîmé) until repaired")
      .define("neverBreaks", true);

    public static final ModConfigSpec.DoubleValue DAMAGED_STAT_PENALTY = BUILDER
      .comment("Share of attack damage, armor and armor toughness lost by a damaged item (0.5 = -50%)")
      .defineInRange("damagedStatPenalty", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue DAMAGED_MINING_PENALTY = BUILDER
      .comment("Share of mining speed lost with a damaged tool in the main hand (0.5 = -50%)")
      .defineInRange("damagedMiningPenalty", 0.5, 0.0, 1.0);

    static
    {
        BUILDER.pop();
        BUILDER.push("safeZones");
    }

    public static final ModConfigSpec.BooleanValue SAFE_ZONES = BUILDER
      .comment("Player-vs-player damage is cancelled inside safe zones (/interregne zone sure)")
      .define("enabled", true);

    public static final ModConfigSpec.BooleanValue SAFE_ZONE_MESSAGES = BUILDER
      .comment("Show an action-bar message when a player enters or leaves a safe zone")
      .define("messages", true);

    static
    {
        BUILDER.pop();
    }

    static
    {
        BUILDER.push("regions");
    }

    public static final ModConfigSpec.IntValue CRATES_SLOWNESS_1 = BUILDER
      .comment("A player carrying more crates than this is slowed down (Slowness I)")
      .defineInRange("cratesSlowness1", 6, 0, 10_000);

    public static final ModConfigSpec.IntValue CRATES_SLOWNESS_2 = BUILDER
      .comment("A player carrying more crates than this is slowed further (Slowness II)")
      .defineInRange("cratesSlowness2", 16, 0, 10_000);

    public static final ModConfigSpec.ConfigValue<String> COMPTOIR_SECONDS = BUILDER
      .comment("Seconds a Comptoir régional takes to produce one crate, for levels 1 to 5, comma-separated")
      .define("comptoirSeconds", "300,240,180,120,60");

    public static final ModConfigSpec.ConfigValue<String> COMPTOIR_UPGRADE_COSTS = BUILDER
      .comment("Couronnes to upgrade a Comptoir régional to levels 2, 3, 4 and 5, comma-separated")
      .define("comptoirUpgradeCosts", "200,400,800,1600");

    public static final ModConfigSpec.IntValue COMPTOIR_STORAGE = BUILDER
      .comment("Crates a Comptoir régional keeps itself when the colony has no warehouse (or a full one); production stops beyond")
      .defineInRange("comptoirStorage", 32, 1, 10_000);

    public static final ModConfigSpec.IntValue COMPTOIR_CATCH_UP_MINUTES = BUILDER
      .comment("When a Comptoir's chunk loads again, it catches up on at most this many minutes of production (0 disables catch-up)")
      .defineInRange("comptoirCatchUpMinutes", 120, 0, 10_080);

    public static final ModConfigSpec.ConfigValue<String> CRATES_LEVEL_2 = BUILDER
      .comment("Crates needed to build or upgrade a building to level 2 (types: bois, brique, ble, pierre, minerai; empty for none)")
      .define("cratesLevel2", "bois:1,pierre:1");

    public static final ModConfigSpec.ConfigValue<String> CRATES_LEVEL_3 = BUILDER
      .comment("Crates needed for level 3")
      .define("cratesLevel3", "bois:2,pierre:1,brique:1");

    public static final ModConfigSpec.ConfigValue<String> CRATES_LEVEL_4 = BUILDER
      .comment("Crates needed for level 4")
      .define("cratesLevel4", "bois:2,pierre:2,brique:2,minerai:1");

    public static final ModConfigSpec.ConfigValue<String> CRATES_LEVEL_5 = BUILDER
      .comment("Crates needed for level 5")
      .define("cratesLevel5", "bois:3,brique:3,ble:3,pierre:3,minerai:3");

    public static final ModConfigSpec.IntValue MARKET_BUY_PRICE = BUILDER
      .comment("Base price, in Couronnes, at which the Marché impérial sells a crate to players")
      .defineInRange("marketBuyPrice", 60, 1, 1_000_000);

    public static final ModConfigSpec.IntValue MARKET_SELL_PRICE = BUILDER
      .comment("Base price, in Couronnes, at which the Marché impérial buys a crate from players")
      .defineInRange("marketSellPrice", 25, 1, 1_000_000);

    public static final ModConfigSpec.IntValue MARKET_BASE_STOCK = BUILDER
      .comment("Imperial stock of each crate type at which base prices apply; less stock raises prices, more lowers them")
      .defineInRange("marketBaseStock", 32, 1, 100_000);

    public static final ModConfigSpec.DoubleValue MARKET_ELASTICITY = BUILDER
      .comment("How strongly the stock moves prices: 1.0 doubles them at zero stock and halves them at 1.5x the base stock")
      .defineInRange("marketElasticity", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue MARKET_MIN_FACTOR = BUILDER
      .comment("Price floor, as a multiple of the base prices")
      .defineInRange("marketMinFactor", 0.5, 0.01, 1.0);

    public static final ModConfigSpec.DoubleValue MARKET_MAX_FACTOR = BUILDER
      .comment("Price ceiling, as a multiple of the base prices")
      .defineInRange("marketMaxFactor", 2.5, 1.0, 100.0);

    public static final ModConfigSpec.IntValue MARKET_RESTOCK_MINUTES = BUILDER
      .comment("Every this many minutes, each imperial stock moves one crate back toward the base stock (0 disables)")
      .defineInRange("marketRestockMinutes", 10, 0, 10_080);

    static
    {
        BUILDER.pop();
    }

    static
    {
        BUILDER.push("politics");
    }

    public static final ModConfigSpec.IntValue PRESENCE_MINUTES = BUILDER
      .comment("Online minutes for each point of presence Influence")
      .defineInRange("presenceMinutes", 10, 1, 1440);

    public static final ModConfigSpec.IntValue PRESENCE_CAP = BUILDER
      .comment("Most presence Influence a player can earn per real day")
      .defineInRange("presenceDailyCap", 12, 0, 10_000);

    public static final ModConfigSpec.IntValue FAVOR_CAP = BUILDER
      .comment("Most Influence the Intendants can grant or remove with one edict")
      .defineInRange("favorCap", 10, 1, 10_000);

    public static final ModConfigSpec.IntValue TITHE_HOURS = BUILDER
      .comment("Real hours between two tithes")
      .defineInRange("titheHours", 48, 1, 24 * 30);

    public static final ModConfigSpec.IntValue TITHE_PER_TYPE = BUILDER
      .comment("Crates of each type (except the colony's own) owed per tithe, before the tithe-weight edict")
      .defineInRange("tithePerType", 2, 0, 64);

    public static final ModConfigSpec.IntValue TITHE_BONUS = BUILDER
      .comment("Influence earned per crate delivered to the tithe")
      .defineInRange("titheInfluencePerCrate", 1, 0, 100);

    public static final ModConfigSpec.IntValue TITHE_PENALTY = BUILDER
      .comment("Influence the colony owner loses when the tithe is not fully paid at the deadline")
      .defineInRange("tithePenalty", 10, 0, 10_000);

    static
    {
        BUILDER.pop();
    }

    static
    {
        BUILDER.push("production");
    }

    public static final ModConfigSpec.IntValue PRODUCTION_SECONDS = BUILDER
      .comment("Every staffed production hut (breeders, fisher, farm) yields its goods this often, whatever its animals or water")
      .defineInRange("productionSeconds", 30, 5, 3600);

    public static final ModConfigSpec.IntValue PRODUCTION_PER_LEVEL = BUILDER
      .comment("Main goods per production cycle, per building level (a level 1 chicken coop: 1 raw chicken every cycle)")
      .defineInRange("productionPerLevel", 1, 0, 64);

    public static final ModConfigSpec.IntValue FIELD_BONUS = BUILDER
      .comment("Extra goods per cycle for each decorative field planted with the farm's crop")
      .defineInRange("fieldBonus", 1, 0, 64);

    public static final ModConfigSpec.IntValue HERD_PER_LEVEL = BUILDER
      .comment("Animals shown in a breeder's pen after each construction or upgrade, per building level (plus a starting pair)")
      .defineInRange("herdPerLevel", 2, 0, 16);

    public static final ModConfigSpec.IntValue FOREST_SAPLINGS = BUILDER
      .comment("Saplings a forester makes sure exist around its hut after each construction or upgrade, per building level (plus 4)")
      .defineInRange("forestSaplingsPerLevel", 2, 0, 16);

    public static final ModConfigSpec.DoubleValue SAPLING_GROWTH = BUILDER
      .comment("Chance per level, every 10 seconds, that a sapling near a staffed forester grows a stage (level 5 at 0.15: 75%)")
      .defineInRange("saplingGrowthPerLevel", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.IntValue KITCHEN_SURPLUS_PER_LEVEL = BUILDER
      .comment("Raw food a restaurant whose own stock is full cooks for the warehouse every production cycle, per level")
      .defineInRange("kitchenSurplusPerLevel", 16, 0, 1024);

    public static final ModConfigSpec.IntValue CRAFT_SURPLUS_PER_LEVEL = BUILDER
      .comment("Items a crafter makes from the warehouse's surplus every production cycle, per level")
      .defineInRange("craftSurplusPerLevel", 16, 0, 1024);

    public static final ModConfigSpec.IntValue CRAFT_TARGET = BUILDER
      .comment("A crafter stops making an item once the warehouse holds this many of it")
      .defineInRange("craftTarget", 64, 1, 100_000);

    public static final ModConfigSpec.IntValue CRAFT_RESERVE = BUILDER
      .comment("Crafters never use an ingredient below this many in the warehouse, so the player always keeps some")
      .defineInRange("craftReserve", 16, 0, 100_000);

    public static final ModConfigSpec.IntValue DECORATION_BLOCKS_PER_COURONNE = BUILDER
      .comment("A decoration costs one Couronne per this many blocks (at least decorationCost)")
      .defineInRange("decorationBlocksPerCouronne", 100, 1, 100_000);

    public static final ModConfigSpec.IntValue DECORATION_BLOCKS_PER_CRATE = BUILDER
      .comment("A decoration needs one regional crate (wood and stone in turn) per this many blocks; 0 disables")
      .defineInRange("decorationBlocksPerCrate", 1000, 0, 100_000);

    static
    {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config()
    {
    }
}
