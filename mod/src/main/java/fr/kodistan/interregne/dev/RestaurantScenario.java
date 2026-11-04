package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import fr.kodistan.interregne.production.Huts;
import fr.kodistan.interregne.region.CrateStores;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The restaurant must work from zero: empty kitchen, empty menu, no cooked food anywhere, only raw food in the
 * warehouse and hungry villagers. Within a day the menu should fill itself, food get cooked and villagers eat.
 */
public class RestaurantScenario implements Scenario
{
    /** Saturation the villagers start with: hungry enough to go and eat. */
    private static final double START_SATURATION = 2;

    /** Cure everyone first, to judge the restaurant alone, without the sickness that keeps villagers from eating. */
    private final boolean healthy;

    private IBuilding restaurant;
    private int startRaw;
    private double startSaturation;
    private int maxCookedInKitchen;
    private int maxMenu;

    public RestaurantScenario(final boolean healthy)
    {
        this.healthy = healthy;
    }

    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        restaurant = colony.getBuildingManager().getBuildings().values().stream().filter(b -> Huts.id(b).equals(Huts.COOK)).findFirst().orElse(null);
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        if (restaurant == null || racks == null)
        {
            throw new IllegalStateException("needs a restaurant and a warehouse (restaurant " + (restaurant != null) + ", warehouse " + (racks != null) + ")");
        }
        lines.add("restaurant level " + restaurant.getBuildingLevel() + ", staffed " + Huts.staffed(restaurant)
          + restaurant.getAllAssignedCitizen().stream().map(c -> " (" + c.getName() + ")").reduce("", String::concat));

