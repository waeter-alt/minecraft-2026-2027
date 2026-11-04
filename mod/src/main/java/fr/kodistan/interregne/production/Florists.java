package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFlorist;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A florist yields flowers on its own, picked among those its level allows and the player did not exclude. Its flower
 * beds are decoration: each one bears a flower at once, never picked, and neither bed nor flower can be broken.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Florists
{
    private Florists()
    {
    }

    public static void produce(final IBuilding building, final int amount)
    {
        if (!(building instanceof BuildingFlorist florist))
        {
            return;
        }
        for (int i = 0; i < amount; i++)
        {
            Huts.give(building, flower(florist));
        }
        if (building.getColony().getWorld() instanceof ServerLevel level)
        {
            bloom(level, building);
        }
    }

    /**
     * A flower on every empty bed.
     */
    public static void bloom(final ServerLevel level, final IBuilding building)
    {
        if (!(building instanceof BuildingFlorist florist))
        {
            return;
        }
        for (final BlockPos bed : florist.getPlantGround())
        {
            final BlockPos above = bed.above();
            if (!level.isLoaded(bed) || !level.getBlockState(above).isAir())
            {
                continue;
            }
            if (flower(florist).getItem() instanceof BlockItem item && !(item.getBlock() instanceof DoublePlantBlock))
            {
                level.setBlock(above, item.getBlock().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static ItemStack flower(final BuildingFlorist florist)
    {
        final ItemStack flower = florist.getFlowerToGrow();
        return flower == null || flower.isEmpty() ? new ItemStack(Items.POPPY) : flower.copyWithCount(1);
    }

    /**
     * Beds and their flowers cannot be broken (except in creative).
     */
    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event)
    {
        if (!event.getPlayer().isCreative() && inBed(event.getLevel(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    private static boolean inBed(final LevelAccessor accessor, final BlockPos pos)
    {
        if (!(accessor instanceof Level level))
        {
            return false;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            return false;
        }
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingFlorist florist && (florist.getPlantGround().contains(pos) || florist.getPlantGround().contains(pos.below())))
            {
                return true;
            }
        }
        return false;
    }
}
