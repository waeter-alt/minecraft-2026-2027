package fr.kodistan.interregne.war;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The troops' own voices (assets/interregne/sounds.json, files under sounds/troops/): men's grunts and war cries
 * for the légionnaire, women's grunts, efforts and screams for the archer. They replace MineColonies' generic
 * barbarian voice lines; sources and licences are in docs/credits-sons.md.
 */
public final class WarSounds
{
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, InterregneMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LEGIONNAIRE_HURT = register("legionnaire.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGIONNAIRE_ATTACK = register("legionnaire.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGIONNAIRE_DEATH = register("legionnaire.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHER_HURT = register("archer.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHER_SHOOT = register("archer.shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHER_DEATH = register("archer.death");

    private static DeferredHolder<SoundEvent, SoundEvent> register(final String id)
    {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, id)));
    }

    public static void register(final IEventBus modBus)
    {
        SOUNDS.register(modBus);
    }

    private WarSounds()
    {
    }
}
