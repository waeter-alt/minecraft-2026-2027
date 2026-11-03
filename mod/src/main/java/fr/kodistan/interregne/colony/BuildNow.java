package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.PlacementModePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Construire maintenant" from the build tool: a hut placed in that mode starts its construction right away,
 * paid like a click on the hut's Build button.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class BuildNow
{
    /**
     * Mode each player picked for their next placement, set by {@link PlacementModePayload}.
     */
    private static final Map<UUID, Boolean> PENDING = new ConcurrentHashMap<>();

    private BuildNow()
    {
    }

    public static void onPayload(final PlacementModePayload payload, final IPayloadContext context)
    {
        PENDING.put(context.player().getUUID(), payload.buildNow());
    }

    /**
     * Called after MineColonies handled a survival build tool placement.
     */
    public static void afterPlacement(final Level world, final Player player, final BlockPos pos)
    {
        final Boolean buildNow = PENDING.remove(player.getUUID());
        if (buildNow == null || !buildNow || !(player instanceof ServerPlayer serverPlayer))
        {
            return;
        }
        // Only a hut that was just created: a pasted, already built hut or a refused placement leaves nothing to start
        final IBuilding building = IColonyManager.getInstance().getBuilding(world, pos);
        if (building == null || building.getBuildingLevel() > 0)
        {
            return;
        }
        BuildContext.begin(serverPlayer);
        try
        {
            building.requestUpgrade(serverPlayer, BlockPos.ZERO);
        }
        finally
        {
            BuildContext.end();
        }
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        PENDING.remove(event.getEntity().getUUID());
    }
}
