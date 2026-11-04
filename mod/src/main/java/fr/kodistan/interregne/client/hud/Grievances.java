package fr.kodistan.interregne.client.hud;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import net.minecraft.client.Minecraft;

/**
 * A citizen's grievances: unmet requests and problems, counted for the hub and the citizen sheets. The floating panel
 * and the glow through walls are gone; the windows say it instead.
 */
public final class Grievances
{
    private Grievances()
    {
    }

    /**
     * How many grievances this citizen has right now.
     */
    public static int count(final ICitizenDataView citizen)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        int count = 0;
        for (final IInteractionResponseHandler interaction : citizen.getOrderedInteractions())
        {
            if (unresolved(interaction, minecraft))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * A request, a sickness, a complaint: anything the citizen waits on, not small talk.
     */
    private static boolean unresolved(final IInteractionResponseHandler interaction, final Minecraft minecraft)
    {
        try
        {
            return interaction.getPriority().getPriority() >= ChatPriority.PENDING.getPriority() && interaction.isVisible(minecraft.level);
        }
        catch (final RuntimeException e)
        {
            return false;
        }
    }
}
