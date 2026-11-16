package fr.kodistan.interregne.commerce;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side of the Relais commercial window: runs what the player asks, answers with the state to show.
 */
public final class RelaisDesk
{
    private RelaisDesk()
    {
    }

    public static void onAction(final RelaisActionPayload payload, final IPayloadContext context)
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
        String notice = null;
        final Contracts contracts = Contracts.get(player.server);
        switch (payload.action())
        {
            case RelaisActionPayload.OFFER ->
            {
                final IColony buyer = IColonyManager.getInstance().getColonyByWorld(payload.other(), player.server.overworld());
                final Crate crate = payload.crate() >= 0 && payload.crate() < Crate.values().length ? Crate.values()[payload.crate()] : null;
                notice = buyer == null || crate == null ? "Offre invalide." : contracts.offer(player, player.server, colony, buyer, crate, payload.quantity(), payload.price());
            }
            case RelaisActionPayload.ACCEPT -> notice = contracts.accept(player, player.server, colony, payload.contract());
            case RelaisActionPayload.CANCEL -> notice = contracts.cancel(player, colony, payload.contract());
            default ->
            {
            }
        }
        send(player, colony, notice);
    }

    public static void send(final ServerPlayer player, final IColony colony, @Nullable final String notice)
    {
        final Contracts contracts = Contracts.get(player.server);
        final List<RelaisStatePayload.Partner> partners = new ArrayList<>();
        for (final IColony other : IColonyManager.getInstance().getColonies(player.server.overworld()))
        {
            if (other.getID() != colony.getID())
            {
                partners.add(new RelaisStatePayload.Partner(other.getID(), other.getName(), Contracts.mayTrade(player.server, colony.getID(), other.getID())));
            }
        }
        final List<RelaisStatePayload.Row> rows = new ArrayList<>();
        for (final Contract contract : contracts.of(colony.getID()))
        {
            final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, player.server.overworld());
            final IColony buyer = IColonyManager.getInstance().getColonyByWorld(contract.buyer, player.server.overworld());
            final Crate crate = Crate.byId(contract.crate);
            final int percent = contract.state == Contract.OFFER || contract.path.length < 2 ? 0 : (int) Math.round(100.0 * contract.progress / (contract.path.length - 1));
            rows.add(new RelaisStatePayload.Row(contract.id, contract.seller, seller == null ? "?" : seller.getName(), contract.buyer, buyer == null ? "?" : buyer.getName(),
              crate == null ? 0 : crate.ordinal(), contract.quantity, contract.unitPrice, contract.state, percent));
        }
        PacketDistributor.sendToPlayer(player, new RelaisStatePayload(colony.getID(), colony.getName(), Contracts.relaisLevel(colony), Contracts.capacity(colony),
          TreasuryPayment.available(player, colony), notice == null ? "" : notice, partners, rows));
    }
}
