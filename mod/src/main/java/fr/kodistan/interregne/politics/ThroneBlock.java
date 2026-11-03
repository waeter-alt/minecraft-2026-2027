package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.ThroneActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Trône impérial, placed by the admins in the palace. Intendants rule the Empire from it; every player reads
 * the proclamations there and delivers the tithe.
 */
public class ThroneBlock extends Block
{
    private static final double REACH_SQ = 8 * 8;

    public ThroneBlock(final Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void onAction(final ThroneActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player)
              || !(player.level().getBlockState(payload.throne()).getBlock() instanceof ThroneBlock)
              || player.distanceToSqr(payload.throne().getCenter()) > REACH_SQ)
        {
            return;
        }
        final MinecraftServer server = player.server;
        if (payload.action() == ThroneActionPayload.DELIVER || payload.action() == ThroneActionPayload.FUND)
        {
            if (payload.action() == ThroneActionPayload.DELIVER)
            {
                Tithe.deliver(player);
            }
            else
            {
                Edicts.fundTreasury(server, player, payload.amount());
            }
            open(player, payload.throne());
            return;
        }
        if (!Empire.get(server).isIntendant(player.getUUID()) && !player.hasPermissions(2))
        {
            player.sendSystemMessage(Component.literal("Seuls les Intendants gouvernent depuis le trône.").withStyle(ChatFormatting.RED));
            return;
        }
        switch (payload.action())
        {
            case ThroneActionPayload.CLEMENTE -> Edicts.setTitheWeight(server, Empire.TitheWeight.CLEMENTE);
            case ThroneActionPayload.ORDINAIRE -> Edicts.setTitheWeight(server, Empire.TitheWeight.ORDINAIRE);
            case ThroneActionPayload.ECRASANTE -> Edicts.setTitheWeight(server, Empire.TitheWeight.ECRASANTE);
            case ThroneActionPayload.WITHDRAW -> Edicts.withdrawTreasury(server, player, payload.amount());
            case ThroneActionPayload.FAVOR, ThroneActionPayload.DISGRACE -> {
                final ServerPlayer target = server.getPlayerList().getPlayerByName(payload.target().trim());
                if (target == null)
                {
                    player.sendSystemMessage(Component.literal("Joueur introuvable (il doit être connecté).").withStyle(ChatFormatting.RED));
                }
                else if (payload.action() == ThroneActionPayload.FAVOR)
                {
                    Edicts.favor(server, target, payload.amount());
                }
                else
                {
                    Edicts.disgrace(server, target, payload.amount());
                }
            }
            default -> {
                return;
            }
        }
        open(player, payload.throne());
    }

    private static void open(final ServerPlayer player, final BlockPos pos)
    {
        final MinecraftServer server = player.server;
        final Empire empire = Empire.get(server);
        final boolean intendantOnline = server.getPlayerList().getPlayers().stream().anyMatch(online -> empire.isIntendant(online.getUUID()));

        final String state = "Trésor impérial : " + Wallet.format(empire.treasury()) + " couronnes"
                               + "\nPoids de la dîme : " + empire.titheWeight().label()
                               + "\nDîme : " + (empire.titheActive() ? "échéance dans " + Proclamations.formatDelay(empire.titheDueAt() - System.currentTimeMillis()) : "aucune en cours")
                               + (intendantOnline ? "" : "\nPilote automatique : actif");

        final IColony colony = Tithe.colonyOf(player);
        final String colonyLine = colony == null || !empire.titheActive() ? ""
                                    : colony.getName() + " doit encore : " + Tithe.describe(Tithe.remaining(server, colony));
        PacketDistributor.sendToPlayer(player, new OpenThronePayload(pos, empire.isIntendant(player.getUUID()) || player.hasPermissions(2), state, colonyLine, empire.treasury()));
    }
}
