package fr.kodistan.interregne.politics;

import fr.kodistan.interregne.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The edicts the Intendants can enact from the Trône impérial. Each one is proclaimed to the whole server;
 * harsh ones feed the Grogne, the discontent that should push players toward revolt.
 */
public final class Edicts
{
    /**
     * Grogne gained when the tithe is set to Écrasante, or someone is disgraced.
     */
    private static final int HARSH_GROGNE = 2;

    /**
     * What an amnesty costs the imperial treasury, and the Grogne it calms.
     */
    private static final long AMNESTY_COST = 500;
    private static final int  AMNESTY_CALM = 3;

    private Edicts()
    {
    }

    public static void setTitheWeight(final MinecraftServer server, final Empire.TitheWeight weight)
    {
        final Empire empire = Empire.get(server);
        if (empire.titheWeight() == weight)
        {
            return;
        }
        empire.setTitheWeight(weight);
        if (weight == Empire.TitheWeight.ECRASANTE)
        {
            empire.addGrogne(HARSH_GROGNE);
        }
        Proclamations.announce(server, "le poids de la dîme est désormais " + weight.label().toLowerCase() + ".");
    }

    public static void favor(final MinecraftServer server, final ServerPlayer target, final int amount)
    {
        final int granted = Math.clamp(amount, 1, Config.FAVOR_CAP.get());
        Influence.add(server, target.getUUID(), granted, "faveur impériale");
        Proclamations.announce(server, target.getGameProfile().getName() + " reçoit la faveur impériale (+" + granted + " d'Influence).");
    }

    public static void disgrace(final MinecraftServer server, final ServerPlayer target, final int amount)
    {
        final int removed = Math.clamp(amount, 1, Config.FAVOR_CAP.get());
        Influence.remove(server, target.getUUID(), removed, "disgrâce impériale");
        Empire.get(server).addGrogne(HARSH_GROGNE);
        Proclamations.announce(server, target.getGameProfile().getName() + " tombe en disgrâce (−" + removed + " d'Influence).");
    }

    public static boolean amnesty(final MinecraftServer server, final ServerPlayer by)
    {
        final Empire empire = Empire.get(server);
        if (empire.treasury() < AMNESTY_COST)
        {
            by.sendSystemMessage(Component.literal("Le trésor impérial ne peut pas payer l'amnistie (" + AMNESTY_COST + " couronnes).").withStyle(ChatFormatting.RED));
            return false;
        }
        empire.addTreasury(-AMNESTY_COST);
        empire.addGrogne(-AMNESTY_CALM);
        Proclamations.announce(server, "une amnistie générale apaise le peuple.");
        return true;
    }
}
