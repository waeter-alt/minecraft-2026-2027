package fr.kodistan.interregne.client;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.region.CrateRequirements;
import fr.kodistan.interregne.region.CrateStores;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The regional crates the hut's next build or upgrade needs, for the build window. Only the player's own inventory is
 * counted here (the client does not see the warehouse racks), so a red row may still be covered by the warehouse.
 */
public final class CrateRows
{
    /**
     * One crate type: its icon, name, how many are needed and whether the player carries enough.
     */
    public record Row(ItemStack icon, Component label, int count, boolean available)
    {
    }

    private CrateRows()
    {
    }

    public static List<Row> forBuilding(final IBuildingView building, final boolean buildable)
    {
        final List<Row> rows = new ArrayList<>();
        final Player player = Minecraft.getInstance().player;
        final int level = building.getBuildingLevel();
        if (!buildable || player == null || level >= building.getBuildingMaxLevel())
        {
            return rows;
        }
        final PlayerMainInvWrapper inventory = new PlayerMainInvWrapper(player.getInventory());
        CrateRequirements.forLevel(level + 1).forEach((crate, count) -> rows.add(new Row(crate.stack(1), Component.literal(crate.displayName()), count,
          player.isCreative() || CrateStores.count(inventory, crate) >= count)));
        return rows;
    }
}
