package fr.kodistan.interregne.client.dev;

import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import fr.kodistan.interregne.client.blocks.BankScreen;
import fr.kodistan.interregne.client.blocks.ComptoirScreen;
import fr.kodistan.interregne.client.blocks.CropChoiceScreen;
import fr.kodistan.interregne.client.blocks.MarketScreen;
import fr.kodistan.interregne.client.blocks.RegionalBankScreen;
import fr.kodistan.interregne.client.blocks.ThroneScreen;
import fr.kodistan.interregne.client.catalogue.CatalogueScreen;
import fr.kodistan.interregne.client.hut.AlliancesScreen;
import fr.kodistan.interregne.client.hut.AppearanceScreen;
import fr.kodistan.interregne.client.hut.CitizenScreen;
import fr.kodistan.interregne.client.hut.ClipboardScreen;
import fr.kodistan.interregne.client.hut.ColonyHubScreen;
import fr.kodistan.interregne.client.hut.HutNav;
import fr.kodistan.interregne.client.hut.HutScreen;
import fr.kodistan.interregne.client.hut.MercenaryScreen;
import fr.kodistan.interregne.client.hut.PermissionsScreen;
import fr.kodistan.interregne.client.hut.StatsScreen;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.OpenMarketPayload;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything {@link UiShot} can capture. A request names one or more specs, comma-separated; each spec expands into
 * targets, each a named way to open one screen in one state:
 * <ul>
 * <li>{@code hub:<page>|all}: the town hall hub (overview, buildings, citizens, intendance, walls). The Intendance comes
 * in fake states: payable, short, empty, list, loading.</li>
 * <li>{@code hubtool:<tool>|all}: the hub's other pages (clipboard, permissions, alliances, appearance, mercenary, stats).</li>
 * <li>{@code building:<type>|all}: a hut window and every page of its left column, the colony's first building of that
 * type ({@code list} names the types present).</li>
 * <li>{@code citizen:<n>}: the n-th citizen's sheet (0 by default).</li>
 * <li>{@code hud}: no screen, the in-game HUD alone.</li>
 * <li>{@code block:<bank|regionalbank|comptoir|market|throne|crops>|all}: our blocks' screens with fake data. They
 * send nothing to the server unless a button is clicked, and captures never click.</li>
 * <li>{@code catalogue}: the build catalogue.</li>
 * </ul>
 * Names of the real colony's data are captured as they are; fake states exist where the real colony can't show them.
 */
public final class UiScenarios
{
    /**
     * Where fake block screens say they are: nothing is ever sent for it.
     */
    private static final BlockPos NOWHERE = new BlockPos(0, -10_000, 0);

    public record Target(String name, int settleTicks, Runnable open)
    {
        Target(final String name, final Runnable open)
        {
            this(name, 0, open);
        }
    }

    private UiScenarios()
    {
    }

