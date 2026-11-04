package fr.kodistan.interregne.client.dev;

import fr.kodistan.interregne.network.IntendanceQuotePayload;
import org.jetbrains.annotations.Nullable;

/**
 * Fake client data for {@link UiShot} captures. Every field is null outside a capture, and the readers then use the
 * real data.
 */
public final class DevOverrides
{
    @Nullable
    public static IntendanceQuotePayload quote;
    /**
     * True to show the Intendance as still waiting for the server's prices.
     */
    public static boolean quoteLoading;
    @Nullable
    public static Long treasury;
    @Nullable
    public static Long purse;
    @Nullable
    public static Long influence;
    /**
     * Forced mouse position in GUI pixels for our screens, so captures never show a stray hover.
     */
    @Nullable
    public static int[] hover;

    private DevOverrides()
    {
    }

    public static void clear()
    {
        quote = null;
        quoteLoading = false;
        treasury = null;
        purse = null;
        influence = null;
        hover = null;
    }
}
