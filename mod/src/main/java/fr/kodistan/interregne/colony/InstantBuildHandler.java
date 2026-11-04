package fr.kodistan.interregne.colony;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.placement.structure.CreativeStructureHandler;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Places a work order's blueprint for free, one step per call, reporting each placed block to its construction site.
 * Block handling mirrors MineColonies' CreativeBuildingStructureHandler (GPL-3.0).
 */
public class InstantBuildHandler extends CreativeStructureHandler
{
    @Nullable
    private final IBuilding        building;
    private final ConstructionSite site;

    public InstantBuildHandler(
      final Level world,
      final Blueprint blueprint,
      final IColony colony,
      final IServerWorkOrder order,
      @Nullable final IBuilding building,
      final ConstructionSite site)
    {
        super(world, order.getLocation(), blueprint, order.getRotationMirror(), true);
        this.building = building;
        this.site = site;
    }

    /**
     * One step per call: the construction site decides how many calls happen each tick.
     */
    @Override
    public int getStepsPerCall()
    {
        return 1;
    }

    @Override
    public void triggerSuccess(final BlockPos pos, final List<ItemStack> list, final boolean placement)
    {
        super.triggerSuccess(pos, list, placement);
        final BlockPos worldPos = getProgressPosInWorld(pos);
        if (building != null)
        {
            building.registerBlockPosition(getBluePrint().getBlockState(pos), worldPos, getWorld());
        }
        if (placement)
        {
            site.onBlockPlaced(worldPos);
        }
    }

    @Override
    public boolean shouldBlocksBeConsideredEqual(final BlockState state1, final BlockState state2)
    {
        final Block block1 = state1.getBlock();
        final Block block2 = state2.getBlock();

        if (block1 == Blocks.FLOWER_POT || block2 == Blocks.FLOWER_POT)
        {
            return block1 == block2;
        }
        if (block1 == Blocks.GRASS_BLOCK && block2 == Blocks.DIRT || block2 == Blocks.GRASS_BLOCK && block1 == Blocks.DIRT)
        {
            return true;
        }
        // A rack, chest or hut block already in place stays as it is: its contents differ from the plan's, so Structurize
        // would break it and put a fresh one, spilling what it held on the ground (every warehouse upgrade did)
        if (block1 == block2 && state1.hasBlockEntity())
        {
            return true;
        }
        return super.shouldBlocksBeConsideredEqual(state1, state2);
    }

    @Override
    public boolean isStackFree(@Nullable final ItemStack itemStack)
    {
        return itemStack == null
                 || itemStack.isEmpty()
                 || itemStack.is(ItemTags.LEAVES)
                 || itemStack.getItem() == ModBlocks.blockDecorationPlaceholder.asItem();
    }

    @Override
    public void onCompletion()
    {
        super.onCompletion();
        site.onPlacementComplete();
    }
}
