package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
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

import java.util.HashSet;
import java.util.Set;

/**
 * Test fixture for the trade-route algorithm (see mémoire "trade-routes-roads"): place two of these, right-click one
 * for a preview of the route to its nearest neighbour, sneak + right-click to actually build it. Not tied to a
 * colony — this is only here to validate {@link RoutePath} in game before it is wired to contracts and caravans.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class PosteCommercialBlock extends Block
{
    /**
     * Routes being built right now, keyed by their two endpoints (order independent), so a second click while one
     * is already rising does not start another on top of it.
     */
    private static final Set<Long> BUILDING = new HashSet<>();

    public PosteCommercialBlock(final Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            final ServerLevel serverLevel = serverPlayer.serverLevel();
            final BlockPos other = RouteRegistry.get(serverPlayer.server).nearestOther(pos);
            if (other == null)
            {
                serverPlayer.sendSystemMessage(Component.literal("Aucun autre Poste commercial détecté.").withStyle(ChatFormatting.GRAY));
            }
            else if (player.isShiftKeyDown())
            {
                build(serverLevel, serverPlayer, pos, other);
            }
            else
            {
                preview(serverLevel, serverPlayer, pos, other);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void preview(final ServerLevel level, final ServerPlayer player, final BlockPos pos, final BlockPos other)
    {
        final double flight = Math.sqrt(pos.distSqr(other));
        final RoutePath.Result route = RoutePath.compute(level, pos, other);
        if (route == null)
        {
            player.sendSystemMessage(Component.literal(String.format("Poste détecté à %.0f blocs (vol d'oiseau) — aucun chemin trouvé "
                                                                       + "(trop loin, ou une zone sur le chemin n'est pas chargée).", flight))
                                       .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.literal(String.format("Poste détecté à %.0f blocs (vol d'oiseau). Route proposée : %d blocs, %d tunnel(s), "
                                                                   + "%d pont(s). Sneak + clic droit pour construire.",
          flight, route.length(), route.tunnels(), route.bridges())).withStyle(ChatFormatting.GOLD));
    }

    private void build(final ServerLevel level, final ServerPlayer player, final BlockPos pos, final BlockPos other)
    {
        final long key = pairKey(pos, other);
        if (BUILDING.contains(key))
        {
            player.sendSystemMessage(Component.literal("Cette route est déjà en chantier.").withStyle(ChatFormatting.RED));
            return;
        }
        final RoutePath.Result route = RoutePath.compute(level, pos, other);
        if (route == null)
        {
            player.sendSystemMessage(Component.literal("Aucun chemin trouvé.").withStyle(ChatFormatting.RED));
            return;
        }
        BUILDING.add(key);
        // Test tool: construction is for show, not a timed cost — cap it short so iterating in game stays fast.
        final int seconds = Math.max(3, Math.min(10, 3 + route.steps().size() / 2000));
        RouteSite.launch(level, RoutePath.interleaved(route), seconds, Component.literal("⚒ Route commerciale"), () -> BUILDING.remove(key));
        player.sendSystemMessage(Component.literal("Chantier de la route lancé : " + route.length() + " blocs, " + seconds + " s.")
                                   .withStyle(ChatFormatting.GOLD));
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private static long pairKey(final BlockPos a, final BlockPos b)
    {
        final long x = a.asLong();
        final long y = b.asLong();
        return x < y ? (x * 31 + y) : (y * 31 + x);
    }

    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (event.getPlacedBlock().getBlock() instanceof PosteCommercialBlock && event.getLevel() instanceof ServerLevel level)
        {
            RouteRegistry.get(level.getServer()).add(event.getPos().immutable());
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
