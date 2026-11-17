package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.economy.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side of the road screen of a Poste commercial: finds the road the post raises, orders the works when asked
 * and answers with the state to show.
 */
public final class RoadDesk
{
    /** How far from a post the player may still use its screen. */
    private static final double USE_RANGE = 12;

    private RoadDesk()
    {
    }

    public static void onAction(final RoadActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player) || player.blockPosition().distSqr(payload.post()) > USE_RANGE * USE_RANGE
              || !(player.serverLevel().getBlockState(payload.post()).getBlock() instanceof PosteCommercialBlock))
        {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final IColony colony = RouteEnds.nearest(level, payload.post());
        if (colony == null)
        {
            return;
        }
        final int road = RouteRegistry.get(level.getServer()).nearestRoad(colony.getID(), payload.post(), PosteCommercialBlock.UPGRADE_REACH);
        if (road < 0)
        {
            return;
        }
        String notice = null;
        if (payload.action() == RoadActionPayload.UPGRADE)
        {
            notice = raise(level, player, colony, road);
        }
        send(player, colony, road, payload.post(), notice);
    }

    /**
     * Shows the road screen.
     */
    public static void send(final ServerPlayer player, final IColony colony, final int road, final BlockPos post, @Nullable final String notice)
    {
        final ServerLevel level = player.serverLevel();
        final RouteRegistry registry = RouteRegistry.get(level.getServer());
        final int[] ends = registry.colonies(road);
        final int level0 = registry.level(road);
        final long cost = level0 >= RoadLevels.MAX ? -1 : RoadLevels.upgradeCost(Config.ROAD_UPGRADE_COSTS.get(), level0);
        final List<String> perks = new ArrayList<>();
        for (int l = 1; l <= RoadLevels.MAX; l++)
        {
            perks.add(perks(l));
        }
        PacketDistributor.sendToPlayer(player, new RoadStatePayload(post, name(level, ends[0]), name(level, ends[1]), registry.path(road).length, level0,
          RoadUpgrade.underWay(road), cost < 0 ? -1 : player.isCreative() ? 0 : cost, TreasuryPayment.available(player, colony), Config.ROAD_UPGRADE_SECONDS.get(),
          notice == null ? "" : notice, perks));
    }

    private static String name(final ServerLevel level, final int colony)
    {
        final IColony found = IColonyManager.getInstance().getColonyByWorld(colony, level);
        return found == null ? "#" + colony : found.getName();
    }

    /**
     * What a road of this level gives, from the configured values.
     */
    private static String perks(final int level)
    {
        final List<String> parts = new ArrayList<>();
        parts.add("+" + Math.round(RoadLevels.perLevel(Config.ROAD_SPEED.get(), level) * 100) + " % de vitesse");
        final long jump = Math.round(RoadLevels.perLevel(Config.ROAD_JUMP.get(), level) * 100);
        if (jump > 0)
        {
            parts.add("+" + jump + " % de saut");
        }
        if (RoadLevels.perLevel(Config.ROAD_STEP.get(), level) >= 0.5)
        {
            parts.add("monte les marches sans sauter");
        }
        final double fall = RoadLevels.perLevel(Config.ROAD_SAFE_FALL.get(), level);
        if (fall > 0)
        {
            parts.add("chute de +" + Math.round(fall) + " bloc" + (fall >= 2 ? "s" : "") + " sans dégât");
        }
        if (Config.ROAD_POTIONS.get() && level >= 4)
        {
            parts.add(level >= 5 ? "régénération et résistance" : "régénération");
        }
        return String.join(" · ", parts);
    }

    /**
     * Pays the next level (treasury first, then the purse) and starts the works, refunded if they cannot start.
     *
     * @return a refusal, or null once the works have started
     */
    @Nullable
    public static String raise(final ServerLevel level, final ServerPlayer player, final IColony colony, final int road)
    {
        final RouteRegistry registry = RouteRegistry.get(level.getServer());
        final int from = registry.level(road);
        if (from >= RoadLevels.MAX)
        {
            return "Route de niveau " + from + "/" + RoadLevels.MAX + " : voie romaine, rien au-dessus.";
        }
        if (RoadUpgrade.underWay(road))
        {
            return "Cette route est déjà en travaux (niveau " + (from + 1) + ").";
        }
        final long cost = player.isCreative() ? 0 : RoadLevels.upgradeCost(Config.ROAD_UPGRADE_COSTS.get(), from);
        if (cost < 0)
        {
            return "Aucun coût configuré pour passer cette route au niveau " + (from + 1) + " (roadUpgradeCosts).";
        }
        final TreasuryPayment paid = TreasuryPayment.pay(player, colony, cost);
        if (paid == null)
        {
            return "Il faut " + Wallet.couronnes(cost, null) + " pour le niveau " + (from + 1) + ", vous n'avez que "
                     + Wallet.couronnes(TreasuryPayment.available(player, colony), null) + ".";
        }
        if (!RoadUpgrade.start(level, road))
        {
            paid.refund(player, colony);
            return "Les travaux n'ont pas pu commencer : vous êtes remboursé.";
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.sendSystemMessage(Component.literal("Travaux lancés : route au niveau " + (from + 1) + " d'ici " + Config.ROAD_UPGRADE_SECONDS.get() + " s"
                                                     + (cost > 0 ? ", payé " + paid.describe() : "") + ".").withStyle(ChatFormatting.GREEN));
        return null;
    }
}
