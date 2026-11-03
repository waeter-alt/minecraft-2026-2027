package fr.kodistan.interregne.economy;

import fr.kodistan.interregne.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The vault of a regional bank: the 5×5 floor around the bank fills with wealth as the treasury does.
 * Coin piles appear all around first, then grow, turn into stacks of ingots, then chests, and finally a hoard.
 * Only air and the vault's own decorations are ever replaced, so player builds around the bank are safe.
 */
public final class Vault
{
    /**
     * Inner ring first, then the outer ring, so a modest treasury gathers close to the bank.
     */
    private static final List<BlockPos> SLOTS = slots();

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
        final int slots = SLOTS.size();
        // Units of wealth to show: each unit raises one slot by one stage, going round the slots in turn
        final int units = balance <= 0 ? 0 : Math.max(1, (int) Math.ceil(ratio * slots * VaultHoardBlock.MAX_STAGE));
        for (int i = 0; i < slots; i++)
        {
            final int stage = units > i ? Math.min(VaultHoardBlock.MAX_STAGE, (units - i - 1) / slots + 1) : 0;
            place(level, bank.offset(SLOTS.get(i)), stage);
        }
    }

    /**
     * Remove every decoration around the bank, e.g. when it is broken.
     */
    public static void clear(final Level level, final BlockPos bank)
    {
        for (final BlockPos offset : SLOTS)
        {
            place(level, bank.offset(offset), 0);
        }
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

    private static List<BlockPos> slots()
    {
        final List<BlockPos> slots = new ArrayList<>();
        for (int radius = 1; radius <= 2; radius++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == radius)
                    {
                        slots.add(new BlockPos(dx, 0, dz));
                    }
                }
            }
        }
        // Alternate sides of the bank so a few piles do not all bunch up on one side
        final List<BlockPos> spread = new ArrayList<>();
        int start = 0;
        for (final int ring : new int[]{8, 16})
        {
            final List<BlockPos> ringSlots = slots.subList(start, start + ring);
            for (int i = 0; i < ring; i++)
            {
                spread.add(ringSlots.get(i % 2 == 0 ? i / 2 : ring - 1 - i / 2));
            }
            start += ring;
        }
        return List.copyOf(spread);
    }
}
