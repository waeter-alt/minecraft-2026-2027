package fr.kodistan.interregne.client.dev;

import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import fr.kodistan.interregne.client.blocks.BankScreen;
import fr.kodistan.interregne.client.blocks.CropChoiceScreen;
import fr.kodistan.interregne.client.blocks.MarketScreen;
import fr.kodistan.interregne.client.blocks.ThroneScreen;
import fr.kodistan.interregne.client.catalogue.CatalogueScreen;
import fr.kodistan.interregne.client.hut.AppearanceScreen;
import fr.kodistan.interregne.client.hut.CitizenScreen;
import fr.kodistan.interregne.client.hut.ClipboardScreen;
import fr.kodistan.interregne.client.hut.ColonyHubScreen;
import fr.kodistan.interregne.client.hut.HutNav;
import fr.kodistan.interregne.client.hut.HutBodies;
import fr.kodistan.interregne.client.hut.HutScreen;
import fr.kodistan.interregne.client.hut.MercenaryScreen;
import fr.kodistan.interregne.client.hut.PermissionsScreen;
import fr.kodistan.interregne.client.hut.StatsScreen;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import fr.kodistan.interregne.commerce.Contract;
import fr.kodistan.interregne.client.routes.RoadScreen;
import fr.kodistan.interregne.routes.RoadStatePayload;
import fr.kodistan.interregne.commerce.RelaisStatePayload;
import fr.kodistan.interregne.client.commerce.KingdomMapScreen;
import fr.kodistan.interregne.commerce.MapStatePayload;
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
 * <li>{@code hubtool:<tool>|all}: the hub's other pages (clipboard, permissions, appearance, mercenary, stats).</li>
 * <li>{@code building:<type>|all}: a hut window and every page of its left column, the colony's first building of that
 * type ({@code list} names the types present).</li>
 * <li>{@code citizen:<n>}: the n-th citizen's sheet (0 by default).</li>
 * <li>{@code hud}: no screen, the in-game HUD alone.</li>
 * <li>{@code hud-war}: the HUD with the war indicator forced on.</li>
 * <li>{@code block:<bank|regionalbank|comptoir|market|throne|crops>|all}: our blocks' screens with fake data. They
 * send nothing to the server unless a button is clicked, and captures never click.</li>
 * <li>{@code catalogue}: the build catalogue.</li>
 * <li>{@code war}: the end-of-war recap, as the winner, the loser, and after a capitulation.</li>
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
                case "banner" -> {
                    // The colony's sign, one capture per kind of crate
                    for (final fr.kodistan.interregne.region.Crate crate : fr.kodistan.interregne.region.Crate.values())
                    {
                        if (arg.isEmpty() || arg.equals("all") || arg.equals(crate.id()))
                        {
                            targets.add(new Target("banner-" + crate.id(), () -> {
                                DevOverrides.bannerCrate = crate.id();
                                Minecraft.getInstance().setScreen(null);
                            }));
                        }
                    }
                }
                case "hud-caisses" -> targets.add(new Target("hud-caisses", () -> {
                    Minecraft.getInstance().setScreen(null);
                    DevOverrides.crates = 23;
                }));
                case "hud-war" -> targets.add(new Target("hud-war", () -> {
                    Minecraft.getInstance().setScreen(null);
                    DevOverrides.war = true;
                }));
                case "block" -> blocks(targets, arg, colony);
                case "catalogue" -> targets.add(new Target("catalogue", 60, () -> {
                    final BlockPos pos = Minecraft.getInstance().player.blockPosition();
                    CatalogueScreen.open(new WindowExtendedBuildTool(pos, 0, Minecraft.getInstance().level.registryAccess()), pos);
                }));
                case "style" -> targets.add(new Target("style", 60, () -> {
                    final BlockPos pos = Minecraft.getInstance().player.blockPosition();
                    CatalogueScreen.openStyleGalleryForUiShot(new WindowExtendedBuildTool(pos, 0, Minecraft.getInstance().level.registryAccess()), pos);
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
                    targets.add(new Target("wallbuild-preview", 80, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.upgradeDemo(colony.getTownHall(), false)));
                    targets.add(new Target("wallbuild-upsite", 300, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.upgradeDemo(colony.getTownHall(), true)));
                    targets.add(new Target("wallbuild-updone", 900, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.reopenDemo(colony.getTownHall())));
                    targets.add(new Target("wallbuild-uphub", 40, () -> ColonyHubScreen.openDev(colony.getTownHall(), "walls", 0)));
                    // Level 3 (stairs by the gate), seen from close on two sides
                    targets.add(new Target("wallbuild-up3", 950, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.upgradeDemo(colony.getTownHall(), true)));
                    targets.add(new Target("wallbuild-close", 60, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.closeUpDemo(colony.getTownHall(), 0)));
                    targets.add(new Target("wallbuild-close2", 60, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.closeUpDemo(colony.getTownHall(), 135)));
                    // Then the planner with « Démolir », and the wall torn down
                    targets.add(new Target("wallbuild-planner", 40, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.reopenDemo(colony.getTownHall())));
                    targets.add(new Target("wallbuild-demolish", 30, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.demolishDemo(colony.getTownHall())));
                    targets.add(new Target("wallbuild-gone", 160, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.reopenDemo(colony.getTownHall())));
                }
                case "walledit" -> {
                    // Over a built wall: the planner retracing it unchanged, then with its first tower moved
                    targets.add(new Target("walledit-same", 80, () -> net.minecraft.client.Minecraft.getInstance().setScreen(new fr.kodistan.interregne.client.walls.WallPlannerScreen(colony.getTownHall()))));
                    targets.add(new Target("walledit-moved", 80, () -> fr.kodistan.interregne.client.walls.WallPlannerScreen.openEditDemo(colony.getTownHall())));
                }
                case "war" -> {
                    // Settled long enough for the title pop and every figure's count-up to finish
                    targets.add(new Target("war-recap-victory", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(true, fr.kodistan.interregne.network.WarRecapPayload.CONQUEST))));
                    targets.add(new Target("war-recap-defeat", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(false, fr.kodistan.interregne.network.WarRecapPayload.CONQUEST))));
                    targets.add(new Target("war-recap-capitulation", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(true, fr.kodistan.interregne.network.WarRecapPayload.CAPITULATION))));
                    targets.add(new Target("war-recap-abandon-win", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(true, fr.kodistan.interregne.network.WarRecapPayload.ABANDON))));
                    targets.add(new Target("war-recap-abandon-loss", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(false, fr.kodistan.interregne.network.WarRecapPayload.ABANDON))));
                    targets.add(new Target("war-recap-reddition-win", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(true, fr.kodistan.interregne.network.WarRecapPayload.REDDITION))));
                    targets.add(new Target("war-recap-reddition-loss", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(false, fr.kodistan.interregne.network.WarRecapPayload.REDDITION))));
                    targets.add(new Target("war-recap-defense-aid", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(withAid(fakeRecap(false, fr.kodistan.interregne.network.WarRecapPayload.CONQUEST)))));
                    targets.add(new Target("war-recap-defense-aid-win", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(withAid(fakeRecap(true, fr.kodistan.interregne.network.WarRecapPayload.ABANDON)))));
                    targets.add(new Target("war-recap-break", 60, () -> fr.kodistan.interregne.client.war.WarRecapScreen.open(fakeRecap(true, fr.kodistan.interregne.network.WarRecapPayload.BREAK))));
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
        final List<String> pages = arg.isEmpty() || arg.equals("all") ? List.of("overview", "buildings", "citizens", "intendance", "walls", "diplomacy") : List.of(arg);
        for (final String page : pages)
        {
            if (page.equals("diplomacy"))
            {
                targets.add(new Target("hub-diplomacy", () -> {
                    final var fake = new java.util.ArrayList<fr.kodistan.interregne.network.DiplomacySyncPayload.Entry>();
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(11, "Port-Sombrebois", "Alix", 14, 410, -120, 3, 0, 0, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(12, "Haute-Garde", "Brenn", 31, -300, 260, 1, 0, 2, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(13, "Les Trois Moulins", "Cassandre", 8, 150, 520, 2, 0, 0, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(14, "Val-de-Cendre", "Dorian", 22, -640, -90, 0, 0, 0, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(15, "Fort-Écarlate avec un nom très long", "Eloïse", 40, 900, 900, 0, 0, 0, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(16, "Rive-Noire", "Fenn", 12, 200, 700, 4, 150, 1, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(17, "Mont-Cuivre", "Gaël", 19, -500, 400, 5, 0, 2, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(18, "Bourg-Ferrant", "Hugo", 9, 700, -300, 5, 0, 2, 1));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(19, "Crête-Grise", "Inès", 15, -200, -800, 7, 0, 1, 2));
                    fr.kodistan.interregne.client.hut.ClientDiplomacy.put(new fr.kodistan.interregne.network.DiplomacySyncPayload(colony.getID(), 6, 3, 20, 500L, 1240L, fake));
                    ColonyHubScreen.openDev(colony.getTownHall(), "diplomacy", 0);
                }));
                // Wars sit below the fold on the full list: this target shows only them.
                targets.add(new Target("hub-diplomacy-war", () -> {
                    final var fake = new java.util.ArrayList<fr.kodistan.interregne.network.DiplomacySyncPayload.Entry>();
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(16, "Rive-Noire", "Fenn", 12, 200, 700, 4, 150, 1, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(17, "Mont-Cuivre", "Gaël", 19, -500, 400, 5, 0, 2, 0));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(18, "Bourg-Ferrant", "Hugo", 9, 700, -300, 5, 0, 2, 1));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(19, "Crête-Grise", "Inès", 15, -200, -800, 7, 0, 1, 2));
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(20, "Roc-aux-Corbeaux", "Jade", 27, 380, 610, 6, 240, 1, 0));
                    // Treasury short of the tribute: shows the grey « Capituler » with have/cost in red.
                    fr.kodistan.interregne.client.hut.ClientDiplomacy.put(new fr.kodistan.interregne.network.DiplomacySyncPayload(colony.getID(), 6, 3, 20, 500L, 320L, fake));
                    ColonyHubScreen.openDev(colony.getTownHall(), "diplomacy", 0);
                }));
                // Only the wars waged on us, so the « Capituler » buttons are above the fold: payable, then short.
                for (final long treasury : new long[] {1240L, 320L})
                {
                    targets.add(new Target(treasury >= 500L ? "hub-diplomacy-surrender" : "hub-diplomacy-surrender-short", () -> {
                        final var fake = new java.util.ArrayList<fr.kodistan.interregne.network.DiplomacySyncPayload.Entry>();
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(19, "Crête-Grise", "Inès", 15, -200, -800, 7, 0, 1, 2));
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(20, "Roc-aux-Corbeaux", "Jade", 27, 380, 610, 6, 240, 1, 0));
                        fr.kodistan.interregne.client.hut.ClientDiplomacy.put(new fr.kodistan.interregne.network.DiplomacySyncPayload(colony.getID(), 6, 3, 20, 500L, treasury, fake));
                        ColonyHubScreen.openDev(colony.getTownHall(), "diplomacy", 0);
                    }));
                }
                // « Exiger la reddition » on the wars suffered: available, then waiting out the attacker's idleness, then troops out
                for (final String state : new String[] {"ready", "wait", "out"})
                {
                    targets.add(new Target("hub-diplomacy-demand-" + state, () -> {
                        final int seconds = state.equals("ready") ? 0 : state.equals("wait") ? 5 * 3600 + 12 * 60 : -1;
                        final var fake = new java.util.ArrayList<fr.kodistan.interregne.network.DiplomacySyncPayload.Entry>();
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(19, "Crête-Grise", "Inès", 15, -200, -800, 7, 0, 1, 0, 0, 0, -1, -1, seconds, "", 0));
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(20, "Roc-aux-Corbeaux", "Jade", 27, 380, 610, 7, 0, 1, 2, 0, 0, -1, -1,
                          fr.kodistan.interregne.network.DiplomacySyncPayload.SURRENDER_NO, "", 0));
                        fr.kodistan.interregne.client.hut.ClientDiplomacy.put(new fr.kodistan.interregne.network.DiplomacySyncPayload(colony.getID(), 6, 3, 20, 500L, 1240L, fake));
                        ColonyHubScreen.openDev(colony.getTownHall(), "diplomacy", 0);
                    }));
                }
                // Assault launcher while another colony holds the defender (grey button, hover says why)
                targets.add(new Target("hub-diplomacy-locked", () -> {
                    final var fake = new java.util.ArrayList<fr.kodistan.interregne.network.DiplomacySyncPayload.Entry>();
                    fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(17, "Mont-Cuivre", "Gaël", 19, -500, 400, 5, 0, 2, 0, 0, 0, -1, -1, -1, "Bourg-Ferrant", 4000));
                    fr.kodistan.interregne.client.hut.ClientDiplomacy.put(new fr.kodistan.interregne.network.DiplomacySyncPayload(colony.getID(), 6, 3, 20, 500L, 1240L, fake));
                    ColonyHubScreen.openDev(colony.getTownHall(), "diplomacy", 0);
                }));
                // Aide de clan: only allies, so the contingent lines are above the fold. « Remplir » payable (900 of
                // 1240), then short; a full contingent, an engaged one, and reinforcements kept for us.
                for (final long treasury : new long[] {1240L, 320L})
                {
                    targets.add(new Target(treasury >= 900L ? "hub-diplomacy-aid" : "hub-diplomacy-aid-short", () -> {
                        final var fake = new java.util.ArrayList<fr.kodistan.interregne.network.DiplomacySyncPayload.Entry>();
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(21, "Haute-Garde", "Brenn", 31, -300, 260, 1, 0, 2, 0, 4, 5, -1, -1));
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(22, "Fort-Écarlate avec un nom très long", "Eloïse", 40, 900, 900, 1, 0, 2, 0, 0, 0, -1, -1));
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(23, "Port-Sombrebois", "Alix", 14, 410, -120, 1, 0, 2, 0, 3, 0, 7, -1));
                        fake.add(new fr.kodistan.interregne.network.DiplomacySyncPayload.Entry(24, "Mont-Cuivre", "Gaël", 19, -500, 400, 1, 0, 2, 0, 10, 0, -1, 6));
                        fr.kodistan.interregne.client.hut.ClientDiplomacy.put(new fr.kodistan.interregne.network.DiplomacySyncPayload(colony.getID(), 6, 3, 20, 500L, treasury, 150L, 10, fake));
                        ColonyHubScreen.openDev(colony.getTownHall(), "diplomacy", 0);
                    }));
                }
                continue;
            }
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
            // Through the hut's own window, as a right-click opens it: catches a type our window redirect misses
            targets.add(new Target(type.getKey() + "-overview", () -> building.getWindow().open()));
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

    /**
     * Feeds the building's own body with fake data at the real building's position, then opens its hut window (on a page when given).
     */
    private static void ownBody(final IColonyView colony, final String type, final String tab, final java.util.function.Consumer<IBuildingView> feed)
    {
        final IBuildingView building = firstOfEachType(colony).get(type);
        if (building == null)
        {
            throw new IllegalArgumentException("No " + type + " in the colony: build one first");
        }
        feed.accept(building);
        if (tab == null)
        {
            HutScreen.open(building);
        }
        else
        {
            HutScreen.openTab(building, tab);
        }
    }

    private static void relais(final IColonyView colony, final String tab, final int level, final int capacity, final long available, final String notice,
      final List<RelaisStatePayload.Partner> partners, final java.util.function.IntFunction<List<RelaisStatePayload.Row>> rows, final List<RelaisStatePayload.Tier> tiers,
      final int escort)
    {
        relais(colony, tab, level, capacity, available, notice, partners, rows, tiers, escort, RelaisStatePayload.Imperial.NONE);
    }

    private static void relais(final IColonyView colony, final String tab, final int level, final int capacity, final long available, final String notice,
      final List<RelaisStatePayload.Partner> partners, final java.util.function.IntFunction<List<RelaisStatePayload.Row>> rows, final List<RelaisStatePayload.Tier> tiers,
      final int escort, final RelaisStatePayload.Imperial imperial)
    {
        ownBody(colony, "relais_commercial", tab, b -> HutBodies.preview(
          new RelaisStatePayload(b.getColony().getID(), "Vielle Ville", level, capacity, available, notice, partners, rows.apply(b.getColony().getID()), tiers, imperial), escort));
    }

    /** The Empire's price curve as the server sends it: base stock 100, base sell price 10, elasticity 1, factor between 0.5 and 2. */
    private static RelaisStatePayload.Imperial imperial(final int state, final long treasury)
    {
        return new RelaisStatePayload.Imperial(400, treasury, 64, new int[] {140, 100, 60, 100, 100}, new double[] {100, 10, 1, 0.5, 2},
          List.of(new RelaisStatePayload.Dock(1L, 840, state), new RelaisStatePayload.Dock(2L, 1_920, RelaisStatePayload.Dock.CLOSED)));
    }

    private static void blocks(final List<Target> targets, final String arg, final IColonyView colony)
    {
        final Map<String, Runnable> blocks = new LinkedHashMap<>();
        blocks.put("bank", () -> BankScreen.open(new OpenBankPayload(NOWHERE, 48_250, 0.02)));
        blocks.put("bank-empty", () -> BankScreen.open(new OpenBankPayload(NOWHERE, 0, 0.02)));
        blocks.put("regionalbank", () -> ownBody(colony, "banque_regionale", null, b -> HutBodies.receive(
          new OpenRegionalBankPayload(b.getPosition(), "dev", "Val-de-Loire", 8_400, 10_000, 2, 2_500, 25_000, true, true))));
        blocks.put("regionalbank-visitor", () -> ownBody(colony, "banque_regionale", null, b -> HutBodies.receive(
          new OpenRegionalBankPayload(b.getPosition(), "dev", "Val-de-Loire", 8_400, 10_000, 2, 2_500, 25_000, false, false))));
        blocks.put("comptoir", () -> ownBody(colony, "depot_regional", null, b -> HutBodies.receive(
          new OpenComptoirPayload(b.getPosition(), "Val-de-Loire", "bois", 2, 90, 60, 1_500, 14, 32, true, 37, 2_340))));
        blocks.put("comptoir-caisses", () -> ownBody(colony, "depot_regional", "caisses", b -> HutBodies.receive(
          new OpenComptoirPayload(b.getPosition(), "Val-de-Loire", "bois", 2, 90, 60, 1_500, 14, 32, true, 37, 2_340))));
        blocks.put("comptoir-nowarehouse", () -> ownBody(colony, "depot_regional", null, b -> HutBodies.receive(
          new OpenComptoirPayload(b.getPosition(), "Val-de-Loire", "bois", 1, 120, 90, 800, 0, 16, false, 0, 40))));
        final int crates = Crate.values().length;
        blocks.put("market", () -> MarketScreen.open(new OpenMarketPayload(NOWHERE, series(crates, 120, 35), series(crates, 80, 25),
          series(crates, 64, -9), series(crates, 3, -1), 5_600)));
        blocks.put("market-poor", () -> MarketScreen.open(new OpenMarketPayload(NOWHERE, series(crates, 120, 35), series(crates, 80, 25),
          series(crates, 2, 0), new int[crates], 30)));
        blocks.put("throne", () -> ThroneScreen.open(new OpenThronePayload(NOWHERE, true, 1, true, 3_600 * 20 + 754, false, "Val-de-Loire",
          series(crates, 6, 2), series(crates, 4, -1), 12_500)));
        blocks.put("throne-member", () -> ThroneScreen.open(new OpenThronePayload(NOWHERE, false, 1, true, 754, true, "Val-de-Loire",
          series(crates, 6, 2), new int[crates], 12_500)));
        final var partners = List.of(new RelaisStatePayload.Partner(2, "Ville Neuve", true), new RelaisStatePayload.Partner(3, "Marches de l'Ouest", true),
          new RelaisStatePayload.Partner(4, "Val-de-Loire", false));
        final var tiers = List.of(new RelaisStatePayload.Tier(150, 2, 1), new RelaisStatePayload.Tier(400, 3, 1), new RelaisStatePayload.Tier(900, 4, 2),
          new RelaisStatePayload.Tier(2_000, 5, 3), new RelaisStatePayload.Tier(4_500, 6, 4));
        final java.util.function.IntFunction<List<RelaisStatePayload.Row>> relaisRows = cid -> List.of(
          new RelaisStatePayload.Row(1, cid, "Vielle Ville", 2, "Ville Neuve", 0, 40, 5, Contract.TRANSIT, 62, 2, 4),
          new RelaisStatePayload.Row(2, 3, "Marches de l'Ouest", cid, "Vielle Ville", 2, 25, 12, Contract.OFFER, 0, 0, 0),
          new RelaisStatePayload.Row(3, cid, "Vielle Ville", 3, "Marches de l'Ouest", 4, 8, 30, Contract.OFFER, 0, 3, 6));
        blocks.put("relais", () -> relais(colony, null, 2, 4, 5_000, "", partners, relaisRows, tiers, 0));
        blocks.put("relais-offre", () -> relais(colony, "offre", 2, 4, 5_000, "", partners, relaisRows, tiers, 0));
        blocks.put("relais-escorte", () -> relais(colony, "offre", 3, 6, 5_000, "", partners, relaisRows, tiers, 2));
        blocks.put("relais-escorte-chere", () -> relais(colony, "offre", 5, 10, 300, "", partners, relaisRows, tiers, 5));
        blocks.put("relais-empire-ferme", () -> relais(colony, "empire", 2, 4, 5_000, "", partners, relaisRows, tiers, 0, imperial(RelaisStatePayload.Dock.CLOSED, 5_000)));
        blocks.put("relais-empire-pauvre", () -> relais(colony, "empire", 2, 4, 120, "", partners, relaisRows, tiers, 0, imperial(RelaisStatePayload.Dock.CLOSED, 120)));
        blocks.put("relais-empire-ouvert", () -> relais(colony, "empire", 2, 4, 5_000, "", partners, relaisRows, tiers, 0, imperial(RelaisStatePayload.Dock.OPEN, 5_000)));
        blocks.put("relais-empire-chantier", () -> relais(colony, "empire", 2, 4, 5_000, "", partners, relaisRows, tiers, 0, imperial(RelaisStatePayload.Dock.BUILDING, 5_000)));
        blocks.put("relais-empire-aucun", () -> relais(colony, "empire", 2, 4, 5_000, "", partners, relaisRows, tiers, 0, RelaisStatePayload.Imperial.NONE));
        blocks.put("relais-refus", () -> relais(colony, null, 1, 2, 20, "L'entrepôt n'a pas assez de caisses de bois.", List.of(), cid -> List.of(), tiers, 0));
        blocks.put("garnison", () -> ownBody(colony, "combatacademy", null, b -> HutBodies.preview(
          new fr.kodistan.interregne.garrison.GarrisonStatePayload(b.getColony().getID(), 0, 3, 220, 5_000, 4, 7, 12, ""))));
        blocks.put("garnison-chere", () -> ownBody(colony, "combatacademy", null, b -> HutBodies.preview(
          new fr.kodistan.interregne.garrison.GarrisonStatePayload(b.getColony().getID(), 0, 5, 550, 140, 9, 11, 16, ""))));
        blocks.put("garnison-pleine", () -> ownBody(colony, "archery", null, b -> HutBodies.preview(
          new fr.kodistan.interregne.garrison.GarrisonStatePayload(b.getColony().getID(), 1, 2, 120, 5_000, 6, 12, 12,
            "Plus de place dans les tours : construisez une tour de garde ou agrandissez la caserne."))));
        blocks.put("garnison-chantier", () -> ownBody(colony, "archery", null, b -> HutBodies.preview(
          new fr.kodistan.interregne.garrison.GarrisonStatePayload(b.getColony().getID(), 1, 0, 0, 5_000, 0, 0, 4, ""))));
        blocks.put("relais-chantier", () -> relais(colony, null, 0, 0, 0, "", List.of(), cid -> List.of(), tiers, 0));
        blocks.put("carte", () -> Minecraft.getInstance().setScreen(KingdomMapScreen.preview(fakeMap(), fakeRoads())));
        blocks.put("carte-vide", () -> Minecraft.getInstance().setScreen(KingdomMapScreen.preview(new MapStatePayload(List.of(), List.of()), List.of())));
        final var perks = List.of("+10 % de vitesse", "+20 % de vitesse · chute de +0 bloc sans dégât", "+30 % de vitesse · +5 % de saut · chute de +1 bloc sans dégât",
          "+40 % de vitesse · +10 % de saut · chute de +2 blocs sans dégât · régénération",
          "+50 % de vitesse · +15 % de saut · monte les marches sans sauter · chute de +3 blocs sans dégât · régénération et résistance");
        blocks.put("route", () -> RoadScreen.preview(new RoadStatePayload(NOWHERE, "Vielle Ville", "Ville Neuve", 481, 2, false, 1_500, 2_300, 120, "", perks)));
        blocks.put("route-pauvre", () -> RoadScreen.preview(new RoadStatePayload(NOWHERE, "Vielle Ville", "Ville Neuve", 481, 3, false, 4_000, 650, 120, "", perks)));
        blocks.put("route-travaux", () -> RoadScreen.preview(new RoadStatePayload(NOWHERE, "Vielle Ville", "Ville Neuve", 481, 1, true, 500, 5_000, 120, "", perks)));
        blocks.put("route-max", () -> RoadScreen.preview(new RoadStatePayload(NOWHERE, "Vielle Ville", "Ville Neuve", 481, 5, false, -1, 5_000, 120, "", perks)));
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

    /** The same recap seen by a defender that two allies' contingents helped (Aide de clan). */
    private static fr.kodistan.interregne.network.WarRecapPayload withAid(final fr.kodistan.interregne.network.WarRecapPayload p)
    {
        return new fr.kodistan.interregne.network.WarRecapPayload(p.victory(), p.outcome(), p.winner(), p.loser(), p.winnerFlag(), p.loserFlag(), p.durationTicks(),
          p.assaults(), p.sent(), p.lost(), p.destroyed(), p.loot(), p.fallen(), p.tribute(), p.influence(), p.shieldTicks(), 14, 5);
    }

    private static fr.kodistan.interregne.network.WarRecapPayload fakeRecap(final boolean victory, final int outcome)
    {
        final var patterns = Minecraft.getInstance().level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);
        final var red = new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
          .add(patterns.getHolderOrThrow(net.minecraft.world.level.block.entity.BannerPatterns.CROSS), net.minecraft.world.item.DyeColor.RED)
          .add(patterns.getHolderOrThrow(net.minecraft.world.level.block.entity.BannerPatterns.BORDER), net.minecraft.world.item.DyeColor.BLACK).build();
        final var blue = new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
          .add(patterns.getHolderOrThrow(net.minecraft.world.level.block.entity.BannerPatterns.STRIPE_CENTER), net.minecraft.world.item.DyeColor.BLUE)
          .add(patterns.getHolderOrThrow(net.minecraft.world.level.block.entity.BannerPatterns.TRIANGLE_TOP), net.minecraft.world.item.DyeColor.YELLOW).build();
        // The winner is always the attacker: sent / lost are its troops, destroyed / loot / fallen the loser's losses
        if (outcome == fr.kodistan.interregne.network.WarRecapPayload.BREAK)
        {
            return new fr.kodistan.interregne.network.WarRecapPayload(victory, outcome, "Hautebrume-sur-Loire", "Valdorée", red, blue,
              0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        final boolean capitulation = outcome == fr.kodistan.interregne.network.WarRecapPayload.CAPITULATION
                                       || outcome == fr.kodistan.interregne.network.WarRecapPayload.REDDITION;
        return new fr.kodistan.interregne.network.WarRecapPayload(victory, outcome, "Hautebrume-sur-Loire", "Valdorée", red, blue,
          20L * 60 * 72, capitulation ? 2 : 4, 38, 11, capitulation ? 3 : 7, 18_450, 9, capitulation ? 25_000 : 0,
          120, 20L * 60 * 60 * 2);
    }

    private static MapStatePayload fakeMap()
    {
        return new MapStatePayload(List.of(
          new MapStatePayload.Colony(1, "Vielle Ville", 0, 0), new MapStatePayload.Colony(2, "Ville Neuve", 600, 120),
          new MapStatePayload.Colony(3, "Marches de l'Ouest", -420, 380), new MapStatePayload.Colony(4, "Val-de-Loire", 180, -520)),
          List.of(new MapStatePayload.Caravan(1, 1, 2, "Vielle Ville → Ville Neuve : 40 × caisse de bois", 270, 55, -75, 45, 2, false),
            new MapStatePayload.Caravan(2, 3, 1, "Marches de l'Ouest → Vielle Ville : 25 × caisse de pierre", -250, 230, 140, 40, 0, false),
            new MapStatePayload.Caravan(3, 4, 1, "Val-de-Loire → Vielle Ville : 8 × caisse de minerai", 90, -260, 10, 50, 3, true)));
    }

    /** Straight roads from the first colony to each of the others, a cell per block. */
    private static List<int[]> fakeRoads()
    {
        final List<int[]> cells = new ArrayList<>();
        final int[][] ends = {{600, 120, 2}, {-420, 380, 3}, {180, -520, 4}};
        for (final int[] end : ends)
        {
            final int length = (int) Math.round(Math.hypot(end[0], end[1]));
            for (int i = 0; i <= length; i++)
            {
                cells.add(new int[] {Math.round((float) end[0] * i / length), Math.round((float) end[1] * i / length), 1, end[2]});
            }
        }
        return cells;
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
        return Arrays.toString(new String[] {"hub:<page|all>", "hubtool:<tool|all>", "building:<type|all>", "citizen:<n>", "hud", "hud-war", "hud-caisses", "block:<name|all> (dont relais, relais-escorte, carte)", "catalogue"});
    }
}
