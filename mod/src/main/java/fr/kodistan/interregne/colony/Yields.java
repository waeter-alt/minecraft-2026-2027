package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import fr.kodistan.interregne.Config;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Production that scales with the building level: raw producers multiply what they gather, and every worker
 * (guards excepted) works faster. Shared by the server hooks and the hut window.
 */
public final class Yields
{
    /**
     * Buildings that gather raw resources. Crafters are left out on purpose: multiplying recipe outputs
     * would let reversible recipes (ingot to block and back) duplicate items forever.
     */
    private static final Set<String> RAW_PRODUCERS = Set.of(
      "miner", "simplequarry", "mediumquarry", "largequarry", "lumberjack", "farmer", "plantation", "florist",
      "fisherman", "cowboy", "shepherd", "chickenherder", "swineherder", "rabbithutch", "beekeeper");

    private static final double MIN_HAPPINESS_SHARE = 0.3;

    private Yields()
    {
    }

    public static boolean isRawProducer(final BuildingEntry type)
    {
        return type.getRegistryName().getNamespace().equals("minecolonies") && RAW_PRODUCERS.contains(type.getRegistryName().getPath());
    }

    public static double outputMultiplier(final int level)
    {
        return 1.0 + Config.OUTPUT_PER_LEVEL.get() * Mth.clamp(level, 1, BuildCosts.MAX_LEVEL);
    }

    public static double speedMultiplier(final int level)
    {
        return 1.0 + (Config.MAX_SPEED.get() - 1.0) * (Mth.clamp(level, 1, BuildCosts.MAX_LEVEL) - 1) / (BuildCosts.MAX_LEVEL - 1);
    }

    /**
     * Speed for the AI working in this building: guards keep vanilla timings.
     */
    public static double speedFor(@Nullable final IBuilding building)
    {
        if (building == null || building instanceof AbstractBuildingGuards || building.getBuildingLevel() < 1)
        {
            return 1.0;
        }
        return speedMultiplier(building.getBuildingLevel());
    }

    /**
     * Multiply gathered items for a raw producer, scaled by its workers' happiness.
     */
    public static List<ItemStack> boost(@Nullable final IBuilding building, final List<ItemStack> drops, final RandomSource random)
    {
        if (building == null || drops.isEmpty() || !isRawProducer(building.getBuildingType()) || building.getBuildingLevel() < 1)
        {
            return drops;
        }
        final double bonus = (outputMultiplier(building.getBuildingLevel()) - 1.0) * happinessShare(building);
        final List<ItemStack> boosted = new ArrayList<>();
        for (final ItemStack stack : drops)
        {
            if (stack.isEmpty())
            {
                continue;
            }
            // Stochastic rounding keeps the average exact even for single items
            final double exact = stack.getCount() * (1.0 + bonus);
            int total = (int) exact;
            if (random.nextDouble() < exact - total)
            {
                total++;
            }
            while (total > 0)
            {
                final int size = Math.min(total, stack.getMaxStackSize());
                boosted.add(stack.copyWithCount(size));
                total -= size;
            }
        }
        return boosted;
    }

    /**
     * Share of the output bonus the workers earn: full when happy, down to 30% when miserable.
     */
    private static double happinessShare(final IBuilding building)
    {
        final Set<ICitizenData> workers = building.getAllAssignedCitizen();
        if (workers.isEmpty())
        {
            return 1.0;
        }
        double happiness = 0;
        for (final ICitizenData citizen : workers)
        {
            happiness += citizen.getCitizenHappinessHandler().getHappiness(building.getColony(), citizen);
        }
        happiness /= workers.size();
        return happinessShare(happiness);
    }

    /**
     * Share of the output bonus a worker this happy earns: full at the configured happiness, down to 30%.
     */
    public static double happinessShare(final double happiness)
    {
        final double full = Config.HAPPINESS_FOR_FULL_BONUS.get();
        return full <= 0 ? 1.0 : Mth.clamp(happiness / full, MIN_HAPPINESS_SHARE, 1.0);
    }
}
