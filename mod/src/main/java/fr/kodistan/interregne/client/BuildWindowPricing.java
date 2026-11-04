package fr.kodistan.interregne.client;

import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import fr.kodistan.interregne.colony.TownHallCap;
import fr.kodistan.interregne.region.CrateRequirements;
import org.jetbrains.annotations.Nullable;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.Wallet;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import fr.kodistan.interregne.colony.Yields;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.WindowConstants.*;

/**
 * The hut's build window lists the materials a builder would need. With instant building that list is wrong:
 * show the construction price instead, put it on the buttons, and disable what the player cannot afford.
 * Lives outside the mixin package because mixin packages may not hold classes referenced at runtime.
 */
public final class BuildWindowPricing
{
    private static final int WHITE = Color.getByName("white", 0);
    private static final int RED   = Color.getByName("red", 0);
    private static final int GOLD  = Color.getByName("gold", 0);
    private static final int AQUA  = Color.getByName("aqua", 0);

    /**
     * Amount of an information row: no price, no icon.
     */
    private static final int INFO = -1;

    /**
     * One line of the price list.
     */
    private record PriceRow(Component label, int amount, boolean affordable, ItemStack icon)
    {
        PriceRow(final Component label, final int amount, final boolean affordable)
        {
            this(label, amount, affordable, ItemStack.EMPTY);
        }
    }

    private BuildWindowPricing()
    {
    }

    /**
     * Server configs only exist on the client while connected; fall back to MineColonies' own window otherwise.
     */
    public static boolean enabled()
    {
        try
        {
            return Config.INSTANT_BUILD.get() && Minecraft.getInstance().player != null;
        }
        catch (final IllegalStateException e)
        {
            return false;
        }
    }

    public static void apply(final WindowBuildBuilding window, final IBuildingView building)
    {
        final Player player = Minecraft.getInstance().player;
        final Item payment = BuildCosts.paymentItem();
        final boolean free = player.isCreative();
        final long carried = Wallet.value(new PlayerMainInvWrapper(player.getInventory()));
        // Regional treasury: constructions are paid from it first, so it counts towards what the player can afford
        final long treasury = ClientTreasury.of(building.getColony());
        final long owned = carried + treasury;
        final List<PriceRow> rows = new ArrayList<>();

        // No builders any more: the builder picker is meaningless
        window.findPaneOfTypeByID(DROPDOWN_BUILDER_ID, DropDownList.class).hide();
        // No hut blocks any more either: picking the hut back up as a block is meaningless
        window.findPaneOfTypeByID(BUTTON_PICKUP_BUILDING, Button.class).hide();

        final Button build = window.findPaneOfTypeByID(BUTTON_BUILD, Button.class);
        if (build.isVisible())
        {
            final int level = building.getBuildingLevel();
            final boolean repair = level == building.getBuildingMaxLevel();
            final int cost = repair ? BuildCosts.forRepair() : BuildCosts.forLevel(level + 1);
            final String action = repair ? "Réparer" : level == 0 ? "Construire" : "Améliorer";
            final int seconds = repair ? BuildCosts.secondsForFlat() : BuildCosts.secondsForLevel(level + 1);
            final String text = repair ? "Réparer"
                                  : level == 0 ? "Construire (niveau 1)"
                                      : "Améliorer au niveau " + (level + 1);
            final Component label = Component.literal(text + " · " + BuildCosts.formatSeconds(seconds));
            priceButton(build, action, cost, free, owned);
            rows.add(new PriceRow(label, cost, free || owned >= cost));
            final ITownHallView townHall = building.getColony().getTownHall();
            final String refusal = repair ? null
              : TownHallCap.refusal(building instanceof ITownHallView, level + 1, townHall == null ? 0 : townHall.getBuildingLevel());
            if (refusal != null)
            {
                build.setText(Component.literal("Hôtel de ville niv. " + (level + 1) + " requis"));
                build.setEnabled(false);
                rows.add(0, new PriceRow(Component.literal(refusal), INFO, false));
            }
        }

        // Regional crates the next level needs (red: not enough on the player)
        CrateRows.forBuilding(building, build.isVisible()).forEach(c -> rows.add(new PriceRow(c.label(), c.count(), c.available(), c.icon())));

        final Button repairButton = window.findPaneOfTypeByID(BUTTON_REPAIR, Button.class);
        if (repairButton.isVisible())
        {
            final int cost = BuildCosts.forRepair();
            final String action = building.isDeconstructed() ? "Reconstruire" : "Réparer";
            priceButton(repairButton, action, cost, free, owned);
            rows.add(new PriceRow(Component.literal(action + " · " + BuildCosts.formatSeconds(BuildCosts.secondsForFlat())), cost, free || owned >= cost));
        }

        final Button demolish = window.findPaneOfTypeByID(BUTTON_DECONSTRUCT_BUILDING, Button.class);
        if (demolish.isVisible())
        {
            final int cost = Config.DEMOLITION_COST.get();
            priceButton(demolish, "Démolir", cost, free, owned);
            rows.add(new PriceRow(Component.literal("Démolir · " + BuildCosts.formatSeconds(Config.DEMOLITION_SECONDS.get())), cost, free || owned >= cost));
        }

        addProductionRows(rows, building);

        rows.add(new PriceRow(Component.literal("Trésor régional"), (int) Math.min(Integer.MAX_VALUE, treasury), true));
        rows.add(new PriceRow(free ? Component.literal("Mode créatif : gratuit") : Component.literal("Couronnes sur toi"), (int) Math.min(Integer.MAX_VALUE, carried), true));

        showRows(window, rows, payment);
    }