    public static List<Target> targets(final String specs, final IColonyView colony)
    {
        final List<Target> targets = new ArrayList<>();
        for (final String raw : specs.split(","))
        {
            final String spec = raw.trim().toLowerCase(Locale.ROOT);
            if (spec.isEmpty())
            {
                continue;
            }
            final int colon = spec.indexOf(':');
            final String kind = colon < 0 ? spec : spec.substring(0, colon);
            final String arg = colon < 0 ? "" : spec.substring(colon + 1);
            switch (kind)
            {
                case "hub" -> hub(targets, arg, colony);
                case "hubtool" -> hubTools(targets, arg, colony);
                case "building" -> buildings(targets, arg, colony);
                case "citizen" -> citizen(targets, arg, colony);
                case "hud" -> targets.add(new Target("hud", () -> Minecraft.getInstance().setScreen(null)));
                case "block" -> blocks(targets, arg);
                case "catalogue" -> targets.add(new Target("catalogue", 60, () -> {
                    final BlockPos pos = Minecraft.getInstance().player.blockPosition();
                    CatalogueScreen.open(new WindowExtendedBuildTool(pos, 0, Minecraft.getInstance().level.registryAccess()), pos);
                }));
                case "walls" -> {
                    // The aerial wall planner: the camera needs a moment to settle and the terrain to be read
                    targets.add(new Target("walls-closed", 80, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.openDemo(colony.getTownHall(), true)));
                    targets.add(new Target("walls-open", 80, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.openDemo(colony.getTownHall(), false)));
                }
                case "wallbuild" -> {
                    // Builds a real wall in the test world: one capture mid-site, one once it stands
                    targets.add(new Target("wallbuild-site", 300, () -> {
                        if (!fr.kodistan.interregne.client.walls.WallPlannerScreen.buildDemo(colony.getTownHall()))
                        {
                            fr.kodistan.interregne.InterregneMod.LOGGER.warn("UiShot: no free square for a demo wall");
                        }
                    }));
                    targets.add(new Target("wallbuild-done", 900, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.reopenDemo(colony.getTownHall())));
                    // Then the hub with its two actions, an upgrade to level 2 and the planner retracing the built wall
                    targets.add(new Target("wallbuild-hub", 40, () -> ColonyHubScreen.openDev(colony.getTownHall(), "walls", 0)));
                    targets.add(new Target("wallbuild-upsite", 300, () -> {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new fr.kodistan.interregne.network.WallUpgradePayload(colony.getID()));
                        fr.kodistan.interregne.client.walls.WallPlannerScreen.reopenDemo(colony.getTownHall());
                    }));
                    targets.add(new Target("wallbuild-updone", 900, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.reopenDemo(colony.getTownHall())));
                    targets.add(new Target("wallbuild-uphub", 40, () -> ColonyHubScreen.openDev(colony.getTownHall(), "walls", 0)));
                }
                case "walledit" -> {
                    // Over a built wall: the planner retracing it unchanged, then with its first tower moved
                    targets.add(new Target("walledit-same", 80, () -> net.minecraft.client.Minecraft.getInstance().setScreen(new fr.kodistan.interregne.client.walls.WallPlannerScreen(colony.getTownHall()))));
                    targets.add(new Target("walledit-moved", 80, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.openEditDemo(colony.getTownHall())));
                }
                default -> throw new IllegalArgumentException("Unknown screen " + spec);
            }
        }
        return targets;
    }

    /**
     * The building types present in the colony, for {@code building:<type>}.
     */
    public static List<String> buildingTypes(final IColonyView colony)
    {
        return new ArrayList<>(firstOfEachType(colony).keySet());
    }

    // --- Hub ---

    private static void hub(final List<Target> targets, final String arg, final IColonyView colony)
    {
        final List<String> pages = arg.isEmpty() || arg.equals("all") ? List.of("overview", "buildings", "citizens", "intendance", "walls") : List.of(arg);
        for (final String page : pages)
        {
            if (!page.equals("intendance"))
            {
                targets.add(new Target("hub-" + page, () -> ColonyHubScreen.openDev(colony.getTownHall(), page, 0)));
                continue;
            }
            for (final String state : List.of("payable", "short", "empty", "list", "care", "list-short", "loading"))
            {
                targets.add(new Target("intendance-" + state, () -> {
                    intendance(state, colony.getID());
                    ColonyHubScreen.openDev(colony.getTownHall(), "intendance", state.startsWith("list") ? 2 : state.equals("care") ? 1 : 0);
                }));
            }
        }
    }

    private static void intendance(final String state, final int id)
    {
        switch (state)
        {
            case "payable", "list", "care" -> money(fullQuote(id), 12_500, 340, 90);
            case "short", "list-short" -> money(fullQuote(id), 180, 25, 3);
            case "empty" -> money(new IntendanceQuotePayload(id, List.of(), 0, 0, List.of(), 0, 0), 12_500, 0, 90);
            default -> DevOverrides.quoteLoading = true;
        }
    }

    private static void money(final IntendanceQuotePayload quote, final long treasury, final long purse, final long influence)
    {
        DevOverrides.quote = quote;
        DevOverrides.treasury = treasury;
        DevOverrides.purse = purse;
        DevOverrides.influence = influence;
    }

    private static IntendanceQuotePayload fullQuote(final int id)
    {
        final List<IntendanceQuotePayload.Care> care = List.of(
          new IntendanceQuotePayload.Care(1, "Jehanne Beaumanoir", true, 120),
          new IntendanceQuotePayload.Care(2, "Gauthier de Montfort-l'Amaury", true, 120),
          new IntendanceQuotePayload.Care(3, "Aude", false, 35),
          new IntendanceQuotePayload.Care(4, "Bertrand Lefèvre", false, 35),
          new IntendanceQuotePayload.Care(5, "Isabeau Chastelain", false, 35));
        final List<IntendanceQuotePayload.Supply> supplies = List.of(
          new IntendanceQuotePayload.Supply(new ItemStack(Items.OAK_PLANKS, 64), Component.literal("Bûcheron"), 96, 171),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.COBBLESTONE, 32), Component.literal("Tailleur de pierre"), 40, 172),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.WHEAT_SEEDS, 16), Component.literal("Fermier"), 12, 173),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.IRON_INGOT, 8), Component.literal("Forgeron"), 240, 174),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.DIAMOND, 2), Component.literal("Forgeron"), -1, 175));
        return new IntendanceQuotePayload(id, care, 345, 12, supplies, 1388, 28);
    }

    private static void hubTools(final List<Target> targets, final String arg, final IColonyView colony)
    {
        final Map<String, Runnable> tools = new LinkedHashMap<>();
        final IBuildingView townHall = colony.getTownHall();
        final BuildingTownHall.View view = (BuildingTownHall.View) townHall;
        tools.put("clipboard", () -> ClipboardScreen.open(colony));
        tools.put("permissions", () -> PermissionsScreen.open(view, null));
        tools.put("alliances", () -> AlliancesScreen.open(view, null));
        tools.put("appearance", () -> AppearanceScreen.open(view, null));
        tools.put("mercenary", () -> MercenaryScreen.open(townHall, null));
        tools.put("stats", () -> StatsScreen.openColony(townHall, null));
        for (final Map.Entry<String, Runnable> tool : tools.entrySet())
        {
            if (arg.isEmpty() || arg.equals("all") || arg.equals(tool.getKey()))
            {
                targets.add(new Target("hubtool-" + tool.getKey(), tool.getValue()));
            }
        }
    }

    // --- Buildings and citizens ---

    private static Map<String, IBuildingView> firstOfEachType(final IColonyView colony)
    {
        final Map<String, IBuildingView> types = new LinkedHashMap<>();
        for (final IBuildingView building : colony.getBuildings())
        {
            types.putIfAbsent(building.getBuildingType().getRegistryName().getPath(), building);
        }
        return types;
    }

    private static void buildings(final List<Target> targets, final String arg, final IColonyView colony)
    {
        final Map<String, IBuildingView> types = firstOfEachType(colony);
        for (final Map.Entry<String, IBuildingView> type : types.entrySet())
        {
            if (!(arg.isEmpty() || arg.equals("all") || arg.equals(type.getKey())))
            {
                continue;
            }
            final IBuildingView building = type.getValue();
            if (type.getKey().equals("townhall"))
            {
                continue;
            }
            targets.add(new Target(type.getKey() + "-overview", () -> HutScreen.open(building)));
            for (final IBuildingModuleView page : HutNav.pages(building))
            {
                // « com.minecolonies.coremod.gui.workerhuts.settings » → « settings »
                final String full = HutNav.key(page);
                final String key = full.substring(full.lastIndexOf('.') + 1).replaceAll("[^a-zA-Z0-9]+", "_");
                targets.add(new Target(type.getKey() + "-" + key, () -> {
                    // From the hut window, as a player would open it
                    HutScreen.open(building);
                    page.getWindow().open();
                }));
            }
        }
        if (targets.isEmpty() && !arg.isEmpty() && !arg.equals("all"))
        {
            throw new IllegalArgumentException("No building of type " + arg + " (present: " + types.keySet() + ")");
        }
    }

    private static void citizen(final List<Target> targets, final String arg, final IColonyView colony)
    {
        final List<ICitizenDataView> citizens = new ArrayList<>(colony.getCitizens().values());
        final int index = arg.isEmpty() ? 0 : Integer.parseInt(arg);
        if (index >= citizens.size())
        {
            throw new IllegalArgumentException("The colony has " + citizens.size() + " citizens");
        }
        targets.add(new Target("citizen-" + index, () -> CitizenScreen.open(citizens.get(index), null)));
    }

    // --- Our blocks, with fake data ---

    private static void blocks(final List<Target> targets, final String arg)
    {
        final Map<String, Runnable> blocks = new LinkedHashMap<>();
        blocks.put("bank", () -> BankScreen.open(new OpenBankPayload(NOWHERE, 48_250, 0.02)));
        blocks.put("bank-empty", () -> BankScreen.open(new OpenBankPayload(NOWHERE, 0, 0.02)));
        blocks.put("regionalbank", () -> RegionalBankScreen.open(
          new OpenRegionalBankPayload(NOWHERE, "dev", "Val-de-Loire", 8_400, 10_000, 2, 2_500, 25_000, true, true)));
        blocks.put("regionalbank-visitor", () -> RegionalBankScreen.open(
          new OpenRegionalBankPayload(NOWHERE, "dev", "Val-de-Loire", 8_400, 10_000, 2, 2_500, 25_000, false, false)));
        blocks.put("comptoir", () -> ComptoirScreen.open(
          new OpenComptoirPayload(NOWHERE, "Val-de-Loire", "Marches de l'Ouest", 2, 90, 60, 1_500, 14, 32, true, 37, 2_340)));
        blocks.put("comptoir-nowarehouse", () -> ComptoirScreen.open(
          new OpenComptoirPayload(NOWHERE, "Val-de-Loire", "Marches de l'Ouest", 1, 120, 90, 800, 0, 16, false, 0, 40)));
        final int crates = Crate.values().length;
        blocks.put("market", () -> MarketScreen.open(new OpenMarketPayload(NOWHERE, series(crates, 120, 35), series(crates, 80, 25),
          series(crates, 64, -9), series(crates, 3, -1), 5_600)));
        blocks.put("market-poor", () -> MarketScreen.open(new OpenMarketPayload(NOWHERE, series(crates, 120, 35), series(crates, 80, 25),
          series(crates, 2, 0), new int[crates], 30)));
        blocks.put("throne", () -> ThroneScreen.open(new OpenThronePayload(NOWHERE, true, 1, true, 3_600 * 20 + 754, false, "Val-de-Loire",
          series(crates, 6, 2), series(crates, 4, -1), 12_500)));
        blocks.put("throne-member", () -> ThroneScreen.open(new OpenThronePayload(NOWHERE, false, 1, true, 754, true, "Val-de-Loire",
          series(crates, 6, 2), new int[crates], 12_500)));
        // The field variant: the farm one would cancel a construction when closed
        blocks.put("crops", () -> CropChoiceScreen.openForField(() -> { }, stack -> { }));
        for (final Map.Entry<String, Runnable> block : blocks.entrySet())
        {
            if (arg.isEmpty() || arg.equals("all") || block.getKey().equals(arg) || block.getKey().startsWith(arg + "-"))
            {
                targets.add(new Target("block-" + block.getKey(), block.getValue()));
            }
        }
    }

    private static int[] series(final int length, final int start, final int step)
    {
        final int[] values = new int[length];
        for (int i = 0; i < length; i++)
        {
            values[i] = Math.max(0, start + step * i);
        }
        return values;
    }

    static String describe()
    {
        return Arrays.toString(new String[] {"hub:<page|all>", "hubtool:<tool|all>", "building:<type|all>", "citizen:<n>", "hud", "block:<name|all>", "catalogue"});
    }
}
