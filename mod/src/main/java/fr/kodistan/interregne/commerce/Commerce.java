package fr.kodistan.interregne.commerce;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of the Relais commercial, the building where colonies make trade contracts.
 */
public final class Commerce
{
    public static final String RELAIS_ID = "relais_commercial";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InterregneMod.MODID);
    public static final DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(InterregneMod.MODID);
    public static final DeferredRegister<BuildingEntry> BUILDINGS = DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, InterregneMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InterregneMod.MODID);

    public static final DeferredBlock<RelaisCommercialBlock> RELAIS_BLOCK = BLOCKS.register(RELAIS_ID, RelaisCommercialBlock::new);
    public static final DeferredItem<Item> RELAIS_ITEM = ITEMS.register(RELAIS_ID, () -> new ItemBlockHut(RELAIS_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BuildingEntry, BuildingEntry> RELAIS_ENTRY = BUILDINGS.register(RELAIS_ID, () -> new BuildingEntry.Builder()
      .setBuildingBlock(RELAIS_BLOCK.get())
      .setBuildingProducer(BuildingRelaisCommercial::new)
      .setBuildingViewProducer(() -> BuildingRelaisCommercial.View::new)
      .setRegistryName(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, RELAIS_ID))
      .createBuildingEntry());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("commerce", () -> CreativeModeTab.builder()
      .title(Component.literal("La Couronne Vacante : commerce"))
      .icon(() -> RELAIS_ITEM.get().getDefaultInstance())
      .displayItems((parameters, output) -> output.accept(RELAIS_ITEM.get()))
      .build());

    private Commerce()
    {
    }

    public static void register(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BUILDINGS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(Commerce::addHutToBuildingEntity);
    }

    /**
     * MineColonies builds the hut block entity type from its own list of huts: ours must be declared valid, or its
     * block entity is dropped on load.
     */
    private static void addHutToBuildingEntity(final BlockEntityTypeAddBlocksEvent event)
    {
        event.modify(com.minecolonies.api.tileentities.MinecoloniesTileEntities.BUILDING.get(), RELAIS_BLOCK.get());
    }
}
