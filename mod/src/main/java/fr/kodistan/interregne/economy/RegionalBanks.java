package fr.kodistan.interregne.economy;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks of the regional banks: the bank itself, placed by a colony's members, and the hoard it lays around itself.
 */
public final class RegionalBanks
{
    public static final DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(InterregneMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InterregneMod.MODID);

    public static final String BANK_ID = "banque_regionale";

    public static final DeferredRegister<BuildingEntry> BUILDINGS = DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, InterregneMod.MODID);

    public static final DeferredBlock<RegionalBankBlock> BANK = BLOCKS.register(BANK_ID, RegionalBankBlock::new);
    public static final DeferredItem<Item> BANK_ITEM = ITEMS.register(BANK_ID, () -> new ItemBlockHut(BANK.get(), new Item.Properties()));

    public static final DeferredHolder<BuildingEntry, BuildingEntry> BANK_ENTRY = BUILDINGS.register(BANK_ID, () -> new BuildingEntry.Builder()
      .setBuildingBlock(BANK.get())
      .setBuildingProducer(BuildingBanqueRegionale::new)
      .setBuildingViewProducer(() -> BuildingBanqueRegionale.View::new)
      .setRegistryName(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, BANK_ID))
      .createBuildingEntry());

    /**
     * Decoration only: no item, no loot table, cannot be pushed or pulled.
     */
    public static final DeferredBlock<VaultHoardBlock> HOARD = BLOCKS.register("tresor_de_la_banque",
      () -> new VaultHoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.0F, 1200.0F).sound(SoundType.CHAIN)
                                  .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

    private RegionalBanks()
    {
    }

    public static void register(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BUILDINGS.register(modBus);
        modBus.addListener(RegionalBanks::addHutToBuildingEntity);
    }

    /**
     * MineColonies builds the hut block entity type from its own list of huts: ours must be declared valid, or its
     * block entity is dropped on load.
     */
    private static void addHutToBuildingEntity(final BlockEntityTypeAddBlocksEvent event)
    {
        event.modify(MinecoloniesTileEntities.BUILDING.get(), BANK.get());
    }
}
