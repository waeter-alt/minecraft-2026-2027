package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import fr.kodistan.interregne.production.Huts;
import fr.kodistan.interregne.region.CrateStores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The beekeeper must run on its own: the hut places its hives (one per level, bees and honey inside, flowers around,
 * scenery nobody touches) and honeycomb/honey land in the warehouse. Unbreakability is a player action: hand-test item.
 */
public class ApiaryScenario implements Scenario
{
    private BuildingBeekeeper apiary;
    private int               startComb;
    private int               startBottles;
    private int               maxHives;
    private int               maxBees;
    private int               maxFlowers;

    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        apiary = colony.getBuildingManager().getBuildings().values().stream()
          .filter(b -> Huts.id(b).equals(Huts.BEEKEEPER) && b instanceof BuildingBeekeeper)
          .map(b -> (BuildingBeekeeper) b).findFirst().orElse(null);
        if (apiary == null)
        {
            throw new IllegalStateException("needs a beekeeper");
        }
        lines.add("beekeeper level " + apiary.getBuildingLevel() + ", staffed " + Huts.staffed(apiary)
          + ", hives already there: " + apiary.getHives().size() + " of " + wanted());

        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        if (racks == null)
        {
            throw new IllegalStateException("needs a warehouse");
        }
        startComb = clear(racks, Items.HONEYCOMB);
        startBottles = clear(racks, Items.HONEY_BOTTLE);
        lines.add("removed " + (startComb + startBottles) + " hive goods from the warehouse");
        return lines;
    }

    @Override
    public String snapshot(final IColony colony, final ServerLevel level)
    {
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        maxHives = Math.max(maxHives, apiary.getHives().size());
        int bees = 0;
        int honeyed = 0;
        int flowers = 0;
        for (final BlockPos hive : apiary.getHives())
        {
            if (level.getBlockState(hive).getBlock() instanceof BeehiveBlock)
            {
                honeyed += level.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL);
                if (level.getBlockEntity(hive) instanceof BeehiveBlockEntity entity)
                {
                    bees += entity.getOccupantCount();
                }
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    final Block block = level.getBlockState(hive.relative(side)).getBlock();
                    if (block == Blocks.POPPY || block == Blocks.DANDELION || block == Blocks.CORNFLOWER
                          || block == Blocks.OXEYE_DAISY || block == Blocks.ALLIUM)
                    {
                        flowers++;
                    }
                }
            }
        }
        maxBees = Math.max(maxBees, bees);
        maxFlowers = Math.max(maxFlowers, flowers);
        return "hives " + apiary.getHives().size() + "/" + wanted()
          + " | bees " + bees + " | honey levels " + honeyed
          + " | flowers " + flowers
          + " | warehouse comb " + count(racks, Items.HONEYCOMB) + " bottles " + count(racks, Items.HONEY_BOTTLE);
    }

    @Override
    public List<String> verdict(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        lines.add(check(maxHives >= wanted(), "the hut placed its hives (up to " + maxHives + " of " + wanted() + " wanted)"));
        lines.add(check(maxBees >= maxHives * 2, "every hive holds its bees (" + maxBees + " bees in " + maxHives + " hives at best)"));
        if (Huts.staffed(apiary))
        {
            final int comb = count(racks, Items.HONEYCOMB);
            final int bottles = count(racks, Items.HONEY_BOTTLE);
            lines.add(check(comb + bottles > 0, "hybrid production landed in the warehouse (" + comb + " honeycomb, " + bottles + " bottles)"));
        }
        else
        {
            lines.add("SKIP hybrid production: the beekeeper has no worker");
        }
        lines.add("INFO flowers around the hives (scenery, random): " + maxFlowers);
        lines.add("INFO unbreakable hives need a hand test (survival pickaxe and shears on a hive)");
        return lines;
    }

    private int wanted()
    {
        return Math.min(apiary.getBuildingLevel(), apiary.getMaximumHives());
    }

    private static String check(final boolean ok, final String what)
    {
        return (ok ? "PASS " : "FAIL ") + what;
    }

    private static int count(final IItemHandler handler, final net.minecraft.world.item.Item item)
    {
        int total = 0;
        if (handler != null)
        {
            for (int slot = 0; slot < handler.getSlots(); slot++)
            {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.is(item))
                {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static int clear(final IItemHandler handler, final net.minecraft.world.item.Item item)
    {
        int removed = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(item))
            {
                removed += handler.extractItem(slot, stack.getCount(), false).getCount();
            }
        }
        return removed;
    }
}
