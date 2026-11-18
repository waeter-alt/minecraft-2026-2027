package fr.kodistan.interregne.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The visible wealth of a regional bank, set into its vault room by {@link Vault}: a pure decoration.
 * It has no item, drops nothing and cannot be pushed, so the vault is never a gold mine.
 * Five kinds, each growing over six stages (see tools/gen_vault_assets.py): coin piles, stacked gold bars,
 * open chests overflowing with gold, gem clusters and jewel cushions.
 */
public class VaultHoardBlock extends Block
{
    public static final int MAX_STAGE = 6;

    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 1, MAX_STAGE);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final DirectionProperty FACING = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

    public enum Kind implements StringRepresentable
    {
        PILE("pile", new int[] {1, 2, 5, 7, 10, 14}),
        INGOTS("ingots", new int[] {3, 5, 8, 11, 11, 13}),
        CHEST("chest", new int[] {15, 15, 15, 15, 15, 15}),
        GEMS("gems", new int[] {4, 5, 6, 6, 7, 8}),
        JEWELS("jewels", new int[] {4, 5, 5, 8, 8, 9});

        private final String id;
        private final VoxelShape[] shapes = new VoxelShape[MAX_STAGE];

        Kind(final String id, final int[] heights)
        {
            this.id = id;
            for (int i = 0; i < MAX_STAGE; i++)
            {
                shapes[i] = Block.box(1, 0, 1, 15, heights[i], 15);
            }
        }

        @Override
        public String getSerializedName()
        {
            return id;
        }
    }

    public VaultHoardBlock(final Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 1).setValue(KIND, Kind.PILE).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(STAGE, KIND, FACING);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context)
    {
        return state.getValue(KIND).shapes[state.getValue(STAGE) - 1];
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public ItemStack getCloneItemStack(final LevelReader level, final BlockPos pos, final BlockState state)
    {
        // Not even creative pick-block: there is no item to hand out
        return ItemStack.EMPTY;
    }
}
