package fr.kodistan.interregne.economy;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.politics.ThroneBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The imperial currency: six tiers, each worth ten of the one below, from the single Couronne
 * to the Trésor du dragon (100 000).
 */
public final class Couronnes
{
    public static final DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(InterregneMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InterregneMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InterregneMod.MODID);

    public static final DeferredItem<CoinItem> COURONNE         = tier("couronne", 1, Rarity.COMMON, false);
    public static final DeferredItem<CoinItem> BOURSE           = tier("bourse", 10, Rarity.COMMON, false);
    public static final DeferredItem<CoinItem> LINGOT           = tier("lingot_imperial", 100, Rarity.UNCOMMON, false);
    public static final DeferredItem<CoinItem> SAC              = tier("sac_de_couronnes", 1_000, Rarity.RARE, false);
    public static final DeferredItem<CoinItem> COFFRET          = tier("coffret_imperial", 10_000, Rarity.EPIC, true);
    public static final DeferredItem<CoinItem> TRESOR_DU_DRAGON = tier("tresor_du_dragon", 100_000, Rarity.EPIC, true);

    /**
     * A counter of the Imperial Bank, placed by the Intendants in the citadel.
     */
    public static final DeferredBlock<BankCounterBlock> GUICHET = BLOCKS.register("guichet_imperial",
      () -> new BankCounterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F).sound(SoundType.WOOD).noLootTable()));
    public static final DeferredItem<BlockItem> GUICHET_ITEM = ITEMS.registerSimpleBlockItem("guichet_imperial", GUICHET);

    /**
     * The Trône impérial, placed by the admins in the palace.
     */
    public static final DeferredBlock<ThroneBlock> TRONE = BLOCKS.register("trone_imperial",
      () -> new ThroneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F).sound(SoundType.STONE).noLootTable()));
    public static final DeferredItem<BlockItem> TRONE_ITEM = ITEMS.registerSimpleBlockItem("trone_imperial", TRONE);

    /**
     * Highest value first, for making change.
     */
    public static final List<DeferredItem<CoinItem>> TIERS_DESCENDING = List.of(TRESOR_DU_DRAGON, COFFRET, SAC, LINGOT, BOURSE, COURONNE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("interregne", () -> CreativeModeTab.builder()
      .title(Component.literal("L'Interrègne"))
      .icon(() -> COURONNE.get().getDefaultInstance())
      .displayItems((parameters, output) -> {
          TIERS_DESCENDING.reversed().forEach(tier -> output.accept(tier.get()));
          output.accept(GUICHET_ITEM.get());
          output.accept(TRONE_ITEM.get());
          output.accept(RegionalBanks.BANK_ITEM.get());
      })
      .build());

    private Couronnes()
    {
    }

    public static void register(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /**
     * Value of the tier just below this one (1 for a Couronne, which cannot be broken).
     */
    public static long lowerTier(final long value)
    {
        return Math.max(1, value / 10);
    }

    private static DeferredItem<CoinItem> tier(final String name, final long value, final Rarity rarity, final boolean shiny)
    {
        return ITEMS.register(name, () -> new CoinItem(value, shiny, new Item.Properties().rarity(rarity)));
    }
}
