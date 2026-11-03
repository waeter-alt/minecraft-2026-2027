package fr.kodistan.interregne.economy;

import fr.kodistan.interregne.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The vault of a regional bank: the floor around the bank (7×7) fills with wealth as the treasury does, in a messy heap.
 * Piles grow highest near the bank and thin out toward the edges, with per-bank noise so no two vaults look alike;
 * only the very heart of a full vault turns into chests. The four straight lanes to the bank always stay clear to walk.
 * Only air and the vault's own decorations are ever replaced, so player builds around the bank are safe.
 */
public final class Vault
{
    private static final int RADIUS = 3;

    private Vault()
    {
    }

    /**
     * Lay out the decorations for this fill level around the bank.
     */
    public static void refresh(final Level level, final BlockPos bank, final long balance, final long capacity)
    {
        if (!Config.VAULT_DECORATIONS.get())
        {
            clear(level, bank);
            return;
        }
        final double ratio = capacity <= 0 ? (balance > 0 ? 1 : 0) : Math.min(1.0, (double) balance / capacity);
        final List<Step> plan = plan(bank);
        final int shown = balance <= 0 ? 0 : Math.max(1, (int) Math.ceil(ratio * plan.size()));
        final Map<BlockPos, Integer> stages = new HashMap<>();
        for (int i = 0; i < shown; i++)
        {
            stages.merge(plan.get(i).offset(), 1, Integer::sum);
        }
        for (final BlockPos offset : offsets())
        {
            place(level, bank.offset(offset), stages.getOrDefault(offset, 0));
        }
        clearLanes(level, bank);
    }

    /**
     * Older vault layouts could cover the lanes: make sure the way to the bank is free.
     */
    private static void clearLanes(final Level level, final BlockPos bank)
    {
        for (int step = 1; step <= RADIUS; step++)
        {
            place(level, bank.offset(step, 0, 0), 0);
            place(level, bank.offset(-step, 0, 0), 0);
            place(level, bank.offset(0, 0, step), 0);
            place(level, bank.offset(0, 0, -step), 0);
        }
    }

    /**
     * Remove every decoration around the bank, e.g. when it is broken.
     */
    public static void clear(final Level level, final BlockPos bank)
    {
        for (final BlockPos offset : offsets())
        {
            place(level, bank.offset(offset), 0);
        }
        clearLanes(level, bank);
    }

    /**
     * One stage of wealth on one tile; the vault grows by applying steps in priority order.
     */
    private record Step(BlockPos offset, double priority)
    {
    }

    /**
     * Every stage of every tile, ordered by when it appears: inner tiles and low stages first, shuffled by
     * per-bank noise so the heap grows lumpy. Outer tiles are capped low, so chests only appear near the bank.
     */
    private static List<Step> plan(final BlockPos bank)
    {
        final RandomSource random = RandomSource.create(bank.asLong() * 31L + 7L);
        final List<Step> steps = new ArrayList<>();
        for (final BlockPos offset : offsets())
        {
            final int ring = Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ()));
            final int cap = Math.clamp(VaultHoardBlock.MAX_STAGE - (ring - 1) * 2 + random.nextInt(3) - 1, 1, VaultHoardBlock.MAX_STAGE);
            final double weight = (RADIUS + 1 - ring) + random.nextDouble() * 1.5;
            for (int stage = 1; stage <= cap; stage++)
            {
                steps.add(new Step(offset, stage / weight + random.nextDouble() * 0.6));
            }
        }
        steps.sort(Comparator.comparingDouble(Step::priority));
        return steps;
    }

    /**
     * The tiles around the bank, minus the four straight lanes leading to it.
     */
    private static List<BlockPos> offsets()
    {
        final List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                if (dx != 0 && dz != 0)
                {
                    offsets.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        return offsets;
    }

    private static void place(final Level level, final BlockPos pos, final int stage)
    {
        final BlockState current = level.getBlockState(pos);
        final boolean ours = current.is(RegionalBanks.HOARD.get());
        if (stage == 0)
        {
            if (ours)
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }
        if (ours)
        {
            if (current.getValue(VaultHoardBlock.STAGE) != stage)
            {
                level.setBlock(pos, current.setValue(VaultHoardBlock.STAGE, stage), Block.UPDATE_ALL);
            }
            return;
        }
        final BlockPos below = pos.below();
        if (current.isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
        {
            level.setBlock(pos, RegionalBanks.HOARD.get().defaultBlockState().setValue(VaultHoardBlock.STAGE, stage), Block.UPDATE_ALL);
        }
    }
}
