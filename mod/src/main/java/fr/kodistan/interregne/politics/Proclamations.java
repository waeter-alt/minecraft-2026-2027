package fr.kodistan.interregne.politics;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Imperial proclamations: a stylized server-wide announcement with a fanfare.
 */
public final class Proclamations
{
    private Proclamations()
    {
    }

    public static void announce(final MinecraftServer server, final String text)
    {
        final Component message = Component.literal("⚜ Par décret de l'Intendance — ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                                    .append(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            player.sendSystemMessage(message);
            player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 0.35F, 1.2F);
        }
    }

    /**
     * "2 j 4 h", "5 h", "12 min"
     */
    public static String formatDelay(final long millis)
    {
        final long minutes = Math.max(0, millis / 60_000);
        final long days = minutes / 1440;
        final long hours = minutes % 1440 / 60;
        if (days > 0)
        {
            return days + " j" + (hours > 0 ? " " + hours + " h" : "");
        }
        if (hours > 0)
        {
            return hours + " h" + (minutes % 60 > 0 ? " " + minutes % 60 : "");
        }
        return minutes + " min";
    }
}
