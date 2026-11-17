package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * What the Dépôt Régional does once built: one per colony, it produces the colony's crate on its own and files it in
 * the warehouse racks, or keeps it inside when there is no warehouse. Its level is the building's, raised by a builder.
 */
public final class DepotRegional
{
    public static final int MAX_LEVEL = BuildingDepotRegional.MAX_LEVEL;

    private static final double REACH_SQ = 8 * 8;

    private DepotRegional()
    {
    }

    /**
     * The colony's built Dépôt (the highest, should a second one exist), or null while there is none.
     */
    @Nullable
    public static BuildingDepotRegional of(final IColony colony)
    {
        BuildingDepotRegional best = null;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingDepotRegional depot && depot.getBuildingLevel() > 0
                  && (best == null || depot.getBuildingLevel() > best.getBuildingLevel()))
            {
                best = depot;
            }
        }
        return best;
    }

    /**
     * The colony whose Dépôt stands here.
     */
    @Nullable
    public static IColony ownerOf(final ServerLevel level, final BlockPos pos)
    {
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            final BuildingDepotRegional depot = of(colony);
            if (depot != null && pos.equals(depot.getPosition()))
            {
                return colony;
            }
        }
        return null;
    }

    /**
     * Opens the window of the Dépôt at this position, for a player allowed to use the colony's huts.
     */
    public static void open(final ServerPlayer player, final BlockPos pos)
    {
        final ServerLevel level = player.serverLevel();
        final IColony colony = ownerOf(level, pos);
        if (colony == null)
        {
            player.sendSystemMessage(Component.literal("Ce dépôt n'est pas encore construit : il produira dès que le bâtisseur aura fini.")
                                       .withStyle(ChatFormatting.GRAY));
        }
        else if (!colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            final Crate crate = RegionData.get(player.server).regionOf(colony);
            player.sendSystemMessage(Component.literal("Dépôt de " + colony.getName() + (crate == null ? "." : ", " + crate.regionName() + "."))
                                       .withStyle(ChatFormatting.GRAY));
        }
        else
        {
            sendWindow(player, colony, pos);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.1F);
        }
    }

    public static void onAction(final RegionActionPayload payload, final ServerPlayer player)
    {
        final ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(payload.block().getCenter()) > REACH_SQ)
        {
            return;
        }
        if (payload.action() == RegionActionPayload.COMPTOIR_OPEN)
        {
            open(player, payload.block());
            return;
        }
        final IColony colony = ownerOf(level, payload.block());
        if (colony == null || !colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            return;
        }
        final RegionData data = RegionData.get(player.server);
        final RegionData.Comptoir comptoir = data.comptoir(colony);
        final IItemHandler purse = new PlayerMainInvWrapper(player.getInventory());

        if (payload.action() == RegionActionPayload.COMPTOIR_TAKE && comptoir.stock > 0)
        {
            final Crate crate = data.regionOf(colony);
            if (crate != null)
            {
                int given = 0;
                while (given < comptoir.stock)
                {
                    final ItemStack stack = crate.stack(Math.min(Crates.STACK_SIZE, comptoir.stock - given));
                    final int offered = stack.getCount();
                    final int placed = offered - ItemHandlerHelper.insertItemStacked(purse, stack, false).getCount();
                    given += placed;
                    if (placed < offered)
                    {
                        break;
                    }
                }
                comptoir.stock -= given;
                data.touch();
                if (given == 0)
                {
                    player.sendSystemMessage(Component.literal("Ton inventaire est plein.").withStyle(ChatFormatting.RED));
                }
            }
        }
        sendWindow(player, colony, payload.block());
    }

    private static void sendWindow(final ServerPlayer player, final IColony colony, final BlockPos pos)
    {
        final RegionData data = RegionData.get(player.server);
        final RegionData.Comptoir comptoir = data.comptoir(colony);
        final Crate crate = data.regionOf(colony);
        final boolean top = comptoir.level >= MAX_LEVEL;
        final int interval = Regions.ticksPerCrate(comptoir.level);
        final int secondsLeft = (int) Math.max(0, (interval - Math.min(comptoir.progress, interval)) / 20);
        PacketDistributor.sendToPlayer(player, new OpenComptoirPayload(pos, colony.getName(), crate == null ? "" : crate.id(), comptoir.level,
          Regions.secondsPerCrate(comptoir.level), top ? 0 : Regions.secondsPerCrate(comptoir.level + 1), top ? -1 : -2,
          comptoir.stock, Config.COMPTOIR_STORAGE.get(), CrateStores.warehouse(colony) != null, secondsLeft,
          Wallet.value(new PlayerMainInvWrapper(player.getInventory()))));
    }
}
