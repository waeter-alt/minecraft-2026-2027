package fr.kodistan.interregne;

import net.neoforged.neoforge.common.ModConfigSpec;

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
      .comment("Requests nobody in the colony can fill are bought automatically from the town hall treasury after a grace period")
      .define("enabled", true);

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
      .defineInRange("sweepSeconds", 30, 0, 3600);

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
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config()
    {
    }
}