    /**
     * The decoration build window: its price by size instead of the builder's material list.
     */
    public static void applyDecoration(final BOWindow window, final long blocks, @Nullable final IColonyView colony)
    {
        final Player player = Minecraft.getInstance().player;
        final boolean free = player.isCreative();
        final long carried = Wallet.value(new PlayerMainInvWrapper(player.getInventory()));
        final long treasury = ClientTreasury.of(colony);
        final long owned = carried + treasury;
        final int cost = BuildCosts.forDecoration(blocks);
        final List<PriceRow> rows = new ArrayList<>();

        final DropDownList builders = window.findPaneOfTypeByID(DROPDOWN_BUILDER_ID, DropDownList.class);
        if (builders != null)
        {
            builders.hide();
        }
        final Button build = window.findPaneOfTypeByID(BUTTON_BUILD, Button.class);
        if (build != null)
        {
            priceButton(build, "Construire", cost, free, owned);
        }
        rows.add(new PriceRow(Component.literal("Décoration · " + blocks + " blocs · " + BuildCosts.formatSeconds(BuildCosts.secondsForFlat())), cost, free || owned >= cost));
        CrateRows.forCrates(CrateRequirements.forDecoration(blocks)).forEach(c -> rows.add(new PriceRow(c.label(), c.count(), c.available(), c.icon())));
        rows.add(new PriceRow(Component.literal("Trésor régional"), (int) Math.min(Integer.MAX_VALUE, treasury), true));
        rows.add(new PriceRow(free ? Component.literal("Mode créatif : gratuit") : Component.literal("Couronnes sur toi"), (int) Math.min(Integer.MAX_VALUE, carried), true));
        showRows(window, rows, BuildCosts.paymentItem());
    }

    private static void showRows(final BOWindow window, final List<PriceRow> rows, final Item payment)
    {
        final ScrollingList list = window.findPaneOfTypeByID(LIST_RESOURCES, ScrollingList.class);
        list.enable();
        list.show();
        list.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return rows.size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final PriceRow row = rows.get(index);
                final boolean last = index == rows.size() - 1;
                final Text name = rowPane.findPaneOfTypeByID(RESOURCE_NAME, Text.class);
                final Text quantity = rowPane.findPaneOfTypeByID(RESOURCE_QUANTITY_MISSING, Text.class);
                name.setText(row.label());
                if (row.amount() == INFO)
                {
                    quantity.setText(Component.empty());
                    name.setColors(AQUA);
                    rowPane.findPaneOfTypeByID(RESOURCE_ICON, ItemIcon.class).setItem(ItemStack.EMPTY);
                    return;
                }
                quantity.setText(Component.literal(Integer.toString(row.amount())));
                name.setColors(last ? GOLD : WHITE);
                quantity.setColors(row.affordable() ? (last ? GOLD : WHITE) : RED);
                rowPane.findPaneOfTypeByID(RESOURCE_ICON, ItemIcon.class).setItem(row.icon().isEmpty() ? new ItemStack(payment) : row.icon());
            }
        });
    }

    /**
     * Current production and speed, and what the next level brings: the reason to upgrade.
     */
    private static void addProductionRows(final List<PriceRow> rows, final IBuildingView building)
    {
        final int level = building.getBuildingLevel();
        final boolean producer = Yields.isRawProducer(building.getBuildingType());
        final boolean canUpgrade = level < building.getBuildingMaxLevel();
        if (producer)
        {
            final String now = level == 0 ? "—" : "×" + format(Yields.outputMultiplier(level));
            final String next = canUpgrade ? " → ×" + format(Yields.outputMultiplier(level + 1)) : "";
            rows.add(new PriceRow(Component.literal("Production " + now + next), INFO, true));
        }
        final String now = level == 0 ? "—" : "×" + format(Yields.speedMultiplier(level));
        final String next = canUpgrade ? " → ×" + format(Yields.speedMultiplier(level + 1)) : "";
        rows.add(new PriceRow(Component.literal("Vitesse de travail " + now + next), INFO, true));
    }

    private static String format(final double value)
    {
        return String.format(java.util.Locale.FRANCE, value == Math.floor(value) ? "%.0f" : "%.2f", value).replaceAll("0$", "");
    }

    private static void priceButton(final Button button, final String action, final int cost, final boolean free, final long owned)
    {
        button.setText(Component.literal(free || cost == 0 ? action : action + " (" + cost + ")"));
        button.setEnabled(free || owned >= cost);
    }
}
