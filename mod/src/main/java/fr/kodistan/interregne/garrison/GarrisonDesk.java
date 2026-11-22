package fr.kodistan.interregne.garrison;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.economy.TreasuryPayment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * The server side of the recruiting page of the combat academy and the archery.
 */
public final class GarrisonDesk
{
    private GarrisonDesk()
    {
    }

    public static void onAction(final GarrisonActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(payload.colony(), player.server.overworld());
        if (colony == null || !colony.getPermissions().isColonyMember(player))
        {
            return;
        }
        final int kind = payload.kind() == Garrison.ARCHERE ? Garrison.ARCHERE : Garrison.LEGIONNAIRE;
        String notice = null;
        if (payload.action() == GarrisonActionPayload.BUY)
        {
            notice = Garrison.buy(player, colony, kind);
        }
        send(player, colony, kind, notice);
    }

    public static void send(final ServerPlayer player, final IColony colony, final int kind, @Nullable final String notice)
    {
        final int level = Garrison.troopLevel(colony, kind);
        PacketDistributor.sendToPlayer(player, new GarrisonStatePayload(colony.getID(), kind, level, Garrison.price(level), TreasuryPayment.available(player, colony),
          Garrison.count(colony, kind), Garrison.count(colony, -1), Garrison.cap(colony), notice == null ? "" : notice));
    }
}
