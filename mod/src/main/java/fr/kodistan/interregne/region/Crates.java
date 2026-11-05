package fr.kodistan.interregne.region;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of the regional crates, the Comptoir régional and the Marché impérial.
 */
public final class Crates
{
    public static final DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(InterregneMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InterregneMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InterregneMod.MODID);

    /**
     * Crates stack to this many: bulky goods.
     */
    public static final int STACK_SIZE = 16;

    private static final Map<Crate, DeferredItem<CrateItem>> CRATE_ITEMS = new EnumMap<>(Crate.class);

    static
    {
        for (final Crate crate : Crate.values())
        {
            final DeferredBlock<Block> block = BLOCKS.register("caisse_" + crate.id(),
              () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 3.0F).sound(SoundType.WOOD)));
            CRATE_ITEMS.put(crate, ITEMS.register("caisse_" + crate.id(), () -> new CrateItem(crate, block.get(), new Item.Properties().stacksTo(STACK_SIZE))));
        }
    }

    /**
     * One per colony, placed by its members; produces the colony's crate.
     */
    public static final DeferredBlock<ComptoirBlock> COMPTOIR = BLOCKS.register("comptoir_regional",
      () -> new ComptoirBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F, 6.0F).sound(SoundType.WOOD)));
    public static final DeferredItem<BlockItem> COMPTOIR_ITEM = ITEMS.registerSimpleBlockItem("comptoir_regional", COMPTOIR);

    /**
     * Placed by the Intendants in the imperial city; unbreakable, like the bank counter.
     */
    public static final DeferredBlock<MarketBlock> MARCHE = BLOCKS.register("marche_imperial",
      () -> new MarketBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F).sound(SoundType.WOOD).noLootTable()));
    public static final DeferredItem<BlockItem> MARCHE_ITEM = ITEMS.registerSimpleBlockItem("marche_imperial", MARCHE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("regions", () -> CreativeModeTab.builder()
      .title(Component.literal("La Couronne Vacante : régions"))
      .icon(() -> CRATE_ITEMS.get(Crate.BOIS).get().getDefaultInstance())
      .displayItems((parameters, output) -> {
          CRATE_ITEMS.values().forEach(item -> output.accept(item.get()));
          output.accept(COMPTOIR_ITEM.get());
          output.accept(MARCHE_ITEM.get());
      })
      .build());

    private Crates()
    {
    }

    public static void register(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    public static Item item(final Crate crate)
    {
        return CRATE_ITEMS.get(crate).get();
    }
}
