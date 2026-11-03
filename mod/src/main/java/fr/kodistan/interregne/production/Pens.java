package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import fr.kodistan.interregne.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A breeder's herd is pure scenery (its goods come from {@link HutProduction}), so after every construction or upgrade
 * the pen is restocked: escaped animals come back, and a larger hut shows a larger herd.
 * MineColonies' herders look for their animals anywhere in the hut's footprint, so the pen is found by scoring that
 * footprint rather than by knowing each style: walkable, open to the sky, natural floor, and fenced in.
 */
public final class Pens
{
    private static final Map<String, EntityType<? extends Animal>> ANIMALS = Map.of(
      Huts.CHICKEN, EntityType.CHICKEN,
      Huts.COWBOY, EntityType.COW,
      Huts.SHEPHERD, EntityType.SHEEP,
      Huts.SWINEHERD, EntityType.PIG,
      Huts.RABBIT, EntityType.RABBIT);

    /**
     * Below this many spots, an area is a doorstep rather than a pen.
     */
    private static final int MIN_PEN = 4;

    private Pens()
    {
    }

    public static boolean isBreeder(final IBuilding building)
    {
        return ANIMALS.containsKey(Huts.id(building));
    }

    /**
     * Bring the herd up to its size for the hut's level, inside the best pen found in the footprint.
     */
    public static void restock(final ServerLevel level, final IBuilding building)
    {
        final EntityType<? extends Animal> type = ANIMALS.get(Huts.id(building));
        if (type == null)
        {
            return;
        }
        final int wanted = 2 + building.getBuildingLevel() * Config.HERD_PER_LEVEL.get();
        final int present = WorldUtil.getEntitiesWithinBuilding(level, Animal.class, building, animal -> animal.getType() == type).size();
        if (present >= wanted)
        {
            return;
        }
        final List<BlockPos> pen = findPen(level, building);
        final List<BlockPos> spots = pen.isEmpty() ? List.of(building.getPosition().above()) : pen;
        for (int i = 0; i < wanted - present; i++)
        {
            final BlockPos spot = spots.get(level.random.nextInt(spots.size()));
            final Animal animal = type.spawn(level, spot, MobSpawnType.MOB_SUMMONED);
            if (animal != null)
            {
                animal.setPersistenceRequired();
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    /**
     * Standing spots (the air block above the floor) of the best pen in the hut's footprint, empty if there is none.
     */
    public static List<BlockPos> findPen(final ServerLevel level, final IBuilding building)
    {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        final BlockPos min = corners.getA();
        final BlockPos max = corners.getB();

        final Set<BlockPos> open = new HashSet<>();
        final Set<BlockPos> natural = new HashSet<>();
        final Set<BlockPos> walkable = new HashSet<>();
        for (int x = Math.min(min.getX(), max.getX()); x <= Math.max(min.getX(), max.getX()); x++)
        {
            for (int z = Math.min(min.getZ(), max.getZ()); z <= Math.max(min.getZ(), max.getZ()); z++)
            {
                for (int y = Math.max(min.getY(), max.getY()); y >= Math.min(min.getY(), max.getY()); y--)
                {
                    final BlockPos feet = new BlockPos(x, y, z);
                    if (!standable(level, feet))
                    {
                        continue;
                    }
                    walkable.add(feet);
                    if (level.canSeeSky(feet))
                    {
                        open.add(feet);
                        if (naturalFloor(level.getBlockState(feet.below())))
                        {
                            natural.add(feet);
                        }
                    }
                }
            }
        }

        // Strictest rule first, then relax: natural open floor, any open floor, anything walkable
        for (final Set<BlockPos> candidates : List.of(natural, open, walkable))
        {
            final List<BlockPos> best = bestGroup(level, candidates);
            if (best.size() >= MIN_PEN)
            {
                return best;
            }
        }
        return List.of();
    }

    /**
     * Split the candidates into connected areas and keep the one most enclosed by fences, then the largest.
     */
    private static List<BlockPos> bestGroup(final ServerLevel level, final Set<BlockPos> candidates)
    {
        final Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> best = new ArrayList<>();
        int bestScore = -1;
        for (final BlockPos start : candidates)
        {
            if (!seen.add(start))
            {
                continue;
            }
            final List<BlockPos> group = new ArrayList<>();
            final Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            int fences = 0;
            while (!queue.isEmpty())
            {
                final BlockPos pos = queue.poll();
                group.add(pos);
                for (final Direction side : Direction.Plane.HORIZONTAL)
                {
                    final BlockPos next = pos.relative(side);
                    if (isFence(level.getBlockState(next)))
                    {
                        fences++;
                    }
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        final BlockPos step = next.above(dy);
                        if (candidates.contains(step) && seen.add(step))
                        {
                            queue.add(step);
                        }
                    }
                }
            }
            final int score = group.size() < MIN_PEN ? group.size() : fences * 4 + group.size();
            if (score > bestScore)
            {
                bestScore = score;
                best = group;
            }
        }
        Collections.shuffle(best);
        return best;
    }

    private static boolean standable(final ServerLevel level, final BlockPos feet)
    {
        final BlockState floor = level.getBlockState(feet.below());
        return floor.isFaceSturdy(level, feet.below(), Direction.UP)
                 && !isFence(floor)
                 && passable(level, feet)
                 && passable(level, feet.above());
    }

    private static boolean passable(final ServerLevel level, final BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean naturalFloor(final BlockState state)
    {
        return state.is(BlockTags.DIRT) || state.is(Blocks.HAY_BLOCK) || state.is(BlockTags.SAND) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH);
    }

    private static boolean isFence(final BlockState state)
    {
        return state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS);
    }
}
