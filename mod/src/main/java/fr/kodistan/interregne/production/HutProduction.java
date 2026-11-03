package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;

/**
 * The guiding rule of production huts: once built and staffed, a hut yields something within minutes, with no setup.
 * Breeders, the fisher and the farm produce by level whatever happens to their animals, water or fields; the restaurant
 * cooks without fuel; saplings around a forester grow faster. Real animals, water and fields remain as scenery.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class HutProduction
{
    private static final int KITCHEN_TICKS = 100;
    private static final int FOREST_TICKS  = 200;

    private static long now;

    private HutProduction()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        now++;
        final boolean kitchen = now % KITCHEN_TICKS == 0;
        final boolean forest = now % FOREST_TICKS == 0;
        final boolean cycle = now % (Config.PRODUCTION_SECONDS.get() * 20L) == 0;
        if (!kitchen && !forest && !cycle)
        {
            return;
        }
        for (final IColony colony : Huts.colonies())
        {
            if (!(colony.getWorld() instanceof ServerLevel level))
            {
                continue;
            }
            for (final IBuilding building : new ArrayList<>(colony.getBuildingManager().getBuildings().values()))
            {
                if (!Huts.staffed(building) || !level.isLoaded(building.getPosition()))
                {
                    continue;
                }
                try
                {
                    tick(colony, level, building, kitchen, forest, cycle);
                }
                catch (final RuntimeException e)
                {
                    InterregneMod.LOGGER.error("Production of {} failed", building.getPosition(), e);
                }
            }
        }
    }

    private static void tick(final IColony colony, final ServerLevel level, final IBuilding building, final boolean kitchen, final boolean forest, final boolean cycle)
    {
        final String id = Huts.id(building);
        if (id.equals(Huts.COOK))
        {
            if (kitchen)
            {
                Kitchen.tick(building);
            }
            if (cycle)
            {
                Kitchen.cookSurplus(colony, building);
            }
            return;
        }
        if (id.equals(Huts.LUMBERJACK))
        {
            if (forest)
            {
                Forest.grow(level, building);
            }
            return;
        }
        if (!cycle)
        {
            return;
        }
        final int amount = building.getBuildingLevel() * Config.PRODUCTION_PER_LEVEL.get();
        final RandomSource random = level.random;
        switch (id)
        {
            case Huts.CHICKEN -> produce(building, Items.CHICKEN, amount, Items.FEATHER, 0.5, Items.EGG, 0.5, random);
            case Huts.COWBOY -> produce(building, Items.BEEF, amount, Items.LEATHER, 0.5, null, 0, random);
            case Huts.SHEPHERD -> produce(building, Items.MUTTON, amount, Items.WHITE_WOOL, 0.5, null, 0, random);
            case Huts.SWINEHERD -> produce(building, Items.PORKCHOP, amount, null, 0, null, 0, random);
            case Huts.RABBIT -> produce(building, Items.RABBIT, amount, Items.RABBIT_HIDE, 0.3, null, 0, random);
            case Huts.FISHERMAN -> {
                int salmon = 0;
                for (int i = 0; i < amount; i++)
                {
                    salmon += random.nextDouble() < 0.3 ? 1 : 0;
                }
                Huts.give(building, new ItemStack(Items.COD, amount - salmon));
                Huts.give(building, new ItemStack(Items.SALMON, salmon));
            }
            case Huts.FARMER -> Farms.produce(colony, building);
            default -> {
            }
        }
    }

    /**
     * The main goods, plus up to two by-products, each with a chance per unit of main goods.
     */
    private static void produce(
      final IBuilding building,
      final Item main,
      final int amount,
      final Item first,
      final double firstChance,
      final Item second,
      final double secondChance,
      final RandomSource random)
    {
        Huts.give(building, new ItemStack(main, amount));
        int firstCount = 0;
        int secondCount = 0;
        for (int i = 0; i < amount; i++)
        {
            firstCount += first != null && random.nextDouble() < firstChance ? 1 : 0;
            secondCount += second != null && random.nextDouble() < secondChance ? 1 : 0;
        }
        if (first != null)
        {
            Huts.give(building, new ItemStack(first, firstCount));
        }
        if (second != null)
        {
            Huts.give(building, new ItemStack(second, secondCount));
        }
    }

    /**
     * Set a production hut up after it is built or upgraded: restock the pen, plant the forest.
     */
    public static void afterConstruction(final ServerLevel level, final IColony colony, final IBuilding building)
    {
        final String id = Huts.id(building);
        if (Pens.isBreeder(building))
        {
            Pens.restock(level, building);
        }
        else if (id.equals(Huts.LUMBERJACK))
        {
            Forest.plant(level, building);
        }
    }
}
