package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of the Poste commercial, the test fixture for the route planner ({@link RoutePlanning}) (see "Avant de coder" in CLAUDE.md:
 * this is deliberately the smallest thing that lets the algorithm be tried in game before anything is wired to
 * contracts, caravans or colonies).
 */
public final class Routes
{
    public static final DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(InterregneMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InterregneMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InterregneMod.MODID);

    public static final DeferredBlock<PosteCommercialBlock> POSTE_COMMERCIAL = BLOCKS.register("poste_commercial",
      () -> new PosteCommercialBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 4.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> POSTE_COMMERCIAL_ITEM = ITEMS.registerSimpleBlockItem("poste_commercial", POSTE_COMMERCIAL);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("routes", () -> CreativeModeTab.builder()
      .title(Component.literal("La Couronne Vacante : routes (test)"))
      .icon(() -> POSTE_COMMERCIAL_ITEM.get().getDefaultInstance())
      .displayItems((parameters, output) -> output.accept(POSTE_COMMERCIAL_ITEM.get()))
      .build());

    private Routes()
    {
    }

    public static void register(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
