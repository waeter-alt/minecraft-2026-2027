package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import fr.kodistan.interregne.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Buildings from level 2 need regional crates on top of their Couronnes. The colony warehouse racks are used first,
 * then the requester's inventory. Shared with the client, which reads the same synced config to show the needs.
 */
public final class CrateRequirements
{
    private CrateRequirements()
    {
    }

    /**
     * Crates needed to build or upgrade a building to this level; empty below level 2.
     */
    public static Map<Crate, Integer> forLevel(final int targetLevel)
    {
        final String recipe = switch (targetLevel)
        {
            case 2 -> Config.CRATES_LEVEL_2.get();
            case 3 -> Config.CRATES_LEVEL_3.get();
            case 4 -> Config.CRATES_LEVEL_4.get();
            case 5 -> Config.CRATES_LEVEL_5.get();
            default -> "";
        };
        return parse(recipe);
    }

    /**
     * "bois:1,pierre:1" into crate counts; unknown types and bad numbers are skipped.
     */
    public static Map<Crate, Integer> parse(final String recipe)
    {
        final Map<Crate, Integer> needs = new EnumMap<>(Crate.class);
        for (final String part : recipe.split(","))
        {
            final String[] pair = part.split(":");
            if (pair.length != 2)
            {
                continue;
            }
            final Crate crate = Crate.byId(pair[0]);
            try
            {
                final int count = Integer.parseInt(pair[1].trim());
                if (crate != null && count > 0)
                {
                    needs.merge(crate, count, Integer::sum);
                }
            }
            catch (final NumberFormatException ignored)
            {
                // Skipped
            }
        }
        return needs;
    }

    /**
     * "1 caisse de bois, 2 caisses de briques"
     */
    public static String describe(final Map<Crate, Integer> crates)
    {
        final List<String> parts = new ArrayList<>();
        crates.forEach((crate, count) -> parts.add(crate.count(count)));
        return String.join(", ", parts);
    }

    /**
     * Check a work order's crates before anything is charged. A refused plan has already told the player what is missing.
     */
    public static Plan plan(final IColony colony, final IServerWorkOrder order, @Nullable final ServerPlayer player)
    {
        if (player == null || player.isCreative() || !(order instanceof WorkOrderBuilding)
              || (order.getWorkOrderType() != WorkOrderType.BUILD && order.getWorkOrderType() != WorkOrderType.UPGRADE))
        {
            return Plan.NONE;
        }
        return plan(colony, player, forLevel(order.getTargetLevel()));
    }

    /**
     * Check that these crates are available, on the player then in the colony warehouse.
     */
    public static Plan plan(final IColony colony, final ServerPlayer player, final Map<Crate, Integer> needs)
    {
        if (needs.isEmpty())
        {
            return Plan.NONE;
        }
        final IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());
        final IItemHandler warehouse = CrateStores.warehouseRacks(colony);
        final Map<Crate, Integer> missing = new EnumMap<>(Crate.class);
        needs.forEach((crate, count) -> {
            final int available = CrateStores.count(inventory, crate) + CrateStores.count(warehouse, crate);
            if (available < count)
            {
                missing.put(crate, count - available);
            }
        });
        if (!missing.isEmpty())
        {
            player.sendSystemMessage(Component.literal("Caisses manquantes pour ce chantier : " + describe(missing) + ".").withStyle(ChatFormatting.RED));
            player.sendSystemMessage(Component.literal("Il faut en tout " + describe(needs) + ", prises sur toi puis dans l'entrepôt de la colonie.")
                                       .withStyle(ChatFormatting.GRAY));
            return Plan.REFUSED;
        }
        return new Plan(false, colony, player, needs);
    }

    /**
     * The crates a work order takes. {@link #take()} once the Couronnes are paid, {@link #refund()} if the order then fails.
     */
    public static final class Plan
    {
        public static final Plan NONE    = new Plan(false, null, null, Collections.emptyMap());
        static final Plan REFUSED = new Plan(true, null, null, Collections.emptyMap());

        private final boolean             refused;
        @Nullable
        private final IColony             colony;
        @Nullable
        private final ServerPlayer        player;
        private final Map<Crate, Integer> needs;
        private final Map<Crate, Integer> taken = new EnumMap<>(Crate.class);

        private Plan(final boolean refused, @Nullable final IColony colony, @Nullable final ServerPlayer player, final Map<Crate, Integer> needs)
        {
            this.refused = refused;
            this.colony = colony;
            this.player = player;
            this.needs = needs;
        }

        /**
         * True when crates are missing: the order must be dropped, nothing has been charged.
         */
        public boolean refused()
        {
            return refused;
        }

        /**
         * Take the crates, from the colony warehouse first, then from the player.
         */
        public void take()
        {
            if (player == null || colony == null)
            {
                return;
            }
            final IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());
            final IItemHandler warehouse = CrateStores.warehouseRacks(colony);
            needs.forEach((crate, count) -> {
                int got = CrateStores.extract(warehouse, crate, count);
                got += CrateStores.extract(inventory, crate, count - got);
                taken.put(crate, got);
            });
            player.sendSystemMessage(Component.literal("Caisses livrées au chantier : " + describe(taken) + ".").withStyle(ChatFormatting.GRAY));
        }

        /**
         * Give the taken crates back to the player.
         */
        public void refund()
        {
            if (player == null)
            {
                return;
            }
            final IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());
            taken.forEach((crate, count) -> {
                for (int left = count; left > 0; left -= Crates.STACK_SIZE)
                {
                    final ItemStack rest = ItemHandlerHelper.insertItemStacked(inventory, crate.stack(Math.min(left, Crates.STACK_SIZE)), false);
                    if (!rest.isEmpty())
                    {
                        player.drop(rest, false);
                    }
                }
            });
            taken.clear();
        }
    }
}
