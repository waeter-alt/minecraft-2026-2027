package fr.kodistan.interregne.garrison;

import com.minecolonies.api.colony.ICitizenData;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Bought guards keep food and happiness but skip sickness, sleep, mourning and graves: they are soldiers meant to die often.
 */
public final class BoughtGuards
{
    private BoughtGuards()
    {
    }

    public static boolean is(@Nullable final ICitizenData citizen)
    {
        if (citizen == null || citizen.getColony() == null || citizen.getColony().getWorld() == null)
        {
            return false;
        }
        final MinecraftServer server = citizen.getColony().getWorld().getServer();
        return server != null && GarrisonRoster.get(server).of(citizen.getColony(), citizen) != null;
    }
}
