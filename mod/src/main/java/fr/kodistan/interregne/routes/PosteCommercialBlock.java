package fr.kodistan.interregne.routes;

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
import net.minecraft.world.level.block.state.BlockState;
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

    private record Studied(RoutePlan plan, long at)
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
        final BlockPos other = registry.nearestUnlinked(pos);
        if (other == null)
        {
            say(player, registry.hasOther(pos) ? "Ce poste est déjà relié à tous les autres." : "Aucun autre Poste commercial détecté.", ChatFormatting.GRAY);
            return;
        }
        final RouteRegistry.Pair pair = RouteRegistry.Pair.of(pos, other);
        if (BUSY.contains(pair))
        {
            say(player, "Cette route est déjà à l'étude ou en chantier.", ChatFormatting.RED);
            return;
        }
        final Studied studied = PLANS.get(pair);
        if (studied != null && level.getGameTime() - studied.at() <= PLAN_TICKS)
        {
            if (build)
            {
                build(level, player, pos, other, studied.plan());
            }
            else
            {
                describe(player, pos, other, studied.plan());
            }
            return;
        }
        final double flight = Math.sqrt(pos.distSqr(other));
        final UUID who = player.getUUID();
        BUSY.add(pair);
        final boolean started = RoutePlanning.study(level, pos.below(), other.below(), player, plan -> {
            BUSY.remove(pair);
            final ServerPlayer asker = level.getServer().getPlayerList().getPlayer(who);
            if (plan == null)
            {
                if (asker != null)
                {
                    say(asker, String.format("Poste détecté à %.0f blocs, mais aucun tracé possible entre les deux.", flight), ChatFormatting.RED);
                }
                return;
            }
            PLANS.put(pair, new Studied(plan, level.getGameTime()));
            if (build)
            {
                build(level, asker, pos, other, plan);
            }
            else if (asker != null)
            {
                describe(asker, pos, other, plan);
            }
        });
        if (!started)
        {
            BUSY.remove(pair);
            say(player, String.format("Poste détecté à %.0f blocs : trop loin (%d blocs au plus).", flight, RoutePlanning.MAX_DISTANCE), ChatFormatting.RED);
            return;
        }
        say(player, String.format("Poste détecté à %.0f blocs (vol d'oiseau). Étude du tracé…", flight), ChatFormatting.GRAY);
    }

    private static void describe(final ServerPlayer player, final BlockPos pos, final BlockPos other, final RoutePlan plan)
    {
        say(player, String.format("Route proposée vers le poste à %.0f blocs : %d blocs de long, %s. Sneak + clic droit pour construire.",
          Math.sqrt(pos.distSqr(other)), plan.length(), works(plan)), ChatFormatting.GOLD);
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

    private static void build(final ServerLevel level, final ServerPlayer player, final BlockPos pos, final BlockPos other, final RoutePlan plan)
    {
        final RouteRegistry.Pair pair = RouteRegistry.Pair.of(pos, other);
        BUSY.add(pair);
        PLANS.remove(pair);
        // Test tool: construction is for show, not a timed cost — kept short so iterating in game stays fast
        final int seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, MIN_SECONDS + plan.length() / 100));
        RouteSite.launch(level, new RouteBuilder(level, plan), seconds, Component.literal("⚒ Route commerciale"), () -> {
            BUSY.remove(pair);
            final RouteRegistry registry = RouteRegistry.get(level.getServer());
            registry.link(pos, other);
            registry.record(plan);
        });
        if (player != null)
        {
            say(player, "Chantier de la route lancé : " + plan.length() + " blocs, " + works(plan) + ", " + seconds + " s.", ChatFormatting.GOLD);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
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
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel)
        {
            RouteRegistry.get(serverLevel.getServer()).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
