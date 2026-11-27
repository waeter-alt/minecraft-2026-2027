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
    private static final ThreadLocal<Boolean> PREPAID = new ThreadLocal<>();

    private BuildContext()
    {
    }

    public static void begin(final ServerPlayer player)
    {
        REQUESTER.set(player);
    }

    /** Orders requested until {@link #end()} were already paid for by the caller (e.g. "Tout reconstruire"). */
    public static void beginPrepaid(final ServerPlayer player)
    {
        REQUESTER.set(player);
        PREPAID.set(true);
    }

    public static void end()
    {
        REQUESTER.remove();
        PREPAID.remove();
    }

    public static boolean prepaid()
    {
        return Boolean.TRUE.equals(PREPAID.get());
    }

    @Nullable
    public static ServerPlayer requester()
    {
        return REQUESTER.get();
    }
}
