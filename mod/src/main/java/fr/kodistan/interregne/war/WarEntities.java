package fr.kodistan.interregne.war;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
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
 * War troops. The slice spawns one melee unit, the légionnaire: it rides on MineColonies' raider base, so guards,
 * citizens and threat tables already know how to fight it (CombatUtils' raider branch, guard patrol calls).
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

    /** Same attributes as MineColonies' raiders; per-assault scaling happens at spawn (setMobAttributes). */
    @SubscribeEvent
    public static void createEntityAttributes(final EntityAttributeCreationEvent event)
    {
        event.put(LEGIONNAIRE.get(), AbstractEntityMinecoloniesRaider.getDefaultAttributes().build());
    }

    public static void register(final IEventBus modEventBus)
    {
        ENTITIES.register(modEventBus);
    }

    private WarEntities()
    {
    }
}
