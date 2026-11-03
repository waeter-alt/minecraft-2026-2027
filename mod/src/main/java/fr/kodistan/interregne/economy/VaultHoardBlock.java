package fr.kodistan.interregne.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The visible wealth of a regional bank, laid around it by {@link Vault}: a pure decoration.
 * It has no item, drops nothing and cannot be pushed, so the vault is never a gold mine.
 * Stages: 1-2 coin piles, 3-4 stacked ingots, 5 a treasure chest, 6 a hoard.
 */
public class VaultHoardBlock extends Block
{
    public static final int MAX_STAGE = 6;

    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 1, MAX_STAGE);

    private static final VoxelShape[] SHAPES = {
      Block.box(3, 0, 3, 13, 2, 13),
      Block.box(1, 0, 1, 15, 5, 15),
      Block.box(1, 0, 1, 13, 3, 13),
      Block.box(2, 0, 2, 13, 8, 14),
      Block.box(1, 0, 2, 15, 11, 14),
      Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(2, 4, 2, 14, 8, 14), Block.box(4, 8, 4, 12, 12, 12), Block.box(6, 12, 5, 10, 14, 11))};

    public VaultHoardBlock(final Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 1));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context)
    {
        return SHAPES[state.getValue(STAGE) - 1];
    }

    @Override
    public ItemStack getCloneItemStack(final LevelReader level, final BlockPos pos, final BlockState state)
    {
        // Not even creative pick-block: there is no item to hand out
        return ItemStack.EMPTY;
    }
}
