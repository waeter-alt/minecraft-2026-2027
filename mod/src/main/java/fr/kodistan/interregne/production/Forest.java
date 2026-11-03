package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * A forester always has something to cut: after each construction or upgrade it makes sure enough saplings or trees
 * stand around its hut, and saplings near a staffed forester grow faster with its level.
 * New saplings match the biome and grow in groves on untouched natural ground, clear of every colony building and of
 * anything built (paths, walls, farmland, other mods' structures): nothing is ever broken to plant them.
 */
public final class Forest
{
    private static final int RADIUS      = 18;
    private static final int MIN_RADIUS  = 5;
    private static final int SPACING     = 3;
    private static final int GROVE       = 6;
    private static final int SAMPLES     = 600;
    private static final int CANOPY      = 7;

    private Forest()
    {
    }

    /**
     * Plant saplings around the hut until saplings plus trunks reach the target for its level.
     */
    public static void plant(final ServerLevel level, final IBuilding building)
    {
        final BlockPos center = building.getPosition();
        final int wanted = 4 + building.getBuildingLevel() * Config.FOREST_SAPLINGS.get();
        int present = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                final int x = center.getX() + dx;
                final int z = center.getZ() + dz;
                if (topBlock(level, x, z, Heightmap.Types.WORLD_SURFACE).is(BlockTags.SAPLINGS)
                      || topBlock(level, x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).is(BlockTags.LOGS))
                {
                    present++;
                }
            }
        }
        if (present >= wanted)
        {
            return;
        }

        final List<Tuple<BlockPos, BlockPos>> footprints = new ArrayList<>();
        for (final IBuilding other : building.getColony().getBuildingManager().getBuildings().values())
        {
            footprints.add(other.getCorners());
        }

        final List<BlockPos> candidates = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++)
        {
            final double angle = level.random.nextDouble() * Math.PI * 2;
            final double distance = MIN_RADIUS + level.random.nextDouble() * (RADIUS - MIN_RADIUS);
            final int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            final int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            final BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!nearFootprint(footprints, spot) && untouched(level, spot))
            {
                candidates.add(spot);
            }
        }

        // Groves: the first sapling anywhere, then each next one close to those already planted
        final List<BlockPos> chosen = new ArrayList<>();
        while (chosen.size() < wanted - present && !candidates.isEmpty())
        {
            BlockPos pick = null;
            if (chosen.isEmpty())
            {
                pick = candidates.get(0);
            }
            else
            {
                final List<BlockPos> close = new ArrayList<>();
                BlockPos nearest = null;
                double nearestDistance = Double.MAX_VALUE;
                for (final BlockPos candidate : candidates)
                {
                    final double distance = distanceToChosen(chosen, candidate);
                    if (distance < SPACING)
                    {
                        continue;
                    }
                    if (distance <= GROVE)
                    {
                        close.add(candidate);
                    }
                    if (distance < nearestDistance)
                    {
                        nearestDistance = distance;
                        nearest = candidate;
                    }
                }
                pick = close.isEmpty() ? nearest : close.get(level.random.nextInt(close.size()));
            }
            if (pick == null)
            {
                break;
            }
            candidates.remove(pick);
            chosen.add(pick);
        }

        for (final BlockPos pos : chosen)
        {
            final Holder<Biome> biome = level.getBiome(pos);
            final BlockState sapling = saplingFor(level, biome);
            if (level.getBlockState(pos.below()).is(BlockTags.SAND))
            {
                // Arid biomes: a patch of coarse dirt under the sapling, the only change made to the ground
                level.setBlockAndUpdate(pos.below(), Blocks.COARSE_DIRT.defaultBlockState());
            }
            if (sapling.canSurvive(level, pos))
            {
                level.setBlockAndUpdate(pos, sapling);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    /**
     * Give each sapling around the hut a chance to grow a stage, higher with the forester's level.
     */
    public static void grow(final ServerLevel level, final IBuilding building)
    {
        final double chance = Config.SAPLING_GROWTH.get() * building.getBuildingLevel();
        if (chance <= 0)
        {
            return;
        }
        final BlockPos center = building.getPosition();
        final int radius = RADIUS + 4;
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                final int x = center.getX() + dx;
                final int z = center.getZ() + dz;
                final BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                final BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof SaplingBlock sapling && level.random.nextDouble() < chance)
                {
                    sapling.advanceTree(level, pos, state, level.random);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.0);
                }
            }
        }
    }

    /**
     * The tree that grows there naturally. Treeless arid biomes get acacias, cold ones spruces, the rest oaks
     * with a few birches.
     */
    private static BlockState saplingFor(final ServerLevel level, final Holder<Biome> biome)
    {
        final String name = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        final Block sapling;
        if (name.contains("cherry"))
        {
            sapling = Blocks.CHERRY_SAPLING;
        }
        else if (name.contains("birch"))
        {
            sapling = Blocks.BIRCH_SAPLING;
        }
        else if (biome.is(BiomeTags.IS_JUNGLE))
        {
            sapling = Blocks.JUNGLE_SAPLING;
        }
        else if (biome.is(BiomeTags.IS_SAVANNA) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(Tags.Biomes.IS_DESERT))
        {
            sapling = Blocks.ACACIA_SAPLING;
        }
        else if (biome.is(BiomeTags.IS_TAIGA) || biome.is(Tags.Biomes.IS_SNOWY) || biome.is(BiomeTags.IS_MOUNTAIN))
        {
            sapling = Blocks.SPRUCE_SAPLING;
        }
        else
        {
            sapling = level.random.nextFloat() < 0.25F ? Blocks.BIRCH_SAPLING : Blocks.OAK_SAPLING;
        }
        return sapling.defaultBlockState();
    }

    /**
     * Natural ground (soil, or sand in arid biomes) all around, and nothing but air, plants or leaves where the tree
     * will stand and spread: no path, wall, farmland or block placed by anyone.
     */
    private static boolean untouched(final ServerLevel level, final BlockPos spot)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                final BlockState ground = level.getBlockState(spot.offset(dx, -1, dz));
                if (!(ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.SNOW_BLOCK)) || ground.is(Blocks.FARMLAND))
                {
                    return false;
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                for (int dy = 0; dy < CANOPY; dy++)
                {
                    final BlockState state = level.getBlockState(spot.offset(dx, dy, dz));
                    if (!state.isAir() && !state.is(BlockTags.LEAVES) && !(state.canBeReplaced() && state.getFluidState().isEmpty()))
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean nearFootprint(final List<Tuple<BlockPos, BlockPos>> footprints, final BlockPos spot)
    {
        for (final Tuple<BlockPos, BlockPos> corners : footprints)
        {
            if (spot.getX() >= Math.min(corners.getA().getX(), corners.getB().getX()) - 3 && spot.getX() <= Math.max(corners.getA().getX(), corners.getB().getX()) + 3
                  && spot.getZ() >= Math.min(corners.getA().getZ(), corners.getB().getZ()) - 3 && spot.getZ() <= Math.max(corners.getA().getZ(), corners.getB().getZ()) + 3)
            {
                return true;
            }
        }
        return false;
    }

    private static double distanceToChosen(final List<BlockPos> chosen, final BlockPos candidate)
    {
        double best = Double.MAX_VALUE;
        for (final BlockPos pos : chosen)
        {
            best = Math.min(best, Math.sqrt(pos.distSqr(candidate)));
        }
        return best;
    }

    private static BlockState topBlock(final ServerLevel level, final int x, final int z, final Heightmap.Types type)
    {
        return level.getBlockState(new BlockPos(x, level.getHeight(type, x, z) - 1, z));
    }
}
