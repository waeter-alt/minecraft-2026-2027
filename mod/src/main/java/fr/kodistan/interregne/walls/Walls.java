package fr.kodistan.interregne.walls;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.WallSite;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.WallBuildPayload;
import fr.kodistan.interregne.network.WallSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server side of colony walls: checks a traced wall again, charges it and starts its site; keeps clients told.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Walls
{
    private static final int MAX_POINTS = 64;
    private static final int MAX_REACH = 160;

    /**
     * Colonies whose wall is being built right now (not saved: a stopping server finishes every site).
     */
    private static final Set<String> BUILDING = new HashSet<>();

    private Walls()
    {
    }

    /**
     * Site duration: half a minute, plus a little per block of wall and per tower, at most two and a half minutes.
     */
    public static int seconds(final WallLayout layout)
    {
        return Math.min(150, 30 + layout.length() / 8 + layout.towers().size() * 2 + layout.gates().size() * 4);
    }

    public static void onBuild(final WallBuildPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(payload.colony(), level);
        if (colony == null || !colony.getPermissions().isColonyMember(player))
        {
            return;
        }
        final String key = RegionalTreasury.keyOf(colony);
        final WallRegistry registry = WallRegistry.get(player.server);
        if (registry.of(colony) != null || BUILDING.contains(key))
        {
            refuse(player, "Cette colonie a déjà sa muraille.");
            return;
        }
        final List<WallTrace.Point> points = WallTrace.unflat(payload.points());
        final List<WallTrace.Point> gates = WallTrace.unflat(payload.gates());
        final int count = points.size();
        if (count < 2 || count > MAX_POINTS || (payload.closed() && count < 3) || gates.size() > WallLevels.MAX_GATES)
        {
            return;
        }
        if (gates.isEmpty())
        {
            refuse(player, "Une muraille a besoin d'au moins une porte.");
            return;
        }
        final BlockPos center = colony.getCenter();
        for (final WallTrace.Point point : points)
        {
            if (Math.abs(point.x() - center.getX()) > MAX_REACH || Math.abs(point.z() - center.getZ()) > MAX_REACH)
            {
                refuse(player, "La muraille s'éloigne trop de l'hôtel de ville.");
                return;
            }
        }

        final int wallLevel = 1;
        final WallLevels.Level look = WallLevels.of(wallLevel);
        final WallLayout layout = WallLayout.of(points, payload.closed(), gates, look, (x, z) -> Terrain.probe(level, x, z),
          (x, z) -> colony.isCoordInColony(level, new BlockPos(x, 64, z)));
        if (layout.blocked() > 0)
        {
            refuse(player, "Le tracé traverse un bâtiment, sort de la colonie ou une porte ne tient pas : retire les parties rouges.");
            return;
        }
        final long cost = player.isCreative() ? 0 : layout.cost(look);
        final TreasuryPayment payment = TreasuryPayment.pay(player, colony, cost);
        if (payment == null)
        {
            refuse(player, "Il te faut " + Wallet.couronnes(cost, null) + " pour cette muraille (trésor régional compris).");
            return;
        }
        if (cost > 0)
        {
            player.sendSystemMessage(Component.literal("Payé : " + payment.describe() + ".").withStyle(ChatFormatting.GRAY));
        }

        final int seconds = seconds(layout);
        final WallRegistry.Wall wall = new WallRegistry.Wall(List.copyOf(points), List.copyOf(gates), payload.closed(), wallLevel);
        BUILDING.add(key);
        sync(player.server, key, wall, true);
        WallSite.launch(level, WallBlocks.of(level, layout, wallLevel), seconds, wallLevel, bannerOf(colony), () -> {
            BUILDING.remove(key);
            registry.put(colony, wall);
            sync(player.server, key, wall, false);
        });
        player.sendSystemMessage(Component.literal("Chantier de la muraille lancé : " + seconds + " s.").withStyle(ChatFormatting.GOLD));
    }

    /**
     * The colony's banner, for the gates.
     */
    private static ItemStack bannerOf(final IColony colony)
    {
        final ItemStack banner = new ItemStack(Items.WHITE_BANNER);
        if (colony.getColonyFlag() != null)
        {
            banner.set(DataComponents.BANNER_PATTERNS, colony.getColonyFlag());
        }
        return banner;
    }

    private static void refuse(final ServerPlayer player, final String message)
    {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static WallSyncPayload payloadOf(final String key, final WallRegistry.Wall wall, final boolean building)
    {
        return new WallSyncPayload(key, WallTrace.flat(wall.points()), WallTrace.flat(wall.gates()), wall.closed(), wall.level(), building);
    }

    private static void sync(final MinecraftServer server, final String key, final WallRegistry.Wall wall, final boolean building)
    {
        PacketDistributor.sendToAllPlayers(payloadOf(key, wall, building));
    }

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        for (final Map.Entry<String, WallRegistry.Wall> entry : WallRegistry.get(player.server).all().entrySet())
        {
            PacketDistributor.sendToPlayer(player, payloadOf(entry.getKey(), entry.getValue(), false));
        }
    }
}
