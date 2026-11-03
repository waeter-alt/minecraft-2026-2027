package fr.kodistan.interregne.colony;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers which player sent the build request currently being handled on the server thread,
 * since MineColonies drops the player before the work order reaches the work manager.
 */
public final class BuildContext
{
    private static final ThreadLocal<ServerPlayer> REQUESTER = new ThreadLocal<>();

    private BuildContext()
    {
    }

    public static void begin(final ServerPlayer player)
    {
        REQUESTER.set(player);
    }

    public static void end()
    {
        REQUESTER.remove();
    }

    @Nullable
    public static ServerPlayer requester()
    {
        return REQUESTER.get();
    }
}
