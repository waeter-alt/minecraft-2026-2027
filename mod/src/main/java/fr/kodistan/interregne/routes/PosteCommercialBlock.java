package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
import fr.kodistan.interregne.routes.plan.RoutePlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test fixture for the trade-route algorithm (see mémoire "trade-routes-roads"): place two of these, right-click one
 * to have the route to its nearest neighbour studied and described, sneak + right-click to build it. Not tied to a
 * colony — this is only here to try the routes in game before they are wired to contracts and caravans.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class PosteCommercialBlock extends Block
{
    /** A studied route is kept this long, so building right after the description does not study it again. */
    private static final long PLAN_TICKS = 20 * 60 * 5;
    private static final int MIN_SECONDS = 3;
    private static final int MAX_SECONDS = 10;

    private record Studied(RoutePlan plan, long at, RouteEnds.End endA, RouteEnds.End endB)
    {
    }

    private static final Set<RouteRegistry.Pair> BUSY = new HashSet<>();
    private static final Map<RouteRegistry.Pair, Studied> PLANS = new HashMap<>();

    public PosteCommercialBlock(final Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            use(serverPlayer.serverLevel(), serverPlayer, pos, player.isShiftKeyDown());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void use(final ServerLevel level, final ServerPlayer player, final BlockPos pos, final boolean build)
    {
        final RouteRegistry registry = RouteRegistry.get(level.getServer());
        // A post placed by a command, a paste or before the registry existed is only known once it is used
        registry.add(pos);
        // The post stands for the colony nearest it; the road goes to the nearest colony no road joins to it yet
        final IColony colonyA = RouteEnds.nearest(level, pos);
        if (colonyA == null)
        {
            say(player, "Aucune colonie détectée : fonde-en une d'abord.", ChatFormatting.GRAY);
            return;
        }
        final IColony colonyB = RouteEnds.nearestUnlinked(level, colonyA, registry);
        // No other colony to reach: the post itself is the far end of the road, which runs to this colony
        final boolean toPost = colonyB == null;
        if (toPost)
        {
            if (registry.isPostLinked(colonyA.getID(), pos))
            {
                say(player, "Ce poste est déjà relié à " + colonyA.getName() + ".", ChatFormatting.GRAY);
                return;
            }
            if (colonyA.isCoordInColony(level, pos))
            {
                say(player, "Ce poste est dans " + colonyA.getName() + " : pose-le dehors, à l'endroit d'où la route doit partir.", ChatFormatting.RED);
                return;
            }
        }
        final RouteRegistry.Pair pair = toPost ? new RouteRegistry.Pair(-1L - colonyA.getID(), pos.asLong())
                                               : RouteRegistry.Pair.ofColonies(colonyA.getID(), colonyB.getID());
        if (BUSY.contains(pair))
        {
            say(player, "Cette route est déjà à l'étude ou en chantier.", ChatFormatting.RED);
            return;
        }
        final String target = toPost ? "poste → " + colonyA.getName() : colonyA.getName() + " → " + colonyB.getName();
        final Studied studied = PLANS.get(pair);
        if (studied != null && level.getGameTime() - studied.at() <= PLAN_TICKS)
        {
            if (build)
            {
                build(level, player, pos, pair, colonyA, colonyB, studied);
            }
            else
            {
                describe(player, target, studied.plan());
            }
            return;
        }
        final double flight = Math.sqrt(colonyA.getCenter().distSqr(toPost ? pos : colonyB.getCenter()));
        final UUID who = player.getUUID();
        // Each end snaps to its colony's gate facing the other one (or its town hall's door, or the edge of its land);
        // a post set by a gate picks that gate.
        final RouteEnds.End endA = toPost ? RouteEnds.of(level, null, pos, colonyA.getCenter()) : RouteEnds.of(level, colonyA, pos, colonyB.getCenter());
        final RouteEnds.End endB = toPost ? RouteEnds.of(level, colonyA, colonyA.getCenter(), pos)
                                          : RouteEnds.of(level, colonyB, colonyB.getCenter(), colonyA.getCenter());
        BUSY.add(pair);
        final boolean started = RoutePlanning.study(level, endA, endB, player, plan -> {
            BUSY.remove(pair);
            final ServerPlayer asker = level.getServer().getPlayerList().getPlayer(who);
            if (plan == null)
            {
                if (asker != null)
                {
                    say(asker, String.format("%s à %.0f blocs, mais aucun tracé possible entre les deux.", target, flight), ChatFormatting.RED);
                }
                return;
            }
            final Studied done = new Studied(plan, level.getGameTime(), endA, endB);
            PLANS.put(pair, done);
            if (build)
            {
                build(level, asker, pos, pair, colonyA, colonyB, done);
            }
            else if (asker != null)
            {
                describe(asker, target, plan);
            }
        });
        if (!started)
        {
            BUSY.remove(pair);
            say(player, String.format("%s à %.0f blocs : trop loin (%d blocs au plus).", target, flight, RoutePlanning.MAX_DISTANCE), ChatFormatting.RED);
            return;
        }
        say(player, String.format("Route à relier : %s, à %.0f blocs (vol d'oiseau). Étude du tracé…", target, flight), ChatFormatting.GRAY);
    }

    private static void describe(final ServerPlayer player, final String target, final RoutePlan plan)
    {
        say(player, String.format("Route proposée %s : %d blocs de long, %s. Sneak + clic droit pour construire.", target, plan.length(), works(plan)), ChatFormatting.GOLD);
    }

    /**
     * The bridges and tunnels of a route, in words, with the length of each.
     */
    private static String works(final RoutePlan plan)
    {
        final List<Integer> bridges = new ArrayList<>();
        final List<Integer> tunnels = new ArrayList<>();
        int i = 0;
        while (i < plan.length())
        {
            int end = i;
            while (end + 1 < plan.length() && plan.mode[end + 1] == plan.mode[i])
            {
                end++;
            }
            if (plan.mode[i] == RoutePlan.BRIDGE)
            {
                bridges.add(end - i + 1);
            }
            else if (plan.mode[i] == RoutePlan.TUNNEL)
            {
                tunnels.add(end - i + 1);
            }
            i = end + 1;
        }
        if (bridges.isEmpty() && tunnels.isEmpty())
        {
            return "sans pont ni tunnel";
        }
        final StringBuilder text = new StringBuilder();
        if (!bridges.isEmpty())
        {
            text.append(bridges.size()).append(bridges.size() > 1 ? " ponts " : " pont ").append(bridges);
        }
        if (!tunnels.isEmpty())
        {
            text.append(text.isEmpty() ? "" : ", ").append(tunnels.size()).append(tunnels.size() > 1 ? " tunnels " : " tunnel ").append(tunnels);
        }
        return text.toString();
    }

    private static void build(final ServerLevel level, final ServerPlayer player, final BlockPos pos, final RouteRegistry.Pair pair, final IColony colonyA,
                              final IColony colonyB, final Studied studied)
    {
        final RoutePlan plan = studied.plan();
        BUSY.add(pair);
        PLANS.remove(pair);
        // Test tool: construction is for show, not a timed cost — kept short so iterating in game stays fast
        final int seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, MIN_SECONDS + plan.length() / 100));
        RouteSite.launch(level, new RouteBuilder(level, plan), seconds, Component.literal("⚒ Route commerciale"), () -> {
            BUSY.remove(pair);
            final RouteRegistry registry = RouteRegistry.get(level.getServer());
            if (colonyB == null)
            {
                registry.linkPost(colonyA.getID(), pos);
            }
            else
            {
                registry.linkColonies(colonyA.getID(), colonyB.getID());
            }
            registry.record(plan, studied.endA(), studied.endB());
            registry.broadcast();
            if (studied.endA().border())
            {
                marker(level, plan, true);
            }
            if (studied.endB().border())
            {
                marker(level, plan, false);
            }
        });
        if (player != null)
        {
            say(player, "Chantier de la route lancé : " + plan.length() + " blocs, " + works(plan) + ", " + seconds + " s.", ChatFormatting.GOLD);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    /**
     * A lantern on a post beside the road's end, where it stops at the edge of a colony without a wall.
     */
    private static void marker(final ServerLevel level, final RoutePlan plan, final boolean first)
    {
        final int n = plan.length();
        if (n < 2)
        {
            return;
        }
        final int i = first ? 0 : n - 1;
        final int j = first ? 1 : n - 2;
        final int dx = Integer.signum(plan.x[j] - plan.x[i]);
        final int dz = Integer.signum(plan.z[j] - plan.z[i]);
        final int x = plan.x[i] - dz * 4;
        final int z = plan.z[i] + dx * 4;
        final BlockPos base = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        level.setBlockAndUpdate(base, Blocks.OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(base.above(), Blocks.LANTERN.defaultBlockState());
    }

    private static void say(final ServerPlayer player, final String text, final ChatFormatting colour)
    {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
        InterregneMod.LOGGER.debug("Poste commercial -> {}: {}", player.getGameProfile().getName(), text);
    }

    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (event.getPlacedBlock().getBlock() instanceof PosteCommercialBlock && event.getLevel() instanceof ServerLevel level)
        {
            RouteRegistry.get(level.getServer()).add(event.getPos().immutable());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        BUSY.clear();
        PLANS.clear();
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean movedByPiston)
    {
        if (level instanceof ServerLevel serverLevel && !state.is(oldState.getBlock()))
        {
            RouteRegistry.get(serverLevel.getServer()).add(pos.immutable());
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel)
        {
            RouteRegistry.get(serverLevel.getServer()).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
