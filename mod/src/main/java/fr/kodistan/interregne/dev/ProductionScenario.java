package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.production.Huts;
import fr.kodistan.interregne.region.CrateStores;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The transforming huts on their own keys: the sawmill and stonemason work the warehouse's surplus with their default
 * recipes, the smeltery and glassblower burn the warehouse's fuel, the plantation yields its hybrid plants. Each
 * section is independent and reports SKIP when its building is not in the colony.
 */
public class ProductionScenario implements Scenario
{
    private static final String SAWMILL     = "sawmill";
    private static final String STONEMASON  = "stonemason";
    private static final String SMELTERY    = "smeltery";
    private static final String GLASSBLOWER = "glassblower";

    private final List<String> present = new ArrayList<>();

    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        if (racks == null)
        {
            throw new IllegalStateException("needs a warehouse");
        }
        present.addAll(colony.getBuildingManager().getBuildings().values().stream().map(Huts::id).distinct().sorted().toList());
        lines.add("buildings: " + present);

        // Enough of each input to sit well above the reserve, nothing of each output anywhere
        give(racks, lines, SAWMILL, new ItemStack(Items.OAK_LOG, 128));
        clear(racks, Items.OAK_PLANKS);
        clear(racks, Items.STICK);
        give(racks, lines, STONEMASON, new ItemStack(Items.STONE, 128));
        clear(racks, Items.STONE_BRICKS);
        give(racks, lines, SMELTERY, new ItemStack(Items.RAW_IRON, 64));
        give(racks, lines, SMELTERY, new ItemStack(Items.COAL, 32));
        clear(racks, Items.IRON_INGOT);
        give(racks, lines, GLASSBLOWER, new ItemStack(Items.SAND, 64));
        give(racks, lines, GLASSBLOWER, new ItemStack(Items.COAL, 32));
        clear(racks, Items.GLASS);
        clear(racks, Items.SUGAR_CANE);
        clear(racks, Items.CACTUS);
        clear(racks, Items.BAMBOO);
        lines.add("craft target " + Config.CRAFT_TARGET.get() + ", reserve " + Config.CRAFT_RESERVE.get()
          + ", budget per level " + Config.CRAFT_SURPLUS_PER_LEVEL.get() + ", production per level " + Config.PRODUCTION_PER_LEVEL.get());
        return lines;
    }

    private void give(final IItemHandler racks, final List<String> lines, final String hut, final ItemStack stack)
    {
        if (!present.contains(hut))
        {
            return;
        }
        final ItemStack rest = ItemHandlerHelper.insertItemStacked(racks, stack, false);
        lines.add(hut + ": warehouse += " + (stack.getCount() - rest.getCount()) + " " + stack.getHoverName().getString());
    }

    @Override
    public String snapshot(final IColony colony, final ServerLevel level)
    {
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        return section(SAWMILL, "logs " + count(racks, Items.OAK_LOG) + " -> planks " + count(racks, Items.OAK_PLANKS) + ", sticks " + count(racks, Items.STICK))
          + section(STONEMASON, "stone " + count(racks, Items.STONE) + " -> bricks " + count(racks, Items.STONE_BRICKS))
          + section(SMELTERY, "raw iron " + count(racks, Items.RAW_IRON) + ", coal " + count(racks, Items.COAL) + " -> ingots " + count(racks, Items.IRON_INGOT))
          + section(GLASSBLOWER, "sand " + count(racks, Items.SAND) + ", coal " + count(racks, Items.COAL) + " -> glass " + count(racks, Items.GLASS))
          + section(Huts.PLANTATION, "cane " + count(racks, Items.SUGAR_CANE) + ", cactus " + count(racks, Items.CACTUS) + ", bamboo " + count(racks, Items.BAMBOO));
    }

    @Override
    public List<String> verdict(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        lines.add(check(SAWMILL, count(racks, Items.OAK_PLANKS) + count(racks, Items.STICK) > 0,
          "the sawmill worked its default recipe (planks " + count(racks, Items.OAK_PLANKS) + ", sticks " + count(racks, Items.STICK) + ")"));
        lines.add(check(STONEMASON, count(racks, Items.STONE_BRICKS) > 0,
          "the stonemason worked its default recipe (stone bricks " + count(racks, Items.STONE_BRICKS) + ")"));
        lines.add(check(SMELTERY, count(racks, Items.IRON_INGOT) > 0,
          "the smeltery smelted on warehouse fuel (ingots " + count(racks, Items.IRON_INGOT) + ")"));
        lines.add(check(GLASSBLOWER, count(racks, Items.GLASS) > 0,
          "the glassblower smelted on warehouse fuel (glass " + count(racks, Items.GLASS) + ")"));
        lines.add(check(Huts.PLANTATION, count(racks, Items.SUGAR_CANE) + count(racks, Items.CACTUS) + count(racks, Items.BAMBOO) > 0,
          "the plantation yielded its plants (cane " + count(racks, Items.SUGAR_CANE) + ", cactus " + count(racks, Items.CACTUS)
            + ", bamboo " + count(racks, Items.BAMBOO) + ")"));
        return lines;
    }

    private String section(final String hut, final String what)
    {
        return present.contains(hut) ? hut + ": " + what + " | " : "";
    }

    private String check(final String hut, final boolean ok, final String what)
    {
        return present.contains(hut) ? (ok ? "PASS " : "FAIL ") + what : "SKIP no " + hut + " in this colony";
    }

    private static int count(final IItemHandler handler, final Item item)
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

    private static int clear(final IItemHandler handler, final Item item)
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
