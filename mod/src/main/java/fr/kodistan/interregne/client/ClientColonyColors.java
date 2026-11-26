package fr.kodistan.interregne.client;

import fr.kodistan.interregne.colony.ColonyColors;
import fr.kodistan.interregne.network.ColonyColorsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Applies the colony colors sent by the server; names are cached by the game, so they are refreshed here. */
public final class ClientColonyColors
{
    private ClientColonyColors()
    {
    }

    public static void receive(final ColonyColorsPayload payload)
    {
        ColonyColors.receive(payload);
        if (Minecraft.getInstance().level != null)
        {
            Minecraft.getInstance().level.players().forEach(Player::refreshDisplayName);
        }
    }
}
