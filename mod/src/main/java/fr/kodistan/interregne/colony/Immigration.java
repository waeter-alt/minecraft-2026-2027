package fr.kodistan.interregne.colony;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Faster colony growth for a one-week game: on top of MineColonies' slow births (a child every 5 to 15 minutes,
 * who then has to grow up), an adult settler moves in at a steady pace whenever a bed is free.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Immigration
{
    private static final int CHECK_INTERVAL = 20;

    /**
     * Seconds elapsed since the last arrival, per colony id.
     */
    private static final Map<Integer, Integer> ELAPSED = new HashMap<>();

    private static int ticks;

    private Immigration()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (++ticks % CHECK_INTERVAL != 0)
        {
            return;
        }
        final int interval = Config.IMMIGRATION_SECONDS.get();
        if (interval <= 0)
        {
            return;
        }

        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (!colony.hasTownHall() || !colony.canMoveIn()
                  || colony.getCitizenManager().getCurrentCitizenCount() >= colony.getCitizenManager().getMaxCitizens())
            {
                // No room: the wait only starts once a bed is free
                ELAPSED.remove(colony.getID());
                continue;
            }
            final int elapsed = ELAPSED.merge(colony.getID(), CHECK_INTERVAL / 20, Integer::sum);
            if (elapsed >= interval)
            {
                ELAPSED.remove(colony.getID());
                welcomeSettler(colony);
            }
        }
    }

    private static void welcomeSettler(final IColony colony)
    {
        int women = 0;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            women += citizen.isFemale() ? 1 : 0;
        }
        final ICitizenData settler = colony.getCitizenManager().createAndRegisterCivilianData();
        // Balance the colony: the new settler is of the less common gender
        settler.setGenderAndGenerateName(women < colony.getCitizenManager().getCurrentCitizenCount() / 2.0);
        colony.getCitizenManager().spawnOrCreateCitizen(settler, colony.getWorld(), colony.getBuildingManager().getTownHall().getPosition());
        IMinecoloniesAPI.getInstance().getEventBus().post(new CitizenAddedModEvent(settler, CitizenAddedModEvent.CitizenAddedSource.HIRED));

        colony.getMessagePlayerEntities().forEach(player -> {
            player.sendSystemMessage(Component.literal("Un nouveau colon s'installe à " + colony.getName() + " : " + settler.getName())
                                       .withStyle(ChatFormatting.AQUA));
            player.playNotifySound(SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.6F, 1.0F);
        });
    }
}
