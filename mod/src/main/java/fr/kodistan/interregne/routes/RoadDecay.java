package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;

/**
 * A road whose alliance was broken vanishes: its cells go in random order over {@code roadDecaySeconds}, only where
 * the chunk is already loaded and never inside a colony's land. Only the blocks of the route builder's own palette are
 * touched, so what a player built beside the road is left alone unless it is made of the same stuff.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class RoadDecay
{
    private static final int STEP_TICKS = 40;

    /** Ground of the road: grass once it is gone (air over a gap). */
    private static final Set<Block> SURFACE = Set.of(Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
      Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS);
    /** Steps, decks, rails and lanterns: simply removed. */
    private static final Set<Block> RAISED = Set.of(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_FENCE, Blocks.LANTERN,
      Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS);

    private RoadDecay()
    {
    }

    /**
     * The alliance between the two colonies is over: forgets the route link and starts the roads built for it vanishing.
     */
    public static void begin(final MinecraftServer server, final int colonyA, final int colonyB)
    {
        RouteRegistry.get(server).beginDecay(colonyA, colonyB);
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        if (server.getTickCount() % STEP_TICKS != 0 || server.overworld() == null)
        {
            return;
        }
        final double fraction = STEP_TICKS / (Config.ROAD_DECAY_SECONDS.get() * 20.0);
        RouteRegistry.get(server).decayStep(server.overworld(), fraction);
    }

    /**
     * Removes the road around one centre-line cell, unless it lies on a colony's land.
     */
    static void erase(final ServerLevel level, final BlockPos centre, final int d2, final int reach)
    {
        if (IColonyManager.getInstance().getColonyByPosFromWorld(level, centre) != null)
        {
            return;
        }
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = -reach; dz <= reach; dz++)
        {
            for (int dx = -reach; dx <= reach; dx++)
            {
                if (dx * dx + dz * dz > d2 || !level.hasChunkAt(pos.set(centre.getX() + dx, centre.getY(), centre.getZ() + dz)))
                {
                    continue;
                }
                for (int y = centre.getY() + 5; y >= centre.getY() - 1; y--)
                {
                    pos.set(centre.getX() + dx, y, centre.getZ() + dz);
                    final BlockState state = level.getBlockState(pos);
                    final Block block = state.getBlock();
                    if (RAISED.contains(block))
                    {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                    else if (SURFACE.contains(block) && y <= centre.getY() && level.getBlockState(pos.above()).isAir())
                    {
                        final boolean solidBelow = level.getBlockState(pos.below()).isSolidRender(level, pos.below());
                        level.setBlock(pos, solidBelow ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
