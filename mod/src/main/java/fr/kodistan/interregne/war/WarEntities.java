package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.colonyEvents.registry.ColonyEventTypeRegistryEntry;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * War troops: the melee légionnaire and the ranged archer. Both ride on MineColonies' raider base, so guards,
 * citizens and threat tables already know how to fight them (CombatUtils' raider branch, guard patrol calls).
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class WarEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, InterregneMod.MODID);

    public static final Supplier<EntityType<EntLegionnaire>> LEGIONNAIRE = ENTITIES.register("legionnaire",
      () -> EntityType.Builder.of(EntLegionnaire::new, MobCategory.MONSTER)
             .sized(0.6F, 1.95F)
             .clientTrackingRange(64)
             .build("legionnaire"));

    public static final Supplier<EntityType<EntArcher>> ARCHER = ENTITIES.register("archer",
      () -> EntityType.Builder.of(EntArcher::new, MobCategory.MONSTER)
             .sized(0.6F, 1.95F)
             .clientTrackingRange(64)
             .build("archer"));

    /** The assault's colony event type: MineColonies saves running events and crashes the colony load on an unknown type. */
    public static final DeferredRegister<ColonyEventTypeRegistryEntry> EVENT_TYPES =
      DeferredRegister.create(CommonMinecoloniesAPIImpl.COLONY_EVENT_TYPES, AssautEvent.TYPE_ID.getNamespace());

    public static final Supplier<ColonyEventTypeRegistryEntry> ASSAUT_EVENT = EVENT_TYPES.register(AssautEvent.TYPE_ID.getPath(),
      () -> new ColonyEventTypeRegistryEntry(AssautEvent::loadFinished, AssautEvent.TYPE_ID));

    /** Same attributes as MineColonies' raiders; per-assault scaling happens at spawn (setMobAttributes). */
    @SubscribeEvent
    public static void createEntityAttributes(final EntityAttributeCreationEvent event)
    {
        event.put(LEGIONNAIRE.get(), AbstractEntityMinecoloniesRaider.getDefaultAttributes().build());
        event.put(ARCHER.get(), AbstractEntityMinecoloniesRaider.getDefaultAttributes().build());
    }

    public static void register(final IEventBus modEventBus)
    {
        ENTITIES.register(modEventBus);
        EVENT_TYPES.register(modEventBus);
    }

    private WarEntities()
    {
    }
}
