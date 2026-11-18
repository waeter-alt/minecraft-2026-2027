package fr.kodistan.interregne.war;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tools for the war troops: one spawn egg per troop and the level wand (see {@link LevelWandItem}),
 * gathered in their own creative tab.
 */
public final class WarItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InterregneMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InterregneMod.MODID);

    /** Red and steel for the légionnaire, green and leather-red hair for the archer: same colours as their models. */
    public static final DeferredItem<DeferredSpawnEggItem> LEGIONNAIRE_EGG = ITEMS.register("legionnaire_spawn_egg",
      () -> new DeferredSpawnEggItem(WarEntities.LEGIONNAIRE, 0xA02A28, 0xA9B0B8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ARCHER_EGG = ITEMS.register("archer_spawn_egg",
      () -> new DeferredSpawnEggItem(WarEntities.ARCHER, 0x3E6A3A, 0x7A3C22, new Item.Properties()));
    public static final DeferredItem<LevelWandItem> LEVEL_WAND = ITEMS.register("baguette_de_niveau",
      () -> new LevelWandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("troupes", () -> CreativeModeTab.builder()
      .title(Component.literal("La Couronne Vacante : troupes"))
      .icon(() -> LEVEL_WAND.get().getDefaultInstance())
      .displayItems((parameters, output) ->
      {
          output.accept(LEGIONNAIRE_EGG.get());
          output.accept(ARCHER_EGG.get());
          output.accept(LEVEL_WAND.get());
      })
      .build());

    public static void register(final IEventBus modBus)
    {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private WarItems()
    {
    }
}
