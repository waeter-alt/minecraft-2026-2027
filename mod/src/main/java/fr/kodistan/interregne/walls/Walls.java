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
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        if (BUILDING.contains(key))
        {
            refuse(player, "La muraille est déjà en chantier.");
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
        // A new wall is paid in full; a retraced one pays only what it adds
        final WallRegistry.Wall old = WallRegistry.get(player.server).of(colony);
        if (old == null)
        {
            apply(player, colony, null, points, gates, payload.closed(), 1, false);
            return;
        }
        if (payload.level() <= old.level())
        {
            apply(player, colony, old, points, gates, payload.closed(), old.level(), true);
            return;
        }
        // An upgrade: the whole wall at the next level, on the trace as it stands in the planner
        final int next = old.level() + 1;
        if (next > WallLevels.MAX)
        {
            refuse(player, "La muraille est déjà au niveau maximal.");
            return;
        }
        final int townHall = colony.getBuildingManager().getTownHall() == null ? 0 : colony.getBuildingManager().getTownHall().getBuildingLevel();
        if (townHall < next)
        {
            refuse(player, "Il faut un hôtel de ville de niveau " + next + " pour une muraille de niveau " + next + ".");
            return;
        }
        apply(player, colony, old, points, gates, payload.closed(), next, false);
    }

    /**
     * Lays the wall out on the ground remembered under the old one (live terrain elsewhere), charges it and starts
     * the site: the old blocks the new wall no longer covers come down first, then the new wall rises.
     *
     * @param difference charge only the difference with the old wall at the same level (a retrace), not the full price
     */
    private static void apply(final ServerPlayer player, final IColony colony, @Nullable final WallRegistry.Wall old, final List<WallTrace.Point> points,
      final List<WallTrace.Point> gates, final boolean closed, final int wallLevel, final boolean difference)
    {
        final ServerLevel level = player.serverLevel();
        final String key = RegionalTreasury.keyOf(colony);
        final Map<Long, Terrain.Probe> remembered = old == null ? Map.of() : old.ground();
        final Map<Long, Terrain.Probe> ground = new HashMap<>(remembered);
        final java.util.Set<Long> own = old == null ? java.util.Set.of() : WallLayout.ownCells(old.points(), old.closed());
        final WallLayout.Ground reader = (x, z) -> {
            final long cell = WallRegistry.cell(x, z);
            final Terrain.Probe known = ground.get(cell);
            if (known != null)
            {
                return known;
            }
            final Terrain.Probe probe = Terrain.probe(level, x, z, own.contains(cell) ? WallBlocks::isWallBlock : null);
            if (probe.loaded() && !probe.blocked())
            {
                ground.put(cell, probe);
            }
            return probe;
        };
        final WallLayout.Claim claim = (x, z) -> colony.isCoordInColony(level, new BlockPos(x, 64, z));
        final WallLevels.Level look = WallLevels.of(wallLevel);
        final WallLayout layout = WallLayout.of(points, closed, gates, look, reader, claim);
        if (layout.blocked() > 0)
        {
            refuse(player, "La muraille touche un bâtiment, sort de la colonie ou une porte ne tient pas : retire les parties rouges.");
            return;
        }
        long cost = layout.cost(look);
        if (difference && old != null)
        {
            final WallLayout before = WallLayout.of(old.points(), old.closed(), old.gates(), look, reader, claim);
            cost = Math.max(0, cost - before.cost(look));
        }
        if (player.isCreative())
        {
            cost = 0;
        }
        final TreasuryPayment payment = TreasuryPayment.pay(player, colony, cost);
        if (payment == null)
        {
            refuse(player, "Il te faut " + Wallet.couronnes(cost, null) + " pour ce chantier (trésor régional compris).");
            return;
        }
        if (cost > 0)
        {
            player.sendSystemMessage(Component.literal("Payé : " + payment.describe() + ".").withStyle(ChatFormatting.GRAY));
        }

        final List<WallBlocks.Step> steps = new ArrayList<>(WallBlocks.of(level, layout, wallLevel, reader));
        final Set<Long> placed = new HashSet<>();
        for (final WallBlocks.Step step : steps)
        {
            if (!step.state().isAir())
            {
                placed.add(step.pos().asLong());
            }
        }
        if (old != null)
        {
            // What the new wall no longer covers comes down first, never below the ground it stood on
            final List<WallBlocks.Step> removals = new ArrayList<>();
            for (final long packed : old.placed())
            {
                final BlockPos pos = BlockPos.of(packed);
                final Terrain.Probe under = ground.get(WallRegistry.cell(pos.getX(), pos.getZ()));
                if (!placed.contains(packed) && (under == null || pos.getY() > under.ground()))
                {
                    removals.add(new WallBlocks.Step(pos, Blocks.AIR.defaultBlockState(), -1));
                }
            }
            removals.sort(Comparator.comparingInt((WallBlocks.Step step) -> step.pos().getY()).reversed());
            steps.addAll(0, removals);
        }

        final int seconds = seconds(layout);
        final WallRegistry.Wall wall = new WallRegistry.Wall(List.copyOf(points), List.copyOf(gates), closed, wallLevel, ground,
          placed.stream().mapToLong(Long::longValue).toArray());
        final WallRegistry registry = WallRegistry.get(player.server);
        BUILDING.add(key);
        sync(player.server, key, wall, true);
        WallSite.launch(level, steps, seconds, wallLevel, bannerOf(colony), colony.getCenter(), () -> {
            BUILDING.remove(key);
            registry.put(colony, wall);
            sync(player.server, key, wall, false);
        });
        final String what = old == null ? "Chantier de la muraille lancé" : wallLevel > old.level() ? "Muraille en route vers le niveau " + wallLevel
                              : "Retouche de la muraille lancée";
        player.sendSystemMessage(Component.literal(what + " : " + seconds + " s.").withStyle(ChatFormatting.GOLD));
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

    /**
     * Tears the colony's whole wall down, gates and all, in a few seconds: every block it placed above the ground it
     * stood on is broken, the highest first; piles in water become water again. Nothing is refunded.
     */
    public static void onDemolish(final fr.kodistan.interregne.network.WallDemolishPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(payload.colony(), player.serverLevel());
        if (colony == null || !colony.getPermissions().isColonyMember(player))
        {
            return;
        }
        final String key = RegionalTreasury.keyOf(colony);
        final WallRegistry registry = WallRegistry.get(player.server);
        final WallRegistry.Wall old = registry.of(colony);
        if (old == null || BUILDING.contains(key))
        {
            return;
        }
        final List<WallBlocks.Step> steps = new ArrayList<>();
        for (final long packed : old.placed())
        {
            final BlockPos pos = BlockPos.of(packed);
            final Terrain.Probe under = old.ground().get(WallRegistry.cell(pos.getX(), pos.getZ()));
            if (under == null || pos.getY() > under.ground())
            {
                steps.add(new WallBlocks.Step(pos, Blocks.AIR.defaultBlockState(), 0));
            }
            else if (under.water())
            {
                steps.add(new WallBlocks.Step(pos, Blocks.WATER.defaultBlockState(), 0));
            }
        }
        // Grass grows back where the wall stood: the soil it smothered and the gate floors
        final java.util.Set<Long> placed = new java.util.HashSet<>();
        for (final long packed : old.placed())
        {
            placed.add(packed);
        }
        final java.util.Set<Long> done = new java.util.HashSet<>();
        for (final long packed : old.placed())
        {
            final BlockPos at = BlockPos.of(packed);
            final long cell = WallRegistry.cell(at.getX(), at.getZ());
            final Terrain.Probe under = old.ground().get(cell);
            if (under == null || under.water() || !done.add(cell))
            {
                continue;
            }
            final BlockPos soil = new BlockPos(at.getX(), under.ground(), at.getZ());
            final net.minecraft.world.level.block.state.BlockState state = player.serverLevel().getBlockState(soil);
            if (state.is(Blocks.DIRT) || placed.contains(soil.asLong()))
            {
                steps.add(new WallBlocks.Step(soil, Blocks.GRASS_BLOCK.defaultBlockState(), 0));
            }
        }
        steps.sort(Comparator.comparingInt((WallBlocks.Step step) -> step.pos().getY()).reversed());
        final int seconds = Math.max(2, Math.min(6, steps.size() / 1500 + 2));
        BUILDING.add(key);
        sync(player.server, key, old, true);
        WallSite.demolish(player.serverLevel(), steps, seconds, () -> {
            BUILDING.remove(key);
            registry.remove(colony);
            // An empty wall tells the clients it is gone
            PacketDistributor.sendToAllPlayers(new WallSyncPayload(key, List.of(), List.of(), false, 0, false));
        });
        player.sendSystemMessage(Component.literal("La muraille tombe.").withStyle(ChatFormatting.GOLD));
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
