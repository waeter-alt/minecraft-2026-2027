package fr.kodistan.interregne.warehouse;

import fr.kodistan.interregne.network.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WarehouseNetwork
{
    public static void register(final PayloadRegistrar r)
    {
        r.playToServer(TerminalWithdrawPayload.TYPE, TerminalWithdrawPayload.STREAM_CODEC, Terminal::onWithdraw);
        r.playToServer(TerminalDepositPayload.TYPE, TerminalDepositPayload.STREAM_CODEC, Terminal::onDeposit);
    }

    private WarehouseNetwork()
    {
    }
}
