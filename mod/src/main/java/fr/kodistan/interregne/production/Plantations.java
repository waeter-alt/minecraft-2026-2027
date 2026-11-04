package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A plantation yields sugar cane, cactus and bamboo on its own from the start, like a farm its crop. Its fields, those
 * of the hut and the plantation field blocks, are decoration: grown at once, never harvested, unbreakable. Each
 * plantation field block adds its plant to the colony's plantations.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Plantations
{
    /**
     * What every plantation grows on its own.
     */
    private static final List<Item> OWN = List.of(Items.SUGAR_CANE, Items.CACTUS, Items.BAMBOO);

    /**
     * How far above or below its working spot a field's plant reaches.
     */
    private static final int REACH = 6;

    private Plantations()
    {
    }

    /**
     * One production cycle: the hut's own plants by level, shared out, plus its share of the colony's field blocks.
     */
    public static void produce(final IColony colony, final IBuilding plantation)
    {
        final int amount = plantation.getBuildingLevel() * Config.PRODUCTION_PER_LEVEL.get();
        for (int i = 0; i < OWN.size(); i++)
        {
            final int share = amount / OWN.size() + (i < amount % OWN.size() ? 1 : 0);
            Huts.give(plantation, new ItemStack(OWN.get(i), share));
        }

        final List<IBuilding> plantations = plantationsOf(colony);
        final long staffed = Math.max(1, plantations.stream().filter(Huts::staffed).count());
        for (final PlantationField field : fieldsOf(colony))
        {
            if (plantations.stream().anyMatch(hut -> hut.getPosition().equals(field.getPosition())) || field.getModule() == null)
            {
                continue;
            }
            Huts.give(plantation, new ItemStack(field.getModule().getItem(), (int) Math.ceil(Config.FIELD_BONUS.get() / (double) staffed)));
        }

        if (colony.getWorld() instanceof ServerLevel level)
        {
            growFields(level, colony);
        }
    }

    /**
     * Grow every field of the colony to full size, for show: new fields fill in, broken plants come back.
     */
    public static void growFields(final ServerLevel level, final IColony colony)
    {
        for (final PlantationField field : fieldsOf(colony))
        {
            if (field.getModule() == null)
            {
                continue;
            }
            final Item item = field.getModule().getItem();
            for (final BlockPos spot : field.getWorkingPositions())
            {
                if (level.isLoaded(spot))
                {
                    grow(level, spot, item);
                }
            }
        }
    }

    private static void grow(final ServerLevel level, final BlockPos spot, final Item item)
    {
        if (item == Items.SUGAR_CANE)
        {
            column(level, spot, Blocks.SUGAR_CANE.defaultBlockState(), 3);
        }
        else if (item == Items.CACTUS)
        {
            column(level, spot, Blocks.CACTUS.defaultBlockState(), 3);
        }
        else if (item == Items.BAMBOO)
        {
            column(level, spot, Blocks.BAMBOO.defaultBlockState(), 6);
        }
        else if (item == Items.KELP)
        {
            underwater(level, spot.above(), Blocks.KELP_PLANT.defaultBlockState());
        }
        else if (item == Items.SEAGRASS)
        {
            underwater(level, spot.above(), Blocks.SEAGRASS.defaultBlockState());
        }
        else if (item == Items.SEA_PICKLE)
        {
            underwater(level, spot.above(), Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 4));
        }
        else if (item == Items.GLOW_BERRIES)
        {
            hanging(level, spot, Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, true));
        }
        else if (item == Items.WEEPING_VINES)
        {
            hanging(level, spot, Blocks.WEEPING_VINES.defaultBlockState());
        }
        else if (item instanceof BlockItem blockItem)
        {
            // Fungi, twisting vines and the like: one plant on the spot, if it can live there
            final BlockState state = blockItem.getBlock().defaultBlockState();
            if (level.getBlockState(spot.above()).isAir() && state.canSurvive(level, spot.above()))
            {
                level.setBlock(spot.above(), state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Stack a plant up from the spot, without neighbour updates so the scenery stays as placed.
     */
    private static void column(final ServerLevel level, final BlockPos spot, final BlockState plant, final int height)
    {
        for (int dy = 1; dy <= height; dy++)
        {
            final BlockPos pos = spot.above(dy);
            final BlockState there = level.getBlockState(pos);
            if (there.is(plant.getBlock()))
            {
                continue;
            }
            if (!there.isAir())
            {
                return;
            }
            level.setBlock(pos, plant, Block.UPDATE_CLIENTS);
        }
    }

    private static void underwater(final ServerLevel level, final BlockPos pos, final BlockState plant)
    {
        if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).getType() == Fluids.WATER)
        {
            level.setBlock(pos, plant, Block.UPDATE_CLIENTS);
        }
    }

    private static void hanging(final ServerLevel level, final BlockPos spot, final BlockState plant)
    {
        for (int dy = 1; dy <= 3; dy++)
        {
            final BlockPos pos = spot.below(dy);
            final BlockState there = level.getBlockState(pos);
            if (there.is(plant.getBlock()))
            {
                continue;
            }
            if (!there.isAir())
            {
                return;
            }
            level.setBlock(pos, plant, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Plants and soil of a field cannot be broken (except in creative).
     */
    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event)
    {
        if (!event.getPlayer().isCreative() && inField(event.getLevel(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    private static boolean inField(final LevelAccessor accessor, final BlockPos pos)
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
        for (final PlantationField field : fieldsOf(colony))
        {
            for (final BlockPos spot : field.getWorkingPositions())
            {
                if (spot.getX() == pos.getX() && spot.getZ() == pos.getZ() && Math.abs(spot.getY() - pos.getY()) <= REACH)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<IBuilding> plantationsOf(final IColony colony)
    {
        return colony.getBuildingManager().getBuildings().values().stream().filter(building -> Huts.PLANTATION.equals(Huts.id(building))).toList();
    }

    private static List<PlantationField> fieldsOf(final IColony colony)
    {
        final List<PlantationField> fields = new ArrayList<>();
        for (final IBuildingExtension extension : colony.getBuildingManager().getBuildingExtensions(extension -> extension instanceof PlantationField))
        {
            fields.add((PlantationField) extension);
        }
        fields.removeIf(Objects::isNull);
        return fields;
    }
}
