package fr.kodistan.interregne.garrison;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells each client which citizens it sees are bought guards, so they are drawn with our troop models. Sent when the
 * player starts tracking the entity: covers the purchase, chunk loads and relogs alike.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class GuardLooks
{
    private GuardLooks()
    {
    }

    @SubscribeEvent
    public static void onStartTracking(final PlayerEvent.StartTracking event)
    {
        if (!(event.getTarget() instanceof AbstractEntityCitizen entity) || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final ICitizenData citizen = entity.getCitizenData();
        if (citizen == null || citizen.getColony() == null)
        {
            return;
        }
        final GarrisonRoster.Guard guard = GarrisonRoster.get(player.server).of(citizen.getColony(), citizen);
        if (guard != null)
        {
            PacketDistributor.sendToPlayer(player, new GuardLookPayload(entity.getId(), guard.kind(), guard.level()));
        }
    }
}
