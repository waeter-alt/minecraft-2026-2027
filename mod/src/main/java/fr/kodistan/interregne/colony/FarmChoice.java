package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.FarmCropPayload;
import fr.kodistan.interregne.network.OpenFarmCropPayload;
import fr.kodistan.interregne.production.Farms;
import fr.kodistan.interregne.production.Huts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Right before a farm is built or upgraded, the player picks its crop in a window; the order waits here meanwhile,
 * with nothing paid yet, and starts once the choice comes back (closing the window cancels it).
 */
public final class FarmChoice
{
    private record Pending(IColony colony, IServerWorkOrder order)
    {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private FarmChoice()
    {
    }

    /**
     * Hold a farm's build or upgrade until its crop is chosen.
     *
     * @return true if the order now waits for the player's choice.
     */
    static boolean ask(final ServerPlayer player, final IColony colony, final IServerWorkOrder order)
    {
        if (!(order instanceof WorkOrderBuilding)
              || (order.getWorkOrderType() != WorkOrderType.BUILD && order.getWorkOrderType() != WorkOrderType.UPGRADE))
        {
            return false;
        }
        final IBuilding building = colony.getBuildingManager().getBuilding(order.getLocation());
        if (building == null || !Huts.FARMER.equals(Huts.id(building)))
        {
            return false;
        }
        PENDING.put(player.getUUID(), new Pending(colony, order));
        PacketDistributor.sendToPlayer(player, new OpenFarmCropPayload(order.getLocation(), Farms.cropOf(player.server, building).id(),
          order.getWorkOrderType() == WorkOrderType.UPGRADE));
        return true;
    }

    public static void onChoice(final FarmCropPayload payload, final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }
            final Pending pending = PENDING.remove(player.getUUID());
            if (pending == null || !pending.order().getLocation().equals(payload.hut()))
            {
                return;
            }
            final Farms.Crop crop = Farms.Crop.byId(payload.crop());
            if (crop == null)
            {
                player.sendSystemMessage(Component.literal("Construction de la ferme annulée.").withStyle(ChatFormatting.GRAY));
                return;
            }
            final IBuilding farm = pending.colony().getBuildingManager().getBuilding(payload.hut());
            if (farm == null)
            {
                return;
            }
            Farms.setCrop(player.server, farm, crop);
            try
            {
                InstantBuilder.proceed(pending.colony(), pending.order(), player);
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.error("Farm construction failed after the crop choice", e);
            }
        });
    }
}
