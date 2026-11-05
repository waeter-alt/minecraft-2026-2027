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
            // Any retouch is paid: what the new trace adds, and never less than the retouch price
            final WallLayout before = WallLayout.of(old.points(), old.closed(), old.gates(), look, reader, claim);
            cost = WallLevels.editCost(cost, before.cost(look), look);
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

        final List<WallBlocks.Step> steps = new ArrayList<>(WallBlocks.of(level, layout, wallLevel, reader, colony.getCenter()));
        final Set<Long> placed = new HashSet<>();
        for (final WallBlocks.Step step : steps)
        {
            if (!step.state().isAir())
            {
                placed.add(step.pos().asLong());
            }
        }
        // Only what changes is rebuilt: a block already standing as it should is left alone
        final int planned = steps.size();
        steps.removeIf(step -> already(level, step));
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

        // A retouch that changes a stretch takes the time of that stretch
        final int seconds = old == null || planned == 0 ? seconds(layout)
                              : Math.max(5, (int) Math.ceil(seconds(layout) * Math.min(1.0, steps.size() / (double) planned)));
        final long before = old == null ? 0 : old.spent() >= 0 ? old.spent() : valueOf(old, reader, claim);
        final WallRegistry.Wall wall = new WallRegistry.Wall(List.copyOf(points), List.copyOf(gates), closed, wallLevel, ground,
          placed.stream().mapToLong(Long::longValue).toArray(), before + cost);
        final WallRegistry registry = WallRegistry.get(player.server);
        BUILDING.add(key);
        sync(player.server, key, wall, true);
        WallSite.launch(level, steps, seconds, wallLevel, bannerOf(colony), colony.getCenter(), player, () -> {
            BUILDING.remove(key);
            registry.put(colony, wall);
            sync(player.server, key, wall, false);
        });
        final String what = old == null ? "Chantier de la muraille lancé" : wallLevel > old.level() ? "Muraille en route vers le niveau " + wallLevel
                              : "Retouche de la muraille lancée";
        player.sendSystemMessage(Component.literal(what + " : " + seconds + " s.").withStyle(ChatFormatting.GOLD));
    }

    /**
     * Whether this block already stands as the step wants it (same block, same facing): it need not be set again.
     */
    private static boolean already(final ServerLevel level, final WallBlocks.Step step)
    {
        final net.minecraft.world.level.block.state.BlockState current = level.getBlockState(step.pos());
        final net.minecraft.world.level.block.state.BlockState wanted = step.state();
        if (wanted.isAir())
        {
            return current.isAir();
        }
        if (!current.is(wanted.getBlock()))
        {
            return false;
        }
        for (final net.minecraft.world.level.block.state.properties.Property<?> property : wanted.getProperties())
        {
            if (property.getName().equals("facing") && current.hasProperty(property) && !current.getValue(property).equals(wanted.getValue(property)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * What a wall saved before its spending was kept is worth: its price at its level today.
     */
    private static long valueOf(final WallRegistry.Wall wall, final WallLayout.Ground reader, final WallLayout.Claim claim)
    {
        final WallLevels.Level look = WallLevels.of(wall.level());
        return WallLayout.of(wall.points(), wall.closed(), wall.gates(), look, reader, claim).cost(look);
    }

    /**
     * What tearing this wall down gives back: everything paid for it.
     */
    private static long refundOf(final ServerLevel level, final IColony colony, final WallRegistry.Wall wall)
    {
        if (wall.spent() >= 0)
        {
            return wall.spent();
        }
        final WallLayout.Ground reader = (x, z) -> {
            final Terrain.Probe known = wall.ground().get(WallRegistry.cell(x, z));
            return known != null ? known : Terrain.probe(level, x, z);
        };
        return valueOf(wall, reader, (x, z) -> true);
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
     * stood on is broken, the highest first; piles in water become water again. Everything paid for it goes back to the
     * regional treasury.
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
        final long refund = player.isCreative() ? 0 : refundOf(player.serverLevel(), colony, old);
        BUILDING.add(key);
        sync(player.server, key, old, true);
        WallSite.demolish(player.serverLevel(), steps, seconds, player, () -> {
            BUILDING.remove(key);
            registry.remove(colony);
            if (refund > 0)
            {
                RegionalTreasury.get(player.server).credit(colony, refund);
                player.sendSystemMessage(Component.literal("Remboursé : " + Wallet.couronnes(refund, null) + " au trésor régional.").withStyle(ChatFormatting.GRAY));
            }
            // An empty wall tells the clients it is gone
            PacketDistributor.sendToAllPlayers(new WallSyncPayload(key, List.of(), List.of(), false, 0, false, 0));
        });
        player.sendSystemMessage(Component.literal("La muraille tombe.").withStyle(ChatFormatting.GOLD));
    }

    private static void refuse(final ServerPlayer player, final String message)
    {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static WallSyncPayload payloadOf(final String key, final WallRegistry.Wall wall, final boolean building)
    {
        return new WallSyncPayload(key, WallTrace.flat(wall.points()), WallTrace.flat(wall.gates()), wall.closed(), wall.level(), building,
          Math.max(0, wall.spent()));
    }

    private static void sync(final MinecraftServer server, final String key, final WallRegistry.Wall wall, final boolean building)
    {
        PacketDistributor.sendToAllPlayers(payloadOf(key, wall, building));
    }

    /**
     * Chunks claimed around a town hall: MineColonies' 4, 30 % further, so a wall has room inside the colony.
     */
    public static final int COLONY_RADIUS = 5;

    /**
     * Chunks added on top of every building's MineColonies claim (1 at levels 1-3, 2 at 4-5): the border
     * leaves room to trace a wall between the houses and the limit. Applied by AbstractBuildingMixin.
     */
    public static final int BUILDING_CLAIM_BONUS = 2;

    /**
     * New colonies claim COLONY_RADIUS chunks around their town hall; existing ones are widened to it.
     */
    @SubscribeEvent
    public static void onServerStarted(final net.neoforged.neoforge.event.server.ServerStartedEvent event)
    {
        final com.minecolonies.api.configuration.ServerConfiguration config =
          (com.minecolonies.api.configuration.ServerConfiguration) com.minecolonies.core.MineColonies.getConfig().getServer();
        if (config.initialColonySize.get() < COLONY_RADIUS)
        {
            config.initialColonySize.set(COLONY_RADIUS);
        }
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final ServerLevel level = event.getServer().getLevel(colony.getDimension());
            if (level == null || !(colony instanceof com.minecolonies.core.colony.Colony real))
            {
                continue;
            }
            final BlockPos center = colony.getCenter();
            if (!(colony.isCoordInColony(level, center.offset(COLONY_RADIUS * 16, 0, 0)) && colony.isCoordInColony(level, center.offset(-COLONY_RADIUS * 16, 0, 0))))
            {
                com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange(real, true, center, COLONY_RADIUS, level, false);
                InterregneMod.LOGGER.info("Colony {} widened to {} chunks around its town hall", colony.getID(), COLONY_RADIUS);
            }
            // The border hugged the buildings (MineColonies claims 1-2 chunks around each): re-apply the roomier
            // claims of AbstractBuildingMixin so an existing colony gains its wall room without a level-up.
            com.minecolonies.core.util.BackUpHelper.reclaimChunks(real);
        }
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
