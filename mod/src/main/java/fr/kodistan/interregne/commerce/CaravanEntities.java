package fr.kodistan.interregne.commerce;

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
 * The people of a caravan: the driver who walks beside the mule, and the soldiers of its escort. Neither is saved with
 * the chunk: the contract owns them and spawns them again when a player comes near.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class CaravanEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, InterregneMod.MODID);

    public static final Supplier<EntityType<EntConducteur>> CONDUCTEUR = ENTITIES.register("conducteur",
      () -> EntityType.Builder.of(EntConducteur::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10).noSave().build("conducteur"));

    public static final Supplier<EntityType<EntEscorte>> ESCORTE = ENTITIES.register("escorte",
      () -> EntityType.Builder.of(EntEscorte::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10).noSave().build("escorte"));

    private CaravanEntities()
    {
    }

    public static void register(final IEventBus modBus)
    {
        ENTITIES.register(modBus);
    }

    @SubscribeEvent
    public static void attributes(final EntityAttributeCreationEvent event)
    {
        event.put(CONDUCTEUR.get(), EntConducteur.attributes().build());
        event.put(ESCORTE.get(), EntEscorte.attributes().build());
    }
}
