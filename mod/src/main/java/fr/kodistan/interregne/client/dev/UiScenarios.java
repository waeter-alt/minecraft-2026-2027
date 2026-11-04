package fr.kodistan.interregne.client.dev;

import com.minecolonies.api.colony.IColonyView;
import fr.kodistan.interregne.client.hut.ColonyHubScreen;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The screens {@link UiShot} knows how to open, and the fake states each one is captured in.
 * <p>
 * Screens: {@code hub:<page>} opens the town hall hub on that page (overview, buildings, citizens, intendance, walls).
 * Scenario {@code real} shows the colony's own data; the Intendance adds payable, short, empty, list and loading.
 */
public final class UiScenarios
{
    private UiScenarios()
    {
    }

    public static List<String> scenarios(final String screen)
    {
        if (screen.equals("hub:intendance"))
        {
            return List.of("payable", "short", "empty", "list", "loading");
        }
        return List.of("real");
    }

    public static void apply(final String screen, final String scenario, final IColonyView colony)
    {
        final int[] hover = DevOverrides.hover;
        DevOverrides.clear();
        DevOverrides.hover = hover;
        if (!screen.equals("hub:intendance") || scenario.equals("real"))
        {
            return;
        }
        final int id = colony.getID();
        switch (scenario)
        {
            case "payable", "list" ->
            {
                DevOverrides.quote = fullQuote(id);
                DevOverrides.treasury = 12_500L;
                DevOverrides.purse = 340L;
                DevOverrides.influence = 90L;
            }
            case "short" ->
            {
                DevOverrides.quote = fullQuote(id);
                DevOverrides.treasury = 180L;
                DevOverrides.purse = 25L;
                DevOverrides.influence = 3L;
            }
            case "empty" ->
            {
                DevOverrides.quote = new IntendanceQuotePayload(id, List.of(), 0, 0, List.of(), 0, 0);
                DevOverrides.treasury = 12_500L;
                DevOverrides.purse = 0L;
                DevOverrides.influence = 90L;
            }
            case "loading" -> DevOverrides.quoteLoading = true;
            default -> throw new IllegalArgumentException("Unknown scenario " + scenario);
        }
    }

    public static void open(final String screen, final String scenario, final IColonyView colony)
    {
        if (!screen.startsWith("hub:"))
        {
            throw new IllegalArgumentException("Unknown screen " + screen);
        }
        ColonyHubScreen.openDev(colony.getTownHall(), screen.substring(4), scenario.equals("list") ? 2 : 0);
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
          new IntendanceQuotePayload.Supply(new ItemStack(Items.OAK_PLANKS, 64), Component.literal("Bûcheron"), 96),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.COBBLESTONE, 32), Component.literal("Tailleur de pierre"), 40),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.WHEAT_SEEDS, 16), Component.literal("Fermier"), 12),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.IRON_INGOT, 8), Component.literal("Forgeron"), 240),
          new IntendanceQuotePayload.Supply(new ItemStack(Items.DIAMOND, 2), Component.literal("Forgeron"), -1));
        return new IntendanceQuotePayload(id, care, 345, 12, supplies, 1388, 28);
    }
}