        // Nothing cooked anywhere, an empty kitchen and menu
        clear(restaurant.getItemHandlerCap(), stack -> true);
        for (final ICitizenData cook : restaurant.getAllAssignedCitizen())
        {
            clear(cook.getInventory(), RestaurantScenario::isFood);
        }
        final RestaurantMenuModule menu = restaurant.getFirstModuleOccurance(RestaurantMenuModule.class);
        for (final ItemStorage dish : new ArrayList<>(menu.getMenu()))
        {
            menu.removeMenuItem(dish.getItemStack());
        }
        final int removed = clear(racks, RestaurantScenario::isCooked);
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            clear(citizen.getInventory(), RestaurantScenario::isCooked);
        }
        lines.add("emptied the kitchen and the menu, removed " + removed + " cooked food from the warehouse and every pocket");

        // Raw food in the warehouse
        for (final ItemStack raw : List.of(new ItemStack(Items.BEEF, 64), new ItemStack(Items.POTATO, 64), new ItemStack(Items.COD, 32)))
        {
            final ItemStack rest = ItemHandlerHelper.insertItemStacked(racks, raw, false);
            lines.add("warehouse += " + (raw.getCount() - rest.getCount()) + " " + raw.getHoverName().getString());
        }
        startRaw = count(racks, RestaurantScenario::isRaw);

        // Hungry villagers
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            citizen.setSaturation(START_SATURATION);
        }
        startSaturation = START_SATURATION;
        final long sick = colony.getCitizenManager().getCitizens().stream().filter(c -> c.getCitizenDiseaseHandler().isSick()).count();
        if (healthy)
        {
            colony.getCitizenManager().getCitizens().forEach(c -> c.getCitizenDiseaseHandler().cure());
            lines.add("cured the " + sick + " sick villagers");
        }
        else
        {
            lines.add(sick + " villagers sick at the start");
        }
        lines.add(colony.getCitizenManager().getCitizens().size() + " villagers set to saturation " + START_SATURATION);
        return lines;
    }

    @Override
    public String snapshot(final IColony colony, final ServerLevel level)
    {
        final RestaurantMenuModule menu = restaurant.getFirstModuleOccurance(RestaurantMenuModule.class);
        final IItemHandler kitchen = restaurant.getItemHandlerCap();
        final IItemHandler racks = CrateStores.warehouseRacks(colony);
        int cookPockets = 0;
        for (final ICitizenData cook : restaurant.getAllAssignedCitizen())
        {
            cookPockets += count(cook.getInventory(), RestaurantScenario::isCooked);
        }
        final int cooked = count(kitchen, RestaurantScenario::isCooked);
        maxCookedInKitchen = Math.max(maxCookedInKitchen, cooked + cookPockets);
        maxMenu = Math.max(maxMenu, menu.getMenu().size());
        return "menu " + menu.getMenu().size()
          + " | kitchen raw " + count(kitchen, RestaurantScenario::isRaw) + " cooked " + cooked
          + " | cook pockets cooked " + cookPockets
          + " | warehouse raw " + count(racks, RestaurantScenario::isRaw) + " cooked " + count(racks, RestaurantScenario::isCooked)
          + " | sick " + colony.getCitizenManager().getCitizens().stream().filter(c -> c.getCitizenDiseaseHandler().isSick()).count()
          + " | villagers " + String.format("avg sat %.1f", averageSaturation(colony)) + " hungry " + hungry(colony) + "/" + colony.getCitizenManager().getCitizens().size()
          + " | time " + level.getDayTime() % 24000
          + "\n      cook " + restaurant.getAllAssignedCitizen().stream().map(this::describe).reduce("", String::concat)
          + "\n      hungry " + colony.getCitizenManager().getCitizens().stream()
                                   .filter(c -> c.getSaturation() < CitizenConstants.LOW_SATURATION).map(this::describe).reduce("", String::concat);
    }

    /** Where a citizen is and what its two AIs are doing, to see where eating gets stuck. */
    private String describe(final ICitizenData citizen)
    {
        final String job = citizen.getJob() == null || citizen.getJob().getWorkerAI() == null ? "-"
          : String.valueOf(((com.minecolonies.core.entity.ai.workers.AbstractAISkeleton<?>) citizen.getJob().getWorkerAI()).getState());
        return citizen.getEntity().map(entity -> " [" + citizen.getName().split(" ")[0]
          + " ai=" + (entity instanceof com.minecolonies.core.entity.citizen.EntityCitizen ec ? ec.getCitizenAI().getState() : "?")
          + " job=" + job
          + " status=" + (citizen.getStatus() == null ? "-" : citizen.getStatus().getTranslationKey().replaceAll(".*\\.", ""))
          + " d=" + entity.blockPosition().distManhattan(restaurant.getPosition())
          + String.format(" sat=%.1f", citizen.getSaturation()) + "]").orElse(" [" + citizen.getName().split(" ")[0] + " no entity]");
    }

    @Override
    public List<String> verdict(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        final int raw = count(CrateStores.warehouseRacks(colony), RestaurantScenario::isRaw);
        final double saturation = averageSaturation(colony);
        lines.add(check(maxMenu > 0, "the menu filled itself (up to " + maxMenu + " dishes)"));
        lines.add(check(maxCookedInKitchen > 0, "food got cooked in the restaurant (up to " + maxCookedInKitchen + " at once)"));
        lines.add(check(raw < startRaw, "raw food left the warehouse (" + startRaw + " -> " + raw + ")"));
        lines.add(check(saturation > startSaturation + 1, String.format("villagers ate (avg saturation %.1f -> %.1f)", startSaturation, saturation)));
        lines.add(check(hungry(colony) == 0, "nobody is still hungry (" + hungry(colony) + " left)"));
        return lines;
    }

    private static String check(final boolean ok, final String what)
    {
        return (ok ? "PASS " : "FAIL ") + what;
    }

    private static boolean isFood(final ItemStack stack)
    {
        return stack.has(DataComponents.FOOD) || ItemStackUtils.ISCOOKABLE.test(stack);
    }

    private static boolean isRaw(final ItemStack stack)
    {
        return ItemStackUtils.ISCOOKABLE.test(stack);
    }

    private static boolean isCooked(final ItemStack stack)
    {
        return stack.has(DataComponents.FOOD) && !ItemStackUtils.ISCOOKABLE.test(stack);
    }

    private static int count(final IItemHandler handler, final java.util.function.Predicate<ItemStack> which)
    {
        int total = 0;
        if (handler != null)
        {
            for (int slot = 0; slot < handler.getSlots(); slot++)
            {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && which.test(stack))
                {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static int clear(final IItemHandler handler, final java.util.function.Predicate<ItemStack> which)
    {
        int removed = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && which.test(stack))
            {
                removed += handler.extractItem(slot, stack.getCount(), false).getCount();
            }
        }
        return removed;
    }

    private static double averageSaturation(final IColony colony)
    {
        return colony.getCitizenManager().getCitizens().stream().mapToDouble(ICitizenData::getSaturation).average().orElse(0);
    }

    private static long hungry(final IColony colony)
    {
        return colony.getCitizenManager().getCitizens().stream().filter(c -> c.getSaturation() < CitizenConstants.LOW_SATURATION).count();
    }
}
