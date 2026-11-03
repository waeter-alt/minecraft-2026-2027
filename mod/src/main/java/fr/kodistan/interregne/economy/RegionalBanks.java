package fr.kodistan.interregne.economy;

import fr.kodistan.interregne.InterregneMod;
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

    public static final DeferredBlock<RegionalBankBlock> BANK = BLOCKS.register("banque_regionale",
      () -> new RegionalBankBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(5.0F, 1200.0F).sound(SoundType.METAL)
                                    .pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> BANK_ITEM = ITEMS.registerSimpleBlockItem("banque_regionale", BANK);

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
    }
}
