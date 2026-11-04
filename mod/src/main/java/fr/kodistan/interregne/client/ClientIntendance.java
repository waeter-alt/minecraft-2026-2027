package fr.kodistan.interregne.client;

import fr.kodistan.interregne.network.IntendanceActionPayload;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Intendance prices last sent by the server, asked again every few seconds while its page is open.
 */
public final class ClientIntendance
{
    private static final long REFRESH_MS = 2000;

    @Nullable
    private static IntendanceQuotePayload quote;
    private static long askedAt;

    private ClientIntendance()
    {
    }

    static void set(final IntendanceQuotePayload payload)
    {
        quote = payload;
    }

    /**
     * The current quote for this colony, or null while the first answer is on its way.
     */
    @Nullable
    public static IntendanceQuotePayload of(final int colony)
    {
        if (Util.getMillis() - askedAt > REFRESH_MS)
        {
            askedAt = Util.getMillis();
            PacketDistributor.sendToServer(new IntendanceActionPayload(colony, IntendanceActionPayload.QUOTE));
        }
        return quote != null && quote.colony() == colony ? quote : null;
    }

    public static void settle(final int colony, final int action)
    {
        askedAt = Util.getMillis();
        PacketDistributor.sendToServer(new IntendanceActionPayload(colony, action));
    }
}
